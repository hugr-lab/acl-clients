# Spec 005: an OpenMetadata connector for duckdb-acl nodes

- **Status**: planned (owner, 2026-10-09: the integration lives here, in acl-clients; implemented after
  the current plan)
- **Date**: 2026-10-09
- **Node side**: duckdb-acl specs 107 / 112 (lineage), 113 (DDL names); a node function for definition
  lineage (a duckdb-acl spec of its own)
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
- **Definition lineage.** A node function (duckdb-acl) that answers a catalog's definition edges on
  demand - what the `DATASET` events carry, not from the ring - and the connector writes them through
  OpenMetadata's lineage API (virtual object <- physical source, column by column). A physical source
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
