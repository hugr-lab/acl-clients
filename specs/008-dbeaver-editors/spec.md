# Spec 008: editors in the DBeaver plugin - a form, our statement, then the node

- **Status**: implemented (008.1 - 008.3; accepted by the owner 2026-10-12, with the recommended answers to the questions below)
- **Date**: 2026-10-12
- **Node side**: duckdb-acl specs 117 (the `platform` catalog: views, `console_info`, the one authorizer, management
  without a marker under a principal), 118 (node and cluster), 120 (the missing ALTER / REVOKE forms,
  `effective_rights`, view-as), 039 (check and repair)
- **Builds on**: spec 007 (the plugin: data tree, consoles, view-as, the Access control branch - read only)
- **Design**: duckdb-acl design/079 §3.2б (the grammar stays the language) and §5.3 (editors and actions) - phases 3-4

## Problem

Spec 007's Access control branch shows what the policy is. Changing it still means writing the
grammar by hand in a management console: what the statement is called, which clause comes first,
how a name is quoted (spec 116), how a capability is spelled. An administrator who sees in the branch
that `analyst` reads `customers.ssn` cannot fix that from there. They need to know the
`ALTER GRANT … SET COLUMNS '…'` form, type it in another tab, then refresh the branch.

Phases 3-4 of design/079 close that gap:

- the branch's objects can be created, edited and dropped;
- a finding of Check can be repaired;
- the node's runtime can be operated: sessions, drain, resume;
- the cluster profile can be edited, and drift moved into it.

## Design

### 0. One rule: a form writes our grammar, the node decides
- Every change is a **form**, then a **preview** of the statements it composes, then **execute**:
  - the preview shows the grammar (`GRANT CATALOG sales TO ROLE analyst …`), never the `acl_*` / `platform.*`
    call it compiles to (design/079 §3.2б);
  - history is the node's audit, never the plugin's.
- The plugin decides nothing. An action shows where `console_info()` says it would work: `my_functions`,
  `bundles`, `admin_catalogs`. That is convenience. The node's authorizer refuses whatever it refuses, and
  its text is what the user reads.
- **No credential in a form.** Issuers and clients name a secret (`FROM SECRET s [IN c]`); the plugin has no
  field for a key, a client secret or a password. The secrets themselves are the secrets service's (spec 082)
  and stay out of this spec.
- No statement into the policy store's tables, and no SQL the grammar has no form for.

### 1. How it sits in DBeaver
- DBeaver's own object-manager seam: an `org.jkiss.dbeaver.objectManager` per editable class of the branch
  (`SQLObjectEditor`-style).
- Through it, **Create** (the folder's popup), **Edit** (the object's properties, made editable where the
  grammar can change them) and **Delete** come from DBeaver itself. **Save** shows the composed script in
  DBeaver's own persist dialog (the "show SQL" preview), and DBeaver then refreshes the node it changed.
- A property that is a list or a structure gets a dialog of its own, opened from the property: a grant's
  capabilities (checkboxes), a COLUMNS list (items with masks and spec 102 paths), a client's REQUIRE /
  ATTRIBUTES, a resource group's limits.
- **Prefill** comes from the same `platform` rows the branch already reads (007.3). The form never shows a
  value it did not read.

### 2. What an edit composes
- **Create** is the object's `CREATE` form, with only the clauses the user filled.
- **Edit** composes one `ALTER … SET <property>` per changed property (the forms of the docs' ALTER table, spec
  120.1). An unchanged property is never written, so an edit cannot erase a fact the form does not show.
  An object with no ALTER for a property gets no edit field for it; the form says "drop and create".
  The fallback is never a silent `CREATE OR REPLACE`.
- **Delete** is the object's `DROP` (`DROP VIRTUAL TABLE c.n`, `REVOKE CATALOG …`, `DROP MAP …`). DBeaver
  confirms it, with the statement shown.
- **Names** are composed from the canonical keys the rows carry, by spec 116's rules, through one helper:
  quote a part that needs it, double a `"`, keep a single-quoted `'…'` path where the grammar takes one.
- **Order**: a save that composes several statements orders them as the policy depends: catalog, object,
  grant.

### 3. How it runs
- Statements run on the user's own connection. Under the principal's prefix a management statement needs no
  marker (spec 117). The run is in **auto-commit**, one statement at a time. It stops at the first refusal or
  failure and says which statements were applied.
  - A management batch is not atomic on the node (docs "Batches and authorization"). A management write is
    not part of the client's transaction (my reading of the code: only RENAME records an undo); a test in
    008.1 pins this down.
- A view-as copy is read-only. A management console and a native console offer no editors; they are for SQL.
- **Stale form**:
  - before executing, the plugin re-reads the object's rows;
  - if they changed since the form opened, the dialog shows what changed and asks first;
  - this is best effort, on the client. A compare-and-set on the node is a duckdb-acl follow-up, not this spec.

### 4. Actions beyond a property
- **Check → Repair**: the finding's repair statement, exactly as the node wrote it, previewed, confirmed,
  executed. The catalog's Check is then re-read.
- **Node** (`operate`): kill a session, set a session's audit or profile level, drain, resume. Each is
  confirmed with the statement shown. Drain says that new clients are refused until resume.
- **Cluster** (`cluster`, a cluster node only):
  - the profile's items (extension, attach, set) are created, updated and removed per group;
  - a **drift** entry becomes a prefilled `ACL CLUSTER …` form. What the node does not know stays for the user
    to fill (an ATTACH's `SECRET` name, never its options' credentials).
- **SQL templates** for the grammar in DBeaver's template store, for whoever writes in the console anyway.

### Not in this spec
- A **DDL tab** and policy export: they wait for duckdb-acl 120.4 (export after the release).
- Lineage views.
- Value editors for STRUCT / MAP beyond Generic's.
- A compare-and-set on the node.
- Any view across nodes (that is the front's or the orchestrator's, design/017 and 019).
- **Full impersonation** (the owner, 2026-10-12, for later): a view-as connection that also runs queries - reads
  the role's data, not only its metadata. Today the node refuses that by design (duckdb-acl 120.3: `metadata_only`);
  opening it is a node decision first - who may (a bundle of its own, never `policy` alone), what the audit records
  (the real principal on every statement), whether writes are in it - then a duckdb-acl spec, then the plugin.

## Security
- Same as spec 007: tokens are the driver's, and the plugin stores and shows none.
- No credential field in any form (section 0). A form for a source or a client names a secret.
- Hiding an action is convenience only. Each stage has a negative test: a role without the right does not get
  the action, and the same statement sent by hand is refused by the node.
- The preview is the statement that runs, built by one composer: what is shown is exactly what is sent.

## Testing
- **Units** (plain JUnit): each form composes its statements, as follows.
  - Create: filled clauses only.
  - Edit: one ALTER per changed property; nothing changed → zero statements.
  - Delete; spec 116 names (case, spaces, `.` and `"` in a part); ordering.
  - **Round trip**: rows read, form prefilled, saved without a change → no statement.
- **Live** (against `dev/node.sh`, gated like 007's):
  - each form's statements run as steward1 (policy); the `platform` rows then show the change, and the
    reverse drop removes it;
  - a catalog admin edits its own catalog and is refused on another;
  - ops1 operates sessions and drain; analyst1 has no editor;
  - a save that fails at its second statement reports the first as applied;
  - a management write inside an open client transaction is applied anyway (section 3).
- **UI** (SWTBot): per stage one path through DBeaver's own create, edit and delete:
  - the persist dialog shows our grammar;
  - Save applies it, and the branch refreshes to the new state;
  - Delete confirms.
- Install check and build as in 007.

## Alternatives considered
- **Our own dialogs only**, no object managers: a parallel UI beside DBeaver's create, edit, delete and save,
  and two ways to do one thing. DBeaver's seam gives the persist preview and the refresh for free.
- **Edit as `CREATE OR REPLACE`** of the whole object: one statement, but it writes every fact the form shows,
  and erases or rewrites what the form does not show or reads wrong. ALTER per property touches only what the
  user changed.
- **An editable preview**: the user could edit the statement before it runs. Rejected: a second editor
  that loses the form. "Open in a management console" with the script is the way to edit by hand.

## Risks
- DBeaver's object-manager API is not a public contract either (spec 007's risk). The Generic and DuckDB
  plugins are the reference we follow.
- A property the views do not carry cannot be edited. Each gap found becomes a duckdb-acl view column first,
  never a guess on the client.
- Not atomic: a multi-statement save can stop halfway. The report says what applied, and the stale check sees
  the new state.

## Stages
A PR each, with three review passes and the build, UI tests and install check green.

- **008.1** - the framework (object managers, the composer, preview, run, stale check, refresh, templates), the
  owner's 2026-10-12 asks from the look at 007 (the hugr logo as the connection's icon, the console submenu named
  `Access control` instead of `duckdb-acl`, **View as this role** on a role of the branch), and the policy core:
  - roles (claims, comment);
  - virtual catalogs (comment);
  - virtual tables and views (phys, columns, RLS, key, types, comment);
  - catalog, schema and object grants (caps, MAIN, RLS, columns with masks and paths).
- **008.2** - the rest of the policy and repair:
  - schemas (alias, refresh), table and scalar functions (macro or alias), references;
  - identity: issuers, clients, mappings;
  - function categories (members, comment), function grants;
  - resource groups (limits, default, role grants);
  - admin bundles, platform grants and source grants (passthrough);
  - Check → Repair.
- **008.3** - the node and the cluster: sessions (kill, audit and profile level), drain and resume; the
  cluster profile's items per group; drift into the profile.

## Questions for the owner (answered 2026-10-12: the recommendations)
1. Edits are ALTER per property, never a silent `CREATE OR REPLACE`.
2. The preview is read-only; "open in a management console" with the script edits by hand.
3. The stale check is the client's for now; a node-side compare-and-set later.
4. Node and cluster operations stay here, as 008.3.

## As built

### 008.1 (2026-10-12)
- **The seam**: an `org.jkiss.dbeaver.objectManager` per editable class (`AclObjectManager<T>`, a `SQLObjectEditor`:
  `AclRoleManager`, `AclManagers$Catalogs | Relations | CatalogGrants | SchemaGrants | ObjectGrants`), the branch's
  folders typed (`type=` - DBeaver's **Create New Role / Catalog / Relation / Grant** on them, **Delete** on an object)
  and given ids (a folder of folders had none - DBeaver's node path, `getNodeUri`, threw an NPE on Create). A new
  object opens DBeaver's own editor (`FEATURE_EDITOR_ON_CREATE`); its properties are the class's `@Property`s,
  editable where the grammar can change them (`editable` - new only - / `updatable`); **Save** is DBeaver's **Persist
  Changes** dialog showing the script (**Execute**, **Open Editor** - the script in an SQL editor, the owner's answer
  2 -, Copy, Cancel), Delete DBeaver's confirmation with **View Script**. The bundle now has a localization
  (`OSGI-INF/l10n/bundle`): DBeaver reads it for every property it collects to edit.
- **The composer** (`AclGrammar` + each class's `createStatements` / `alterStatements(changed)` / `dropStatements`):
  names from the rows' canonical keys, a part quoted when it is not a plain lower-case name or is a duckdb reserved
  word (the grammar's own words read as names - checked on the node); text as `'…'`, quotes doubled; a user's clause
  (RLS, COLUMNS, claims, view SQL, key) written as typed - the preview shows it.
  - **Role**: `CREATE ROLE r [CLAIMS (…)]` + `ALTER ROLE r SET COMMENT` (CREATE has no comment clause); `SET CLAIMS (…)`
    / `''`, `SET COMMENT`; `DROP ROLE`. The default claims are one text property, the list form
    (`tenant = 'acme', unit = 'eu'`), read from `role_claims`.
  - **Virtual catalog**: `CREATE VIRTUAL CATALOG c [COMMENT]`, `ALTER … SET COMMENT`, `DROP VIRTUAL CATALOG c` - without
    CASCADE: a catalog a role holds a grant on is refused by the node, which names the roles.
  - **Relation** (Kind table | view at Create): `CREATE VIRTUAL TABLE c.n AS <phys> [COLUMNS] [RLS] [PRIMARY KEY]
    [COMMENT]` / `CREATE VIRTUAL VIEW c.n [PRIMARY KEY] [COMMENT] AS <select>`, then `SET TYPES` when set; one
    statement per changed property - `SET PHYS`, `SET COLUMNS (…)` / `''` (the alias form), `SET RLS (…)` / `''`,
    `SET AS`, `SET PRIMARY KEY` / `DROP PRIMARY KEY`, `SET TYPES (aliases = …, enums = …)` (`default` = the node's),
    `COMMENT ON VIRTUAL TABLE | VIEW`; `DROP VIRTUAL TABLE | VIEW`. The declared columns are one text property read
    from `relation_columns` (`name [= expr] [NOT NULL | NULL]`, by `pos`), the key from `keys`.
  - **Catalog grant**: `GRANT CATALOG c TO ROLE r [WITH (…) | CAPS '{}'] [MAIN] [RLS] [COLUMNS]`; `ALTER GRANT … SET
    CAPS '<json>' | '' (unstated), SET MAIN true | false, SET RLS '…', SET COLUMNS '…'`; `REVOKE CATALOG`.
  - **Schema and object grants** have no ALTER and a GRANT replaces the grant whole (checked on the node: caps, RLS,
    columns, comment, INTO all replaced), so an edit writes the GRANT again with every property the form shows -
    which are all of the grant's facts - and the preview says so; `REVOKE SCHEMA | OBJECT` (spec 120). An inherited
    schema-grant row (caps materialised from an ancestor) states none: unstated.
  - **Capabilities** are boxes in a Capabilities category: **Unstated** and one per capability (select … manage); a
    tick makes the grant state its own list, Unstated clears it.
- **What is offered** (spec section 0): each action needs the platform function its grammar compiles to in
  console_info's `my_functions` (`create_role`, `alter_grant`, `add_relation`, `revoke_object`, …) and - for a catalog's
  administrator without the policy bundle - the object's catalog among its `admin_catalogs` (folded as the node folds
  names); a view-as copy (read-only) and a connection without console_info edit nothing. The node judges every
  statement regardless.
- **How it runs** (section 3): on the user's connection, in auto-commit (`OPTION_AVOID_TRANSACTIONS`), one statement
  at a time; the error of a refused statement lists the ones that ran before it ("Applied before it (a management batch
  is not atomic)"). A management write is applied whatever the client's transaction does - checked through the driver
  (a ROLLBACK keeps it). Before a save of a stored object, its row is read again and every property the user changed
  is compared with the value the form was opened with; a moved one is shown (`comment: was 'a', now 'b'`) and asked
  about - No saves nothing. After a save, the object reads its own row again (DBeaver refreshes the node).
- **The owner's asks** (the look at 007): the hugr logo as the type's / driver's icon; the console submenu
  `Access control ▸`; **View as this role** on a role of the branch (the property tester `io.github.hugrlab.acl.role`
  - the connection's view-as offer); SQL templates for the main forms (`templates/duckdb_acl-templates.xml`, context
  `sql_duckdb_acl_duckdb_acl_jdbc`: `aclrole`, `aclcatalog`, `acltable`, `aclview`, `aclgrant`, `aclgrantobject`,
  `aclaltergrant`, `aclcheck`, `aclviewas`). An object's `toString()` is its name (DBeaver's titles).
- **Dev realm**: `curator1` (`curator1-pass`; the realm role `curator` → `manage` on the catalog `scratch`, `GRANT SOURCE
  memory.main`) beside steward1, ops1, analyst1.
- **Tests**: unit `EditStatementsTest` (every class's create / one-per-property / no change → nothing / drop, spec 116
  names, reserved words), `ConsoleTest.whatAnEditorIsOffered`; live `LiveEditTest` (DBeaver's command path - the
  managers, a property edit through `PropertySourceEditable` as the panel makes it, the script, `saveChanges`): a role
  from Create to Drop with the stale question answered No, a catalog with its table and the three grants (and the
  refused drop of a catalog held by a grant), the curator's own catalog written and another not offered (the node
  refusing the statement sent by hand), analyst1 offered nothing, a refusal reporting what ran before it, a write
  inside a client transaction kept after ROLLBACK; UI `LiveConsoleTest.aRoleThroughDbeaversEditor` (Create New Role →
  the editor → Save → Persist Changes shows our grammar → Execute → reopened from the tree, an edit is one ALTER →
  Delete → View Script shows `DROP ROLE "t008 ui"` → Yes) and `viewAsThisRoleFromTheBranch`; `PluginLoadedTest`
  (the templates). The live and UI tests need `dev/node.sh` (`ACL_E2E_URL`) and run locally; CI builds, runs the
  units and the install check, as for 007.
- **Found on the node**: `CLAIMS (tenant = 'a,b')` cut the value to `a` (fixed in duckdb-acl, PR #213); its "already
  exists" message names a role `".r"` and a catalog `"c.c"` (a follow-up there).
- **Not here**: 008.2 / 008.3; a DDL tab (120.4); editing a view's declared column list (its `(col TYPE, …)` at
  Create); a list provider for the physical objects (`phys` is typed).

### 008.1 addendum - a grant's Row filter and Columns tabs (2026-10-12)
The owner, looking at 008.1: a grant's RLS and columns were two small fields at the top, their syntax unclear there -
move them into tabs beside Capabilities, with a check of the RLS, the columns, the masks. Decided the same day: the
node checks (duckdb-acl spec 122, `platform.check_grant`), not the client.
- **Row filter** and **Columns** are folder pages of the grant's editor (`org.jkiss.dbeaver.databaseEditor`, `type =
  "folder"`: `GrantRowFilterPage`, `GrantColumnsPage`, for catalog and object grants; a schema grant carries
  capabilities only). The two properties are hidden from the top grid (`hidden = true`); the tabs set them through the
  editor's own command context by their descriptor (DBeaver's by-id setter only knows the properties it shows), so
  Save previews and writes them as before.
- **Row filter**: the predicate in a multi-line editor, beside the object's columns (double-click inserts a name).
- **Columns**: the object's columns as the node answers them (`check_grant` with no list: every column it exposes,
  with its type - the grantor needs no grant on the catalog) in a tree - a STRUCT's fields and a list of structs'
  (`items[].price`) as children - each **Visible / Masked / Hidden**, a mask's expression in its own cell; under it the
  `COLUMNS (…)` the tree writes. The tree reads the grant's list and writes it back in spec 102's forms (`address` whole,
  `address.city` narrowed, `address, address.ssn = '***'` a masked field of the whole); a field shown keeps its struct, a
  struct shown again with every field hidden is shown whole; an item the tree does not know is kept as written
  (`GrantColumns`, pure). A catalog grant's list is text (names across the catalog - not probed).
- **Check with the node** on both: `platform.check_grant` with the tab's values - one row per finding (the write's
  own words for a refusal, in red), "The write would be accepted / refused". Nothing is written.
- **Tests**: unit `GrantColumnsTest` (the tree from types, round trips of every form, edits, unknown items kept); live
  `LiveEditTest.aGrantIsCheckedBeforeItIsWritten` (the object's columns, a refused predicate, a mask answered, nothing
  written); UI `LiveConsoleTest.aGrantsRowFilterAndColumnsTabs` (analyst's grant on sales.orders: the tabs show the
  predicate and the columns, Check answers, an edit of the predicate is previewed by Save as the GRANT with it).
- Needs a node with duckdb-acl spec 122 (PR #214); an older node answers the tabs' reads with its refusal.
- The tabs are read-only where the object manager refuses the edit (a catalog admin reading a grant); a new grant's
  columns are read again each time its tab is shown (its object is chosen on Properties).
- **Not here**: the same tabs for a virtual table's own COLUMNS / RLS (008.2).

### 008.2a (2026-10-12) - a table's own columns and filter, schemas, functions, references, repair
- **A virtual table's Row filter and Column list** (`RelationRowFilterPage`, `RelationColumnsPage`; the navigator
  already has a "Columns" folder, hence "Column list"): the predicate over the source's physical columns in its own
  editor; the source's columns (read from the node's attached tree, `platform.attached.<db>.<schema>` -
  `AclRelation.physicalColumns`) each Exposed or not, under a name of the table's (a rename), through an expression (a
  mask), with NOT NULL / NULL - and computed columns the source lacks (added and removed); the `COLUMNS (…)` it
  writes under the table, nothing changed = the alias form. The model is `RelationColumns` (pure; a field path kept as
  written). A view's columns are its SELECT's: the tabs say so. The two properties left the top grid (`hidden`). No
  Check yet (the node binds the definition where it is written; a `check_relation` would be duckdb-acl's).
- **Virtual schemas**: Create (Kind alias | expansion, Phys path, Comment) → `CREATE VIRTUAL SCHEMA c.path AS | FROM
  <phys>`; `ALTER … SET PHYS` (an alias), `COMMENT ON VIRTUAL SCHEMA`; `DROP VIRTUAL SCHEMA` (no CASCADE); **Refresh
  from the source…** on an expansion → `ALTER VIRTUAL SCHEMA c.path REFRESH [PRUNE]` (chosen in the dialog).
- **Functions**: Create (Kind table | scalar, Form macro | alias, Params, Returns, Primary key, Template / Target,
  Comment) → `CREATE VIRTUAL TABLE FUNCTION | SCALAR …`; `SET MACRO` / `SET ALIAS OF`, `SET | DROP PRIMARY KEY` (a
  table function; the key read from `keys`), `COMMENT ON VIRTUAL …`; parameters and result have no ALTER; `DROP
  VIRTUAL TABLE FUNCTION | SCALAR`.
- **References**: Create (From, To, To kind relation | function, On pairs, Arguments, Expression, Cardinality,
  Optional, Join method, Comment) → `CREATE VIRTUAL REFERENCE …`; `ALTER VIRTUAL REFERENCE … SET CARDINALITY |
  OPTIONAL | COMMENT` (spec 120); its ends and condition by a re-create; `DROP VIRTUAL REFERENCE`.
- **Check → Repair…** on a finding: the node's repair is often a template with alternatives (`REPAIR VIRTUAL TABLE c.a
  REMAP (gone = <column>)  -- or DROP MISSING COLUMNS`) - `RepairStatements` splits it into whole statements (a tail
  alternative joined to the first's object), the dialog (`StatementDialog`) offers them, the statement editable, and
  Execute only once no `<…>` place is left; it runs on the connection and the catalog is read again.
- Offered as in 008.1 - each action's platform function (`add_schema_alias`, `alter_function`, `add_reference`, …) in
  `my_functions`, a catalog admin in its catalogs.
- **Tests**: unit `RelationColumnsTest`, `RepairStatementsTest`, `EditStatementsTest.schemasFunctionsAndReferences`;
  live `LiveEditTest.schemasFunctionsReferencesAndARepair` (an alias and an expansion with its refresh, a macro
  edited, a reference and its hint, a table's source columns, a finding repaired by its alternative); UI
  `LiveConsoleTest.aTablesColumnListAndARepair` (the Column list tab, Repair… through the dialog).

### 008.2b (2026-10-12) - identity, functions, resource groups, administration
- **Issuers**: Create (Name, Url, Secret, Secret service) → `CREATE ISSUER n URL '…' [FROM SECRET s [IN c]]`; `ALTER
  ISSUER n SET URL`, `SET FROM SECRET` / `DROP FROM SECRET`; `DROP ISSUER`. **Clients** (under their issuer): each
  clause a property in the grammar's own syntax - Audiences, Azp, Require (`tier = 'gold', 'x' IN groups, region IN
  ('eu'), mail LIKE '%@x'`), Roles from, Roles constant, Unmapped (ignore | as_role), Attributes (`tenant = 'tenant',
  units = ('a', 'b'), fixed = CONSTANT 'v'`), Subject, Token type, Client id, Flows, Secret; read back from the view's
  typed columns (REQUIRE's `path / op / values`, ATTRIBUTES' `name / paths / constant`); `CREATE CLIENT n ISSUER i …`,
  one `ALTER CLIENT n SET <clause>` per changed clause - a cleared list `X ()`, a cleared text `TOKEN TYPE ''` /
  `CLIENT ID ''` (checked on the node) - and the implicit client of a short-form issuer through `ALTER ISSUER i SET …`
  (the node refuses ALTER CLIENT on it; its secret as `SET CLIENT FROM SECRET` / `DROP CLIENT FROM SECRET` - not
  exercised here, it needs a secrets service). No field for a key or a credential. **Role mappings**: Create (Source
  claim-value | group, External value, Scope kind client | issuer, Scope name, Role) → `MAP …`; `DROP MAP …`; no edit
  (a mapping is its key).
- **Function categories**: Create (Name, Comment) → `CREATE FUNCTION CATEGORY`; `SET COMMENT`; `DROP`; **members**
  (`ALTER FUNCTION CATEGORY c ADD (lower)` / `DROP (system.main.upper)`, `TABLE` after a table function). **Function
  grants**: Role (empty = every role), Category or Function, Allowed → `GRANT | DENY FUNCTION [CATEGORY] x TO ROLE r |
  TO ALL ROLES`; Allowed changed = the GRANT / DENY again; `REVOKE … FROM …`.
- **Resource groups**: the six limits as properties (`window_start` … `max_sessions`, a size like `'32MiB'` quoted),
  Default, Comment → `CREATE RESOURCE GROUP g (…) [DEFAULT] [COMMENT]`; `SET (only the changed limits)`, `SET | DROP
  DEFAULT`, `SET COMMENT`; a limit is set or changed, never unset - the node refuses NULL, and the plugin says so
  before it; `DROP`. A role's group: `GRANT | REVOKE RESOURCE GROUP g TO | FROM ROLE r`.
- **Administration** (a role's bundles) and **Platform grants** (views, functions, sources): `GRANT ADMIN <bundle>` /
  `REVOKE ADMIN <bundle>`; `GRANT | DENY VIEW | FUNCTION platform.x`, `GRANT SOURCE db[.schema]`, `REVOKE …` -
  passthrough's alone: offered where `grant_admin` / `grant_platform` / `grant_source` are callable, refused by the
  node otherwise. A catalog's `manage` row is its catalog grant's caps (not dropped here).
- **Tests**: unit `EditStatementsTest.identity` (an issuer, a client created and read back, cleared clauses, the
  implicit client through its issuer, mappings), `.functionsGroupsAndAdministration`; live
  `LiveEditTest.identityFunctionsAndGroups` (an issuer, its client created, read back in the grammar's syntax and
  edited; a mapping made and dropped; a category with a member and its grant; a group with a limit changed and bound
  to a role; GRANT ADMIN not offered to policy and refused by the node).
- **Found**: DBeaver collects only the properties its panel shows - a hidden property set by its id did nothing; the
  tabs and the tests set by descriptor (008.1 addendum).
- **Not here**: 008.3 (node, cluster); a stored reference's pairs / arguments shown (they are `reference_columns`;
  only a new one's are written); a Check for a virtual table's definition (duckdb-acl would add `check_relation`).

### 008.3 (2026-10-12) - the node and the cluster
- **Operations** (`Operable`: an object says which statements an operation offers, its title and explanation, and the
  platform function it compiles to; one command `io.github.hugrlab.acl.run` with the kind, one dialog -
  `StatementDialog`: the alternatives, the statement editable, Execute once no `<…>` place is left; offered where the
  function is callable): a **session**'s Kill session… (`KILL SESSION '<id>'`), Audit level… (`SET SESSION '<id>' AUDIT
  LEVEL ALL | DECISIONS | DENIED | OFF | DEFAULT`), Profile level… (`PROFILE SESSION '<id>' ON | SAMPLED | OFF`); the
  **node** (its Load row) Drain the node… / Resume the node… (`DRAIN NODE` / `RESUME NODE` - this node, never the
  fleet); a **drift** entry's Add to the cluster profile… (what the node has, as the CLUSTER statement that makes it the
  profile's: `CLUSTER SET threads = 16`; a source `CLUSTER ATTACH '<path>' AS a (TYPE <type>, SECRET <secret>)` - the
  places the node cannot know left to fill, never a credential; an extension `… VERSION '<version>' FROM
  <repository>`); 008.2's repair and refresh are operations too.
- **The cluster profile's items** (a cluster node's, the cluster bundle's): Create (Kind setting | source | extension,
  Name, Group = `IN GROUP`, and per kind Value; Path, Type, Secret, Options, Lineage, Depends on; Version, Repository;
  Comment) → `CLUSTER SET` / `CLUSTER ATTACH` / `CLUSTER INSTALL EXTENSION`; a change is the SET again, the ATTACH again
  (a re-point), `CLUSTER UPDATE EXTENSION … VERSION`; the drop `RESET` / `DETACH` / `REMOVE EXTENSION`. The stored spec
  (a MAP) reads back into the properties; a source's options (JSON) as ATTACH writes them. "Applied here" lists the
  effective items, read only (`AclClusterEffective`). The answer a CLUSTER statement gives (class, applied here, note)
  is not shown yet - the items' Class column says how a change reaches the nodes.
- **Dev**: `ACL_CLUSTER=1 dev/node.sh` makes a cluster node (`acl_deployment = 'cluster'`); ops1 holds the cluster
  bundle.
- **Tests**: unit `EditStatementsTest.nodeAndCluster`; live `LiveOperateTest` (ops1: analyst2's session found by its
  appearance, its audit and profile levels set, killed; drain then resume; on a cluster node an item made, read back,
  dropped through the editor's path, and the drift's statement into the profile).
