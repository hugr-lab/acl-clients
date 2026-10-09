"""A Python step of an orchestrated job writing through a duckdb-acl node (spec 004).

The job reports its own run to the OpenLineage backend under the node's dataset names
(`acl://<ns>` + `<vcat>.<schema>.<table>`), and hands the node its run as the parent of the node's runs
(the `x-openlineage-parent` call header) - one step, with the node's column-level edges under it.
"""
import datetime
import os
import uuid

import adbc_driver_flightsql as fs
import adbc_driver_flightsql.dbapi as flightsql
import pyarrow as pa
from openlineage.client import OpenLineageClient
from openlineage.client.event_v2 import InputDataset, Job, OutputDataset, Run, RunEvent, RunState
from openlineage.client.transport.http import HttpConfig, HttpTransport

DOOR = os.environ.get("ACL_URI", "grpc+tls://localhost:32800")
NODE_NS = os.environ.get("ACL_LINEAGE_NAMESPACE", "acl://dev")  # the node's acl_lineage_namespace
JOB_NS, JOB = "python", "nightly.orders_load"
HEADER = fs.DatabaseOptions.RPC_CALL_HEADER_PREFIX.value  # adbc.flight.sql.rpc.call_header.

run_id = str(uuid.uuid4())
client = OpenLineageClient(transport=HttpTransport(HttpConfig(url=os.environ.get("OPENLINEAGE_URL",
                                                                                 "http://localhost:5050"))))
inputs = [InputDataset(namespace=NODE_NS, name="sales.main.orders")]
outputs = [OutputDataset(namespace=NODE_NS, name="sales.main.py_out")]


def emit(state):
    client.emit(RunEvent(eventType=state, eventTime=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                         run=Run(runId=run_id), job=Job(namespace=JOB_NS, name=JOB),
                         producer="https://github.com/hugr-lab/acl-clients/examples/pipelines/python",
                         inputs=inputs, outputs=outputs))


emit(RunState.START)
conn = flightsql.connect(DOOR, db_kwargs={
    fs.DatabaseOptions.AUTHORIZATION_HEADER.value: "Bearer " + os.environ["ACL_TOKEN"],
    fs.DatabaseOptions.TLS_SKIP_VERIFY.value: os.environ.get("ACL_INSECURE", "") and "true" or "false",
    fs.DatabaseOptions.WITH_COOKIE_MIDDLEWARE.value: "true",
    # this run is the parent of every run the node records for these statements
    HEADER + "x-openlineage-parent": f"{JOB_NS}/{JOB}/{run_id}",
}, autocommit=True)
state = RunState.COMPLETE
try:
    cur = conn.cursor()
    cur.execute("SELECT id, amount FROM sales.main.orders")
    rows = [(int(i), float(a) * 2) for i, a in cur.fetchall()]
    # executemany is ONE DoPut, and the node records one run for it (duckdb-acl spec 112)
    cur.executemany("INSERT INTO sales.main.py_out VALUES (?, ?)", rows)
    # a bulk ingest is a run of its own, with the same parent
    cur.adbc_ingest("py_out", pa.table({"id": pa.array([i + 100 for i, _ in rows], pa.int32()),
                                        "total": [t for _, t in rows]}), mode="append", catalog_name="sales")
    print("wrote", len(rows), "rows twice")
except Exception as e:  # the run says FAIL, and the reason is printed
    state = RunState.FAIL
    print("ERROR", type(e).__name__, e)
finally:
    conn.close()
    emit(state)
print("run", run_id, state.value)
