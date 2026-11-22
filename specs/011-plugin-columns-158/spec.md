# Spec 011: the plugin writes columns, masks and versions as the node reads them now

- **Status**: implemented
- **Date**: 2026-11-22
- **Node side**: duckdb-acl spec 158 (columns: the object's shape, the grant's test and masks), spec 167 / 168 (a
  grant's `VERSIONS`), spec 173 (`ingest`), spec 180 (`MASK '…'` / `VERSIONS '…'` as one literal)
- **Builds on**: spec 008 (the editors: the Columns and Row filter tabs, the relation's Columns tab)

## Problem

The editors of spec 008 wrote the column model of duckdb-acl spec 102, which spec 158 replaced:

- **An object grant** is written whole by `GRANT OBJECT …` (the grammar has no ALTER for it; a grant written again
  replaces it). The plugin read only its caps, RLS and `columns`, so a change of any property wrote the grant again
  without its `MASK` and its `VERSIONS`: a masked column was then read in the clear - a widening the user never saw.
- Its `columns` is now a test on names - `STRUCT(mode, names)` in `platform.object_grants`, `a, b` / `* EXCLUDE (a, b)`
  as written - and its masks a clause of their own; the Columns tab still wrote spec 102's list (`ssn = NULL`,
  `address.city`), which the node refuses.
- **A catalog grant** takes no column list since spec 158; the plugin still offered one (and wrote `COLUMNS` on create).
  A list stored by an older build refuses every read under the grant (`acl_check_catalog`: `catalog_columns`).
- **A virtual table's own list** is a SELECT list (`name`, `<expr> AS name`, `name: <expr>`, `a.b`, a star): the tab
  wrote `name = <expr>` and `NOT NULL` / `NULL` marks, which the node refuses.
- The `ingest` capability (spec 173) had no box (a grant holding it kept it, but it could not be given).

## Design

- **Object grant** (`AclObjectGrant`): properties *Columns* (the test as the node prints it: empty = every column,
  `a, b`, `* EXCLUDE (a, b)` - read from the view's STRUCT), *Masks* (`col AS <expr>, …` from the view's list) and
  *Versions* (`CURRENT`, `ANY`, `AT (…)`, `BETWEEN … AND …`; empty = the catalog grant's). Written:
  `GRANT OBJECT c.n TO ROLE r [caps] [RLS '…'] [COLUMNS '…'] [MASK '…'] [VERSIONS '…']` - every text the user wrote is
  a literal the node reads with duckdb's parser, never SQL of the statement (spec 180). A row whose `columns` or
  `masks` is NULL is one the node could not read: written again it would show what they hide, so the plugin refuses
  to write it (revoke it, or write it in the SQL editor).
- **Columns tab** (`GrantColumns`): one row per column the object exposes (`platform.check_grant` with no list) -
  Visible, Masked (the expression the role reads instead) or Hidden; no field tree (spec 158 has none). *Columns the
  object gains later* - Shown (`* EXCLUDE (hidden)`) or Hidden (`COLUMNS (shown)`) - is the grant's mode, kept as
  stored (a grant of every column: Shown). A name or a mask the object no longer has is kept as written (the node's
  check says what it is). Check passes the RLS, the test and the masks (`check_grant(…, caps :=, masks :=)`).
- **Catalog grant**: no column list on create; a stored one is shown (*Legacy columns*, what refuses its reads) and
  can only be cleared (`ALTER GRANT … SET COLUMNS ''`). Its *Versions* are shown; RLS is written as a literal.
- **A virtual table's Columns tab** (`RelationColumns`): `name`, `<column> AS <name>` (a rename), `<expr> AS <name>`
  (an expression of the source, or a computed column); no nullability. An item it does not take apart (a star, `a.b`,
  `name: <expr>`) is kept as written. The stored list is read from `platform.relation_columns` (`expr` empty = the
  column itself, `name` NULL = a star).
- **Caps**: an `ingest` box.

## Enforcement & security

The node decides every grant; the plugin's part is to write what the user sees. A grant written again carries every
property the node holds - its masks and versions included - or is not written. A text of the user's is a literal
(the node reads it as the value it is), and the node refuses a comment in a mask list, so no text hides the next item.

## Tests

- Unit (`io.github.hugrlab.acl.dbeaver.unit`): `GrantColumnsTest` (the test and masks read and written, the mode kept,
  unknown names and masks kept), `RelationColumnsTest` (the SELECT list forms), `EditStatementsTest` (an object grant
  written again keeps its masks and versions; an unreadable one is refused; a catalog grant writes no COLUMNS and
  clears a legacy list; every text a literal).
- Live (`LiveEditTest`, against the dev node): an object grant with a mask and VERSIONS changed in its RLS keeps both.
- Run 2026-11-22 against a fresh `ACL_CLUSTER=1 ACL_METADATA=1 dev/node.sh` (hugr-node 2cab612): unit 43/43, every
  Live class but `LiveConsoleTest` green. Its three SWTBot cases (`aRoleThroughDbeaversEditor`: no `Delete` in the
  context menu; `aGrantsRowFilterAndColumnsTabs`, `aFunctionsDefinitionTab`: no editor opens) fail the same on main on
  macOS - a UI matter of its own; the Columns tab's new texts there are updated but not run.

## Found by its review, fixed

- A save compared with the node only the properties it changed, while it writes the grant whole: a mask written on the
  node meanwhile was taken away by a caps edit. An object grant compares every property (caps, RLS, test, masks,
  versions); one the node now holds that this cannot read refuses the save.
- A stored test or masks in a shape the plugin does not read whole (an unknown mode, `only` with no names, a mask item
  without its expression, a value that is no STRUCT / LIST) read as every column / no mask: now unreadable - revoke
  only.
- Switching *later columns* and back dropped a name the object lacks (a column gone from the source): each mode keeps
  its own.
- A virtual table's list was written back in the source's order, a column listed twice lost (`amount, amount AS
  amount2`): the items keep their order, a second reading of a column is a computed column.
- `* EXCLUDE ssn` (no parentheses) is read; a column masked anew reads `NULL`; an edit of the Columns tab that writes
  nothing (every column hidden under a list, a mask cleared) is taken back, so the tab shows what Save writes.
- Re-review: the stale dialog says that saving over an object grant replaces what the node holds, masks included.
- Left: a NEW object grant on an object the role already holds a grant on replaces it (the grammar's rule; the plugin
  does not look first) - its masks with it. The tree shows the existing grant; a follow-up may refuse the create.

## After the merge: no text read by the plugin (the owner, 2026-11-22)

The tabs read the grant's and the relation's texts back with a splitter and regular expressions of their own, and so
read some otherwise than duckdb (`* EXCLUDE a, b`, an `E'…'` or `$$…$$` string in a mask) - against the rule that
duckdb's parser reads SQL. The node already gives its reading: `platform.object_grants` (the test's mode and names,
the masks as (column, mask)) and `platform.relation_columns` (name, expr per item). So the tabs are filled from those
and keep their own state while unsaved; the plugin only composes the text it writes - no splitter, no pattern (the
texts are not edited by hand: hidden from the properties panel, edited by the tabs alone). A relation item is the
source's column when the node printed it as one reference (`a` / `"a"`, compared, not read). A cluster source's
options (JSON) are read by a JSON parser (Gson), not by hand.

## Alternatives

- ALTER for an object grant: the node has none (a grant written again is its edit); keeping every property is the
  plugin's job.
- Editing a catalog grant's versions: a versions change writes the grant again (`GRANT CATALOG … VERSIONS`), which a
  legacy column list would not survive - left to the SQL editor, as the console does.
