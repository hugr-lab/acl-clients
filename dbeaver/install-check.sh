#!/usr/bin/env bash
# acl-clients spec 007: install the built p2 repository into a COPY of the DBeaver release the plugin is built against,
# headless (p2 director) - a manifest or dependency error fails here, not on a user's machine. Run after build.sh.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
DBEAVER_VERSION="${DBEAVER_VERSION:-26.2.2}"
CACHE="$HERE/.dbeaver/$DBEAVER_VERSION"
SITE="$HERE/io.github.hugrlab.acl.dbeaver.site/target/repository"
[ -d "$SITE" ] || { echo "no p2 repository at $SITE - run dbeaver/build.sh first" >&2; exit 1; }
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
case "$(uname -s)" in
	Darwin)
		cp -R "$CACHE/macos/DBeaver.app" "$WORK/"
		LAUNCHER="$WORK/DBeaver.app/Contents/MacOS/dbeaver"
		CONFIG="$WORK/DBeaver.app/Contents/Eclipse/configuration"
		;;
	*)
		cp -R "$CACHE/linux/dbeaver" "$WORK/"
		LAUNCHER="$WORK/dbeaver/dbeaver"
		CONFIG="$WORK/dbeaver/configuration"
		;;
esac
# the user's keyring is never touched (DBeaver passes its own -eclipse.keyring otherwise)
"$LAUNCHER" -nosplash -consoleLog -application org.eclipse.equinox.p2.director \
	-repository "file:$SITE" -installIU io.github.hugrlab.acl.dbeaver.feature.feature.group \
	-eclipse.keyring "$WORK/keyring" 2>&1 | tee "$WORK/director.log"
grep -q "Operation completed" "$WORK/director.log" || { echo "FAIL: the director did not complete" >&2; exit 1; }
for bundle in io.github.hugrlab.acl.dbeaver io.github.hugrlab.acl.jdbc; do
	grep -q "^$bundle," "$CONFIG/org.eclipse.equinox.simpleconfigurator/bundles.info" \
		|| { echo "FAIL: $bundle is not in the installation's bundles.info" >&2; exit 1; }
done
echo "PASS: the plugin and its driver bundle install into DBeaver $DBEAVER_VERSION"
