package io.github.hugrlab.acl.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

/** The IdP side of every flow: a GET of a JSON document and a form POST answered with JSON. */
final class Http {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient CLIENT = HttpClient.newBuilder()
	                                            .connectTimeout(Duration.ofSeconds(10))
	                                            .followRedirects(HttpClient.Redirect.NORMAL)
	                                            .build();

	/** A response: the status and its body as a JSON object (empty when the body is not one). */
	record Response(int status, Map<String, Object> body) {
		boolean ok() {
			return status / 100 == 2;
		}

		String text(String key) {
			Object value = body.get(key);
			return value == null ? null : value.toString();
		}

		long number(String key, long fallback) {
			Object value = body.get(key);
			if (value instanceof Number n) {
				return n.longValue();
			}
			try {
				return value == null ? fallback : Long.parseLong(value.toString());
			} catch (NumberFormatException e) {
				return fallback;
			}
		}

		@Override
		public String toString() {
			return "Response[" + status + "]"; // the body may hold tokens: never printed
		}

		/** The OAuth error of a refusal, as {@code error: description}. */
		String error() {
			String error = text("error");
			String description = text("error_description");
			if (error == null) {
				return "HTTP " + status;
			}
			return description == null ? error : error + ": " + description;
		}
	}

	private Http() {
	}

	static Response get(String url) throws IOException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
		                          .timeout(Duration.ofSeconds(15))
		                          .header("Accept", "application/json")
		                          .GET()
		                          .build();
		return send(request);
	}

	static Response postForm(String url, Map<String, String> form) throws IOException {
		StringJoiner body = new StringJoiner("&");
		form.forEach((k, v) -> {
			if (v != null) {
				body.add(encode(k) + "=" + encode(v));
			}
		});
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
		                          .timeout(Duration.ofSeconds(15))
		                          .header("Accept", "application/json")
		                          .header("Content-Type", "application/x-www-form-urlencoded")
		                          .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
		                          .build();
		return send(request);
	}

	static Map<String, Object> parse(byte[] json) throws IOException {
		return JSON.readValue(json, new TypeReference<Map<String, Object>>() {});
	}

	static String write(Object value) throws IOException {
		return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value);
	}

	static String encode(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static Response send(HttpRequest request) throws IOException {
		try {
			HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
			Map<String, Object> body;
			try {
				body = parse(response.body());
			} catch (IOException e) {
				body = Map.of();
			}
			return new Response(response.statusCode(), body);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}
}
