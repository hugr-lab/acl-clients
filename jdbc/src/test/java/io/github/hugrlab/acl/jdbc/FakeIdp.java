package io.github.hugrlab.acl.jdbc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A small OIDC provider for the tests: discovery, authorize (redirects straight back with a code,
 * as a signed-in browser would), the device endpoint, and the token endpoint for the password,
 * refresh, device and authorization-code grants. PKCE is checked for real. Every grant is recorded.
 */
final class FakeIdp implements AutoCloseable {
	static final String CLIENT = "acl-desktop";

	final HttpServer server;
	final String issuer;
	final List<String> grants = Collections.synchronizedList(new ArrayList<>());
	final AtomicInteger minted = new AtomicInteger();
	final Map<String, String> codeChallenges = new ConcurrentHashMap<>();
	final Map<String, String> codeRedirects = new ConcurrentHashMap<>();

	/** Seconds an access token lives; tests set it to 0 to force the refresh path. */
	volatile long accessLifetime = 300;
	/** The device grant answers {@code authorization_pending} this many times before approving. */
	volatile int devicePending = 1;
	/** When set, the device grant answers this error instead of approving. */
	volatile String deviceError;
	/** When set, authorize redirects back with this error instead of a code. */
	volatile String authorizeError;
	/** Refresh tokens the IdP has revoked (a session ended there). */
	volatile boolean refuseRefresh;
	final AtomicInteger devicePolls = new AtomicInteger();
	volatile String deviceChallenge;

	FakeIdp() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/test";
		server.createContext("/realms/test/.well-known/openid-configuration",
		    ex -> json(ex, 200, "{\"issuer\":\"" + issuer + "\",\"authorization_endpoint\":\"" + issuer + "/auth\","
		                            + "\"token_endpoint\":\"" + issuer + "/token\",\"device_authorization_endpoint\":\""
		                            + issuer + "/device\"}"));
		server.createContext("/realms/test/auth", this::authorize);
		server.createContext("/realms/test/device", ex -> {
			Map<String, String> form = form(ex);
			if (!CLIENT.equals(form.get("client_id"))) {
				json(ex, 400, "{\"error\":\"invalid_client\"}");
				return;
			}
			if (!"S256".equals(form.get("code_challenge_method")) || form.get("code_challenge") == null) {
				json(ex, 400, "{\"error\":\"invalid_request\",\"error_description\":\"Missing parameter: code_challenge_method\"}");
				return;
			}
			deviceChallenge = form.get("code_challenge");
			json(ex, 200, "{\"device_code\":\"dev-1\",\"user_code\":\"ABCD-EFGH\",\"verification_uri\":\"" + issuer
			                  + "/device-page\",\"verification_uri_complete\":\"" + issuer
			                  + "/device-page?code=ABCD-EFGH\",\"expires_in\":60,\"interval\":0}");
		});
		server.createContext("/realms/test/token", this::token);
		server.start();
	}

	private void authorize(HttpExchange ex) throws IOException {
		Map<String, String> q = Flows.query(ex.getRequestURI().getRawQuery());
		String redirect = q.get("redirect_uri");
		String target;
		if (authorizeError != null) {
			target = redirect + "?error=" + authorizeError + "&state=" + q.get("state");
		} else if (!"S256".equals(q.get("code_challenge_method")) || q.get("code_challenge") == null
		           || !"code".equals(q.get("response_type")) || !CLIENT.equals(q.get("client_id"))) {
			target = redirect + "?error=invalid_request&state=" + q.get("state");
		} else {
			String code = "code-" + minted.incrementAndGet();
			codeChallenges.put(code, q.get("code_challenge"));
			codeRedirects.put(code, redirect);
			target = redirect + "?code=" + code + "&state=" + q.get("state");
		}
		ex.getResponseHeaders().set("Location", target);
		ex.sendResponseHeaders(302, -1);
		ex.close();
	}

	private void token(HttpExchange ex) throws IOException {
		Map<String, String> form = form(ex);
		String grant = form.get("grant_type");
		if (!CLIENT.equals(form.get("client_id"))) {
			json(ex, 401, "{\"error\":\"invalid_client\"}");
			return;
		}
		switch (grant) {
		case "password":
			grants.add("password:" + form.get("username"));
			if ("secret".equals(form.get("password"))) {
				issue(ex);
			} else {
				json(ex, 401, "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid user credentials\"}");
			}
			return;
		case "refresh_token":
			grants.add("refresh");
			if (refuseRefresh || form.get("refresh_token") == null || !form.get("refresh_token").startsWith("rt-")) {
				json(ex, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"Session not active\"}");
			} else {
				// a refresh answer without a new refresh token: the client keeps the old one
				json(ex, 200, "{\"access_token\":\"at-" + minted.incrementAndGet() + "\",\"expires_in\":"
				                  + accessLifetime + "}");
			}
			return;
		case "urn:ietf:params:oauth:grant-type:device_code":
			devicePolls.incrementAndGet();
			if (deviceChallenge == null || !deviceChallenge.equals(Flows.challenge(form.get("code_verifier")))) {
				json(ex, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"PKCE verification failed\"}");
			} else if (deviceError != null) {
				json(ex, 400, "{\"error\":\"" + deviceError + "\"}");
			} else if (devicePending-- > 0) {
				json(ex, 400, "{\"error\":\"authorization_pending\"}");
			} else {
				grants.add("device");
				issue(ex);
			}
			return;
		case "authorization_code":
			String code = form.get("code");
			String challenge = code == null ? null : codeChallenges.remove(code);
			if (challenge == null || !challenge.equals(Flows.challenge(form.get("code_verifier")))
			    || !form.get("redirect_uri").equals(codeRedirects.remove(code))) {
				json(ex, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"PKCE verification failed\"}");
				return;
			}
			grants.add("authcode");
			issue(ex);
			return;
		default:
			json(ex, 400, "{\"error\":\"unsupported_grant_type\"}");
		}
	}

	private void issue(HttpExchange ex) throws IOException {
		int n = minted.incrementAndGet();
		json(ex, 200, "{\"access_token\":\"at-" + n + "\",\"expires_in\":" + accessLifetime + ",\"refresh_token\":\"rt-"
		                  + n + "\",\"refresh_expires_in\":1800,\"token_type\":\"Bearer\"}");
	}

	private static Map<String, String> form(HttpExchange ex) throws IOException {
		return Flows.query(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
	}

	private static void json(HttpExchange ex, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type", "application/json");
		ex.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(bytes);
		}
	}

	Discovery discovery() {
		return config -> List.of(new DoorIssuer(issuer, CLIENT, issuer + "/token", issuer + "/device"));
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
