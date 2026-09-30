#!/usr/bin/env bash
# The JDBC driver's end-to-end tests against the dev node and Keycloak (dev/node.sh, dev/keycloak.sh),
# in a Maven container. The node advertises its issuer as localhost:18070 and the tests dial the door
# at localhost:32800, so inside the container both "localhost" ports are forwarded to the host.
#
#   dev/jdbc-e2e.sh            # the live tests only
#   dev/jdbc-e2e.sh all        # every test
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="${MAVEN_IMAGE:-maven:3-eclipse-temurin-17}"
FILTER="-Dtest=LiveDoorE2ETest"
[ "${1:-}" = all ] && FILTER=""
docker run --rm -v acl-m2:/root/.m2 -v "$HERE/jdbc":/src -w /src \
	-e ACL_E2E_URL="jdbc:acl://localhost:32800?disableCertificateVerification=true" \
	"$IMAGE" bash -c "
		(command -v socat >/dev/null || (apt-get update -qq && apt-get install -y -qq socat >/dev/null))
		socat TCP-LISTEN:18070,fork,reuseaddr TCP:host.docker.internal:18070 &
		socat TCP-LISTEN:32800,fork,reuseaddr TCP:host.docker.internal:32800 &
		sleep 1
		mvn -B test $FILTER | grep -E \"Tests run:|FAIL|ERROR\\]\"; test \${PIPESTATUS[0]} -eq 0"
