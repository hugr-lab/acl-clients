# Spec 007: a DBeaver plugin - the node's tree, its consoles, the catalog as a role sees it

- **Status**: accepted (by the owner 2026-10-11; a local JDK 21 for Tycho and SWTBot UI tests)
- **Date**: 2026-10-11
- **Node side**: duckdb-acl specs 115 (metadata for tools), 116 (quoted names), 117 (the `platform` catalog),
  118 (node and cluster), 119 (metadata at scale), 120 (maintenance: `effective_rights`, `exposures`, view-as)
- **Builds on**: spec 006 (the driver's DatabaseMetaData, `acl.mode`, `USE`)
- **Design**: duckdb-acl design/079 §5 - phase 2 (the MVP); decided 2026-10-09: a module `dbeaver/` of this
  repository, MIT

## Problem

Spec 006 made the driver answer a tool's metadata, and `examples/dbeaver/drivers.xml` puts it into DBeaver's
Generic provider by hand. What that cannot give:

- **Setup by hand**: the user merges XML into DBeaver's config and points at a jar - nobody does that twice.
- **No administration**: the `platform` catalog (spec 117) is in the tree as data, its views as tables; nothing
  says what a role holds, what decides it, who reads a source.
- **No consoles**: a management or native session is a second connection the user configures with
  `acl.mode` by hand.
- **No view as a role** - the owner's goal for the console (duckdb-acl 120.3): whoever configures grants sees
  the catalog as a role sees it. The node has the mode; DBeaver has no way to ask for it on every connection it
  opens (it reads metadata over a connection of its own by default).
- **Dialect**: the `ACL` grammar is unknown to the editor (no keywords, no highlighting).

## Design

### 0. Shape
- `dbeaver/` - a Tycho (Maven) build of three artefacts: the plugin `io.github.hugrlab.acl.dbeaver` (model and
  UI in one bundle for the MVP), a feature, and a p2 repository (the update site) zipped as a release asset.
  The driver jar (spec 006's `-all`) is inside the plugin (`drivers/`), so installing the plugin is the whole
  setup.
- Built with Maven + Tycho on the owner's machine (a local JDK 21 since 2026-10-11 - `brew install openjdk@21
  maven`, `JAVA_HOME=/opt/homebrew/opt/openjdk@21`; DBeaver 26 is Java 21) and in Docker in CI
  (`maven:3-eclipse-temurin-21`), against DBeaver CE's p2 repository (`https://dbeaver.io/update/ce/latest/`)
  and the Eclipse release it is built on.
- Like DBeaver's own DuckDB plugin (`org.jkiss.dbeaver.ext.duckdb`): a data source with `parent="generic"`, our
  driver (`io.github.hugrlab.acl.jdbc.AclDriver`, `jdbc:acl://{host}:{port}`, the properties of spec 006's
  drivers.xml), a Generic meta model and a dialect of our own. Everything the tree shows comes from the
  driver's DatabaseMetaData or from the principal's own surfaces - never a decision on the client (design/079
  §5.4).

### 1. The data tree (007.1)
- Catalogs → schemas as their nested paths (`raw` → `eu`, spec 116's names), tables and views, table
  functions (parameters, result columns) and scalar functions (`split-procedures-and-functions`), keys and
  references (spec 006 `acl_keys` / `acl_references`), comments.
- The meta model reads what spec 006 answers and only that; lazy by construction (spec 006: every listing
  scoped by what the user expands - the node's spec 119 narrows the sources it reads).
- `platform` appears as a catalog only for an administrator (the node decides); in 007.1 it is shown as the
  node lists it (views, `attached` as nested schemas, metadata only).
- The dialect: the `ACL` grammar's keywords (management statements, `ACL NATIVE`, `VIEW AS`) for highlighting
  and completion; duckdb's own SQL is the Generic dialect's.

### 2. Consoles and view-as (007.2)
Three actions on a connection, each opening a connection of its own (a copy of the user's, one property
changed), named and coloured so it is never taken for the original:
- **Management console** - `acl.mode=manage`: every statement is management (spec 006).
- **Native console** - `acl.mode=native`; shown only when `platform.console_info()` says the user holds
  `passthrough`; the node refuses anyway without it.
- **View as role…** - the roles from `platform.roles` (the policy bundle's view; the action is shown only to
  `policy` / `passthrough`, the node judges again), then a connection whose every physical connection runs
  `SET SESSION VIEW AS ROLE '<r>'` as its bootstrap query - DBeaver's metadata connection included - read-only,
  "as <r>" in its name. Its tree is the role's (the same code as any tree); a data preview is refused by the
  node (`a view-as session reads metadata only`) and the plugin says so instead of showing a failure dialog
  where it can. Requires the driver to keep the Flight session cookie (it does, spec 006).

### 3. The Access control branch, read only (007.3)
A folder beside the catalogs, shown when `console_info()` answers (absent, it is not there):
- **Roles** → a role's claims, catalog / schema / object grants, admin bundles, resource groups; a tab
  **Effective rights** per catalog (`platform.effective_rights`, spec 120.2).
- **Virtual catalogs** → objects with phys, columns / masks, RLS, keys, references, `created_by`; a tab **Check**
  (`platform.check_catalog`, the findings with their repair statements - copied, not run, in the MVP).
- **Sources** (the `attached` tree) → per physical object **Exposures** (`platform.exposures`).
- **Identity** (issuers → clients → mappings), **Function categories**, **Resource groups**, **Node** (sessions,
  load), **Cluster** (items, drift) - each only when `console_info().my_views` holds its views.
Every node of the branch is one `platform` view or read function with a WHERE for what is expanded; nothing is
cached beyond the view DBeaver keeps, Refresh re-reads.

### Not in the MVP (design/079 phases 3-4)
Editors (a form → the `ACL …` statement previewed → executed), running a check's repair, node operations
(kill, drain), cluster profile edits, lineage, STRUCT/MAP value editors beyond Generic's.

## Security
- Tokens stay the driver's (its cache); the plugin stores none and shows none.
- The plugin decides nothing: what is shown is what the node answers to this principal; a hidden action is
  convenience, the node refuses regardless.
- No SQL into the policy store's tables; every read is a `platform` view or function.
- A native console and a view-as connection are marked (name, colour); a view-as connection is read-only.

## Testing
- **Build** in Docker (Tycho) on every PR; the p2 repository is the release asset (spec 002's workflow).
- **Install check**, headless: the built p2 repository installed into DBeaver CE (the Linux tarball, in Docker)
  with `org.eclipse.equinox.p2.director` - proves the bundle resolves against a real DBeaver (a manifest or a
  dependency error fails here, not on a user's machine).
- **Units** (plain JUnit, no OSGi): the SQL each tree node and tab composes (filters as literals, quoting,
  spec 116 names), the console connection properties and bootstrap, the dialect's keywords.
- **Live** (gated like spec 006's e2e, against `dev/node.sh`): the meta model's listings and the Access control
  queries run through the driver as an admin and as an ordinary role - the same SQL the plugin sends.
- **UI tests** (SWTBot under `tycho-surefire` with the UI harness, run locally - a display is needed; in CI
  under Xvfb when it proves stable): a real DBeaver runtime from the target platform with the plugin, against
  `dev/node.sh` - the connection type is offered, the tree expands (a nested schema, a table function's
  columns), each console opens with its mode, **View as a role** opens a connection whose tree is the role's and
  whose data preview is refused, the Access control branch shows its folders for an admin and is absent for a
  role.
- A short manual pass in the owner's own DBeaver (install from the zip) before a release - not the gate.

## Alternatives considered
- **Only drivers.xml** (spec 006): no setup story, no administration, no view-as.
- **A separate repository** (`hugr-lab/dbeaver-acl`): its own release cycle; decided against 2026-10-09 (one
  Docker toolchain and one dev node with the driver).
- **Building against a DBeaver source checkout** instead of its p2 site: pins a version, but means building
  DBeaver - a CI hour for nothing the p2 site does not give.

## Risks
- DBeaver publishes only `latest` on its p2 site: the build follows DBeaver's releases, it cannot pin one;
  the install check catches a break, the fix is forward.
- DBeaver's Generic and navigator APIs are not a public contract; the DuckDB plugin in DBeaver's own tree is
  the reference we follow.
- UI tests are slower and flakier than units; they cover the paths above, the rest is units and live queries.

## Stages
007.1 skeleton + data tree + dialect; 007.2 consoles + view-as; 007.3 the Access control branch (read only).
A PR each, three review passes, the build and install check green.
