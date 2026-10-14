"""The OpenMetadata source for duckdb-acl nodes (acl-clients spec 005): a CustomDatabase service's
`sourcePythonClass`. One database per virtual catalog the connector's principal reads, its schemas, tables and
views with the node's column types, the comments; the definition lineage of duckdb-acl spec 129 through
OpenMetadata's lineage API. An object the node no longer lists is removed (soft-deleted).

The FQNs are `<service>.<catalog>.<schema>.<object>` - what OpenMetadata's `namespaceToServiceMapping` makes of the
node's OpenLineage names (`<namespace>` :: `<catalog>.<schema>.<object>`), so acl-otel's runtime runs land on these
entities.
"""

from __future__ import annotations

import logging
from collections.abc import Iterable
from dataclasses import dataclass, field

from metadata.generated.schema.api.data.createDatabase import CreateDatabaseRequest
from metadata.generated.schema.api.data.createDatabaseSchema import CreateDatabaseSchemaRequest
from metadata.generated.schema.api.data.createTable import CreateTableRequest
from metadata.generated.schema.api.lineage.addLineage import AddLineageRequest
from metadata.generated.schema.entity.data.database import Database
from metadata.generated.schema.entity.data.databaseSchema import DatabaseSchema
from metadata.generated.schema.entity.data.table import Table, TableType
from metadata.generated.schema.entity.services.connections.database.customDatabaseConnection import (
    CustomDatabaseConnection,
)
from metadata.generated.schema.entity.services.databaseService import DatabaseService
from metadata.generated.schema.metadataIngestion.workflow import Source as WorkflowSource
from metadata.generated.schema.type.entityLineage import ColumnLineage, EntitiesEdge, LineageDetails
from metadata.generated.schema.type.entityLineage import Source as LineageSource
from metadata.generated.schema.type.entityReference import EntityReference
from metadata.ingestion.api.models import Either
from metadata.ingestion.api.steps import InvalidSourceException, Source
from metadata.ingestion.models.delete_entity import DeleteEntity
from metadata.ingestion.ometa.ometa_api import OpenMetadata
from metadata.utils import fqn
from metadata.utils.filters import filter_by_database, filter_by_schema, filter_by_table

from acl_openmetadata.node import Node, NodeOptions, Snapshot
from acl_openmetadata.types import to_column

logger = logging.getLogger("acl_openmetadata")


# --- planning: pure, from what the node answered (unit-tested without a server) -----------------------------------


def table_fqn(service: str, catalog: str, schema: str, name: str) -> str:
    return fqn._build(service, catalog, schema, name)


def column_fqn(table: str, field_path: str) -> str:
    # a field path (duckdb-acl spec 102): `address.city`, `items[].price` - a nested column's parts in OpenMetadata
    parts = [p.replace("[]", "") for p in field_path.split(".") if p.replace("[]", "")]
    # the table's FQN is already built (quoted where a part holds a dot): the column's parts are appended to it
    return table + "." + fqn._build(*parts) if parts else table


@dataclass
class LineageEdge:
    target: str  # the virtual object's FQN
    source: str  # its source's FQN
    columns: dict[str, set[str]] = field(default_factory=dict)  # target column FQN -> source column FQNs


def source_fqn(row: dict, service: str, source_services: dict[str, str]) -> str | None:
    """The FQN a row's source has in OpenMetadata: the node's own objects under this service, a physical source
    under the prefix its namespace maps to; None when nothing maps it."""
    namespace, name = row["source_namespace"], row["source_name"]
    if namespace == row["namespace"]:
        return fqn._build(service, *fqn.split(name))  # another virtual object of the node: `<vcat>.<schema>.<object>`
    prefix = source_services.get(namespace)
    if prefix is None:
        return None
    return fqn._build(*fqn.split(prefix), *fqn.split(name))


def plan_lineage(rows: list[dict], service: str, source_services: dict[str, str]) -> tuple[list[LineageEdge], set[str]]:
    """The table-level edges with their column lineage (DIRECT edges into a column); the namespaces nothing maps."""
    edges: dict[tuple[str, str], LineageEdge] = {}
    unmapped: set[str] = set()
    for row in rows:
        if not row.get("source_name"):
            continue  # an object with no edge: listed, nothing to draw
        source = source_fqn(row, service, source_services)
        if source is None:
            unmapped.add(row["source_namespace"])
            continue
        target = table_fqn(service, row["catalog"], row["schema"], row["object"])
        edge = edges.setdefault((target, source), LineageEdge(target, source))
        if row.get("type") == "DIRECT" and row.get("field") and row.get("source_field"):
            edge.columns.setdefault(column_fqn(target, row["field"]), set()).add(
                column_fqn(source, row["source_field"])
            )
    return list(edges.values()), unmapped


# --- the source --------------------------------------------------------------------------------------------------------


class AclNodeSource(Source):
    def __init__(self, config: WorkflowSource, metadata: OpenMetadata):
        self.config = config
        self.metadata = metadata
        self.service_connection: CustomDatabaseConnection = config.serviceConnection.root.config
        options = self.service_connection.connectionOptions
        self.options = NodeOptions.from_options(dict(options.root) if options else {})
        self.service = config.serviceName
        self.snapshot: Snapshot | None = None
        super().__init__()

    @classmethod
    def create(cls, config_dict: dict, metadata: OpenMetadata, pipeline_name: str | None = None) -> AclNodeSource:
        config = WorkflowSource.model_validate(config_dict)
        connection = config.serviceConnection.root.config
        if not isinstance(connection, CustomDatabaseConnection):
            raise InvalidSourceException(f"Expected CustomDatabaseConnection, got {connection}")
        return cls(config, metadata)

    def prepare(self):
        node = Node.connect(self.options)
        try:
            self.snapshot = node.read()
        finally:
            node.close()

    def test_connection(self) -> None:
        node = Node.connect(self.options)
        try:
            node.query("SELECT 1")
        finally:
            node.close()

    def close(self) -> None:
        pass

    # what the filters of the service connection leave
    def _database_kept(self, catalog: str) -> bool:
        return not filter_by_database(self.service_connection.databaseFilterPattern, catalog)

    def _schema_kept(self, schema: str) -> bool:
        return not filter_by_schema(self.service_connection.schemaFilterPattern, schema)

    def _table_kept(self, name: str) -> bool:
        return not filter_by_table(self.service_connection.tableFilterPattern, name)

    def _iter(self, *_, **__) -> Iterable[Either]:
        snapshot = self.snapshot
        yield Either(right=self.metadata.get_create_service_from_source(entity=DatabaseService, config=self.config))
        seen_databases, seen_schemas, seen_tables = set(), set(), set()
        listed = {t["catalog"] for t in snapshot.tables} | {c for c, _ in snapshot.schemas}
        for catalog in sorted(listed):
            if not self._database_kept(catalog):
                continue
            seen_databases.add(fqn._build(self.service, catalog))
            yield Either(
                right=CreateDatabaseRequest(
                    name=catalog, service=self.service, description=snapshot.catalogs.get(catalog) or None
                )
            )
        for catalog, schema in snapshot.schemas:
            if fqn._build(self.service, catalog) not in seen_databases or not self._schema_kept(schema):
                continue
            seen_schemas.add(fqn._build(self.service, catalog, schema))
            yield Either(
                right=CreateDatabaseSchemaRequest(name=schema, database=fqn._build(self.service, catalog))
            )
        for table in snapshot.tables:
            schema_fqn = fqn._build(self.service, table["catalog"], table["schema"])
            if schema_fqn not in seen_schemas or not self._table_kept(table["name"]):
                continue
            seen_tables.add(table_fqn(self.service, table["catalog"], table["schema"], table["name"]))
            columns = [
                to_column(c["name"], c["type"], c["comment"] or None, c["position"])
                for c in snapshot.columns.get((table["catalog"], table["schema"], table["name"]), [])
            ]
            yield Either(
                right=CreateTableRequest(
                    name=table["name"],
                    databaseSchema=schema_fqn,
                    tableType=TableType.View if table["type"] == "VIEW" else TableType.Regular,
                    columns=columns,
                    description=table["comment"] or None,
                )
            )
        yield from self._deletions(seen_databases, seen_schemas, seen_tables)
        yield from self._lineage()

    def _deletions(self, databases: set[str], schemas: set[str], tables: set[str]) -> Iterable[Either]:
        """What OpenMetadata holds under this service that the node no longer lists (or the filters no longer keep)
        is removed - soft-deleted, as OpenMetadata's own sources mark a dropped table. Walked down the hierarchy:
        a database gone takes its schemas and tables with it (recursive)."""
        for database in list(self.metadata.list_all_entities(entity=Database, params={"service": self.service})):
            database_fqn = database.fullyQualifiedName.root
            if database_fqn not in databases:
                yield Either(right=DeleteEntity(entity=database, recursive=True))
                continue
            for schema in list(
                self.metadata.list_all_entities(entity=DatabaseSchema, params={"database": database_fqn})
            ):
                schema_fqn = schema.fullyQualifiedName.root
                if schema_fqn not in schemas:
                    yield Either(right=DeleteEntity(entity=schema, recursive=True))
                    continue
                for table in list(self.metadata.list_all_entities(entity=Table, params={"databaseSchema": schema_fqn})):
                    if table.fullyQualifiedName.root not in tables:
                        yield Either(right=DeleteEntity(entity=table))

    def _lineage(self) -> Iterable[Either]:
        rows = self.snapshot.lineage
        if not rows:
            return
        edges, unmapped = plan_lineage(rows, self.service, self.options.source_services)
        for namespace in sorted(unmapped):
            logger.warning(
                "no OpenMetadata service for the source namespace %s - its edges are left out (sourceServices)",
                namespace,
            )
        for edge in edges:
            target = self.metadata.get_by_name(entity=Table, fqn=edge.target)
            source = self.metadata.get_by_name(entity=Table, fqn=edge.source)
            if target is None or source is None:
                logger.info("lineage %s <- %s: an end OpenMetadata does not know", edge.target, edge.source)
                continue
            columns = [
                ColumnLineage(fromColumns=sorted(sources), toColumn=to_column_fqn)
                for to_column_fqn, sources in sorted(edge.columns.items())
            ]
            yield Either(
                right=AddLineageRequest(
                    edge=EntitiesEdge(
                        fromEntity=EntityReference(id=source.id, type="table"),
                        toEntity=EntityReference(id=target.id, type="table"),
                        lineageDetails=LineageDetails(
                            columnsLineage=columns or None,
                            source=LineageSource.ViewLineage,
                            description="duckdb-acl definition lineage (acl_lineage_definitions)",
                        ),
                    )
                )
            )
