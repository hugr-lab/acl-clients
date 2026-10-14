# acl-openmetadata - an OpenMetadata connector for duckdb-acl nodes

OpenMetadata's own mechanism for a source it has no connector for: a **CustomDatabase** service whose
`sourcePythonClass` is `acl_openmetadata.source.AclNodeSource` (acl-clients spec 005). It connects to a node's
Flight SQL door as a service principal and writes into OpenMetadata:

- **the catalog the principal reads**: one database per virtual catalog, its schemas (a nested schema `raw.eu` is
  one schema), tables and views with the node's column types - a STRUCT's fields as child columns, a list's element
  type, DECIMAL's precision and scale, the node's own type text as the display - and the comments as descriptions;
- **the definition lineage** (duckdb-acl spec 129): each virtual object <- the sources it is made of, column by
  column, through OpenMetadata's lineage API;
- **what the node no longer lists is removed** (soft-deleted, as OpenMetadata's own sources mark a dropped table);
  a re-run changes nothing that did not change.

The FQNs are `<service>.<catalog>.<schema>.<object>` - what OpenMetadata's `namespaceToServiceMapping` makes of the
node's OpenLineage names, so the runtime runs acl-otel sends (`api/v1/openlineage/lineage`) land on the same entities.

## The node's side

A role for the connector, with `select` and the explicit **`lineage`** capability on each catalog it syncs
(duckdb-acl spec 129; without `lineage` the catalog is still synced, with no definition lineage):

```sql
CREATE ROLE om_connector;
GRANT CATALOG sales TO ROLE om_connector WITH (select, lineage) MAIN;
GRANT CATALOG inventory TO ROLE om_connector WITH (select, lineage);
```

It reads only what that role sees - no administration scope, no `ACL NATIVE`. The node needs a lineage namespace
(`acl_lineage_namespace`) for the definition lineage's names.

## Install

Into the environment that runs the ingestion - OpenMetadata's ingestion image (Airflow), or wherever you run
`metadata ingest`:

```sh
pip install acl-openmetadata          # or: pip install -e integrations/openmetadata
```

```dockerfile
FROM docker.getcollate.io/openmetadata/ingestion:2.0.5
RUN pip install --no-cache-dir acl-openmetadata
```

## Configure

`examples/ingest.yaml` is a workflow (`metadata ingest -c examples/ingest.yaml`); in OpenMetadata's UI the same
options go into a **Custom Database** service: Source Python Class `acl_openmetadata.source.AclNodeSource`,
Connection Options:

| option | |
| --- | --- |
| `uri` | the node's door, `grpc+tls://host:port` |
| `tokenEnv` | the environment variable holding a bearer token (default `ACL_NODE_TOKEN`) |
| `issuer`, `clientId`, `clientSecretEnv`, `scope` | OAuth client credentials against the node's issuer - the secret in the variable `clientSecretEnv` names |
| `insecure` | `true` skips the door's certificate check (a dev node's self-signed certificate) |
| `sourceServices` | JSON: a physical source's OpenLineage namespace -> the OpenMetadata FQN prefix of its datasets |

**No credential is ever a connection option**: OpenMetadata keeps connection options in the clear, so the connector
refuses `token`, `clientSecret`, `password`, `secret` there and reads them from the variables the options name - set
them in the ingestion's environment.

`sourceServices` resolves a definition's physical end: a source with a declared identity (duckdb-acl spec 112 §9)
is named the OpenLineage way - `postgres://pg.example:5432` and `shop.public.orders` - so it maps to the OpenMetadata
service of its own connector: `{"postgres://pg.example:5432": "pg_prod"}`. A source without one is named
`<namespace>/source/<alias>` and `schema.table` - map it to a service and a database:
`{"acl://prod/source/lake": "pg_prod.shop"}`. An edge whose end OpenMetadata does not know is left out (logged).
The node's own objects (a view over a virtual table) need no mapping. The service filters (database / schema / table
patterns) apply as in OpenMetadata's own sources.

## Develop

```sh
uv venv -p 3.11 .venv && VIRTUAL_ENV=.venv uv pip install -e ".[test]"
.venv/bin/python -m pytest -q tests                 # unit tests (CI)
# end to end, from the repository root: Keycloak, a node, an OpenMetadata
dev/keycloak.sh && ACL_OPENMETADATA=1 ACL_NODE_PORT=32810 dev/node.sh &
dev/openmetadata.sh && dev/openmetadata-e2e.sh
```
