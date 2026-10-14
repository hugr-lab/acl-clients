#!/usr/bin/env bash
# acl-clients spec 005: an OpenMetadata 2.0.5 for the connector (dev/openmetadata-compose.yml - the release's own
# compose without its Airflow), on 127.0.0.1:${OM_PORT:-8585}. Waits until it answers. `down` stops it (and keeps
# nothing: `down -v`).
#   dev/openmetadata.sh [down]
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
COMPOSE=(docker compose -f "$HERE/openmetadata-compose.yml")
if [ "${1:-}" = down ]; then
	"${COMPOSE[@]}" down -v
	exit 0
fi
"${COMPOSE[@]}" up -d
OM="http://127.0.0.1:${OM_PORT:-8585}"
for _ in $(seq 1 120); do curl -sf "$OM/api/v1/system/version" >/dev/null && break; sleep 5; done
curl -sf "$OM/api/v1/system/version" || { echo "OpenMetadata did not come up" >&2; exit 1; }
echo
echo "OpenMetadata on $OM (admin@open-metadata.org / admin - the release's test defaults)"
