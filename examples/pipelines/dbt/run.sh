#!/usr/bin/env bash
# The dbt recipe against the dev node (spec 004): dbt-ol runs the project and sends dbt's own lineage,
# the node sends its runs under the same parent.
#   dev/marquez.sh; ACL_PIPELINES=1 ACL_OTEL=... dev/node.sh &     # once
#   examples/pipelines/dbt/run.sh [dbt args]                       # default: run
# Env: ACL_TOKEN (default dev/token.sh), OPENLINEAGE_PARENT_ID (default a fresh airflow-like step),
# OPENLINEAGE_URL (default http://localhost:5050).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
VENV="$HERE/.venv"
if [ ! -f "$VENV/.installed" ]; then # a marker written last: a half-done install is done again
	python3 -m venv "$VENV"
	# the node is duckdb 2.0: its quack protocol needs a 2.0 client (a pre-release until 2.0.0 is out)
	"$VENV/bin/pip" install -q "dbt-core==1.12.5" "dbt-duckdb==1.11.0" "openlineage-dbt==1.53.0"
	"$VENV/bin/pip" install -q --pre --upgrade "duckdb>=2.0.0.dev0"
	touch "$VENV/.installed"
fi
export ACL_TOKEN="${ACL_TOKEN:-$("$ROOT/dev/token.sh")}"
export ACL_CA_CERT="${ACL_CA_CERT:-$ROOT/dev/.work/node.crt}"
export OPENLINEAGE_URL="${OPENLINEAGE_URL:-http://localhost:5050}"
export OPENLINEAGE_NAMESPACE="${OPENLINEAGE_NAMESPACE:-dbt}"
export OPENLINEAGE_PARENT_ID="${OPENLINEAGE_PARENT_ID:-airflow/daily.dbt_build/$(python3 -c 'import uuid; print(uuid.uuid4())')}"
export DBT_PROFILES_DIR="$HERE"
export PATH="$VENV/bin:$PATH" # dbt-ol runs the `dbt` beside it
cd "$HERE"
echo "parent $OPENLINEAGE_PARENT_ID"
"$VENV/bin/dbt-ol" "${@:-run}"
