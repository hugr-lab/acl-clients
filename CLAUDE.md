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
  AclConnection       Arrow's connection wrapped (spec 006): USE for catalog/schema, acl.mode prefixes
  AclDatabaseMetaData the listings as SQL on the principal's surfaces (MetadataSql), RowsResultSet answers
  AclResultSet, DuckTypes, AclStruct   duckdb's types on results; Forwarding* generated (tools/)
examples/{go,python,dotnet,java}
examples/pipelines/   spark/ (3.5, 4), dbt/, python/ - with lineage (spec 004)
examples/dbeaver/     drivers.xml - the driver as a DBeaver Generic driver (spec 006)
dbeaver/              the DBeaver plugin (spec 007): Tycho against one DBeaver release's bundles (build.sh fetches it);
  io.github.hugrlab.acl.jdbc       the driver's -all jar as a bundle (DBeaver loads `bundle:/<id>`)
  io.github.hugrlab.acl.dbeaver    the connection type (Generic parent), AclMetaModel (nested schemas), the dialect,
                                   our navigator tree (no visibleIf - DBeaver's JEXL allows only its own packages)
  io.github.hugrlab.acl.dbeaver.tests  SWTBot UI tests in a real DBeaver; Live* need ACL_E2E_URL (dev/node.sh)
  .feature / .site                 the feature and the p2 repository (zipped: the release asset)
integrations/openmetadata/  spec 005: the OpenMetadata connector (acl_openmetadata.source.AclNodeSource, a CustomDatabase
                      Source over the door; types.py duckdb types -> columns, node.py the door + the principal's surfaces)
dev/                  keycloak.sh, node.sh [ACL_PIPELINES=1] [ACL_METADATA=1] [ACL_OPENMETADATA=1], token.sh, jdbc-e2e.sh,
                      marquez.sh, pipelines-e2e.sh, openmetadata.sh, openmetadata-e2e.sh
specs/NNN-slug/spec.md
```

## Commands

A local JDK 21 and Maven exist since 2026-10-11 (Homebrew; `JAVA_HOME=/opt/homebrew/opt/openjdk@21` - Maven's own
default is a newer JDK); no .NET. CI and the examples build in Docker:

```sh
dev/keycloak.sh && dev/node.sh &                   # the dev node (needs a built ../duckdb-acl)
docker run --rm -v acl-m2:/root/.m2 -v "$PWD/jdbc":/src -w /src maven:3-eclipse-temurin-17 mvn -B test
dev/jdbc-e2e.sh [all]                               # driver e2e against the dev node + Keycloak
dbeaver/build.sh && dbeaver/install-check.sh        # the DBeaver plugin (local JDK 21) - UI tests included
dev/marquez.sh && ACL_PIPELINES=1 ACL_OTEL=<acl-otel ext> dev/node.sh &   # lineage on, quack door :31900
dev/pipelines-e2e.sh [spark3|spark4|dbt|python]     # the recipes + the joined graph in Marquez
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
