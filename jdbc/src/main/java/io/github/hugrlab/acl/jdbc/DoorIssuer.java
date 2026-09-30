package io.github.hugrlab.acl.jdbc;

/**
 * One issuer the door trusts, as its discovery answers it: the Flight Handshake payload
 * {@code discover-auth} returns {@code {"issuers":[{"issuer","client_id","token_endpoint",
 * "device_authorization_endpoint"}]}}.
 */
record DoorIssuer(String issuer, String clientId, String tokenEndpoint, String deviceEndpoint) {
}
