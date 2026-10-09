# Spec 004: pipeline recipes - Spark, dbt and a Python job, with lineage

- **Status**: implemented
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
3. **Targets: Spark 3.5 and 4.** Both run on Java 17 (Spark 4 requires it), which the driver already
   targets; the Spark recipe is checked on both. A Spark 3.x cluster on Java 11 is out of scope (owner,
   2026-10-09): the driver stays Java 17 until a deployment asks for 11.

Not in this spec: mapping nested types for JDBC (a STRUCT reported as JSON text so Spark can read it) -
a driver behaviour of its own, a follow-up spec; a JDBC dialect for Spark (`truncate`, type names).

## Enforcement & security

Nothing here reaches past what the node already decides. The environment variables are the
orchestrator's, read only for the two lineage headers; a token never comes from the environment by
this path.

## Testing

- Unit: the env default (set / not set / property wins / off switch).
- e2e (`dev/`): the Spark recipe on Spark 3.5 and 4; each recipe against the dev node with lineage on and Marquez - the joined graph
  checked through Marquez's API (the job's datasets are the node's).

## Alternatives considered

- **Tailor `acl_lineage_namespace` to Spark** (`acl://host:port`): refused by the owner in duckdb-acl
  112 - several engines share a node.
- **Recipes as docs only**: they drift; runnable directories are checked by the e2e.

## As built

- **Driver.** `lineageFromEnv` (default true): `AclConfig` adds `x-openlineage-parent` /
  `x-openlineage-root-parent` from `OPENLINEAGE_PARENT_ID` / `OPENLINEAGE_ROOT_PARENT_ID` when the
  connection sets neither; the environment is injected for the unit tests (4 new cases).
- **dev.** `ACL_PIPELINES=1 dev/node.sh` adds `sales.spark_out`, `sales.py_out`, the schema
  `sales.dbt_home` (`AS memory.dbt_home`, granted select/insert/update/delete/create/drop), the quack
  door on :31900, lineage on under `acl://dev`, and acl-otel to Marquez when `ACL_OTEL` names it; the
  dev certificate also carries `host.docker.internal`. `dev/marquez.sh`, `dev/pipelines-e2e.sh`.
- **Recipes, checked by the e2e (2026-10-09):** Spark 3.5 (`apache/spark:3.5.6-java17`) and 4
  (`4.0.1-scala2.13-java17`), OpenLineage 1.53 - Spark's job and the node's runs meet at
  `acl://dev` + `sales.main.spark_out`; dbt twice in a row - the node's runs reach the model's final
  name through the swap (that needed duckdb-acl 113 to record a RENAME as a run: in a live alias the
  final name never appeared before); the Python job and the node's runs meet at `sales.main.py_out`.
- **Found on the way:** `pip install --pre` pulls dbt-core 2.0 (a release candidate of the new
  engine) - the recipe pins dbt-core 1.12.5 and takes the 2.0 duckdb client separately; the quack client
  speaks http to `localhost` (documented).
