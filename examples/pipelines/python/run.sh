#!/usr/bin/env bash
# The Python recipe against the dev node (spec 004).
#   dev/marquez.sh; ACL_PIPELINES=1 ACL_OTEL=... dev/node.sh &     # once
#   examples/pipelines/python/run.sh
# Env: ACL_TOKEN (default dev/token.sh), OPENLINEAGE_URL (default http://localhost:5050), ACL_URI.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
VENV="$HERE/.venv"
if [ ! -f "$VENV/.installed" ]; then # a marker written last: a half-done install is done again
	python3 -m venv "$VENV"
	"$VENV/bin/pip" install -q "adbc-driver-flightsql==1.12.0" "pyarrow==26.0.0" "openlineage-python==1.53.0"
	touch "$VENV/.installed"
fi
export ACL_TOKEN="${ACL_TOKEN:-$("$ROOT/dev/token.sh")}"
export ACL_CA_CERT="${ACL_CA_CERT:-$ROOT/dev/.work/node.crt}" # the dev node's self-signed certificate
exec "$VENV/bin/python" "$HERE/job.py"
