package io.github.hugrlab.acl.jdbc;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A person at a browser, for the end-to-end tests: opens the page the driver asks for, fills
 * Keycloak's login form with the test user, confirms a device code or a consent screen when one is
 * shown, and follows the redirects back - to the driver's loopback for the browser sign-in.
 */
final class KeycloakBrowser implements Interaction {
	private static final Pattern FORM = Pattern.compile("<form[^>]*action=\"([^\"]+)\"[^>]*>(.*?)</form>",
	    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	private static final Pattern INPUT = Pattern.compile("<input[^>]*>", Pattern.CASE_INSENSITIVE);
	private static final Pattern ATTR = Pattern.compile("(\\w[\\w-]*)=\"([^\"]*)\"");

	private final String user;
	private final String password;
	// cookies by hand: Keycloak marks its session cookies Secure even over http, which a browser still
	// sends to localhost (a secure context) and java.net.CookieManager does not
	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
	private final Map<String, String> cookies = new java.util.concurrent.ConcurrentHashMap<>();
	volatile int pagesSubmitted;

	KeycloakBrowser(String user, String password) {
		this.user = user;
		this.password = password;
	}

	@Override
	public boolean browserAvailable() {
		return true;
	}

	@Override
	public void openBrowser(URI uri) throws IOException {
		walk(get(uri));
	}

	@Override
	public AutoCloseable showDeviceCode(String userCode, String verificationUri, String verificationUriComplete) {
		Thread person = new Thread(() -> {
			try {
				walk(get(URI.create(verificationUriComplete != null ? verificationUriComplete : verificationUri)));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		person.start();
		return () -> person.join(10_000);
	}

	private void walk(HttpResponse<String> page) throws IOException {
		for (int step = 0; step < 6 && page != null; step++) {
			Matcher form = FORM.matcher(page.body());
			if (!form.find()) {
				return; // a page with nothing to submit: signed in, or the loopback's own answer
			}
			String action = form.group(1).replace("&amp;", "&");
			Map<String, String> fields = new LinkedHashMap<>();
			boolean login = false;
			Matcher input = INPUT.matcher(form.group(2));
			while (input.find()) {
				Map<String, String> attrs = attrs(input.group());
				String name = attrs.get("name");
				if (name == null) {
					continue;
				}
				String type = attrs.getOrDefault("type", "text");
				if (name.equals("username")) {
					fields.put(name, user);
					login = true;
				} else if (name.equals("password")) {
					fields.put(name, password);
				} else if (type.equals("submit")) {
					if (name.equals("accept") || !fields.containsKey("accept")) {
						fields.put(name, attrs.getOrDefault("value", ""));
					}
				} else {
					fields.put(name, attrs.getOrDefault("value", ""));
				}
			}
			if (fields.containsKey("cancel") && fields.containsKey("accept")) {
				fields.remove("cancel");
			}
			pagesSubmitted++;
			URI target = page.uri().resolve(action);
			page = post(target, fields);
			if (login && page.body().contains("Invalid username or password")) {
				throw new IOException("Keycloak refused the test user's password");
			}
		}
	}

	private static Map<String, String> attrs(String tag) {
		Map<String, String> out = new LinkedHashMap<>();
		Matcher m = ATTR.matcher(tag);
		while (m.find()) {
			out.put(m.group(1).toLowerCase(), m.group(2));
		}
		return out;
	}

	private HttpResponse<String> get(URI uri) throws IOException {
		return send(HttpRequest.newBuilder(uri).GET().build());
	}

	private HttpResponse<String> post(URI uri, Map<String, String> fields) throws IOException {
		StringJoiner body = new StringJoiner("&");
		fields.forEach((k, v) -> body.add(Http.encode(k) + "=" + Http.encode(v)));
		return send(HttpRequest.newBuilder(uri)
		                .header("Content-Type", "application/x-www-form-urlencoded")
		                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
		                .build());
	}

	private HttpResponse<String> send(HttpRequest request) throws IOException {
		try {
			for (int hop = 0; hop < 10; hop++) {
				HttpRequest.Builder withCookies = HttpRequest.newBuilder(request, (k, v) -> true);
				if (!cookies.isEmpty()) {
					StringJoiner header = new StringJoiner("; ");
					cookies.forEach((k, v) -> header.add(k + "=" + v));
					withCookies.setHeader("Cookie", header.toString());
				}
				HttpResponse<String> response = http.send(withCookies.build(), HttpResponse.BodyHandlers.ofString());
				for (String set : response.headers().allValues("set-cookie")) {
					String pair = set.split(";", 2)[0];
					int eq = pair.indexOf('=');
					if (eq > 0) {
						cookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
					}
				}
				String location = response.headers().firstValue("location").orElse(null);
				if (response.statusCode() / 100 != 3 || location == null) {
					return response;
				}
				request = HttpRequest.newBuilder(response.uri().resolve(location)).GET().build();
			}
			throw new IOException("too many redirects");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}
	}
}
