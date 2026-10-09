# A Python job through a duckdb-acl node

`job.py` is a step of an orchestrated job: it emits its own START / COMPLETE to the OpenLineage backend,
naming the node's datasets as the node does (`acl://dev` + `sales.main.orders`), and hands the node its
run as the parent (the ADBC call header `x-openlineage-parent`). The node then records, under that run:

- one run for the `executemany` (one DoPut, however many rows - duckdb-acl spec 112);
- one run for the bulk ingest (`adbc_ingest`);

```sh
examples/pipelines/python/run.sh
```
