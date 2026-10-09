# dbt through a duckdb-acl node

A dbt-duckdb project whose models live in the node's schema `sales.dbt_home` (granted `select, insert,
update, delete, create, drop`). `run.sh` makes a venv (dbt-core 1.12, dbt-duckdb 1.11, openlineage-dbt) and runs
`dbt-ol`.

```sh
examples/pipelines/dbt/run.sh            # twice: the second run swaps every model
```

- **Attach the node under the virtual catalog's name** (`alias: sales` in `profiles.yml`): dbt names a
  model `<alias>.<schema>.<table>`, which is then the node's own name for it.
- **The stock materializations** run as they are: `table` and `view` (create `__dbt_tmp`, rename the
  old one away, rename the new one in, drop the old - duckdb-acl spec 113), `incremental` with
  `incremental_strategy='append'`. `delete+insert` (a `unique_key`) fails in the quack client itself
  (`PlanDelete not implemented`): a `DELETE` through an attached quack catalog is not planned there.
- `generate_schema_name` returns the custom schema as is (dbt would make it `main_dbt_home`).
- **The parent**: a pre-hook SETs `acl_lineage_parent` on the node's session from
  `OPENLINEAGE_PARENT_ID` - through `quack_query_by_name`, since a plain `SET` would run in dbt's own
  duckdb.
- The client's duckdb must be 2.0 (the node's); until 2.0.0 is released that is a pre-release.
- The quack client speaks plain http to `localhost`: reach a TLS door by another name its certificate
  carries (the dev certificate has `host.docker.internal`, which Docker Desktop resolves on the host;
  on Linux add it to `/etc/hosts` or set `ACL_QUACK_DOOR` to a name of the node's certificate).
- dbt-ol names the model in its adapter's namespace (`duckdb://:memory:`), the node in its own; the
  names are the same.
