#!/usr/bin/env bash
# acl-clients spec 007: build the DBeaver plugin - the driver's -all jar into the plugin, then Tycho against one
# DBeaver release's bundles (fetched once into dbeaver/.dbeaver/<version>/).
#   dbeaver/build.sh [mvn goals...]        (default: clean verify)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
DBEAVER_VERSION="${DBEAVER_VERSION:-$(cat "$HERE/dbeaver.version")}"
CACHE="$HERE/.dbeaver/$DBEAVER_VERSION"
mkdir -p "$CACHE"
# the release's own digests (GitHub's asset sha256): what is downloaded is what DBeaver published
SHA_MACOS="7f47579bc583571ca0ccef9fdce7d6cc56f84b74844e4e700ab7a46d1e501ec5"
SHA_LINUX="27b39a79a69ec70f9f95c6ee3b68514fcc7677acef93287e8df8e13cc53384d5"
fetch() { # <asset> <sha256>
	[ -f "$CACHE/$1" ] || gh release download "$DBEAVER_VERSION" -R dbeaver/dbeaver -p "$1" -D "$CACHE"
	echo "$2  $CACHE/$1" | shasum -a 256 -c - >/dev/null || { echo "FAIL: $1 does not match its sha256" >&2; rm -f "$CACHE/$1"; exit 1; }
}
case "$(uname -s)" in
	Darwin)
		if [ ! -d "$CACHE/macos/DBeaver.app" ]; then
			fetch "dbeaver-ce-$DBEAVER_VERSION-macos-aarch64.dmg" "$SHA_MACOS"
			mnt="$(mktemp -d)"
			hdiutil attach -nobrowse -readonly -mountpoint "$mnt" "$CACHE/dbeaver-ce-$DBEAVER_VERSION-macos-aarch64.dmg" >/dev/null
			trap 'hdiutil detach "$mnt" >/dev/null 2>&1 || true' EXIT
			rm -rf "$CACHE/macos.tmp" && mkdir -p "$CACHE/macos.tmp" && cp -R "$mnt/DBeaver.app" "$CACHE/macos.tmp/"
			rm -rf "$CACHE/macos" && mv "$CACHE/macos.tmp" "$CACHE/macos"
			hdiutil detach "$mnt" >/dev/null
			trap - EXIT
		fi
		export DBEAVER_HOME="$CACHE/macos/DBeaver.app/Contents/Eclipse"
		export JAVA_HOME="${JAVA_HOME_21:-/opt/homebrew/opt/openjdk@21}"
		;;
	*)
		if [ ! -d "$CACHE/linux/dbeaver" ]; then
			fetch "dbeaver-ce-$DBEAVER_VERSION-linux-x86_64.tar.gz" "$SHA_LINUX"
			# into a temp directory first: an interrupted extraction must not be reused as a release
			rm -rf "$CACHE/linux.tmp" && mkdir -p "$CACHE/linux.tmp"
			tar -xzf "$CACHE/dbeaver-ce-$DBEAVER_VERSION-linux-x86_64.tar.gz" -C "$CACHE/linux.tmp"
			rm -rf "$CACHE/linux" && mv "$CACHE/linux.tmp" "$CACHE/linux"
		fi
		export DBEAVER_HOME="$CACHE/linux/dbeaver"
		;;
esac
# the driver, shaded, inside the plugin (spec 006's -all jar)
(cd "$HERE/../jdbc" && mvn -B -q -DskipTests package)
mkdir -p "$HERE/io.github.hugrlab.acl.jdbc/lib"
rm -f "$HERE/io.github.hugrlab.acl.jdbc/lib/"*.jar
# the newest -all jar the package made (a release build carries the tag's version, a local one the snapshot's)
cp "$(ls -t "$HERE"/../jdbc/target/acl-jdbc-*-all.jar | head -1)" "$HERE/io.github.hugrlab.acl.jdbc/lib/"
cd "$HERE"
if [ "$#" -eq 0 ]; then set -- clean verify; fi
mvn -B "$@"
