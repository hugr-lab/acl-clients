package io.github.hugrlab.acl.jdbc;

import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.CallOptions;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.FlightClient;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.Location;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.auth.ClientAuthHandler;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.memory.RootAllocator;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Discovery over the door's own Flight Handshake: payload {@code discover-auth}, answered without
 * authentication (duckdb-acl spec 064). The Flight client is the one Arrow's JDBC driver carries
 * (shaded) - the only place this driver touches its shaded names, pinned with the driver's version.
 */
final class FlightDiscovery implements Discovery {
	@Override
	public List<DoorIssuer> discover(AclConfig config) throws IOException {
		Location location = config.useEncryption ? Location.forGrpcTls(config.host, config.port)
		                                         : Location.forGrpcInsecure(config.host, config.port);
		try (RootAllocator allocator = new RootAllocator()) {
			FlightClient.Builder builder = FlightClient.builder(allocator, location);
			InputStream roots = null;
			if (config.useEncryption) {
				if (config.disableCertificateVerification) {
					builder.verifyServer(false);
				} else if (config.tlsRootCerts != null) {
					roots = new FileInputStream(config.tlsRootCerts);
					builder.trustedCertificates(roots);
				}
			}
			byte[][] answer = new byte[1][];
			try (FlightClient client = builder.build()) {
				client.authenticate(new ClientAuthHandler() {
					@Override
					public void authenticate(ClientAuthSender outgoing, Iterator<byte[]> incoming) {
						outgoing.send("discover-auth".getBytes(StandardCharsets.UTF_8));
						// Arrow's hasNext() only says whether a message has ARRIVED; next() waits for
						// the answer, or throws once the stream ended without one (an older door)
						try {
							answer[0] = incoming.next();
						} catch (IllegalStateException e) {
							answer[0] = null;
						}
					}

					@Override
					public byte[] getCallToken() {
						return new byte[0];
					}
				}, CallOptions.timeout(15, TimeUnit.SECONDS));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("interrupted", e);
			} finally {
				if (roots != null) {
					roots.close();
				}
			}
			return parse(answer[0]);
		} catch (IOException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new IOException("the door at " + config.host + ":" + config.port + " did not answer discovery: "
			                          + e.getMessage(),
			    e);
		}
	}

	static List<DoorIssuer> parse(byte[] json) throws IOException {
		List<DoorIssuer> out = new ArrayList<>();
		if (json == null || json.length == 0) {
			return out; // a door that predates discovery answers nothing: configure issuer + clientId
		}
		Object issuers = Http.parse(json).get("issuers");
		if (issuers instanceof List<?> list) {
			for (Object item : list) {
				if (item instanceof Map<?, ?> m && m.get("issuer") != null) {
					out.add(new DoorIssuer(text(m, "issuer"), text(m, "client_id"), text(m, "token_endpoint"),
					    text(m, "device_authorization_endpoint")));
				}
			}
		}
		return out;
	}

	private static String text(Map<?, ?> m, String key) {
		Object value = m.get(key);
		return value == null ? null : value.toString();
	}
}
