# duckdb-acl JDBC driver

`jdbc:acl://host:port` connects to a duckdb-acl node's Flight SQL door. It signs the user in, then
opens the connection through Apache Arrow's Flight SQL JDBC driver with the token it obtained.

## How a connection gets its token

1. **Which issuer.** The driver asks the door, over its Flight Handshake (`discover-auth`, no
   credentials needed), which OIDC issuer it trusts and with which public client id. Setting `issuer`
   / `clientId` overrides that; with `discovery=false` they are required.
2. **A token already held.** Tokens are cached per issuer, client and sign-in. A token that is still
   valid is reused. One that has expired is refreshed silently with the refresh token.
3. **Otherwise, sign in** (`flow`):

   | flow | what happens | use it for |
   | --- | --- | --- |
   | `authcode` | the system browser opens the IdP's login page; the driver catches the redirect on `127.0.0.1` (authorization code + PKCE) | desktop tools - the default when a browser is available |
   | `device` | a small window (or stderr) shows a code to enter at the IdP's page on any device | no browser on this machine |
   | `password` | the driver runs the IdP's password grant with `user` / `password` | tools that only have those fields, if the IdP allows the grant |
   | `token` | `token` is used as given, never refreshed | scripts, a token from elsewhere |
   | `auto` (default) | `token` if set, else `password` if user and password are set, else `authcode` if a browser is available, else `device` | |

Connections opened at the same moment share one sign-in, so a tool that opens three connections
shows one browser window.

The door checks the token when the connection is made. After that, the connection is a session on
the node that lasts until it is closed or idle (duckdb-acl spec 059, `acl_session_token_binding =
'connect'`). A token that expires in the middle of a connection therefore does not break it. The next
connection gets a fresh token, silently.

## Properties

Set them in the URL (`jdbc:acl://door:32800?flow=device`) or as JDBC properties; a property wins over
the URL.

| property | default | |
| --- | --- | --- |
| `flow` | `auto` | `auto`, `authcode`, `device`, `password`, `token` |
| `user`, `password` | | for `flow=password` (never passed to the door) |
| `token` | | for `flow=token` |
| `issuer` | the door's | the OIDC issuer URL; must be one the door trusts |
| `clientId` | the door's | a **public** client of the issuer (no secret lives in a desktop driver) |
| `scope` | `openid` | |
| `tokenCache` | `memory` | `memory` (this JVM), `file` (also across restarts), `none`. A password sign-in is kept in memory only, keyed by the password too |
| `tokenCacheFile` | `~/.acl-jdbc/tokens.json` | owner-only (0600) where the OS supports it |
| `loginTimeout` | `300` | seconds to wait for a browser or device sign-in |
| `redirectPort` | `0` | the loopback port for the browser redirect; `0` = any free port |
| `discovery` | `true` | ask the door for issuer and client |

Every other property goes to Arrow's driver unchanged. The TLS ones are also used for discovery:

| property | default | |
| --- | --- | --- |
| `useEncryption` | `true` | TLS to the door |
| `disableCertificateVerification` | `false` | development only |
| `tlsRootCerts` | | PEM file with the CA that signed the door's certificate |
| `trustStore`, `trustStorePassword`, `useSystemTrustStore` | | Arrow's trust store settings |

## The IdP's client

The browser and device sign-ins run as the client id the door names (`ALTER ISSUER ... CLIENT ID`) or
`clientId`. That client must be **public** and allow:

- the standard flow, with PKCE S256 and a redirect URI of `http://127.0.0.1/*` (the driver listens
  on a free loopback port; RFC 8252);
- the device authorization grant;
- direct access grants, for `flow=password` only.

`dev/keycloak.sh` creates such a client (`acl-desktop`) in Keycloak.

## Build and test

It needs Java 17 and Maven, or just Docker:

```sh
mvn package                                   # target/acl-jdbc-<version>-all.jar
docker run --rm -v acl-m2:/root/.m2 -v "$PWD":/src -w /src maven:3-eclipse-temurin-17 mvn -B package
```

The unit tests run against an in-process fake IdP, covering every flow, refresh, the cache and PKCE
verification. `../dev/jdbc-e2e.sh` runs the end-to-end tests against the dev node and Keycloak:

- a browser sign-in, with Keycloak's login form filled in by the test;
- a device sign-in;
- the password sign-in and its refusal;
- tenant-sliced reads.

Arrow's memory layer needs `--add-opens=java.base/java.nio=ALL-UNNAMED` on Java 17+. The build sets
it for the tests; a host application (DBeaver) sets it in its JVM options.

## Releasing

Push a tag `v<major>.<minor>.<patch>` (for example `v0.1.0`). `.github/workflows/release.yml` then:

1. takes the version from the tag and runs the tests;
2. creates the GitHub release with `acl-jdbc-<version>-all.jar` (the one jar DBeaver loads) and its
   `.sha256`;
3. publishes `io.github.hugr-lab:acl-jdbc:<version>` to **Maven Central**. The published artifacts
   are the jar with its dependencies in the pom, the `all` classifier, sources and javadoc, all
   signed.

The Central step runs only once the repository has four secrets, and says it was skipped until then:

- `CENTRAL_USERNAME` / `CENTRAL_PASSWORD`: a user token from central.sonatype.com, whose account has
  verified the namespace `io.github.hugr-lab` (via the GitHub organization);
- `GPG_PRIVATE_KEY`: an armored private key whose public half is on a keyserver
  (`keys.openpgp.org`);
- `GPG_PASSPHRASE`: that key's passphrase.

Once the driver is on Central, DBeaver can add it by its Maven coordinates (Driver Manager →
Libraries → Add Artifact) and offers updates by itself.
