"""A duckdb-acl node as the connector sees it: its Flight SQL door, as a service principal.

Everything is read through the principal's own surfaces - information_schema and duckdb-acl spec 129's
acl_lineage_definitions() - so the connector sees the catalog exactly as its role does, and needs neither an
administration scope nor ACL NATIVE. No credential is ever a value of the connector's configuration: a token, or a
client secret, is read from the environment variable the configuration names.
"""

from __future__ import annotations

import json
import logging
import os
import urllib.parse
import urllib.request
from dataclasses import dataclass, field

logger = logging.getLogger("acl_openmetadata")


@dataclass
class NodeOptions:
    """The service connection's connectionOptions (all strings, as OpenMetadata keeps them)."""

    uri: str  # grpc+tls://host:port - the node's Flight SQL door
    token_env: str = "ACL_NODE_TOKEN"  # a bearer token in this variable, or:
    issuer: str | None = None  # the node's issuer, for OAuth client credentials
    client_id: str | None = None
    client_secret_env: str | None = None  # the client secret's variable (never the secret itself)
    scope: str | None = None
    insecure: bool = False  # skip the door's certificate check (a dev node's self-signed certificate)
    # a source's OpenLineage namespace -> the OpenMetadata FQN prefix its datasets live under: a service
    # (`pg_prod`, when the name carries the database: `postgres://host:5432` names `db.schema.table`) or a
    # service and database (`pg_prod.shop`, for `acl://<ns>/source/<alias>`, whose names are `schema.table`)
    source_services: dict[str, str] = field(default_factory=dict)

    @staticmethod
    def from_options(options: dict[str, str]) -> NodeOptions:
        def opt(name: str) -> str | None:
            value = options.get(name)
            return None if value is None or str(value) == "" else str(value)

        uri = opt("uri")
        if not uri:
            raise ValueError("acl_openmetadata: connectionOptions.uri names the node's door (grpc+tls://host:port)")
        for forbidden in ("token", "clientSecret", "password", "secret"):
            if opt(forbidden):
                # a credential in the service connection would be stored in OpenMetadata in the clear
                raise ValueError(
                    f"acl_openmetadata: connectionOptions.{forbidden} is refused - name an environment variable "
                    "(tokenEnv, clientSecretEnv) that holds it"
                )
        services = opt("sourceServices")
        return NodeOptions(
            uri=uri,
            token_env=opt("tokenEnv") or "ACL_NODE_TOKEN",
            issuer=opt("issuer"),
            client_id=opt("clientId"),
            client_secret_env=opt("clientSecretEnv"),
            scope=opt("scope"),
            insecure=(opt("insecure") or "false").lower() in ("1", "true", "yes"),
            source_services=json.loads(services) if services else {},
        )


def acquire_token(options: NodeOptions) -> str:
    """The bearer the door takes: client credentials when configured, else the token variable."""
    if options.issuer and options.client_id and options.client_secret_env:
        secret = os.environ.get(options.client_secret_env)
        if not secret:
            raise ValueError(f"acl_openmetadata: the client secret's variable {options.client_secret_env} is not set")
        discovery = options.issuer.rstrip("/") + "/.well-known/openid-configuration"
        with urllib.request.urlopen(discovery, timeout=30) as response:  # noqa: S310 - the issuer's own URL
            token_endpoint = json.load(response)["token_endpoint"]
        form = {"grant_type": "client_credentials", "client_id": options.client_id, "client_secret": secret}
        if options.scope:
            form["scope"] = options.scope
        request = urllib.request.Request(
            token_endpoint, data=urllib.parse.urlencode(form).encode(), method="POST",
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        with urllib.request.urlopen(request, timeout=30) as response:  # noqa: S310
            return json.load(response)["access_token"]
    token = os.environ.get(options.token_env)
    if not token:
        raise ValueError(
            f"acl_openmetadata: no credential - set {options.token_env}, or issuer + clientId + clientSecretEnv"
        )
    return token


@dataclass
class Snapshot:
    """What the principal reads, once per run."""

    catalogs: dict[str, str | None]  # catalog -> comment
    schemas: list[tuple[str, str]]  # (catalog, schema)
    tables: list[dict]  # catalog, schema, name, type, comment
    columns: dict[tuple[str, str, str], list[dict]]  # (catalog, schema, table) -> name, position, type, comment
    lineage: list[dict] | None  # spec 129's rows; None when the role holds no `lineage`


class Node:
    def __init__(self, connection):
        self.connection = connection

    @staticmethod
    def connect(options: NodeOptions) -> Node:
        import adbc_driver_flightsql.dbapi as flight
        from adbc_driver_flightsql import DatabaseOptions

        db_kwargs = {
            DatabaseOptions.AUTHORIZATION_HEADER.value: "Bearer " + acquire_token(options),
            # one session on the door for the run (duckdb-acl spec 050)
            DatabaseOptions.WITH_COOKIE_MIDDLEWARE.value: "true",
        }
        if options.insecure:
            db_kwargs[DatabaseOptions.TLS_SKIP_VERIFY.value] = "true"
        # reads only: no transaction of ours on the door (a DBAPI connection opens one unless told otherwise)
        return Node(flight.connect(options.uri, db_kwargs=db_kwargs, autocommit=True))

    def query(self, sql: str) -> list[dict]:
        cursor = self.connection.cursor()
        try:
            cursor.execute(sql)
            names = [d[0] for d in cursor.description]
            return [dict(zip(names, row)) for row in cursor.fetchall()]
        finally:
            cursor.close()

    def close(self) -> None:
        self.connection.close()

    def read(self) -> Snapshot:
        catalogs: dict[str, str | None] = {}
        for row in self.query("SELECT database_name, comment FROM duckdb_databases() ORDER BY 1"):
            catalogs[row["database_name"]] = row["comment"]
        schemas = [
            (r["catalog_name"], r["schema_name"])
            for r in self.query("SELECT catalog_name, schema_name FROM information_schema.schemata ORDER BY 1, 2")
        ]
        tables = [
            {"catalog": r["table_catalog"], "schema": r["table_schema"], "name": r["table_name"],
             "type": r["table_type"], "comment": r["comment"]}
            for r in self.query(
                'SELECT table_catalog, table_schema, table_name, table_type, "TABLE_COMMENT" AS comment '
                "FROM information_schema.tables ORDER BY 1, 2, 3"
            )
        ]
        columns: dict[tuple[str, str, str], list[dict]] = {}
        for r in self.query(
            'SELECT table_catalog, table_schema, table_name, column_name, ordinal_position, data_type, '
            '"COLUMN_COMMENT" AS comment FROM information_schema.columns ORDER BY 1, 2, 3, 5'
        ):
            columns.setdefault((r["table_catalog"], r["table_schema"], r["table_name"]), []).append(
                {"name": r["column_name"], "position": r["ordinal_position"], "type": r["data_type"],
                 "comment": r["comment"]}
            )
        lineage: list[dict] | None
        try:
            lineage = self.query("SELECT * FROM acl_lineage_definitions()")
        except Exception as error:  # noqa: BLE001 - the node's refusal is the answer
            if "lineage capability" not in str(error):
                raise
            logger.warning("the connector's role holds no `lineage` capability: no definition lineage (spec 129)")
            lineage = None
        return Snapshot(catalogs, schemas, tables, columns, lineage)
