# Spec 004: pipeline recipes - Spark, dbt and a Python job, with lineage

- **Status**: draft
- **Date**: 2026-10-09
- **Node side**: duckdb-acl specs 111 (where a Flight statement runs), 112 (lineage names, one run per
  batch), 113 (dbt's own statements)
- **Found by**: the Spark / dbt / Python spikes of 2026-10-09 against a node with acl-otel and Marquez

## Problem

The spikes proved that the three common pipeline engines work through a node and that their lineage
joins the node's in one graph - but only with settings nobody would guess:

- **Spark** names a JDBC table `acl://<host>:<port>` + the name it was given. It lines up with the
  node's `acl://<ns>` + `<vcat>.<schema>.<table>` only with three-part table names and a `pattern`
  namespace resolver (`hostList` keeps the port). A read of a table with a STRUCT column fails in Spark
  (`UNRECOGNIZED_SQL_TYPE JAVA_OBJECT`), and `truncate` needs a JDBC dialect.
- **dbt** (dbt-duckdb, the node attached through quack) lines up only when the node is attached under
  the virtual catalog's name; since duckdb-acl 113 the stock `table` / `view` / `incremental (append)`
  materializations run unchanged, `delete+insert` fails in the quack client itself.
- **A Python job** (ADBC) passes its parent run as a call header; nothing documents it.
- **The parent run** reaches the node only when each client passes it by hand. Airflow (and the other
  orchestrators OpenLineage integrates with) hand a task its parent as `OPENLINEAGE_PARENT_ID`
  (`<namespace>/<job>/<runId>`, the form duckdb-acl spec 109 checks); the JDBC driver ignores it.

## Design

1. **Recipes** under `examples/pipelines/`, each a runnable directory with a README that states what
   lines up and what does not:
   - `spark/` - a PySpark job reading and writing through the driver; the `spark-submit` flags (the
     OpenLineage listener, the `pattern` resolver mapping the door's address to the node's namespace,
     the parent/root parent conf); three-part names; reading a STRUCT table through `query` with the
     columns Spark can map; `mode("append")` (overwrite = drop + create, which needs `drop`/`create`
     on the schema; `truncate` needs a dialect - not offered).
   - `dbt/` - a dbt-duckdb project: `profiles.yml` attaching the node through quack **under the
     virtual catalog's name**, the stock materializations, `generate_schema_name` returning the
     custom schema as is, a pre-hook that SETs `acl_lineage_parent` from `OPENLINEAGE_PARENT_ID`, and
     the incremental strategies that work (`append`) and the one that does not (`delete+insert`,
     quack client).
   - `python/` - an ADBC job emitting its own START/COMPLETE with the node's dataset names, and the
     `x-openlineage-parent` call header.
   - `dev/marquez.sh` - a Marquez to look at the joined graph; each recipe runs against `dev/node.sh`
     with lineage on.
2. **The driver takes the parent from the environment.** When the connection's properties carry no
   `x-openlineage-parent` / `x-openlineage-root-parent`, the driver adds them from
   `OPENLINEAGE_PARENT_ID` / `OPENLINEAGE_ROOT_PARENT_ID` if set (`acl.lineageFromEnv=false` turns it
   off). A value the node refuses (spec 109) fails the connection with the node's message - nothing is
   rewritten on the client.
3. **Java 11.** The driver is built with `maven.compiler.release` 11, so a Spark on Java 11 (still the
   common cluster JDK) loads it; the tests keep running on 17 and 11. The cost: the driver's 17-only
   syntax goes (8 places - records in `Tokens`, `Oidc`, `DoorIssuer`, `Http`, a text block / switch
   arrows) and becomes plain final classes. Arrow's Flight SQL JDBC itself runs on 11.

Not in this spec: mapping nested types for JDBC (a STRUCT reported as JSON text so Spark can read it) -
a driver behaviour of its own, a follow-up spec; a JDBC dialect for Spark (`truncate`, type names).

## Enforcement & security

Nothing here reaches past what the node already decides. The environment variables are the
orchestrator's, read only for the two lineage headers; a token never comes from the environment by
this path.

## Testing

- Unit: the env default (set / not set / property wins / off switch); the driver compiles and its tests
  pass on Java 11.
- e2e (`dev/`): each recipe against the dev node with lineage on and Marquez - the joined graph
  checked through Marquez's API (the job's datasets are the node's).

## Alternatives considered

- **Tailor `acl_lineage_namespace` to Spark** (`acl://host:port`): refused by the owner in duckdb-acl
  112 - several engines share a node.
- **Recipes as docs only**: they drift; runnable directories are checked by the e2e.
