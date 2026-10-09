#!/usr/bin/env bash
# One duckdb-acl node for the examples and the driver: the Flight SQL door over TLS (a self-signed
# certificate, made here), Keycloak's `acl-dev` realm as the issuer with `acl-desktop` as its client
# (discovery + the password handshake), and a small tenant-sliced policy. Uses duckdb-acl's own build.
# Holds the node until Ctrl+C.
#
#   ACL_REPO=../duckdb-acl dev/node.sh            # grpc+tls://localhost:32800
#   ACL_PIPELINES=1 dev/node.sh                    # + the pipeline recipes' objects (spec 004): writable
#       tables, a dbt schema, the quack door on :31900, lineage on - sent to Marquez (dev/marquez.sh)
#       by acl-otel when ACL_OTEL names its built extension
#   ACL_METADATA=1 dev/node.sh                     # + what a tool's tree shows (spec 006): nested types with a
#       COMMENT (a STRUCT in a LIST too), a view, table functions, a nested schema sales.raw.eu, a second
#       catalog, a masked column
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ACL_REPO="${ACL_REPO:-$HERE/../../duckdb-acl}"
BUILD="${BUILD_DIR:-$ACL_REPO/build/release}"
DUCKDB="$BUILD/duckdb"
PORT="${ACL_NODE_PORT:-32800}"
KC_REALM="${ACL_KEYCLOAK:-http://localhost:18070/realms/acl-dev}"
WORK="$HERE/.work"
[ -x "$DUCKDB" ] || { echo "no duckdb CLI at $DUCKDB - build duckdb-acl first" >&2; exit 1; }
mkdir -p "$WORK"
# made once; again when an older one lacks host.docker.internal (the quack client reaches a TLS door
# by a name other than localhost - the pipeline recipes)
if [ ! -f "$WORK/node.crt" ] || ! openssl x509 -in "$WORK/node.crt" -noout -text 2>/dev/null | grep -q host.docker.internal; then
	openssl req -x509 -newkey rsa:2048 -nodes -days 365 -subj "/CN=localhost" \
		-addext "subjectAltName=DNS:localhost,DNS:host.docker.internal,IP:127.0.0.1" \
		-keyout "$WORK/node.key" -out "$WORK/node.crt" 2>/dev/null
fi
PIPELINES=""
PIPELINES_SERVE=""
OTEL=""
if [ -n "${ACL_PIPELINES:-}" ]; then
	QUACK_PORT="${ACL_QUACK_PORT:-31900}"
	MARQUEZ="${MARQUEZ_URL:-http://localhost:5050}"
	OTEL_EXT="${ACL_OTEL:-}"
	if [ -n "$OTEL_EXT" ]; then
		OTEL="LOAD '$OTEL_EXT'; SET GLOBAL acl_otel_lineage = '$MARQUEZ';"
		if [ -n "${ACL_OTEL_ENDPOINT:-}" ]; then # another backend's path (OpenMetadata, DataHub)
			OTEL="$OTEL SET GLOBAL acl_otel_lineage_endpoint = '$ACL_OTEL_ENDPOINT';"
		fi
	fi
	PIPELINES="CREATE SCHEMA memory.dbt_home;
CREATE TABLE spark_out(id INTEGER, total DOUBLE);
CREATE TABLE py_out(id INTEGER, total DOUBLE);
SET GLOBAL acl_allow_anonymous_admin = true;
ACL ADMIN CREATE VIRTUAL TABLE sales.spark_out AS memory.main.spark_out;
ACL ADMIN CREATE VIRTUAL TABLE sales.py_out AS memory.main.py_out;
ACL ADMIN CREATE VIRTUAL SCHEMA sales.dbt_home AS memory.dbt_home;
ACL ADMIN GRANT TABLE sales.spark_out TO ROLE analyst WITH (select, insert);
ACL ADMIN GRANT TABLE sales.py_out TO ROLE analyst WITH (select, insert);
ACL ADMIN GRANT SCHEMA sales.dbt_home TO ROLE analyst WITH (select, insert, update, delete, create, drop);
SET GLOBAL acl_allow_anonymous_admin = false;
SET GLOBAL acl_lineage_level = 'on';
SET GLOBAL acl_lineage_namespace = 'acl://dev';"
	PIPELINES_SERVE="SELECT acl_quack_serve('quack:0.0.0.0:$QUACK_PORT', 'dev-quack-server-token', '$WORK/node.crt', '$WORK/node.key');"
fi
METADATA=""
if [ -n "${ACL_METADATA:-}" ]; then
	# spec 006: every kind of object a JDBC tool lists, granted to the ordinary role analyst
	METADATA="CREATE TYPE mood AS ENUM ('calm', 'busy');
CREATE SCHEMA memory.raw_eu;
CREATE TABLE customers (id INTEGER, name VARCHAR,
    address STRUCT(city VARCHAR, zip VARCHAR, geo STRUCT(lat DOUBLE, lon DOUBLE)),
    tags VARCHAR[], scores INTEGER[3], attrs MAP(VARCHAR, INTEGER), balance DECIMAL(18,3), mood mood,
    ssn VARCHAR, seen TIMESTAMP WITH TIME ZONE, visits STRUCT(city VARCHAR, n INTEGER)[]);
INSERT INTO customers VALUES
    (1, 'Ann', {'city': 'Berlin', 'zip': '10115', 'geo': {'lat': 52.5, 'lon': 13.4}}, ['a', 'b'], [1, 2, 3],
     MAP {'x': 1}, 12.345, 'calm', '123-45-6789', TIMESTAMPTZ '2026-10-09 10:00:00+00',
     [{'city': 'Berlin', 'n': 2}, {'city': 'Paris', 'n': 1}]);
CREATE TABLE memory.raw_eu.events (id INTEGER, kind VARCHAR);
INSERT INTO memory.raw_eu.events VALUES (1, 'click');
CREATE TABLE products (sku VARCHAR, price DOUBLE);
INSERT INTO products VALUES ('p-1', 9.5);
SET GLOBAL acl_allow_anonymous_admin = true;
ACL ADMIN CREATE VIRTUAL TABLE sales.customers AS memory.main.customers PRIMARY KEY (id)
    COMMENT 'customers with nested types';
ACL ADMIN GRANT TABLE sales.customers TO ROLE analyst WITH (select)
    COLUMNS (id, name, address, tags, scores, attrs, balance, mood, ssn = '***', seen, visits);
ACL ADMIN CREATE VIRTUAL VIEW sales.big_orders COMMENT 'orders over 50'
    AS SELECT id, amount FROM memory.main.orders WHERE amount > 50;
ACL ADMIN CREATE VIRTUAL SCHEMA sales.raw.eu AS memory.raw_eu COMMENT 'raw zone, eu';
-- USE SCHEMA needs a schema grant (duckdb-acl spec 114): the catalog grant lists it, but does not seat it
ACL ADMIN GRANT SCHEMA sales.raw.eu TO ROLE analyst WITH (select);
ACL ADMIN CREATE VIRTUAL TABLE FUNCTION sales.orders_over(threshold INTEGER)
    RETURNS TABLE (id INTEGER, amount INTEGER) COMMENT 'orders at or over a threshold'
    AS SELECT id::INTEGER AS id, amount::INTEGER AS amount FROM memory.main.orders WHERE amount >= acl_arg(1);
SELECT acl_add_table_function('sales', 'all_tenants', 'SELECT DISTINCT tenant FROM memory.main.orders', '',
    'tenant VARCHAR', 'every tenant');
ACL ADMIN CREATE VIRTUAL SCALAR sales.shout(text VARCHAR) RETURNS VARCHAR AS upper(acl_arg(1));
ACL ADMIN CREATE VIRTUAL REFERENCE sales.order_customer FROM orders TO customers ON (id = id);
ACL ADMIN CREATE VIRTUAL CATALOG inventory COMMENT 'a second catalog';
ACL ADMIN CREATE VIRTUAL TABLE inventory.products AS memory.main.products COMMENT 'the price list';
ACL ADMIN GRANT CATALOG inventory TO ROLE analyst WITH (select);
SET GLOBAL acl_allow_anonymous_admin = false;"
fi
cat > "$WORK/node.sql" <<SQL
LOAD httpfs;
$OTEL
SET GLOBAL acl_jwks_locations = 'https://, $KC_REALM/';
CREATE TABLE orders AS
    SELECT i AS id, CASE WHEN i % 2 = 0 THEN 'acme' ELSE 'globex' END AS tenant, i * 10 AS amount
    FROM range(10) t(i);
ATTACH ':memory:' AS store;
SELECT acl_use_db('store', 'acl', true);
SET GLOBAL acl_allow_anonymous_admin = true;
ACL ADMIN CREATE VIRTUAL CATALOG sales;
ACL ADMIN CREATE VIRTUAL TABLE sales.orders AS memory.main.orders;
ACL ADMIN CREATE ROLE analyst;
ACL ADMIN GRANT CATALOG sales TO ROLE analyst WITH (select) MAIN;
ACL ADMIN GRANT TABLE sales.orders TO ROLE analyst WITH (select) RLS (tenant = acl_claim('tenant'));
-- duckdb-acl spec 095: the realm's keys by its OIDC discovery; the one client runs every flow, the
-- door's password handshake included (a node runs it as exactly one client)
ACL ADMIN CREATE ISSUER '$KC_REALM' AUDIENCES ('account') ROLE CLAIM 'realm_access.roles'
    CLAIM MAP '{"tenant": "tenant"}' CLIENT ID 'acl-desktop' FLOWS (password, authcode, device);
SET GLOBAL acl_allow_anonymous_admin = false;
$PIPELINES
$METADATA
SELECT acl_flight_serve('grpc+tls://0.0.0.0:$PORT', '$WORK/node.crt', '$WORK/node.key');
$PIPELINES_SERVE
SELECT 'node up: grpc+tls://localhost:$PORT' AS status;
SQL
echo "node: grpc+tls://localhost:$PORT (self-signed: $WORK/node.crt), issuer $KC_REALM"
{ cat "$WORK/node.sql"; while true; do sleep 3600; done; } | "$DUCKDB" -unsigned
