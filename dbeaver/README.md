# duckdb-acl for DBeaver

A DBeaver plugin (spec 007): duckdb-acl as a connection type of its own, with the driver inside - nothing to
download, nothing to merge into DBeaver's config.

## Install

1. Download `acl-dbeaver-<version>.zip` from the [releases](https://github.com/hugr-lab/acl-clients/releases).
2. DBeaver → **Help → Install New Software… → Add… → Archive…** → the zip → select **duckdb-acl** → Next, accept,
   restart DBeaver.
3. **New Database Connection → duckdb-acl**: host, port (the node's Flight SQL door, 32800 by default), and either a
   user and password (the door's password sign-in) or none (the browser sign-in) - the driver's properties are
   spec 006's (`jdbc/README.md`).

Built against DBeaver CE 26.2.2 (Java 21).

## What you get (007.1)

- The node's tree as the signed-in principal sees it: catalogs, schemas nested by their path (`raw` → `eu`),
  tables (columns, declared keys and references), views, table functions and scalars (parameters, result columns).
- The ACL management words highlighted in the SQL editor.

Coming: management and native consoles, **View as role…** (007.2), the Access control branch (007.3).

## Develop

A local JDK 21 and Maven (`brew install openjdk@21 maven`):

```sh
dbeaver/build.sh                    # the driver jar, then Tycho against DBeaver 26.2.2 (fetched once into dbeaver/.dbeaver/)
dbeaver/install-check.sh            # the p2 repository installed into a copy of that DBeaver (headless)
ACL_METADATA=1 dev/node.sh &        # (after dev/keycloak.sh) - then the live UI tests run too:
ACL_E2E_URL="jdbc:acl://localhost:32800?disableCertificateVerification=true" dbeaver/build.sh
```

The UI tests start that DBeaver with the plugin (SWTBot, `io.github.hugrlab.acl.dbeaver.tests`); they need a display
(CI: Xvfb) and do not take the focus. `-DskipUITests=true` skips them.

Notes:
- The target platform is the DBeaver release's own bundles (`dbeaver/target-platform/`), not DBeaver's p2 site -
  that keeps only `latest`.
- DBeaver loads a bundled driver as a whole bundle jar (`bundle:/<id>`): `io.github.hugrlab.acl.jdbc` is the
  driver's shaded jar unpacked into a bundle.
- The navigator tree is ours (`plugin.xml`), without `visibleIf`: DBeaver evaluates those with JEXL limited to its own
  packages, so a condition on a class of this plugin is silently false.
