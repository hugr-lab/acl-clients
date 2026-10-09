#!/usr/bin/env bash
# The Spark recipe against the dev node (spec 004): Spark 3.5 or 4, its OpenLineage listener sending to
# Marquez, a namespace resolver that names the door as the node's namespace.
#
#   dev/marquez.sh; ACL_PIPELINES=1 ACL_OTEL=... dev/node.sh &      # once
#   SPARK=3.5|4 examples/pipelines/spark/run.sh                      # default 3.5
#
# Env: ACL_TOKEN (default: dev/token.sh), OPENLINEAGE_PARENT_ID (default: a fresh airflow-like step),
# OPENLINEAGE_ROOT_PARENT_ID (optional), MARQUEZ_URL as a container sees it (default
# http://host.docker.internal:5050), ACL_LINEAGE_NAMESPACE - the node's acl_lineage_namespace
# (default acl://dev).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
SPARK="${SPARK:-3.5}"
case "$SPARK" in
3.5) IMAGE=apache/spark:3.5.6-java17-python3; OL=io.openlineage:openlineage-spark_2.12:1.53.0 ;;
4 | 4.0) IMAGE=apache/spark:4.0.1-scala2.13-java17-python3-ubuntu; OL=io.openlineage:openlineage-spark_2.13:1.53.0 ;;
*) echo "SPARK is 3.5 or 4" >&2; exit 2 ;;
esac
JAR="${ACL_JDBC_JAR:-$(ls -t "$ROOT"/jdbc/target/acl-jdbc-*-all.jar 2>/dev/null | head -1 || true)}"
[ -f "$JAR" ] || { echo "build the driver first: mvn -B package in jdbc/ (see CLAUDE.md)" >&2; exit 1; }
# the token rides the environment into the container, never the command line (`ps` shows that)
export ACL_TOKEN="${ACL_TOKEN:-$("$ROOT/dev/token.sh")}"
RUN_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
export OPENLINEAGE_PARENT_ID="${OPENLINEAGE_PARENT_ID:-airflow/daily.spark_orders/$RUN_ID}"
# <namespace>/<job>/<runId> - the namespace may itself hold a '/', the run id is a UUID (the node
# refuses anything else, duckdb-acl spec 109)
split_ref() { # <ref> -> "ns job run" or nothing
	python3 - "$1" <<'PY'
import re, sys
m = re.fullmatch(r"(.+)/([^/]+)/([0-9a-fA-F-]{36})", sys.argv[1])
print(*m.groups()) if m else None
PY
}
read -r PARENT_NS PARENT_JOB PARENT_RUN <<<"$(split_ref "$OPENLINEAGE_PARENT_ID")" || true
[ -n "${PARENT_RUN:-}" ] || { echo "OPENLINEAGE_PARENT_ID is not <namespace>/<job>/<uuid>" >&2; exit 2; }
ROOT_CONF=()
if [ -n "${OPENLINEAGE_ROOT_PARENT_ID:-}" ]; then
	read -r ROOT_NS ROOT_JOB ROOT_RUN <<<"$(split_ref "$OPENLINEAGE_ROOT_PARENT_ID")" || true
	[ -n "${ROOT_RUN:-}" ] || { echo "OPENLINEAGE_ROOT_PARENT_ID is not <namespace>/<job>/<uuid>" >&2; exit 2; }
	ROOT_CONF=(--conf spark.openlineage.rootParentJobNamespace="$ROOT_NS"
		--conf spark.openlineage.rootParentJobName="$ROOT_JOB"
		--conf spark.openlineage.rootParentRunId="$ROOT_RUN")
fi
export ACL_DOOR="${ACL_DOOR:-host.docker.internal:32800}"
DOOR="$ACL_DOOR"
NS_URI="${ACL_LINEAGE_NAMESPACE:-acl://dev}"
NS="${NS_URI#*://}" # the resolver's name is the namespace's host part: acl://<door> -> acl://<name>
echo "spark $SPARK, parent $OPENLINEAGE_PARENT_ID"
docker run --rm --add-host=host.docker.internal:host-gateway -u 0 \
	-v "$HERE":/work -v "$JAR":/work/acl-jdbc.jar:ro -v acl-spark-ivy:/tmp/ivy \
	-e ACL_TOKEN -e OPENLINEAGE_PARENT_ID -e OPENLINEAGE_ROOT_PARENT_ID -e ACL_DOOR \
	"$IMAGE" /opt/spark/bin/spark-submit --master 'local[2]' \
	--conf spark.jars.ivy=/tmp/ivy --packages "$OL" --jars /work/acl-jdbc.jar \
	--conf spark.driver.extraJavaOptions=--add-opens=java.base/java.nio=ALL-UNNAMED \
	--conf spark.extraListeners=io.openlineage.spark.agent.OpenLineageSparkListener \
	--conf spark.openlineage.transport.type=http \
	--conf spark.openlineage.transport.url="${MARQUEZ_URL:-http://host.docker.internal:5050}" \
	--conf spark.openlineage.namespace=spark \
	--conf spark.openlineage.parentJobNamespace="$PARENT_NS" \
	--conf spark.openlineage.parentJobName="$PARENT_JOB" \
	--conf spark.openlineage.parentRunId="$PARENT_RUN" \
	"${ROOT_CONF[@]+"${ROOT_CONF[@]}"}" \
	--conf "spark.openlineage.dataset.namespaceResolvers.$NS.type=pattern" \
	--conf "spark.openlineage.dataset.namespaceResolvers.$NS.regex=${DOOR//./\\.}" \
	/work/job.py
