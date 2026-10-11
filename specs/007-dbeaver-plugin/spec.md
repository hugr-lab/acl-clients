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
  maven`; DBeaver 26 is Java 21) and on a Linux runner in CI, against one DBeaver release's own bundles (see As
  built - DBeaver's p2 site keeps only `latest`).
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
  `policy` / `passthrough`, the node judges again), then a connection that declares the mode on every call (the
  driver's `acl.viewAs` - see As built 007.2; the first build used a bootstrap query, which DBeaver only logs when it
  fails) - DBeaver's metadata connection included - read-only, "as <r>" in its name. Its tree is the role's (the same
  code as any tree); a data preview is refused by the node (`a view-as session reads metadata only`).

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
- The plugin is built against one DBeaver release's internals (`loadSchemas`, the Generic constructors); a DBeaver
  that changes them breaks it at run time, not at install (no version range on Generic yet) - the next release's
  build and install check is where it shows.
- DBeaver's Generic and navigator APIs are not a public contract; the DuckDB plugin in DBeaver's own tree is
  the reference we follow.
- UI tests are slower and flakier than units; they cover the paths above, the rest is units and live queries.

## Stages
007.1 skeleton + data tree + dialect; 007.2 consoles + view-as; 007.3 the Access control branch (read only).
A PR each, three review passes, the build and install check green.

## As built

### 007.1 (2026-10-11)
- **The target platform is a DBeaver release's own bundles**, not its p2 site (`latest` only): `dbeaver/build.sh`
  fetches DBeaver CE 26.2.2 (the macOS dmg locally, the Linux tarball in CI) into `dbeaver/.dbeaver/` and
  `target-platform.target` points at it (`${env_var:DBEAVER_HOME}`, a Directory location); SWTBot and JUnit 4 come
  from Eclipse's repositories. Tycho 5.0.4, Java 21.
- **The driver is a bundle of its own** (`io.github.hugrlab.acl.jdbc`): DBeaver loads a bundled driver as a whole
  bundle jar (`<file path="bundle:/io.github.hugrlab.acl.jdbc"/>` - `bundle=` is a product filter, not a path), so the
  shaded `-all` jar is unpacked into that bundle's classes; the plugin requires it, so it installs with the plugin.
- **Our navigator tree, without `visibleIf`**: DBeaver evaluates `visibleIf` with JEXL permitted only on
  `org.jkiss.dbeaver.*` / `com.dbeaver.*` classes, so any condition over a class of ours (`AclSchema`, `AclMetaModel`)
  is silently false - Generic's tree inherited as is hid views, and functions under our schema. The tree is Generic's cut
  to what a node has: tables (columns, keys, foreign keys, references), views, functions, nested schemas; no
  indexes, sequences, synonyms, triggers, procedures, and no per-schema Data Types (the system catalog's types).
- **Nested schemas**: `AclMetaModel.loadSchemas` keeps the catalog's top level and hangs `raw.eu` under `raw`
  (`SchemaPath` reads spec 116 paths, a quoted part is one part); `AclSchema` keeps the whole path as its name (what
  every listing filters by) and shows the last part (`DBPOverloadedObject`). Not yet: the SQL editor's resolution of
  a nested schema by name (the catalog lists only the top level).
- **Tests**: `PluginLoadedTest` (the connection type and its driver registered in a running DBeaver, the workbench
  window up - not focused: macOS keeps a background process from the focus, SWTBot drives by events),
  `SchemaPathTest`, `LiveTreeTest` (against `ACL_METADATA=1 dev/node.sh` as analyst1: the tree - nested `eu`, views,
  functions, keys, no Data Types - and a query read through DBeaver's own execution context, the node's RLS). The
  first-run Product Config wizard is off (`-Dproduct.config.disable=true`); a test connection saves its password
  (else DBeaver prompts). `install-check.sh`: the p2 repository into a copy of that DBeaver by the p2 director, the
  user's keyring untouched. CI: build + UI tests under Xvfb + install check; the release attaches the zipped p2
  repository (versions from the tag, `tycho-versions`).

#### 007.1 review (three passes)
- **Names**: DBeaver quotes a schema's name whole, so every SQL it generated for an object of a nested schema named
  a one-part schema `"raw.eu"` (refused by the node as "no access") - `AclTable` / `AclView` / `AclProcedure` /
  `AclSchema` (as a `DBPQualifiedObject`) build their SQL name part by part (`AclNames`): `sales.raw.eu.events`.
  `AclCatalog.getSchema` finds a schema by its path at any depth (foreign keys, search, the editor look schemas up by
  name), a nested schema's parent object is its parent schema (the navigator walks parents), `getChild` finds a
  child schema; the bare last part is shown only under a parent.
- **JEXL**: the model moved to `org.jkiss.dbeaver.ext.acl.model` (the bundle id stays ours) - with model classes
  outside DBeaver's packages every `visibleIf` of its editors over our objects is silently false.
- **The meta model by its id** (`meta-model` driver parameter), not the driver class - that took a Generic data
  source configured by hand with spec 006's drivers.xml too.
- **Keywords**: only the grammar's words DBeaver does not have (re-adding AS, VIEW, MAP retypes them), and never a
  reason to quote (`mustBeQuoted`: `main`, `platform` are names first); `$$ … $$` strings (`SQLDollarQuoteRule`, as
  DBeaver's DuckDB dialect).
- **Sign-in**: the driver is `anonymous` - a browser sign-in is not asked for a password on every connect; the
  README says what "Save password" stores.
- **Build**: `lib/` is made by build.sh and an empty one fails the driver bundle (a fresh clone failed CI and would
  have shipped nothing); the downloaded DBeaver is checked against the release's sha256; SWTBot pinned (4.3.0); the
  qualifier is the build's time (Maven 3.10's default stamp gave every snapshot one version, p2 never updated it);
  the plugin's release is a job of its own after the driver's (its setup-java overwrote Central's settings, and its
  failure stopped Central), `persist-credentials: false`; a pre-release tag `v0.2.0-rc1` is OSGi `0.2.0.rc1` and a
  final `v0.2.0` is `0.2.0.v<time>` (a qualifier sorts above none, so the final must carry one above the rc's for p2
  to offer it as an update); CI installs GTK beside Xvfb (DBeaver does not start without it) and keys its Maven
  cache on the plugin's poms; the DBeaver archive is extracted into a temp directory first; one `dbeaver.version`
  for both scripts. Proven on a clean Linux runner image in Docker (build, UI tests under Xvfb, install check,
  the release path for a tag).
- **Tests**: unit tests in their own headless module (`-DskipUITests` used to skip the only one); the live tests
  share one connection, each class waits for the workbench, the dump reaches a nested table's columns, the nesting
  is checked line by line, and DBeaver's own name for `sales.raw.eu.events` is read through its execution context.
- **Left**: the admin's `platform` tree (the dev realm has no admin user - 007.3 needs one); catalog / schema
  comments (JDBC's listings carry none); the default catalog after connect with several catalogs (Generic takes the
  schema as the active object without `query-get-active-db`; such a query would be prefixed in a console's mode);
  refreshing a schema does not re-read its nested ones (refresh the catalog); signing the p2 repository; the
  driver's ENUM values arrive as their dictionary index (`mood` = 0, spec 006 - a driver fix of its own); the node's
  `duckdb_schemas()` with a filter matching nothing answers 20 columns (duckdb-acl, its own fix).

### 007.2 (2026-10-11)
- **Where the actions are**: a `duckdb-acl` submenu of the navigator's popup on a connection
  (`popup:org.eclipse.ui.popup.any`, an `instanceof DBNDataSource` before the test so no other node evaluates it);
  each item's `visibleWhen` is the property tester `io.github.hugrlab.acl.console` over what the connection read at
  connect. The command without its kind (Quick Access) does nothing.
- **What was read**: the meta model now makes an `AclDataSource` (Generic's) whose `initialize` reads
  `SELECT bundles, admin_catalogs, my_views, my_functions FROM platform.console_info()` once, on the user's own
  connection only (data mode, no view-as - a console's copy is prefixed, a view-as copy is the role's; neither records
  a denial). A principal with no administration is refused it - one recorded denial per connect (rate-limited by the
  node's `acl_audit_denials_per_second`) - and is offered nothing. Rules (`ConsoleInfo.offers`): **manage** = a
  bundle, an administered catalog or a readable platform view; **native** = `passthrough`; **view-as** = `view_as`
  callable (the policy bundle's alone since duckdb-acl 120.3's addendum - a point grant no longer offers it) and
  `platform.roles` readable. `createDataSourceImpl` also passes **our dialect**: Generic's made its own, so until now a
  connected session used Generic's dialect and ours reached only a disconnected editor (007.1's highlighting held
  there only); now keywords, `mustBeQuoted` and `$$` are ours on every connection.
- **The copy** (`ConsoleCopy` - pure, unit-tested; `AclConsoles.open`): `new DataSourceDescriptor(original, registry)`
  (a deep copy - nothing mutable shared) with a new id, a name `<original> · manage | · native | · as <role>`,
  **temporary** (`isDetached()`: never persisted, nor its secrets), `acl.mode` `manage` / `native` / `data`. Asked
  again, the temporary copy of the same connection, kind and role (provider properties `acl.console.*` - never sent
  to the driver) is reused. A console runs **in auto-commit** (DBeaver's Production type alone is manual-commit, and
  a management or native statement would wait for a commit); native is that built-in *Production* type (red,
  confirms execution),
  view-as *Test* and read-only; the user's own colour is cleared where a type is set (it would hide the marker); the
  manage console keeps the original's type. Manage and native drop the user's bootstrap statements (each would be
  prefixed: `ACL SET …` refused, `ACL NATIVE SET …` run physically).
- **View-as fails closed.** The first build appended `SET SESSION VIEW AS ROLE` to the copy's bootstrap queries -
  DBeaver only logs a failed bootstrap query, so a refused SET left a `· as r` connection that was the viewer's own
  session; then a driver-side SET at connect - and the node re-seated a cookie whose session died (idle, killed,
  restart, another node, a lost cookie) as the viewer. Now the driver's `acl.viewAs` (spec 006 addendum)
  **declares the mode on every call** (`x-acl-view-as`): the door puts each call's session in the mode, judged like
  the SET, or refuses the call - a re-seated session is in the mode again (duckdb-acl 120.3 addendum). The connect
  makes one call, a refusal fails it. When the viewer loses the right (or the role comes to hold administration) the
  node refuses the session's statements, never runs them as the viewer. The action connects the copy at once (a
  progress service): refused, the copy is removed and the error shown. A role named with `${...}` is refused by the
  plugin - DBeaver substitutes variables in a connection's properties. The view-as copy drops the user's `catalog` /
  `schema` and default catalog / schema (the driver refuses them with `acl.viewAs`, the mode refuses a USE).
- **View as role…** reads `SELECT role FROM platform.roles ORDER BY role` on the original connection, then an
  `ElementListSelectionDialog`; cancelling the read (or the connect) ends the action silently (`monitor.isCanceled()`). The copy stays in the navigator (its tree is the
  point). A console opens an SQL editor (`SQLEditorHandlerOpenEditor.openSQLConsole`) on its copy.
- **Not done**: "the plugin says so instead of a failure dialog" for a data preview under view-as - the node's
  refusal text (`a view-as session reads metadata only`) is what DBeaver shows, which says it. A console's own tree
  is the virtual catalog (metadata is never prefixed) while its data tab is prefixed - `ACL NATIVE SELECT … FROM
  sales.main.orders` names a physical object: use a console for SQL, the user's connection for the tree. The
  driver's `setClientInfo` (DBeaver's application name) answers Arrow's refusal - logged by DBeaver, harmless.
- **Dev realm**: `dev/keycloak.sh` adds the realm role `steward` and the user `steward1` (`steward1-pass`); `dev/node.sh`
  creates the node role `steward`, `GRANT ADMIN policy` and maps the claim value through the implicit client (a
  privileged role is reached only by the client's own mapping, duckdb-acl spec 095).
- **Tests**: unit `ConsoleTest` (the offers per bundle, each copy's settings, `${...}` refused, the driver's list
  forms incl. `java.sql.Array`); live `LiveConsoleTest` (SWTBot against `dev/node.sh`): steward1 is offered manage
  and view-as and not native, analyst1 nothing (no submenu); **Management console** from the menu opens an SQL editor
  on a temporary `· manage` connection; **View as role…** → the dialog → `analyst` → a read-only, connected `· as
  analyst`; → `steward` → the node's refusal shown and no connection left; a view-as copy's tree to depth 6 is
  **equal** to analyst1's own, it reads no console_info and its data read is refused (`metadata only`); a refused
  view-as copy does not connect (`holds administration`); a management console runs `CHECK VIRTUAL CATALOG sales`.
  The test JVM gets `--add-opens=java.base/java.nio=ALL-UNNAMED` (DBeaver's own `dbeaver.ini` has it; an update
  through Arrow's driver, a DoPut, needs it). Driver: `AclConnectionTest` (the header on every call - also as Arrow's
  own config reads it -, one call at connect, a refusal closes the connection, data mode only, no catalog / schema), `LiveMetadataE2ETest` (steward1 as analyst lists analyst1's catalogs
  and reads no rows; as steward - refused, no connection).
- **Review** (three passes, before the PR): the bootstrap-query view-as and the node's re-seat (both fail closed now,
  above), a point grant offering view-as (node), the prod type's manual commit, the user's colour over the marker,
  console_info read on the view-as copy, cancel shown as an error, `${...}`, the user's init queries prefixed in a
  console, a copy closed while connecting (it leaked a session), no `java.sql.Array` unit case, duplicate copies, the
  dialect change unstated - each fixed or stated above.
- **Measured** (not 007.2's): the whole tree to depth 6 - 75 nodes - takes ~21 s for analyst1 and for the view-as
  copy alike; the node answers a table's `information_schema.columns` in ~215 ms and `acl_references()` in ~270 ms
  warm (pyarrow, same node), so a whole-tree walk is the node's listing cost per node - the duckdb-acl follow-up (a
  per-node metadata cache), not the plugin's.

### 007.3 (2026-10-11)
- **The branch**: a folder `Access control` beside the catalogs, `visibleIf="object.accessControl"` (the connection's
  console_info answered - our model's `org.jkiss.dbeaver.ext.acl.model` classes, which DBeaver's JEXL may call), each
  sub-folder where the principal reads its platform view (`shows('<view>')`) or calls its read function
  (`calls('<function>')`):
  - **Roles** → claims, catalog / schema / object grants, administration, resource groups, **Effective rights** →
    per catalog the role holds a grant on → what it reads of each object and why (`platform.effective_rights`: the
    deciding level, caps, MAIN, RLS, columns, masks, writable, refusal);
  - **Virtual catalogs** → relations (declared columns, keys), schemas, functions, references, **Check**
    (`platform.check_catalog`: each finding with its repair statement, to copy);
  - **Sources** - the first part of every relation's and schema's physical name → **Exposures**
    (`platform.exposures`: who reads what of it, and why);
  - **Identity** → issuers → clients; role mappings; **Function categories** → members; function grants; resource
    groups;
  - **Node** → sessions, load, doors; **Cluster** → profile, applied here, drift (a standalone node has no drift - an
    empty folder, not the node's refusal).
- **One statement per node**, read when it is expanded and kept until it is refreshed: `PlatformSql` composes
  `SELECT * FROM platform."<view>" WHERE "<column>" = '<value>'` / `SELECT * FROM platform."<function>"('<arg>', …)`
  (literals, quotes doubled) - never the policy store's own tables; the values as the properties panel shows them
  (`PlatformValues`: a capabilities STRUCT as the names it holds, a list joined). Each node's columns are its
  properties (the object's editor shows them and its folders as tabs - DBeaver's own).
- **Found**: the node's Flight door announced an empty schema for a prepared platform read function (taken for a
  command) - Arrow's JDBC driver handed no result set; fixed in duckdb-acl (spec 117 addendum, PR #210). And a driver
  connected to a node that ignores `x-acl-view-as` (older) would be the viewer's own: `acl.viewAs` now proves the mode
  at connect (spec 006).
- **Dev realm**: `ops1` (`operator` → the node's cluster bundle) beside `steward1` (policy) and `analyst1`.
- **Tests**: unit `PlatformSqlTest` (statements, value rendering); live `LiveAccessControlTest` - steward1 sees roles
  with grants and effective rights (analyst's customers with its `ssn` mask), catalogs with objects and Check, the
  `memory` source with its exposures, identity, categories, and no Node / Cluster; ops1 sees Node (its sessions) and
  Cluster and no Roles; analyst1 has no branch.
- **Not here** (the MVP's line, spec "Not in the MVP"): editors, running a repair, node operations, profile edits.
