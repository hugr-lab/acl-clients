#!/usr/bin/env bash
# The Python recipe against the dev node (spec 004).
#   dev/marquez.sh; ACL_PIPELINES=1 ACL_OTEL=... dev/node.sh &     # once
#   examples/pipelines/python/run.sh
# Env: ACL_TOKEN (default dev/token.sh), OPENLINEAGE_URL (default http://localhost:5050), ACL_URI.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
VENV="$HERE/.venv"
if [ ! -x "$VENV/bin/python" ]; then
	python3 -m venv "$VENV"
	"$VENV/bin/pip" install -q adbc-driver-flightsql pyarrow "openlineage-python==1.53.0"
fi
export ACL_TOKEN="${ACL_TOKEN:-$("$ROOT/dev/token.sh")}"
export ACL_INSECURE="${ACL_INSECURE:-1}" # the dev node's self-signed certificate
exec "$VENV/bin/python" "$HERE/job.py"
