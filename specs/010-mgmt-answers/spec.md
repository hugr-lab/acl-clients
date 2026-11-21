# Spec 010: the plugin reads what a management statement answers

- **Status**: implemented
- **Date**: 2026-11-20
- **Node side**: duckdb-acl spec 179 (every management statement and top-level `platform.<op>(…)` call answers
  `(ok BOOLEAN, message VARCHAR)`), hugr-node spec 035 stage 3 (a GRANT's hint: the groups whose source set leaves out
  a source the granted role now reads, with the repair to run - never applied by itself)
- **Builds on**: spec 008 (the editors, the branch's operations)

## Problem

A management statement now answers `(ok, message)`. The plugin ran every statement and threw the result away: DBeaver's
own persist path (`DBExecUtils.executePersistAction`) only executes, and the branch's operations (`AclRunHandler`) did
`execute(statement)` and refreshed the tree. So a user never saw:

- what did not happen (`RESUME NODE` on a node that was not draining: `the node was not draining`);
- what the node says beside a write - drain's `<n> sessions still open` (the watch loop of spec 008.3), a repair's or
  a refresh's count, and the GRANT hint of hugr-node spec 035, which exists only to be read.

## Design

- **`AclAnswer`** (`model.access`, pure): `read(ResultSet)` - the first row's `ok` and `message` by column name; none
  when the result has no such pair (a CHECK's rows, a query) or no row. An `ok` that is NULL or not a boolean is not ok
  (the node never answers one; a client must not read it as success); a NULL message is empty. A STRUCT answer's own
  fields after the pair (hugr-node's CLUSTER forms) are left to the tree. `says()` = a false or a non-empty message;
  `report(statements, answers)` = each statement that said something with its message (a false marked `not done:`).
- **Editors**: `AclObjectManager` composes its statements as `AnsweredAction`s (one `Answers` per command) and runs them
  itself on the JDBC session (`beforeExecute` / `afterExecute` as DBeaver does, a refusal the same `DBCException`), so
  it reads each answer. After a command's last statement, what they said is shown once, never holding the save: a
  statement that was not done as a warning box, otherwise (a count, a grant's hint) as DBeaver's notification - a modal
  box after every repair or grant was noise (the live tests' DBeaver could not even close). A plain success says
  nothing.
- **Operations** (`AclRunHandler`): the statement's answer is read and, when it says something, shown in a message box
  (a warning if not done) before the tree is refreshed - the user ran the operation to read its answer (drain's
  sessions still open, the watch loop of 008.3).
- **A refusal stays an error**, as before (the node refuses with an error, never with `ok = false`), raised as DBeaver
  raises it (`DBSQLException` with the statement). Once a statement ran, a failure to read its answer is no refusal:
  the write stands and the answer is none. When a later statement of the command is refused, what the earlier ones said
  is still shown, and the error's "Applied before it" marks a statement that answered not done.
- **Found by its review, fixed**: the above (a read failure reported as a refusal, the answers lost with a later
  refusal, an empty script run), and a not-done operation's icon (a warning, not an error).
- Nothing composes or parses the message: it is the node's text, shown as it is.

## Tests

- `AclAnswerTest` (unit): the pair read by name, a STRUCT's extra fields, a NULL / non-boolean `ok` not ok, no pair =
  no answer; what is said and what is silent; the report's text; `read` over a result by label, no row, no pair.
- `LiveOperateTest` (dev/node.sh): the operations' statements read through `AclAnswer` - DRAIN NODE answers ok with
  `<n> session(s) still open`, RESUME NODE ok and silent, a second RESUME `ok = false`, `the node was not draining`, a
  session's kill ok.
- The live editor tests (`LiveEditTest`) save through the new run path; a statement it failed to run or read fails
  them; which of the notification and the warning box shows is not tested in the UI. The answers reach the plugin over Flight only with duckdb-acl's spec 179 addendum (#319): a prepared management
  statement was announced as an update and Arrow's JDBC driver lost its answer.

## Alternatives

- DBeaver's persist path with a listener for the result: it has none - `executePersistAction` drops the statement's
  result, so the plugin runs the statement itself.
- One box per statement: a command's statements (a grant and its masks) are one save; one box says what all of them
  answered.
