# acl-clients

This repository holds client tooling for [duckdb-acl](https://github.com/hugr-lab/duckdb-acl) nodes: a
JDBC driver that signs the user in by itself (for DBeaver and any JDBC tool), and short examples in Go,
Python, .NET and Java.

A duckdb-acl node serves clients through its **Flight SQL door**, which is Arrow Flight SQL over TLS.
Any Flight SQL client can connect. The door needs an OIDC access token from an issuer the node trusts,
and it offers two ways in:

- **A token** your IdP issued, sent as `authorization: Bearer <token>`.
- **A user name and password**: the door runs the IdP's password grant itself and hands the token back
  (Flight's BasicAuth handshake; the issuer needs a `CLIENT ID`, and the door needs TLS).

The JDBC driver adds the two sign-ins a desktop tool wants: **a browser window** (authorization code
with PKCE) and **a device code**. It also keeps and refreshes tokens, so nobody pastes a JWT.

| | what | auth |
| --- | --- | --- |
| [`jdbc/`](jdbc/) | the duckdb-acl JDBC driver, `jdbc:acl://host:port` | browser, device code, password, token |
| [`examples/go`](examples/go/) | ADBC Flight SQL driver (Go) | token or password |
| [`examples/python`](examples/python/connect.py) | ADBC Flight SQL driver (Python) | token or password |
| [`examples/dotnet`](examples/dotnet/) | Apache.Arrow.Flight.Sql | token or password |
| [`examples/java`](examples/java/) | Arrow's Flight SQL JDBC driver, plain JDBC | token or password |
| [`examples/pipelines`](examples/pipelines/) | Spark 3.5 / 4, dbt, a Python job - with OpenLineage | token |

In every example, the password path through ADBC (Go and Python) needs a node that includes
duckdb-acl spec 089. arrow-go sends BasicAuth without base64 padding, and older nodes refused it.

## A node to try them against

`dev/` sets up a local node and a Keycloak realm. It needs a built duckdb-acl
(`ACL_REPO`, default `../duckdb-acl`) and a Keycloak: the one from duckdb-acl's `.env`, `localhost:18070`.

```sh
dev/keycloak.sh                  # realm acl-dev: users analyst1 (tenant acme), analyst2 (globex);
                                 # public client acl-desktop (PKCE, device, password)
dev/node.sh                      # the node: Flight SQL door on grpc+tls://localhost:32800
                                 # (self-signed), table orders sliced by tenant
dev/token.sh [user] [password]   # an access token, for the examples' ACL_TOKEN
```

Every example then reads `orders` and prints only the signed-in user's tenant:

```sh
cd examples/python && ACL_TOKEN=$(../../dev/token.sh) ACL_INSECURE=1 python3 connect.py
cd examples/go     && ACL_USER=analyst2 ACL_PASSWORD=analyst2-pass ACL_INSECURE=1 go run .
```

The pipeline recipes need lineage on the node and an OpenLineage backend - see
[`examples/pipelines`](examples/pipelines/README.md):

```sh
dev/marquez.sh
ACL_PIPELINES=1 ACL_OTEL=../acl-otel/build/release/extension/acl_otel/acl_otel.duckdb_extension dev/node.sh
dev/pipelines-e2e.sh             # Spark 3.5 and 4, dbt, Python - and the joined graph checked in Marquez
```

`ACL_INSECURE=1` (or `disableCertificateVerification=true` for JDBC) accepts the dev node's
self-signed certificate. Never use it against a real node.

## DBeaver

1. Build the driver (`cd jdbc && mvn package`, or with Docker, see [`jdbc/README.md`](jdbc/README.md)).
   The result is one jar: `jdbc/target/acl-jdbc-<version>-all.jar`.
2. **Database → Driver Manager → New**:
   - Driver name: `duckdb-acl`
   - Class name: `io.github.hugrlab.acl.jdbc.AclDriver`
   - URL template: `jdbc:acl://{host}:{port}`
   - Default port: the door's port
   - Libraries: add the `-all.jar`
3. Arrow's memory layer needs one JVM flag. Add it to `dbeaver.ini`, below `-vmargs`:
   `--add-opens=java.base/java.nio=ALL-UNNAMED`.
4. Create a connection with that driver:
   - **Leave user and password empty** to sign in through the browser. DBeaver opens your IdP's login
     page, and after that the connection works like any other.
   - **Fill in user and password** to use the password sign-in, if your IdP allows it.
   - On the **Driver properties** tab, `flow` selects a sign-in explicitly (`authcode`, `device`,
     `password`, `token`). `tokenCache=file` keeps the sign-in across DBeaver restarts. See
     [`jdbc/README.md`](jdbc/README.md) for every property.

The driver asks the door which issuer and client to use, so a connection needs only host and port.

[`examples/dbeaver/drivers.xml`](examples/dbeaver/drivers.xml) is the same driver as a DBeaver driver
definition, with the Generic provider's settings that fit the node: functions listed as well as
procedures, and the active catalog switched with `USE` (spec 006). The tree DBeaver shows is the
principal's own: catalogs, nested schemas (`raw.eu`), tables and views with their comments, columns
with duckdb's types (`STRUCT(...)`, `VARCHAR[]`), and table functions with their parameters and result
columns. A second connection with `acl.mode=manage` runs management SQL (`ACL ...`) as typed, and
`acl.mode=native` native SQL (`ACL NATIVE ...`) - the node decides by the principal's scope. A `USE`
is the session's in every mode (the tree is always virtual), so switching the active catalog works in
each.

## License

MIT. The driver jar bundles Apache Arrow's Flight SQL JDBC driver (Apache License 2.0) unchanged.
Parts of the driver (`DuckTypes`, `AclStruct`, `AclDatabaseMetaData`, `MetadataSql`) are adapted from
[duckdb-java](https://github.com/duckdb/duckdb-java) (MIT, Copyright 2018-2025 Stichting DuckDB
Foundation); its licence is in [`jdbc/THIRD-PARTY.md`](jdbc/THIRD-PARTY.md) and ships in both jars as
`META-INF/THIRD-PARTY.md`.
