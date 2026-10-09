#!/usr/bin/env bash
# A Marquez for the pipeline recipes (spec 004): the OpenLineage backend acl-otel sends the node's
# lineage to, and the recipes' engines send theirs to - one graph to look at.
#   dev/marquez.sh            # up, API on http://localhost:${MARQUEZ_PORT:-5050}
#   dev/marquez.sh down       # gone, data included
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
export MARQUEZ_PORT="${MARQUEZ_PORT:-5050}"
if [ "${1:-up}" = "down" ]; then
	docker compose -p acl-clients-marquez -f "$HERE/marquez-compose.yml" down -v
	exit 0
fi
docker compose -p acl-clients-marquez -f "$HERE/marquez-compose.yml" up -d
for _ in $(seq 1 60); do
	curl -sf "http://localhost:$MARQUEZ_PORT/api/v1/namespaces" >/dev/null && { echo "marquez: http://localhost:$MARQUEZ_PORT"; exit 0; }
	sleep 2
done
echo "marquez did not come up on :$MARQUEZ_PORT" >&2
exit 1
