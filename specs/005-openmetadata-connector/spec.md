# Spec 005: an OpenMetadata connector for duckdb-acl nodes

- **Status**: implemented (owner, 2026-10-09: the integration lives here, in acl-clients; built 2026-10-14)
- **Date**: 2026-10-09
- **Node side**: duckdb-acl specs 107 / 112 (lineage), 113 (DDL names); the node function for definition
  lineage is duckdb-acl spec 129 (`acl_lineage_definitions([catalog])`, the explicit `lineage` capability)
- **Found by**: the OpenMetadata spike of 2026-10-09 (OpenMetadata 2.0.5 in docker, the dev node with
  acl-otel)

## Problem

OpenMetadata is entity-first: it draws lineage only between tables it already knows. The spike:

- **The runtime lineage works.** acl-otel's transport reaches OpenMetadata's
  `POST /api/v1/openlineage/lineage` unchanged (`acl_otel_lineage_endpoint =
  'api/v1/openlineage/lineage'`, the OpenMetadata bot's JWT as the key). The node's names map through
  OpenMetadata's `namespaceToServiceMapping` (`acl://dev` -> a database service): `sales.main.orders`
  becomes `<service>.sales.main.orders`. With the tables in place, a dbt build gave column lineage
  `orders -> order_totals__dbt_tmp -> order_totals`.
- **The tables are not in place.** OpenMetadata's own auto-create from OpenLineage events fails on
  2.0.x (issue #27548: `updatedAt` / `updatedBy` not stamped, refused by Postgres and MySQL). The fix
  (PR #34574, `main` 2026-10-02, not in 2.0.5, no backport) creates a table only under a mapped service,
  only from a run's schema facet, with a struct's children empty.
- **OpenMetadata takes no `DatasetEvent`** - neither on the REST endpoint (400) nor in its Kafka /
  Kinesis connector. So what a node knows statically never arrives: an object no run has touched, the
  lineage of a definition (which physical columns a virtual object is made of), a dropped object.
- The same shape elsewhere (oluies.github.io/duckdb-openmetadata-lineage): the entities created through
  OpenMetadata's REST API by hand, the lineage as OpenLineage events.

The owner's direction: acl-otel stays an OpenLineage transport, never bound to OpenMetadata; keeping
OpenMetadata's catalog current is OpenMetadata's side - a process it runs.

## Design

**A custom OpenMetadata connector** (OpenMetadata's own mechanism: a `CustomDatabase` service whose
`sourcePythonClass` names a `metadata.ingestion.api.steps.Source` of `openmetadata-ingestion` 2.0),
in `integrations/openmetadata/`, installed into OpenMetadata's ingestion image or run with
`metadata ingest`; scheduled from OpenMetadata like any connector.

- **Connection.** Flight SQL (ADBC) to a node's door as a service principal - it sees the catalog as
  its role does. A static token, or OAuth client credentials against the node's issuer.
- **Metadata.** `information_schema.schemata / tables / columns` of the principal: the database service
  -> one database per virtual catalog -> schemas -> tables and views, with the exposed column types
  (nested STRUCT / LIST fields as children), comments; objects no longer listed are removed. The FQN is
  `<service>.<vcat>.<schema>.<object>` - what OpenMetadata maps the node's OpenLineage names to, so
  acl-otel's runtime runs land on these entities.
- **Definition lineage.** duckdb-acl spec 129's `acl_lineage_definitions([catalog])` answers a catalog's
  definition edges on demand - what the `DATASET` events carry, not from the ring - and the connector writes them
  through OpenMetadata's lineage API (virtual object <- physical source, column by column). The connector's role
  holds the explicit `lineage` capability on each catalog it syncs (`GRANT CATALOG c TO ROLE om WITH (select,
  lineage)`); it reads only what the role sees. A physical source
  resolves when OpenMetadata knows it (its own connector, or the identity of duckdb-acl spec 112 §9).
- **Later:** per-role tags (`acl.role.<r>`), the connector's own test-connection, a profiler.

## Enforcement & security

The connector reads only what its principal is granted; it never uses `ACL NATIVE` or the policy
catalog. Its token is kept by OpenMetadata's secrets handling for the service connection, never in a
file of ours.

## Testing

The connector against the dev node (`ACL_PIPELINES=1`) and an OpenMetadata in docker: the entities
created, a re-run idempotent, a dropped object removed; then the dbt recipe with acl-otel sending to
OpenMetadata - the runs' edges on the connector's entities.

## Alternatives considered

- **A catalog sync in acl-otel** (REST calls to OpenMetadata): binds a transport to one backend.
- **Wait for OpenMetadata's auto-create (#34574)**: creates tables from runs only - no definitions, no
  untouched objects, no drops, flat structs.
- **Definitions sent as RunEvents** (an OpenLineage-legal workaround in acl-otel): still no drops, and it
  changes what every backend sees.
- **Upstream: `DatasetEvent` support in OpenMetadata** - the right layer for the static picture; not
  filed (owner, 2026-10-09: wait).

## As built (2026-10-14)

- `integrations/openmetadata/` (MIT, `acl-openmetadata` 0.1.0, openmetadata-ingestion ~=2.0.5, Python 3.10-3.11):
  `acl_openmetadata.source.AclNodeSource` - a CustomDatabase `Source`: the service, a database per catalog
  (`duckdb_databases()`'s comment its description), the schemas of `information_schema.schemata`, the tables and
  views of `information_schema.tables` (VIEW -> `TableType.View`, `TABLE_COMMENT` the description), the columns of
  `information_schema.columns` (`types.py`: duckdb's type text parsed - STRUCT / UNION fields as children, `T[]` and
  `T[n]` as ARRAY with their element type, MAP, DECIMAL's precision and scale, duckdb's own scalars mapped before
  OpenMetadata's ColumnTypeParser, an unbounded VARCHAR with the length 1 OpenMetadata requires, the node's text the
  display); then the deletions (walked down: databases by service, schemas by database, tables by schema - what the
  node no longer lists, soft-deleted); then the lineage: `acl_lineage_definitions()` planned into table edges with
  column lineage (`plan_lineage`: DIRECT edges into a field are column lineage, a nested field path a nested column,
  an INDIRECT edge draws the table edge), each end resolved by FQN, written as `AddLineageRequest`
  (`ViewLineage`). A role without `lineage` syncs the catalog and logs that no lineage follows.
- **The connection** (`node.py`): ADBC Flight SQL, the cookie middleware (one door session), autocommit (reads only),
  a bearer from `tokenEnv` or OAuth client credentials (`issuer` + `clientId` + `clientSecretEnv`, the endpoint by
  discovery). A credential as a connection option (`token`, `clientSecret`, `password`, `secret`) is refused:
  OpenMetadata stores connection options in the clear.
- **dev**: `dev/openmetadata-compose.yml` (the 2.0.5 release's postgres compose without Airflow, the server on
  127.0.0.1:8585), `dev/openmetadata.sh`, the Keycloak client `acl-om` (client credentials, the realm role
  `om_connector` - `dev/keycloak.sh`), `ACL_OPENMETADATA=1 dev/node.sh` (ACL_METADATA's objects, the namespace
  `acl://dev`, the role `om_connector` with select + lineage on sales and inventory), `dev/openmetadata-e2e.sh` +
  `dev/openmetadata_e2e.py`: the physical side made in OpenMetadata (`dev_memory.memory.*`, as its own connector
  would), then the ingestion and its checks - the service; a table under `<service>.<catalog>.<schema>` with a STRUCT
  column's fields, the type text (lower-cased by OpenMetadata), the comment; a View; the nested schema `"raw.eu"`; a
  catalog without a grant absent; the second catalog; the definition lineage table <- source, column by column, a
  view's; a re-run keeping the entity; an object dropped on the node (a steward's `DROP VIRTUAL TABLE`) soft-deleted,
  restored on the node and back. The credentials (the ingestion bot's JWT, acl-om's secret, the steward's token) are
  fetched into the run's environment, never written.
- CI: the connector's unit tests (Python 3.11).
- Found: OpenMetadata 2.0.5's python client answers None for `get_by_name(..., include="all")`; the lineage API's
  edges name entity ids.
