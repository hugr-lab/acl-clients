package io.github.hugrlab.acl.jdbc;

import java.util.List;

/**
 * One issuer the door trusts, as its discovery answers it (duckdb-acl spec 095): the Flight Handshake
 * payload {@code discover-auth} returns {@code {"issuers":[{"name","issuer","token_endpoint",
 * "device_authorization_endpoint","authorization_endpoint","clients":[{"name","client_id","flows"}]}]}}.
 * The clients are the ones a driver can sign in as: a public client id and the flows it runs there.
 */
record DoorIssuer(String name, String issuer, String tokenEndpoint, String deviceEndpoint,
                  String authorizationEndpoint, List<DoorClient> clients) {

	/** A client of the issuer that a driver may run a flow as. */
	record DoorClient(String name, String clientId, List<String> flows) {
		boolean runs(String flow) {
			return flows.contains(flow);
		}
	}

	/** An issuer the connection names itself, with no discovery behind it. */
	static DoorIssuer configured(String issuer) {
		return new DoorIssuer(null, issuer, null, null, null, List.of());
	}
}
