"""acl-clients spec 005 end to end (dev/openmetadata-e2e.sh runs it): the connector ingests the dev node
(ACL_OPENMETADATA=1 dev/node.sh) into an OpenMetadata 2.0.5 (dev/openmetadata.sh) - the catalog as the connector's
role sees it, the definition lineage of duckdb-acl spec 129, idempotent, a dropped object removed.

The credentials come from the environment the shell script made: OM_JWT (the ingestion bot's), ACL_OM_CLIENT_SECRET
(Keycloak's acl-om), ACL_STEWARD_TOKEN (a policy admin's, to drop and restore an object). Nothing is written to a file.
"""

import os
import sys

import adbc_driver_flightsql.dbapi as flight
from adbc_driver_flightsql import DatabaseOptions
from metadata.generated.schema.api.data.createDatabase import CreateDatabaseRequest
from metadata.generated.schema.api.data.createDatabaseSchema import CreateDatabaseSchemaRequest
from metadata.generated.schema.api.data.createTable import CreateTableRequest
from metadata.generated.schema.api.services.createDatabaseService import CreateDatabaseServiceRequest
from metadata.generated.schema.entity.data.table import Column, DataType, Table, TableType
from metadata.generated.schema.entity.services.connections.database.customDatabaseConnection import (
    CustomDatabaseConnection,
    CustomDatabaseType,
)
from metadata.generated.schema.entity.services.connections.metadata.openMetadataConnection import (
    OpenMetadataConnection,
)
from metadata.generated.schema.entity.services.databaseService import (
    DatabaseConnection,
    DatabaseService,
    DatabaseServiceType,
)
from metadata.generated.schema.security.client.openMetadataJWTClientConfig import OpenMetadataJWTClientConfig
from metadata.ingestion.ometa.ometa_api import OpenMetadata
from metadata.workflow.metadata import MetadataWorkflow

OM = os.environ.get("OM_URL", "http://localhost:8585/api")
NODE = os.environ.get("ACL_NODE_URI", "grpc+tls://localhost:32810")
ISSUER = os.environ["ACL_ISSUER"]
SERVICE = "acl_dev"
SOURCE = "dev_memory"  # what OpenMetadata knows of the node's physical source (its own connector would make it)

failures = 0


def check(name, ok, detail=""):
    global failures
    print(("  ok:   " if ok else "  FAIL: ") + name + (f": {str(detail)[:200]}" if detail and not ok else ""))
    if not ok:
        failures += 1


def client() -> OpenMetadata:
    return OpenMetadata(
        OpenMetadataConnection(
            hostPort=OM, authProvider="openmetadata", securityConfig=OpenMetadataJWTClientConfig(jwtToken=os.environ["OM_JWT"])
        )
    )


def physical_source(metadata: OpenMetadata) -> None:
    """The node's physical tables as OpenMetadata would know them from their own connector: `dev_memory.memory.<schema>`."""
    metadata.create_or_update(
        CreateDatabaseServiceRequest(
            name=SOURCE, serviceType=DatabaseServiceType.CustomDatabase,
            connection=DatabaseConnection(config=CustomDatabaseConnection(
                type=CustomDatabaseType.CustomDatabase, sourcePythonClass="none.None")),
        )
    )
    metadata.create_or_update(CreateDatabaseRequest(name="memory", service=SOURCE))
    tables = {
        "main": {"orders": ["id", "tenant", "amount"], "products": ["sku", "price"],
                 "customers": ["id", "name", "address", "tags", "scores", "attrs", "balance", "mood", "ssn", "seen",
                               "visits"]},
        "raw_eu": {"events": ["id", "kind"]},
    }
    for schema, objects in tables.items():
        metadata.create_or_update(CreateDatabaseSchemaRequest(name=schema, database=f"{SOURCE}.memory"))
        for name, columns in objects.items():
            metadata.create_or_update(CreateTableRequest(
                name=name, databaseSchema=f"{SOURCE}.memory.{schema}",
                columns=[Column(name=c, dataType=DataType.STRING) for c in columns],
            ))


def ingest() -> MetadataWorkflow:
    config = {
        "source": {
            "type": "customDatabase",
            "serviceName": SERVICE,
            "serviceConnection": {"config": {
                "type": "CustomDatabase",
                "sourcePythonClass": "acl_openmetadata.source.AclNodeSource",
                "connectionOptions": {
                    "uri": NODE, "insecure": "true",
                    "issuer": ISSUER, "clientId": "acl-om", "clientSecretEnv": "ACL_OM_CLIENT_SECRET",
                    "sourceServices": '{"acl://dev/source/memory": "%s.memory"}' % SOURCE,
                },
            }},
            "sourceConfig": {"config": {"type": "DatabaseMetadata"}},
        },
        "sink": {"type": "metadata-rest", "config": {}},
        "workflowConfig": {"openMetadataServerConfig": {
            "hostPort": OM, "authProvider": "openmetadata", "securityConfig": {"jwtToken": os.environ["OM_JWT"]},
        }},
    }
    workflow = MetadataWorkflow.create(config)
    workflow.execute()
    workflow.raise_from_status()
    workflow.stop()
    return workflow


def as_steward(sql: str) -> None:
    conn = flight.connect(NODE, db_kwargs={
        DatabaseOptions.AUTHORIZATION_HEADER.value: "Bearer " + os.environ["ACL_STEWARD_TOKEN"],
        DatabaseOptions.TLS_SKIP_VERIFY.value: "true",
        DatabaseOptions.WITH_COOKIE_MIDDLEWARE.value: "true",
    }, autocommit=True)
    cur = conn.cursor()
    cur.execute(sql)
    cur.fetchall()
    cur.close()
    conn.close()


def table(metadata: OpenMetadata, fqn_: str, fields=None):
    return metadata.get_by_name(entity=Table, fqn=fqn_, fields=fields or ["columns"])


def main() -> int:
    metadata = client()
    physical_source(metadata)

    print("first ingestion")
    ingest()
    service = metadata.get_by_name(entity=DatabaseService, fqn=SERVICE)
    check("the service is there", service is not None)
    customers = table(metadata, f"{SERVICE}.sales.main.customers", ["columns", "tableConstraints"])
    check("a virtual table, under <service>.<catalog>.<schema>", customers is not None)
    if customers is not None:
        address = next((c for c in customers.columns if c.name.root == "address"), None)
        check("a STRUCT column carries its fields",
              address is not None and [c.name.root for c in address.children or []] == ["city", "zip", "geo"],
              address)
        # OpenMetadata keeps the display lower-cased
        check("the node's type text is kept",
              address is not None and address.dataTypeDisplay.lower().startswith("struct(city varchar"), address)
        check("the table's comment is its description",
              customers.description is not None and "nested types" in customers.description.root, customers.description)
    view = table(metadata, f"{SERVICE}.sales.main.big_orders")
    check("a virtual view is a View", view is not None and view.tableType == TableType.View, view and view.tableType)
    nested = table(metadata, f'{SERVICE}.sales."raw.eu".events')
    check("a nested schema (a dot in its name) is one schema", nested is not None)
    check("a catalog the role holds no grant on is absent",
          metadata.get_by_name(entity=Table, fqn=f"{SERVICE}.scratch.main.orders") is None)
    products = table(metadata, f"{SERVICE}.inventory.main.products")
    check("the second catalog is a second database", products is not None)

    orders = table(metadata, f"{SERVICE}.sales.main.orders")
    lineage = metadata.get_lineage_by_name(entity=Table, fqn=f"{SERVICE}.sales.main.orders", up_depth=1, down_depth=1)
    upstream = {e["fromEntity"] for e in (lineage or {}).get("upstreamEdges", [])}  # the edges name ids
    source_orders = table(metadata, f"{SOURCE}.memory.main.orders")
    check("the definition lineage: the virtual table <- its physical source",
          source_orders is not None and str(source_orders.id.root) in {str(u) for u in upstream}, lineage)
    edge = next((e for e in (lineage or {}).get("upstreamEdges", [])
                 if source_orders is not None and e["fromEntity"] == str(source_orders.id.root)), None)
    columns = (edge or {}).get("lineageDetails", {}).get("columnsLineage", [])
    check("column by column",
          any(c.get("toColumn", "").endswith("orders.amount") and any(f.endswith("main.orders.amount") for f in c.get("fromColumns", []))
              for c in columns), columns)
    view_lineage = metadata.get_lineage_by_name(entity=Table, fqn=f"{SERVICE}.sales.main.big_orders", up_depth=1, down_depth=0)
    check("a view's lineage reaches its source", bool((view_lineage or {}).get("upstreamEdges")), view_lineage)

    print("second ingestion: nothing changes")
    ingest()
    again = table(metadata, f"{SERVICE}.sales.main.customers")
    check("re-run: the same entity", again is not None and customers is not None and again.id == customers.id)

    print("an object dropped on the node is removed")
    as_steward("DROP VIRTUAL TABLE inventory.products")
    try:
        ingest()
        gone = metadata.get_by_name(entity=Table, fqn=f"{SERVICE}.inventory.main.products")
        check("the dropped object is gone from OpenMetadata (soft-deleted)", gone is None, gone)
    finally:
        as_steward("CREATE VIRTUAL TABLE inventory.products AS memory.main.products COMMENT 'the price list'")
    ingest()
    # found by the default (non-deleted) lookup: OpenMetadata restored the soft-deleted entity (the 2.0.5 client's
    # include="all" answers None)
    back = metadata.get_by_name(entity=Table, fqn=f"{SERVICE}.inventory.main.products")
    check("restored on the node, it is back", back is not None and not back.deleted, back and back.deleted)

    print("PASS: OpenMetadata connector" if failures == 0 else f"FAIL: {failures} check(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
