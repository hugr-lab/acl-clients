#!/usr/bin/env bash
# acl-clients spec 007: build the DBeaver plugin - the driver's -all jar into the plugin, then Tycho against one
# DBeaver release's bundles (fetched once into dbeaver/.dbeaver/<version>/).
#   dbeaver/build.sh [mvn goals...]        (default: clean verify)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
DBEAVER_VERSION="${DBEAVER_VERSION:-26.2.2}"
CACHE="$HERE/.dbeaver/$DBEAVER_VERSION"
mkdir -p "$CACHE"
case "$(uname -s)" in
	Darwin)
		if [ ! -d "$CACHE/macos/DBeaver.app" ]; then
			gh release download "$DBEAVER_VERSION" -R dbeaver/dbeaver -p "dbeaver-ce-$DBEAVER_VERSION-macos-aarch64.dmg" -D "$CACHE" --skip-existing
			mnt="$(mktemp -d)"
			hdiutil attach -nobrowse -readonly -mountpoint "$mnt" "$CACHE/dbeaver-ce-$DBEAVER_VERSION-macos-aarch64.dmg" >/dev/null
			mkdir -p "$CACHE/macos" && cp -R "$mnt/DBeaver.app" "$CACHE/macos/"
			hdiutil detach "$mnt" >/dev/null
		fi
		export DBEAVER_HOME="$CACHE/macos/DBeaver.app/Contents/Eclipse"
		export JAVA_HOME="${JAVA_HOME_21:-/opt/homebrew/opt/openjdk@21}"
		;;
	*)
		if [ ! -d "$CACHE/linux/dbeaver" ]; then
			gh release download "$DBEAVER_VERSION" -R dbeaver/dbeaver -p "dbeaver-ce-$DBEAVER_VERSION-linux-x86_64.tar.gz" -D "$CACHE" --skip-existing
			mkdir -p "$CACHE/linux" && tar -xzf "$CACHE/dbeaver-ce-$DBEAVER_VERSION-linux-x86_64.tar.gz" -C "$CACHE/linux"
		fi
		export DBEAVER_HOME="$CACHE/linux/dbeaver"
		;;
esac
# the driver, shaded, inside the plugin (spec 006's -all jar)
(cd "$HERE/../jdbc" && mvn -B -q -DskipTests package)
rm -f "$HERE/io.github.hugrlab.acl.dbeaver/drivers/acl/"*.jar
cp "$HERE"/../jdbc/target/acl-jdbc-*-SNAPSHOT-all.jar "$HERE/io.github.hugrlab.acl.dbeaver/drivers/acl/"
cd "$HERE"
if [ "$#" -eq 0 ]; then set -- clean verify; fi
mvn -B "$@"
