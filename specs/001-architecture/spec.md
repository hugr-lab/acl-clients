# Spec 001: the client tooling - a signing-in JDBC driver and the examples

- **Status**: implemented
- **Date**: 2026-09-30

## Summary

A duckdb-acl node's Flight SQL door verifies an OIDC access token and never runs a sign-in for a
desktop user. The one exception is the password grant it performs on a BasicAuth handshake. Getting
the token is the client's job (duckdb-acl design 016). This repository is that client side:

- a JDBC driver that signs the user in (browser, device code, password, token) and keeps the token
  fresh;
- examples in four languages that show the two ways in any stock Flight SQL client already has.

## Problem

Without this repository, a DBeaver user pastes a JWT into a driver property and pastes it again when
it expires, or relies on the door's password handshake, which only works where the IdP allows the
password grant. Many shops refuse that grant, so their users had no way in from a GUI tool.

A script author has the ADBC and Flight SQL drivers, but nothing shows the handful of options that
matter:

- the cookie middleware, for a connection-long session;
- TLS to the door;
- the bearer header or BasicAuth.

## Design

**The driver is a layer, not a fork.** `jdbc:acl://host:port` is parsed, a token is obtained, and
the connection is Arrow's `flight-sql-jdbc-driver` at `jdbc:arrow-flight-sql://host:port` with
`token=<access token>`. Every property the driver does not own passes through, including TLS, trust
store and Arrow's own options. `user` and `password` never reach Arrow's driver, which would otherwise
start a BasicAuth handshake. The distributable is one shaded jar: our classes, Arrow's driver as is,
and Jackson relocated under our package.

**Discovery before credentials.** The driver asks the door over its Flight Handshake with payload
`discover-auth` (duckdb-acl spec 064, unauthenticated). The answer lists the issuers the door trusts
with their client ids. The driver then reads the issuer's `.well-known/openid-configuration` for the
authorization, token and device endpoints. The door's answer carries no authorization endpoint, and
the IdP is the authority for its own endpoints anyway. When the IdP's document cannot be read, the
door's copy of the token and device endpoints is enough for the password and device flows.

The handshake goes through the Flight client Arrow's driver carries in shaded form. It is the only
code that touches shaded names, and it is pinned to Arrow's version by the build. `issuer` /
`clientId` override the door's answer, and `discovery=false` skips it.

**Four flows, a public client.** All of them run against the issuer's endpoints with the client id,
and no secret:

- **Authorization code + PKCE S256**: a loopback listener on `127.0.0.1:<free port>/callback`
  (RFC 8252), the system browser, and a state check.
- **Device authorization grant** (RFC 8628): polls, honours `slow_down`, and shows the code in a
  non-modal window or on stderr. It carries PKCE too: Keycloak requires it on the device grant of a
  client with S256 set, and IdPs that don't ignore it.
- **Password grant.**
- **Token as given.**

`flow=auto` picks the first that applies: a token, then user and password, then a browser, then a
device code.

**Tokens between connections.** A cache keyed by issuer, client and sign-in (the user for password,
"interactive" otherwise):

- a token with more than 30 s left is reused;
- an expired one is refreshed, keeping the old refresh token when the answer carries none;
- a refused refresh clears the entry and signs in again.

`tokenCache=memory` (default) lives as long as the JVM. `file` also persists to an owner-only file.
`none` signs in on every connection.

A password sign-in is special in two ways:

- It is keyed by a SHA-256 of the password as well, so a wrong password is judged by the IdP, never
  answered from the cache.
- It is kept in memory only, so nothing derived from a password reaches the disk. Acquisitions for the same key are serialized, so concurrent
connects share one sign-in.

**Freshness is judged at connect.** Arrow's driver takes a static token. The node's default
`acl_session_token_binding = 'connect'` (duckdb-acl spec 059) checks the token when the session is
established. The cookie session then outlives the token until it is idle or closed, so a
mid-connection expiry does not break a query, and the next connection gets a fresh token. Injecting a
fresh token per call would need a hook Arrow's driver does not expose. That is a follow-up if
`every_use` deployments need it.

**Examples.** Go and Python (ADBC), .NET (Apache.Arrow.Flight.Sql) and Java (plain JDBC over Arrow's
driver). Each reads `ACL_TOKEN` or `ACL_USER` / `ACL_PASSWORD`. ADBC gets
`adbc.flight.sql.rpc.with_cookie_middleware=true`. .NET has no cookie support in grpc-dotnet, so a
`DelegatingHandler` carries the cookie.

## Enforcement & security

- No secret in the driver: public clients only, and PKCE on both interactive grants.
- The loopback redirect listens on 127.0.0.1 only, accepts one callback with the matching state, and
  closes.
- Error messages never carry a token or a password. The file cache is 0600 where POSIX allows; on
  Windows it sits in the user's profile.
- The node decides everything that matters: it verifies every token offline and slices the data. The
  driver can only fail to obtain a token.
- TLS by default. Disabling verification is a named, development-only property.

## Testing

- `jdbc/`: unit tests against an in-process fake IdP that verifies PKCE on both grants:
  - each flow, including refusal, timeout, `slow_down` and denial;
  - `auto` resolution;
  - cache reuse, refresh, a refused refresh, keys kept apart;
  - one sign-in for concurrent connects;
  - the file cache and its permissions;
  - discovery parsing and the issuer choice;
  - the delegate's URL and properties.
- `dev/jdbc-e2e.sh`: against the dev node and Keycloak, where a test "browser" fills Keycloak's login
  form:
  - browser sign-in → acme's rows;
  - device sign-in → globex's;
  - password → globex's;
  - wrong password → SQLSTATE 28000 with the IdP's refusal;
  - two connections, one sign-in.
- The examples were run against the same node with a token and with a password.

## Alternatives considered

- **A local sign-in proxy (acl-login) in front of stock drivers.** It covers non-JDBC tools too, but
  it is a process the user has to run. For DBeaver the driver is simpler. The proxy stays an option
  for CLI tools.
- **Forking Arrow's driver to inject a token per call.** Rejected: it would be a maintenance burden
  for what the node's connect-time binding already covers.
- **Our own Flight client for discovery.** Rejected: it would bring a second gRPC stack into the jar.
