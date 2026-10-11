# duckdb-acl for DBeaver

A DBeaver plugin (spec 007): duckdb-acl as a connection type of its own, with the driver inside - nothing to
download, nothing to merge into DBeaver's config.

## Install

1. Download `acl-dbeaver-<version>.zip` from the [releases](https://github.com/hugr-lab/acl-clients/releases)
   (its `.sha256` beside it).
2. DBeaver → **Help → Install New Software… → Add… → Archive…** → the zip → select **duckdb-acl** → Next, accept.
   The plugin is not signed yet: DBeaver asks to trust unsigned content - accept, then restart DBeaver.
3. **New Database Connection → duckdb-acl**: host and port of the node's Flight SQL door (the template says 32800,
   `dev/node.sh`'s port - your node's is its operator's choice).
   - **Sign-in**: leave user and password empty for the browser sign-in (the IdP's own page; nothing is stored by
     DBeaver), or enter them for the door's password sign-in. With "Save password" on, DBeaver keeps that password
     in its own credentials store - prefer the browser sign-in, or turn on DBeaver's secure storage.
   - **TLS**: a node with a certificate your machine does not trust needs it in the system trust store; the driver's
     TLS options are on the Driver properties tab (`disableCertificateVerification` is for a dev node only).

Built against DBeaver CE 26.2.2 (Java 21).

## What you get

- The node's tree as the signed-in principal sees it: catalogs, schemas nested by their path (`raw` → `eu`),
  tables (columns, declared keys and references), views, table functions and scalars (parameters, result columns).
- The ACL management words highlighted in the SQL editor.
- For an administrator, **duckdb-acl ▸** on a connection (right-click in the navigator) - each item shown only when
  the node's `platform.console_info()` says it works for you (the node judges again anyway):
  - **Management console** - an SQL editor where every statement is management (`CREATE VIRTUAL …`, `GRANT …`,
    `CHECK VIRTUAL CATALOG …`): the bundles `policy`, a catalog's `manage`, any platform view.
  - **Native console** - plain SQL outside the virtual catalog (`passthrough` only). Its connection is DBeaver's
    *Production* type: red, and DBeaver confirms what it executes (in auto-commit).
  - **View as role…** - choose a role, get a read-only connection (*Test* type) whose tree is the catalog as that
    role sees it - what a user holding it gets in their own DBeaver. Metadata only: a data preview is refused
    ("a view-as session reads metadata only"). The `policy` bundle; a role that itself administers is viewed by
    `passthrough` only - the node refuses it and no connection is left. Every call of the connection says which role
    it views as, so a session the node seats again (after its idle timeout, a restart) is in the mode again - it
    never goes on as you; once you lose the right (or the role comes to hold administration) its statements are
    refused - open a new one. The catalog selector does not apply there (the role's catalog is the view).

  Each opens a connection of its own - a copy of yours (same node, same sign-in) named `<yours> · manage`,
  `· native`, `· as <role>`; it is temporary: never saved, gone when DBeaver restarts (delete it sooner as any
  connection). Asked again, the same copy is reused. A console does not run your connection's bootstrap
  statements (they would be prefixed).

- For an administrator, an **Access control** folder beside the catalogs (read only): roles with their grants and
  **effective rights** per catalog (what the role reads of each object and why), virtual catalogs with their objects
  and the **check** (findings with the repair statement to copy), sources with their **exposures** (who reads what),
  identity (issuers, clients, role mappings), function categories, resource groups, and - for the node's operators -
  its sessions, load and doors and the cluster profile. Each part shows only what your scopes read; F5 on a node
  reads it again.

## Develop

A local JDK 21 and Maven (`brew install openjdk@21 maven`):

```sh
dbeaver/build.sh                    # the driver jar, then Tycho against DBeaver 26.2.2 (fetched once into dbeaver/.dbeaver/)
dbeaver/install-check.sh            # the p2 repository installed into a copy of that DBeaver (headless)
ACL_METADATA=1 dev/node.sh &        # (after dev/keycloak.sh: analyst1, and steward1 - the policy bundle) - then the live UI tests run too:
ACL_E2E_URL="jdbc:acl://localhost:32800?disableCertificateVerification=true" dbeaver/build.sh
```

The UI tests start that DBeaver with the plugin (SWTBot, `io.github.hugrlab.acl.dbeaver.tests`); they need a display
(CI: Xvfb) and do not take the focus. `-DskipUITests=true` skips them.

Notes:
- The target platform is the DBeaver release's own bundles (`dbeaver/target-platform/`), not DBeaver's p2 site -
  that keeps only `latest`.
- DBeaver loads a bundled driver as a whole bundle jar (`bundle:/<id>`): `io.github.hugrlab.acl.jdbc` is the
  driver's shaded jar unpacked into a bundle.
- The navigator tree is ours (`plugin.xml`), without `visibleIf`, and the model's classes live in
  `org.jkiss.dbeaver.ext.acl.model`: DBeaver evaluates `visibleIf` with JEXL limited to its own packages, so a
  condition over a class elsewhere is silently false (its editors' tabs too).
- A nested schema's name is its path (`raw.eu`, what the driver's listings filter by); the SQL DBeaver writes quotes
  each part (`sales.raw.eu.events` - `sales."raw.eu"` would be a one-part schema to the node).
