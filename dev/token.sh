#!/usr/bin/env bash
# A dev access token from the `acl-dev` realm by the password grant (the examples' ACL_TOKEN).
#   dev/token.sh [user] [password]      # defaults: analyst1 / analyst1-pass (the realm script's)
set -euo pipefail
KC_REALM="${ACL_KEYCLOAK:-http://localhost:18070/realms/acl-dev}"
curl -sf -d grant_type=password -d client_id=acl-desktop -d "username=${1:-analyst1}" \
	-d "password=${2:-${ACL_DEV_PASSWORD:-analyst1-pass}}" "$KC_REALM/protocol/openid-connect/token" |
	python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'
