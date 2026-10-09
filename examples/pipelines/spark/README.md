# Spark through a duckdb-acl node

`job.py` reads `sales.main.orders` through the acl JDBC driver, aggregates, and appends to
`sales.main.spark_out`. `run.sh` runs it in Spark's own image - 3.5 or 4 (`SPARK=3.5|4`) - with
OpenLineage's listener.

```sh
SPARK=4 examples/pipelines/spark/run.sh
```

What makes Spark's lineage and the node's one graph:

- **Three-part names** in `dbtable` / `query`: Spark names a JDBC table by the name it was given.
- **A `pattern` namespace resolver** that turns the door's address into the node's namespace
  (`acl://door:32800` -> `acl://dev`): `spark.openlineage.dataset.namespaceResolvers.<ns>.type=pattern`
  with `.regex=<door host:port>`. (`hostList` keeps the port.)
- **The parent**: the same `OPENLINEAGE_PARENT_ID` goes to Spark's listener (`parentJobNamespace` /
  `parentJobName` / `parentRunId`) and to the node - the driver sends it from the environment.

Limits:

- write with `mode("append")`. `overwrite` drops and creates the table, which needs `create` and
  `drop` on its schema; `truncate` needs a JDBC dialect, not offered.
- a table with a STRUCT / LIST / MAP column: read it through `query` with the columns Spark can map
  (Spark has no JDBC type for nested values).
- `UNRECOGNIZED_SQL_TYPE JAVA_OBJECT` means a nested column reached Spark.
- the `InaccessibleObjectException` OpenLineage logs at start is its own and harmless.
