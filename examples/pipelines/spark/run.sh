#!/usr/bin/env bash
# The Spark recipe against the dev node (spec 004): Spark 3.5 or 4, its OpenLineage listener sending to
# Marquez, a namespace resolver that names the door as the node's namespace.
#
#   dev/marquez.sh; ACL_PIPELINES=1 ACL_OTEL=... dev/node.sh &      # once
#   SPARK=3.5|4 examples/pipelines/spark/run.sh                      # default 3.5
#
# Env: ACL_TOKEN (default: dev/token.sh), OPENLINEAGE_PARENT_ID (default: a fresh airflow-like step),
# MARQUEZ_URL as a container sees it (default http://host.docker.internal:5050), ACL_NAMESPACE - the
# node's acl_lineage_namespace without its scheme (default dev).
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
TOKEN="${ACL_TOKEN:-$("$ROOT/dev/token.sh")}"
RUN_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
PARENT="${OPENLINEAGE_PARENT_ID:-airflow/daily.spark_orders/$RUN_ID}"
PARENT_NS="${PARENT%%/*}"; REST="${PARENT#*/}"; PARENT_JOB="${REST%/*}"; PARENT_RUN="${REST##*/}"
DOOR="${ACL_DOOR:-host.docker.internal:32800}"
NS="${ACL_NAMESPACE:-dev}"
echo "spark $SPARK, parent $PARENT"
docker run --rm --add-host=host.docker.internal:host-gateway -u 0 \
	-v "$HERE":/work -v "$JAR":/work/acl-jdbc.jar:ro -v acl-spark-ivy:/tmp/ivy \
	-e ACL_TOKEN="$TOKEN" -e OPENLINEAGE_PARENT_ID="$PARENT" -e ACL_DOOR="$DOOR" \
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
	--conf spark.openlineage.rootParentJobNamespace="$PARENT_NS" \
	--conf spark.openlineage.rootParentJobName="${PARENT_JOB%%.*}" \
	--conf spark.openlineage.rootParentRunId="$PARENT_RUN" \
	--conf "spark.openlineage.dataset.namespaceResolvers.$NS.type=pattern" \
	--conf "spark.openlineage.dataset.namespaceResolvers.$NS.regex=${DOOR//./\\.}" \
	/work/job.py
