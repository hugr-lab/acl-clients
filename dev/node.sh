#!/usr/bin/env bash
# One duckdb-acl node for the examples and the driver: the Flight SQL door over TLS (a self-signed
# certificate, made here), Keycloak's `acl-dev` realm as the issuer with `acl-desktop` as its client
# (discovery + the password handshake), and a small tenant-sliced policy. Uses duckdb-acl's own build.
# Holds the node until Ctrl+C.
#
#   ACL_REPO=../duckdb-acl dev/node.sh            # grpc+tls://localhost:32800
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
if [ ! -f "$WORK/node.crt" ]; then
	openssl req -x509 -newkey rsa:2048 -nodes -days 365 -subj "/CN=localhost" \
		-addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
		-keyout "$WORK/node.key" -out "$WORK/node.crt" 2>/dev/null
fi
cat > "$WORK/node.sql" <<SQL
LOAD httpfs;
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
ACL ADMIN CREATE ISSUER '$KC_REALM' KEYS FROM '$KC_REALM/protocol/openid-connect/certs'
    AUDIENCES ('account') ALGS (RS256) ROLE CLAIM 'realm_access.roles'
    CLAIM MAP '{"tenant": "tenant"}' CLIENT ID 'acl-desktop';
SET GLOBAL acl_allow_anonymous_admin = false;
SELECT acl_flight_serve('grpc+tls://0.0.0.0:$PORT', '$WORK/node.crt', '$WORK/node.key');
SELECT 'node up: grpc+tls://localhost:$PORT' AS status;
SQL
echo "node: grpc+tls://localhost:$PORT (self-signed: $WORK/node.crt), issuer $KC_REALM"
{ cat "$WORK/node.sql"; while true; do sleep 3600; done; } | "$DUCKDB" -unsigned
