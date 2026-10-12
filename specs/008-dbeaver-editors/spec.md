# Spec 008: editors in the DBeaver plugin - a form, our statement, then the node

- **Status**: accepted (by the owner 2026-10-12, with the recommended answers to the questions below)
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
