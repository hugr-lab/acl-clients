#!/usr/bin/env bash
# acl-clients spec 005 end to end: the connector (integrations/openmetadata) ingests the dev node into OpenMetadata.
# Needs: dev/keycloak.sh (the client acl-om), ACL_OPENMETADATA=1 dev/node.sh (a node with duckdb-acl spec 129 - by
# default on :32810, beside a standing dev node on :32800), dev/openmetadata.sh, uv (or a Python 3.11).
# The credentials are fetched into this run's environment and never written: the ingestion bot's JWT from
# OpenMetadata, acl-om's client secret and a steward's token from Keycloak.
#   ACL_NODE_URI=grpc+tls://localhost:32810 dev/openmetadata-e2e.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
ACL_REPO="${ACL_REPO:-$ROOT/../duckdb-acl}"
OM="http://127.0.0.1:${OM_PORT:-8585}"
export ACL_NODE_URI="${ACL_NODE_URI:-grpc+tls://localhost:32810}"
export OM_URL="$OM/api"
env_get() { grep -E "^$1=" "$ACL_REPO/.env" 2>/dev/null | head -1 | cut -d= -f2-; }
KC="http://${KEY_CLOAK_HOST:-$(env_get KEY_CLOAK_HOST)}:${KEY_CLOAK_PORT:-$(env_get KEY_CLOAK_PORT)}"
export ACL_ISSUER="$KC/realms/acl-dev"
fail() { echo "FAIL: $*" >&2; exit 1; }
curl -sf "$OM/api/v1/system/version" >/dev/null || fail "no OpenMetadata at $OM (dev/openmetadata.sh)"
curl -sf "$ACL_ISSUER/.well-known/openid-configuration" >/dev/null || fail "no Keycloak realm at $ACL_ISSUER (dev/keycloak.sh)"

# the connector's environment: OpenMetadata's ingestion library and the connector, once
VENV="$HERE/.work/omvenv"
if [ ! -x "$VENV/bin/python" ]; then
	if command -v uv >/dev/null; then
		uv venv -q -p 3.11 "$VENV"
		VIRTUAL_ENV="$VENV" uv pip install -q -e "$ROOT/integrations/openmetadata[test]"
	else
		python3.11 -m venv "$VENV"
		"$VENV/bin/pip" install -q -e "$ROOT/integrations/openmetadata[test]"
	fi
fi
"$VENV/bin/python" -m pytest -q "$ROOT/integrations/openmetadata/tests" | tail -1

# the ingestion bot's JWT: the admin's login (the release's default), then the bot's auth mechanism
admin_token="$(curl -sf -X POST "$OM/api/v1/users/login" -H 'Content-Type: application/json' \
	-d "{\"email\":\"admin@open-metadata.org\",\"password\":\"$(printf admin | base64)\"}" |
	python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')"
bot_user="$(curl -sf "$OM/api/v1/bots/name/ingestion-bot" -H "Authorization: Bearer $admin_token" |
	python3 -c 'import json,sys; print(json.load(sys.stdin)["botUser"]["id"])')"
OM_JWT="$(curl -sf "$OM/api/v1/users/auth-mechanism/$bot_user" -H "Authorization: Bearer $admin_token" |
	python3 -c 'import json,sys; print(json.load(sys.stdin)["config"]["JWTToken"])')"
export OM_JWT
# acl-om's client secret (it stays in Keycloak; the run reads it), a steward's token
kc_admin="$(curl -sf -d grant_type=password -d client_id=admin-cli \
	-d "username=${KEY_CLOAK_ADMIN_USER:-$(env_get KEY_CLOAK_ADMIN_USER)}" \
	-d "password=${KEY_CLOAK_ADMIN_PASS:-$(env_get KEY_CLOAK_ADMIN_PASS)}" \
	"$KC/realms/master/protocol/openid-connect/token" | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')"
cid="$(curl -sf "$KC/admin/realms/acl-dev/clients?clientId=acl-om" -H "Authorization: Bearer $kc_admin" |
	python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')" || fail "no client acl-om (dev/keycloak.sh)"
ACL_OM_CLIENT_SECRET="$(curl -sf "$KC/admin/realms/acl-dev/clients/$cid/client-secret" -H "Authorization: Bearer $kc_admin" |
	python3 -c 'import json,sys; print(json.load(sys.stdin)["value"])')"
export ACL_OM_CLIENT_SECRET
ACL_STEWARD_TOKEN="$("$HERE/token.sh" steward1 "${ACL_KC_STEWARD1_PASS:-steward1-pass}")"
export ACL_STEWARD_TOKEN

"$VENV/bin/python" "$HERE/openmetadata_e2e.py"
