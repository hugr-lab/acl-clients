# duckdb-acl JDBC driver

`jdbc:acl://host:port` connects to a duckdb-acl node's Flight SQL door. It signs the user in, then
opens the connection through Apache Arrow's Flight SQL JDBC driver with the token it obtained.

## How a connection gets its token

1. **Which issuer and client.** The driver asks the door, over its Flight Handshake (`discover-auth`,
   no credentials needed), which OIDC issuers it trusts and, for each, the clients a driver may sign
   in as - a public client id and the flows it runs (duckdb-acl spec 095). It takes the first issuer
   with a client for the flow it runs, and that issuer's first such client. `issuer` (a URL, or the
   door's name for it) and `client` (the door's name for a client) choose instead; `clientId` uses a
   client id of your own. With `discovery=false`, `issuer` and `clientId` are required.
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
| `issuer` | the door's | the OIDC issuer, by URL or by the door's name; must be one the door trusts |
| `client` | the door's | the door's client to sign in as, by name (`acl_clients()` on the node) |
| `clientId` | the door's | a **public** client id of your own instead of the door's clients (no secret lives in a desktop driver) |
| `scope` | `openid` | |
| `tokenCache` | `memory` | `memory` (this JVM), `file` (also across restarts), `none`. A password sign-in is kept in memory only, keyed by the password too |
| `tokenCacheFile` | `~/.acl-jdbc/tokens.json` | owner-only (0600) where the OS supports it |
| `loginTimeout` | `300` | seconds to wait for a browser or device sign-in |
| `redirectPort` | `0` | the loopback port for the browser redirect; `0` = any free port |
| `discovery` | `true` | ask the door for its issuers and clients |
| `catalog`, `schema` | the principal's main catalog | where the connection starts: `USE <catalog>` / `USE SCHEMA <schema>` once connected (Arrow's own `catalog` is a Flight session option the door refuses) |
| `acl.mode` | `data` | what every statement is sent as: `data` (as written), `manage` (`ACL <statement>`), `native` (`ACL NATIVE <statement>`). The node decides by the principal's scope. Sent as written in every mode: a text that already starts with `ACL`, and a text that is one `USE` statement (`USE c`, `USE c.s`, `USE SCHEMA s` - the session's catalog is virtual; a physical one is `ACL NATIVE USE ...`). A batch is prefixed as a whole. `native` keeps the text's comments after the prefix; `manage` drops its leading ones (the node's management grammar reads none) |
| `nested` | `object` | STRUCT / MAP / LIST values from `getObject`: `object` (`java.sql.Struct`, `Map`, `java.sql.Array`) or `json` (one JSON text - BI tools, Spark) |
| `lineageFromEnv` | `true` | send `OPENLINEAGE_PARENT_ID` / `OPENLINEAGE_ROOT_PARENT_ID` (an orchestrator's task) as the node's lineage parent headers - each one the connection does not set itself (`x-openlineage-parent` / `x-openlineage-root-parent`) |

Every other property goes to Arrow's driver unchanged. The TLS ones are also used for discovery:

| property | default | |
| --- | --- | --- |
| `useEncryption` | `true` | TLS to the door |
| `disableCertificateVerification` | `false` | development only |
| `tlsRootCerts` | | PEM file with the CA that signed the door's certificate |
| `trustStore`, `trustStorePassword`, `useSystemTrustStore` | | Arrow's trust store settings |

## What a tool sees (spec 006)

The connection is Arrow's, wrapped where the door needs it said differently:

- **`setCatalog` / `setSchema`** run `USE "<catalog>"` / `USE SCHEMA "<schema>"` (duckdb-acl spec 114;
  a nested schema is its dotted path, `raw.eu`). `getCatalog` / `getSchema` read
  `current_database()` / `current_schema()`, kept until a `USE` through any statement of the
  connection. A `USE` needs a session of the client's own, and a schema needs a schema grant.
- **`DatabaseMetaData`**: Arrow's answers where the node now sends them (identifier quote, keywords,
  search escape, terms, catalog-at-start, transactions - duckdb-acl spec 115); the listings run as SQL
  on the principal's own surfaces - `duckdb_tables()` / `duckdb_views()`,
  `duckdb_columns()`, `duckdb_functions()`, `acl_function_columns()`, `duckdb_types()`, `acl_keys()`,
  `acl_references()`, catalogs and schemas from `information_schema.schemata` (a nested schema by its
  path, `raw.eu`) - so they describe exactly what the principal can read, and an empty answer
  still has JDBC's columns. Every filter a tool passes goes into the SQL: nothing is listed whole and
  filtered afterwards, and nothing is cached. The listing SQL calls no function but those listings
  (an ordinary role reads its own tree through the function gate) and is never prefixed by
  `acl.mode`.
- **Results** name duckdb's types: `getColumnTypeName` is `STRUCT(city VARCHAR, ...)`,
  `DECIMAL(18,3)`, `VARCHAR[]` (the field metadata `ARROW:FLIGHT:SQL:TYPE_NAME`) - a prepared
  statement's `getMetaData` too, before it runs. A STRUCT value is a `java.sql.Struct` (`AclStruct`)
  at any depth, a LIST a `java.sql.Array` (`AclArray`) whose elements are those objects, a MAP a
  `Map`; `getObject(i, Struct.class / Array.class / Map.class / String.class)` answer the same. A
  `BIGNUM` or `BIT` (duckdb's own bytes on the wire) reads as its text.
- **Listings the node has nothing for** (indexes, privileges, UDTs, pseudo columns, super types and
  tables, attributes, row identifiers, version columns, client info properties) are empty, with
  JDBC's columns.
- **A prepared statement** refuses the methods that take SQL text (`executeQuery(String)`,
  `addBatch(String)`, ...), as JDBC 4.3 says.

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

With `ACL_METADATA=1 ../dev/node.sh` and `ACL_E2E_METADATA=1 ../dev/jdbc-e2e.sh`, `LiveMetadataE2ETest`
reads the whole tree as an ordinary role (types, functions, keys, catalogs, modes) and writes what it
saw to `target/metadata-dump.txt`.

`DuckTypes`, `AclStruct` and parts of `AclDatabaseMetaData` / `MetadataSql` are adapted from
duckdb-java (MIT); see [`THIRD-PARTY.md`](THIRD-PARTY.md), shipped in the jar as
`META-INF/THIRD-PARTY.md` (CI checks both jars).

The `Forwarding*` bases and `UnsupportedResultSet` are generated from the JDK's `java.sql` interfaces
by `tools/GenForwarding.java` - regenerate rather than edit them. From `jdbc/`, on JDK 17:

```sh
docker run --rm -v "$PWD/tools":/w -w /w eclipse-temurin:17 bash -c "java GenForwarding.java"
cp tools/out/*.java src/main/java/io/github/hugrlab/acl/jdbc/ && rm -rf tools/out
```

CI regenerates them on JDK 17 and fails when any of the eight files differs.

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
