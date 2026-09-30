#!/usr/bin/env python3
"""Connect to a duckdb-acl node over Arrow Flight SQL with ADBC, and read as a principal.

Two ways to authenticate, the same two every example here shows:

  ACL_TOKEN=<jwt>                      a token your IdP issued (sent as `authorization: Bearer`)
  ACL_USER=<user> ACL_PASSWORD=<pw>    the node's password handshake: the node exchanges them at the
                                       IdP (the issuer's CLIENT ID, OAuth password grant) - TLS only

  ACL_URI      grpc+tls://localhost:32800 by default (dev/node.sh)
  ACL_INSECURE 1 to accept the dev node's self-signed certificate

    pip install adbc-driver-flightsql pyarrow
    ACL_TOKEN=$(../../dev/token.sh) ACL_INSECURE=1 python3 connect.py
    ACL_USER=analyst1 ACL_PASSWORD=analyst1-pass ACL_INSECURE=1 python3 connect.py
"""
import os
import sys

import adbc_driver_flightsql.dbapi as flight_sql
from adbc_driver_flightsql import DatabaseOptions

uri = os.environ.get("ACL_URI", "grpc+tls://localhost:32800")
# the node's session is the client's connection, identified by a cookie the client must echo: without
# it every call is a fresh session, and a transaction (autocommit off, DBAPI's default) is refused
kwargs = {"adbc.flight.sql.rpc.with_cookie_middleware": "true"}
if os.environ.get("ACL_INSECURE") == "1":
    kwargs[DatabaseOptions.TLS_SKIP_VERIFY.value] = "true"
if os.environ.get("ACL_TOKEN"):
    kwargs[DatabaseOptions.AUTHORIZATION_HEADER.value] = "Bearer " + os.environ["ACL_TOKEN"]
elif os.environ.get("ACL_USER"):
    # ADBC runs the Flight BasicAuth handshake with these; the node answers with the bearer the IdP
    # issued, and the driver sends it on every call after
    kwargs["username"] = os.environ["ACL_USER"]
    kwargs["password"] = os.environ.get("ACL_PASSWORD", "")
else:
    sys.exit("set ACL_TOKEN, or ACL_USER and ACL_PASSWORD")

with flight_sql.connect(uri, db_kwargs=kwargs) as conn:
    with conn.cursor() as cur:
        # the principal's catalog: virtual names only - the physical databases behind them are invisible
        cur.execute("SHOW TABLES")
        print("tables:", [row[0] for row in cur.fetchall()])
        # row-level security: the token's tenant claim filters every read
        cur.execute("SELECT id, tenant, amount FROM orders ORDER BY id")
        table = cur.fetch_arrow_table()
        print(table.to_pandas() if hasattr(table, "to_pandas") else table)
