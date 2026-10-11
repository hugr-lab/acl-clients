#!/usr/bin/env bash
# The dev IdP for these clients: duckdb-acl's standing realm `acl-dev` (users analyst1/acme,
# analyst2/globex, viewer1 - see duckdb-acl test/live/keycloak_realm.sh), an administrator steward1 (the
# realm role `steward`, the node's policy bundle - spec 007.2's consoles and view-as), an operator ops1 (`operator`,
# the cluster bundle - spec 007.3's node and cluster folders) plus one public client,
# `acl-desktop`, that speaks every flow the driver offers: auth code + PKCE (a loopback redirect on
# 127.0.0.1), device, password, refresh. The node advertises it through `discover-auth`, so a client
# needs nothing but the node's address. Idempotent.
#
#   ACL_REPO=../duckdb-acl dev/keycloak.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ACL_REPO="${ACL_REPO:-$HERE/../../duckdb-acl}"
"$ACL_REPO/test/live/keycloak_realm.sh"
env_get() { grep -E "^$1=" "$ACL_REPO/.env" 2>/dev/null | head -1 | cut -d= -f2-; }
KC="http://${KEY_CLOAK_HOST:-$(env_get KEY_CLOAK_HOST)}:${KEY_CLOAK_PORT:-$(env_get KEY_CLOAK_PORT)}"
REALM="${ACL_KC_REALM:-acl-dev}"
TOKEN=$(curl -sf -d grant_type=password -d client_id=admin-cli \
	-d "username=${KEY_CLOAK_ADMIN_USER:-$(env_get KEY_CLOAK_ADMIN_USER)}" \
	-d "password=${KEY_CLOAK_ADMIN_PASS:-$(env_get KEY_CLOAK_ADMIN_PASS)}" \
	"$KC/realms/master/protocol/openid-connect/token" | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')
AUTH="Authorization: Bearer $TOKEN"
# a dev realm over plain http: Keycloak's default sslRequired=external refuses a request it does not see as
# local, and through Docker Desktop's port forwarding the node's calls may not look local - "HTTPS required"
curl -sf -X PUT "$KC/admin/realms/$REALM" -H "$AUTH" -H "Content-Type: application/json" \
	-d '{"sslRequired":"none"}' >/dev/null
echo "  realm $REALM: sslRequired=none (dev only)"
code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$KC/admin/realms/$REALM/clients" -H "$AUTH" \
	-H "Content-Type: application/json" -d '{
	"clientId":"acl-desktop","publicClient":true,
	"standardFlowEnabled":true,"directAccessGrantsEnabled":true,
	"redirectUris":["http://127.0.0.1/*","http://localhost/*"],"webOrigins":["+"],
	"attributes":{"pkce.code.challenge.method":"S256","oauth2.device.authorization.grant.enabled":"true"},
	"protocolMappers":[{"name":"tenant","protocol":"openid-connect","protocolMapper":"oidc-usermodel-attribute-mapper",
		"config":{"user.attribute":"tenant","claim.name":"tenant","jsonType.label":"String","access.token.claim":"true"}}]}')
echo "  client acl-desktop (PKCE + device + password): $([ "$code" = 201 ] && echo created || echo "exists ($code)")"
# spec 007.2: an administrator - the realm role `steward`, which dev/node.sh maps to the node's policy bundle
code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$KC/admin/realms/$REALM/roles" -H "$AUTH" \
	-H "Content-Type: application/json" -d '{"name":"steward"}')
body='{"username":"steward1","enabled":true,"emailVerified":true,"email":"steward1@'"$REALM"'.local",
	"firstName":"steward1","lastName":"user","requiredActions":[],"attributes":{"tenant":["acme"]}}'
created=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$KC/admin/realms/$REALM/users" -H "$AUTH" \
	-H "Content-Type: application/json" -d "$body")
uid=$(curl -sf "$KC/admin/realms/$REALM/users?username=steward1&exact=true" -H "$AUTH" \
	| python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')
curl -sf -o /dev/null -X PUT "$KC/admin/realms/$REALM/users/$uid/reset-password" -H "$AUTH" \
	-H "Content-Type: application/json" -d "{\"type\":\"password\",\"value\":\"${ACL_KC_STEWARD1_PASS:-steward1-pass}\",\"temporary\":false}"
rid=$(curl -sf "$KC/admin/realms/$REALM/roles/steward" -H "$AUTH" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
curl -s -o /dev/null -X POST "$KC/admin/realms/$REALM/users/$uid/role-mappings/realm" -H "$AUTH" \
	-H "Content-Type: application/json" -d "[{\"id\":\"$rid\",\"name\":\"steward\"}]"
echo "  user steward1 (role steward): $([ "$created" = 201 ] && echo created || echo converged)"
# spec 007.3: an operator - the realm role `operator`, which dev/node.sh maps to the node's cluster bundle (node and cluster views)
code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$KC/admin/realms/$REALM/roles" -H "$AUTH" \
	-H "Content-Type: application/json" -d '{"name":"operator"}')
body='{"username":"ops1","enabled":true,"emailVerified":true,"email":"ops1@'"$REALM"'.local",
	"firstName":"ops1","lastName":"user","requiredActions":[],"attributes":{"tenant":["acme"]}}'
created=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$KC/admin/realms/$REALM/users" -H "$AUTH" \
	-H "Content-Type: application/json" -d "$body")
uid=$(curl -sf "$KC/admin/realms/$REALM/users?username=ops1&exact=true" -H "$AUTH" \
	| python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')
curl -sf -o /dev/null -X PUT "$KC/admin/realms/$REALM/users/$uid/reset-password" -H "$AUTH" \
	-H "Content-Type: application/json" -d "{\"type\":\"password\",\"value\":\"${ACL_KC_OPS1_PASS:-ops1-pass}\",\"temporary\":false}"
rid=$(curl -sf "$KC/admin/realms/$REALM/roles/operator" -H "$AUTH" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
curl -s -o /dev/null -X POST "$KC/admin/realms/$REALM/users/$uid/role-mappings/realm" -H "$AUTH" \
	-H "Content-Type: application/json" -d "[{\"id\":\"$rid\",\"name\":\"operator\"}]"
echo "  user ops1 (role operator): $([ "$created" = 201 ] && echo created || echo converged)"
