#!/usr/bin/env bash
# spec 004: every pipeline recipe against the dev node, then the joined graph checked in Marquez - the
# engine's job and the node's runs meet at the node's own dataset names.
#   dev/marquez.sh
#   ACL_PIPELINES=1 MARQUEZ_URL=http://localhost:5050 ACL_OTEL=<acl-otel build>/extension/acl_otel/acl_otel.duckdb_extension dev/node.sh &
#   dev/pipelines-e2e.sh [spark3|spark4|dbt|python ...]          # default: all
# MARQUEZ_PORT if Marquez is not on 5050.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
PORT="${MARQUEZ_PORT:-5050}"
API="http://localhost:$PORT/api/v1"
NS="${ACL_LINEAGE_NAMESPACE:-acl://dev}"
WHICH="${*:-spark3 spark4 dbt python}"
fail() { echo "FAIL: $*" >&2; exit 1; }
# the jobs that touch a dataset of the node, from Marquez's lineage graph
jobs_of() {
	sleep 4 # acl-otel and the engines send asynchronously
	curl -sf "$API/lineage?nodeId=dataset:$NS:$1&depth=1" |
		python3 -c 'import json,sys; [print(n["id"]) for n in json.load(sys.stdin)["graph"] if n["type"]=="JOB"]'
}
expect() { # <dataset> <job namespace of the engine>
	local jobs; jobs="$(jobs_of "$1")"
	echo "$jobs" | grep -q "^job:$2:" || fail "$1: no $2 job in Marquez ($jobs)"
	echo "$jobs" | grep -q "^job:$NS/client/" || fail "$1: no run of the node in Marquez ($jobs)"
	echo "  ok: $1 - the $2 job and the node's runs in one graph"
}
for what in $WHICH; do
	case "$what" in
	spark3 | spark4)
		version="${what#spark}"; [ "$version" = 3 ] && version=3.5
		SPARK="$version" MARQUEZ_URL="http://host.docker.internal:$PORT" "$ROOT/examples/pipelines/spark/run.sh" \
			>"$HERE/.work/$what.log" 2>&1 || { tail -20 "$HERE/.work/$what.log"; fail "spark ${what#spark}"; }
		grep -q '^DONE' "$HERE/.work/$what.log" || fail "spark ${what#spark} did not finish"
		expect sales.main.spark_out spark ;;
	dbt)
		for run in 1 2; do # the second run replaces each model: the stock table / view swap
			OPENLINEAGE_URL="http://localhost:$PORT" "$ROOT/examples/pipelines/dbt/run.sh" >"$HERE/.work/dbt.log" 2>&1 ||
				{ tail -20 "$HERE/.work/dbt.log"; fail "dbt run $run"; }
			grep -q 'PASS=3 WARN=0 ERROR=0' "$HERE/.work/dbt.log" || fail "dbt run $run did not pass all three models"
		done
		# dbt-ol names a model in its adapter's namespace (duckdb://<path>), the node in its own: the
		# NAME is the same (the node attached under the catalog's name), the namespace a mapping away
		jobs="$(jobs_of sales.dbt_home.order_totals)"
		echo "$jobs" | grep -q "^job:$NS/client/" || fail "sales.dbt_home.order_totals: no run of the node ($jobs)"
		curl -sf "$API/search?q=order_totals&limit=50" | python3 -c '
import json, sys
r = json.load(sys.stdin)["results"]
assert any(x["type"] == "DATASET" and x["name"] == "sales.dbt_home.order_totals" and x["namespace"].startswith("duckdb://") for x in r), r
assert any(x["type"] == "JOB" and x["name"].endswith(".order_totals") and not x["namespace"].startswith("acl://") for x in r), r
' || fail "dbt's own job and dataset for order_totals are not in Marquez"
		echo "  ok: sales.dbt_home.order_totals - the node's runs (the swap followed), dbt's job under the same name" ;;
	python)
		OPENLINEAGE_URL="http://localhost:$PORT" "$ROOT/examples/pipelines/python/run.sh" >"$HERE/.work/python.log" 2>&1 ||
			{ tail -20 "$HERE/.work/python.log"; fail python; }
		grep -q 'COMPLETE$' "$HERE/.work/python.log" || fail "the python job did not complete"
		expect sales.main.py_out python ;;
	*) fail "unknown recipe $what" ;;
	esac
done
echo "PASS: $WHICH"
