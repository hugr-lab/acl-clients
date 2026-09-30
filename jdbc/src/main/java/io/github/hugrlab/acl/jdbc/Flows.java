package io.github.hugrlab.acl.jdbc;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The four ways to a token, each against the issuer's own endpoints with a public client (no secret
 * lives in a desktop driver): authorization code + PKCE through a loopback redirect (RFC 8252), the
 * device authorization grant (RFC 8628), the resource-owner password grant, and refresh.
 */
final class Flows {
	private static final SecureRandom RANDOM = new SecureRandom();

	/** Added to the polling interval on {@code slow_down} (RFC 8628 §3.5); tests shorten it. */
	static long slowDownStepMillis = 5_000;

	private Flows() {
	}

	static long now() {
		return System.currentTimeMillis() / 1000;
	}

	static Tokens password(Oidc oidc, String clientId, String scope, String user, String password)
	    throws AuthException {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("grant_type", "password");
		form.put("client_id", clientId);
		form.put("username", user);
		form.put("password", password);
		form.put("scope", scope);
		return grant(oidc, form, "the password sign-in");
	}

	static Tokens refresh(Oidc oidc, String clientId, Tokens previous) throws AuthException {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("grant_type", "refresh_token");
		form.put("client_id", clientId);
		form.put("refresh_token", previous.refreshToken());
		return grant(oidc, form, "the token refresh").keepingRefresh(previous);
	}

	static Tokens device(Oidc oidc, String clientId, String scope, Interaction interaction, int timeoutSeconds)
	    throws AuthException {
		if (oidc.deviceEndpoint() == null) {
			throw new AuthException("the issuer " + oidc.issuer() + " offers no device sign-in "
			                        + "(no device_authorization_endpoint) - use flow=authcode or flow=password");
		}
		// PKCE on the device grant too: an IdP whose client demands it (Keycloak with S256 set) refuses
		// the request without it, and one that does not ignores the parameters
		String verifier = randomUrlSafe(32);
		Map<String, String> request = new LinkedHashMap<>();
		request.put("client_id", clientId);
		request.put("scope", scope);
		request.put("code_challenge", challenge(verifier));
		request.put("code_challenge_method", "S256");
		Http.Response start;
		try {
			start = Http.postForm(oidc.deviceEndpoint(), request);
		} catch (IOException e) {
			throw new AuthException("the device sign-in could not reach " + oidc.deviceEndpoint(), e);
		}
		if (!start.ok()) {
			throw new AuthException("the IdP refused the device sign-in: " + start.error());
		}
		String deviceCode = start.text("device_code");
		long intervalMillis = Math.max(0, start.number("interval", 5)) * 1000;
		long deadline = System.currentTimeMillis()
		    + Math.min(timeoutSeconds, start.number("expires_in", timeoutSeconds)) * 1000L;
		try (AutoCloseable shown = interaction.showDeviceCode(start.text("user_code"), start.text("verification_uri"),
		         start.text("verification_uri_complete"))) {
			Map<String, String> poll = new LinkedHashMap<>();
			poll.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
			poll.put("client_id", clientId);
			poll.put("device_code", deviceCode);
			poll.put("code_verifier", verifier);
			while (System.currentTimeMillis() < deadline) {
				Thread.sleep(intervalMillis);
				Http.Response answer = Http.postForm(oidc.tokenEndpoint(), poll);
				if (answer.ok()) {
					return accepted(answer, "the device sign-in");
				}
				String error = answer.text("error");
				if ("authorization_pending".equals(error)) {
					continue;
				}
				if ("slow_down".equals(error)) {
					intervalMillis += slowDownStepMillis;
					continue;
				}
				throw new AuthException("the device sign-in ended: " + answer.error());
			}
			throw new AuthException("the device sign-in was not completed within " + timeoutSeconds + " s");
		} catch (AuthException e) {
			throw e;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AuthException("the device sign-in was interrupted", e);
		} catch (Exception e) {
			throw new AuthException("the device sign-in failed: " + e.getMessage(), e);
		}
	}

	static Tokens authorizationCode(Oidc oidc, String clientId, String scope, Interaction interaction,
	                                int redirectPort, int timeoutSeconds) throws AuthException {
		if (oidc.authorizationEndpoint() == null) {
			throw new AuthException("the issuer " + oidc.issuer() + " offers no browser sign-in "
			                        + "(no authorization_endpoint) - use flow=device or flow=password");
		}
		String verifier = randomUrlSafe(32);
		String state = randomUrlSafe(16);
		CompletableFuture<Map<String, String>> callback = new CompletableFuture<>();
		HttpServer server;
		try {
			server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), redirectPort), 0);
		} catch (IOException e) {
			throw new AuthException("could not listen on 127.0.0.1:" + redirectPort + " for the sign-in redirect", e);
		}
		server.createContext("/callback", exchange -> {
			Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
			boolean ours = state.equals(query.get("state"));
			String page = !ours ? "This sign-in request is not the one duckdb-acl is waiting for."
			              : query.containsKey("error") ? "Sign-in failed: " + escape(query.get("error"))
			                                           : "Signed in. You can close this window.";
			byte[] body = ("<!doctype html><html><head><meta charset=\"utf-8\"><title>duckdb-acl</title></head>"
			               + "<body style=\"font-family:sans-serif;margin:3em\"><p>" + page + "</p></body></html>")
			                  .getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
			exchange.sendResponseHeaders(ours ? 200 : 400, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
			if (ours) {
				callback.complete(query);
			}
		});
		server.start();
		try {
			String redirect = "http://127.0.0.1:" + server.getAddress().getPort() + "/callback";
			String authorize = oidc.authorizationEndpoint() + (oidc.authorizationEndpoint().contains("?") ? "&" : "?")
			                   + "response_type=code&client_id=" + Http.encode(clientId) + "&redirect_uri="
			                   + Http.encode(redirect) + "&scope=" + Http.encode(scope) + "&state=" + state
			                   + "&code_challenge=" + challenge(verifier) + "&code_challenge_method=S256";
			interaction.openBrowser(URI.create(authorize));
			Map<String, String> answer = callback.get(timeoutSeconds, TimeUnit.SECONDS);
			if (answer.containsKey("error")) {
				String description = answer.get("error_description");
				throw new AuthException("the IdP refused the browser sign-in: " + answer.get("error")
				                        + (description != null ? ": " + description : ""));
			}
			Map<String, String> form = new LinkedHashMap<>();
			form.put("grant_type", "authorization_code");
			form.put("client_id", clientId);
			form.put("code", answer.get("code"));
			form.put("redirect_uri", redirect);
			form.put("code_verifier", verifier);
			return grant(oidc, form, "the browser sign-in");
		} catch (TimeoutException e) {
			throw new AuthException("the browser sign-in was not completed within " + timeoutSeconds + " s");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AuthException("the browser sign-in was interrupted", e);
		} catch (ExecutionException | IOException e) {
			throw new AuthException("the browser sign-in failed: " + e.getMessage(), e);
		} finally {
			server.stop(0);
		}
	}

	private static Tokens grant(Oidc oidc, Map<String, String> form, String what) throws AuthException {
		Http.Response answer;
		try {
			answer = Http.postForm(oidc.tokenEndpoint(), form);
		} catch (IOException e) {
			throw new AuthException(what + " could not reach " + oidc.tokenEndpoint(), e);
		}
		if (!answer.ok()) {
			throw new AuthException("the IdP refused " + what + ": " + answer.error());
		}
		return accepted(answer, what);
	}

	private static Tokens accepted(Http.Response answer, String what) throws AuthException {
		Tokens tokens = Tokens.from(answer, now());
		if (tokens.accessToken() == null) {
			throw new AuthException("the IdP answered " + what + " without an access token");
		}
		return tokens;
	}

	static String challenge(String verifier) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String randomUrlSafe(int bytes) {
		byte[] raw = new byte[bytes];
		RANDOM.nextBytes(raw);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
	}

	static Map<String, String> query(String raw) {
		Map<String, String> out = new HashMap<>();
		if (raw == null) {
			return out;
		}
		for (String pair : raw.split("&")) {
			int eq = pair.indexOf('=');
			if (eq > 0) {
				out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
				    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
			}
		}
		return out;
	}

	private static String escape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
