#!/usr/bin/env bash
# spec 004: every pipeline recipe against the dev node, then the joined graph checked in Marquez - the
# engine's job and the node's runs meet at the node's own dataset names.
#   dev/marquez.sh
#   ACL_PIPELINES=1 MARQUEZ_URL=http://localhost:5050 ACL_OTEL=<acl-otel build>/extension/acl_otel/acl_otel.duckdb_extension dev/node.sh &
#   dev/pipelines-e2e.sh [spark3|spark4|dbt|python ...]          # default: all
# MARQUEZ_PORT if Marquez is not on 5050. Each recipe runs under a parent job named for this very run,
# so what an earlier run left in Marquez never satisfies a check.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
mkdir -p "$HERE/.work"
PORT="${MARQUEZ_PORT:-5050}"
API="http://localhost:$PORT/api/v1"
export ACL_LINEAGE_NAMESPACE="${ACL_LINEAGE_NAMESPACE:-acl://dev}"
NS="$ACL_LINEAGE_NAMESPACE"
STAMP="e2e$(date +%s)"
WHICH="${*:-spark3 spark4 dbt python}"
fail() { echo "FAIL: $*" >&2; exit 1; }
new_parent() { # <recipe> -> a parent ref whose job name is this run's own
	echo "e2e/$STAMP.$1/$(python3 -c 'import uuid; print(uuid.uuid4())')"
}
# the jobs touching one of the node's datasets, polled until <pattern> shows (acl-otel and the engines
# send asynchronously)
wait_jobs() { # <dataset> <grep pattern>
	local jobs=""
	for _ in $(seq 1 30); do
		jobs="$(curl -sf --max-time 5 "$API/lineage?nodeId=dataset:$NS:$1&depth=1" |
			python3 -c 'import json,sys; [print(n["id"]) for n in json.load(sys.stdin)["graph"] if n["type"]=="JOB"]' 2>/dev/null || true)"
		if echo "$jobs" | grep -q "$2"; then
			echo "$jobs"
			return 0
		fi
		sleep 2
	done
	fail "$1: no job matching '$2' in Marquez within 60 s (saw: $(echo $jobs))"
}
node_run_of() { # <dataset> <parent job name>: the node's run under this run's parent
	wait_jobs "$1" "^job:$NS/client/[^:]*:$2\." >/dev/null
}
for what in $WHICH; do
	case "$what" in
	spark3 | spark4)
		version="${what#spark}"; [ "$version" = 3 ] && version=3.5
		parent="$(new_parent "$what")"
		OPENLINEAGE_PARENT_ID="$parent" SPARK="$version" MARQUEZ_URL="http://host.docker.internal:$PORT" \
			"$ROOT/examples/pipelines/spark/run.sh" >"$HERE/.work/$what.log" 2>&1 ||
			{ tail -20 "$HERE/.work/$what.log"; fail "spark $version"; }
		grep -q '^DONE' "$HERE/.work/$what.log" || fail "spark $version did not finish"
		node_run_of sales.main.spark_out "$STAMP.$what"
		wait_jobs sales.main.spark_out "^job:spark:" >/dev/null
		echo "  ok: spark $version - the node's runs under this run's parent, Spark's job on the same dataset" ;;
	dbt)
		for run in 1 2; do # the second run replaces each model: the stock table / view swap
			parent="$(new_parent "dbt$run")"
			OPENLINEAGE_PARENT_ID="$parent" OPENLINEAGE_URL="http://localhost:$PORT" \
				"$ROOT/examples/pipelines/dbt/run.sh" >"$HERE/.work/dbt.log" 2>&1 ||
				{ tail -20 "$HERE/.work/dbt.log"; fail "dbt run $run"; }
			grep -q 'PASS=3 WARN=0 ERROR=0' "$HERE/.work/dbt.log" || fail "dbt run $run did not pass all three models"
			# the node followed the swap: its runs of THIS parent reach the model's final name
			node_run_of sales.dbt_home.order_totals "$STAMP.dbt$run"
		done
		# dbt-ol names a model in its adapter's namespace (duckdb://<path>), the node in its own: the NAME
		# is the same (the node attached under the catalog's name), the namespace a mapping away
		curl -sf --max-time 5 "$API/search?q=order_totals&limit=50" | python3 -c '
import json, sys
r = json.load(sys.stdin)["results"]
assert any(x["type"] == "DATASET" and x["name"] == "sales.dbt_home.order_totals" and x["namespace"].startswith("duckdb://") for x in r), r
' || fail "dbt's own dataset sales.dbt_home.order_totals is not in Marquez"
		echo "  ok: dbt - the node's runs follow the swap to sales.dbt_home.order_totals, dbt's dataset of the same name" ;;
	python)
		ACL_JOB_NAME="$STAMP.python" OPENLINEAGE_URL="http://localhost:$PORT" \
			"$ROOT/examples/pipelines/python/run.sh" >"$HERE/.work/python.log" 2>&1 ||
			{ tail -20 "$HERE/.work/python.log"; fail python; }
		run_id="$(sed -n 's/^run \([0-9a-f-]*\) COMPLETE$/\1/p' "$HERE/.work/python.log")"
		[ -n "$run_id" ] || fail "the python job did not complete"
		# the job's own run is the parent: its run id names the node's runs of this execution
		curl -sf --max-time 5 "$API/jobs/runs/$run_id" >/dev/null || fail "the python job's run $run_id is not in Marquez"
		node_run_of sales.main.py_out "$STAMP.python"
		wait_jobs sales.main.py_out "^job:python:$STAMP.python\$" >/dev/null
		echo "  ok: python - its run $run_id and the node's runs on sales.main.py_out" ;;
	*) fail "unknown recipe $what" ;;
	esac
done
echo "PASS: $WHICH"
