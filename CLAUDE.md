# acl-clients — development guidelines

This is client tooling for [duckdb-acl](https://github.com/hugr-lab/duckdb-acl) nodes: the signing-in
JDBC driver (`jdbc/`), examples in Go, Python, .NET and Java (`examples/`), and a local node plus
Keycloak to run them against (`dev/`). Read `specs/001-architecture/spec.md` for the model. The node's
side lives in duckdb-acl:

- spec 064: discovery and the password handshake;
- spec 095: issuers and clients - discovery lists `clients[]` per issuer (our spec 003), and the
  door's password handshake runs as exactly one `FLOWS (password)` client;
- spec 059: token freshness at connect;
- spec 089: unpadded BasicAuth.

## Layout

```text
jdbc/                 Maven, Java 17: io.github.hugrlab.acl.jdbc
  AclDriver           the java.sql.Driver: parse, acquire a token, delegate to Arrow's driver
  AclConfig           URL + properties; our keys vs. passthrough
  TokenProvider       issuer choice, cache, refresh, flow
  Flows               authcode+PKCE (loopback), device (RFC 8628), password, refresh
  FlightDiscovery     the door's discover-auth handshake - the ONLY code using Arrow's shaded names
  Oidc, Http, Tokens, TokenCache, Interaction, DeviceCodeWindow
examples/{go,python,dotnet,java}
dev/                  keycloak.sh, node.sh, token.sh, jdbc-e2e.sh
specs/NNN-slug/spec.md
```

## Commands

There is no local JDK, Maven or .NET on the owner's machine; everything builds in Docker:

```sh
dev/keycloak.sh && dev/node.sh &                   # the dev node (needs a built ../duckdb-acl)
docker run --rm -v acl-m2:/root/.m2 -v "$PWD/jdbc":/src -w /src maven:3-eclipse-temurin-17 mvn -B test
dev/jdbc-e2e.sh [all]                               # driver e2e against the dev node + Keycloak
(cd examples/go && GOWORK=off ACL_TOKEN=$(../../dev/token.sh) ACL_INSECURE=1 go run .)
(cd examples/python && ACL_USER=analyst2 ACL_PASSWORD=analyst2-pass ACL_INSECURE=1 python3 connect.py)
docker run --rm -v "$PWD/examples/dotnet":/src -w /src -e ACL_URI=https://host.docker.internal:32800 \
  -e ACL_TOKEN=... -e ACL_INSECURE=1 mcr.microsoft.com/dotnet/sdk:8.0 dotnet run
```

Notes:

- `GOWORK=off`: a parent `go.work` would otherwise capture the Go example.
- A container reaches the Mac's node as `host.docker.internal`. `dev/jdbc-e2e.sh` forwards the
  container's own `localhost:18070/32800` with socat, because the door advertises `localhost`.
- Keycloak marks its session cookies `Secure` even over http. A browser still sends them to
  localhost, but `java.net.CookieManager` does not, which is why the test browser keeps cookies by
  hand.

## Style

- Java: tabs, braces always, short comments that say why, and records where they fit. Never put a
  token or a password in a message or `toString`.
- Every change lands with tests. The unit tests use the fake IdP; flows that talk to a real node also
  get an e2e case.
- Arrow's version is pinned in `jdbc/pom.xml`. Bumping it can move the shaded names
  `FlightDiscovery` uses, and the e2e catches that.
- One spec per feature under `specs/`, updated with the code.
