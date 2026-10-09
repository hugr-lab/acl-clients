# Pipelines through a duckdb-acl node, with lineage

Three engines read virtual tables through a node and write back - and their OpenLineage events and the
node's join into one graph. Each directory runs against the dev node (`dev/node.sh` with
`ACL_PIPELINES=1`) and a Marquez (`dev/marquez.sh`); `dev/pipelines-e2e.sh` runs them all and checks the
graph.

| | engine | through | lines up by |
| --- | --- | --- | --- |
| [`spark/`](spark/) | Spark 3.5 and 4 (PySpark) | the acl JDBC driver | three-part names + a `pattern` namespace resolver |
| [`dbt/`](dbt/) | dbt-duckdb + dbt-ol | the quack door, attached under the virtual catalog's name | the same dataset names; dbt's namespace is its adapter's |
| [`python/`](python/) | a job using ADBC | the Flight SQL door | the job emits the node's names itself |

## The names to use

A node names a virtual dataset `<acl_lineage_namespace>` + `<catalog>.<schema>.<object>` - for the dev
node `acl://dev` + `sales.main.orders` (duckdb-acl spec 112). An engine's own events join the node's
when they name a table the same way:

- write table names in **three parts** (`sales.main.orders`, `sales.dbt_home.model`) - the node accepts
  them like the short form;
- give the engine the node's **namespace** for the door's address: Spark's namespace resolver, a job's
  own `namespace=`; dbt-ol keeps its adapter's (`duckdb://…`), so a catalog relates the two by name.

## The parent run

The node records its runs as steps of the engine's run when it gets the parent
(`<namespace>/<job>/<runId>`, OpenLineage's `OPENLINEAGE_PARENT_ID`):

- **JDBC** (Spark): the driver sends `OPENLINEAGE_PARENT_ID` / `OPENLINEAGE_ROOT_PARENT_ID` from the
  environment as the lineage headers (`lineageFromEnv`, on by default) - set by Airflow for its task;
- **ADBC** (Python): the call header `x-openlineage-parent`;
- **quack** (dbt): a pre-hook that SETs `acl_lineage_parent` on the node's session.

A malformed parent is refused by the node where it enters (duckdb-acl spec 109).
