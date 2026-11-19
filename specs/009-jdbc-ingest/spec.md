# Spec 009: bulk ingest through the JDBC driver, and a load by key

- **Status**: implemented
- **Date**: 2026-11-19
- **Node side**: duckdb-acl spec 173 (`acl.mode` = `merge` | `remove`, the `ingest` capability), 049 / 051 (ingest,
  create / replace), 050 / 055 (the session, a load in a transaction of its own)
- **Decided by the owner** (2026-11-19): add `acl.mode` to our driver - ADBC's Flight SQL driver sends no ingest
  options (1.12), so a load by key has no client but a hand-written one.

## Problem

The node loads a stream by the table's key (duckdb-acl spec 173) when the ingest command carries the option
`acl.mode`. JDBC has no bulk-load call at all, and Arrow's Flight SQL JDBC driver (19.0.0) exposes none: its
`ArrowFlightSqlClientHandler` keeps the `FlightSqlClient` private and offers no ingest. A Java client could only
open a second Flight connection of its own - another session, its own sign-in, without the connection's headers
(`x-acl-view-as`) or USE.

## Design

- **`AclIngest`** (public interface, `connection.unwrap(AclIngest.class)`): `long ingest(catalog, schema, table,
  InputStream arrowStream, Mode mode)` and a short form without catalog / schema (the session's, duckdb-acl spec
  114). `Mode`: `APPEND`, `CREATE`, `REPLACE` (the protocol's `TableDefinitionOptions`) and `MERGE`, `REMOVE`
  (append options + `acl.mode` = `merge` / `remove`). Returns the rows the node reports.
- **The rows are an Arrow IPC stream**: the format, not a Java type - the driver's Arrow is shaded, so a caller's
  `VectorSchemaRoot` could never be passed, and any Arrow version (or another language's bytes) writes the format.
- **On the connection's own client** (`FlightIngest`): the handler's `sqlClient.client` (the `FlightClient`) and
  `getOptions()` (bearer, headers) reached by reflection from `ArrowFlightConnection.getClientHandler()`; its middleware carries the
  session cookie, so the load is the connection's session - its USE, its view-as (the node refuses a write there),
  its token. A missing member fails by name ("built against another Arrow version"), pinned with `arrow.version`
  like `FlightDiscovery`'s shaded names.
- **The DoPut is driven here** (`CommandStatementIngest` packed as Arrow does, `startPut`, a batch per
  `loadNextBatch`), not by Arrow's `executeIngest`: a stream that fails after the put began (a truncated file, a
  source that drops) must cancel the call (`error()`), and Arrow's rethrows with the call left open - the door then
  waits for the next batch holding the session's lock, and every later statement of the connection hangs (found by
  the review).
- A refusal (Arrow's `FlightRuntimeException`) is an `SQLException` with the node's reason; a failure of Arrow's own
  takes the first message down its causes (its status carries none).
- **Not here**: `temporary` (spec 173 refuses it for a load by key; a staged temp is plain SQL's), the protocol's
  transaction id (the node refuses a load inside an open transaction - spec 055), the `acl.mode` connection
  property (data / manage / native, spec 006 - a different thing; the ingest's mode is the call's argument).

## Tests

- `AclIngestTest`: an in-process Flight SQL server (Arrow's shaded `NoOpFlightSqlProducer`) behind Arrow's real
  JDBC connection - each mode's table options and `acl.mode`, catalog / schema present or absent, the row count,
  a refusal's text in the `SQLException`, a missing table / stream and an unreadable stream refused; the
  connection's headers on every put (a server middleware); a stream cut in its second batch - the server sees the
  call end, the reason is said, the next load on the connection runs (fails without the fix: no reason, the call
  left open).
- e2e (`LiveDoorE2ETest.aLoadByKey`, dev node's `sales.stock`, key `sku`): merge with a key twice (the last row),
  merge over existing keys (updated), remove, append, and a merge into `orders` (no `ingest`, no key) refused.

## Alternatives considered

- **A second Flight connection of the driver's own**: another session, a second token acquisition, the
  connection's headers and USE lost.
- **`PreparedStatement` batches turned into an ingest**: a JDBC batch is parameter sets of one statement; reading
  a MERGE into it would be the driver inventing SQL.
- **The shaded `ArrowReader` / `VectorSchemaRoot` in the API**: ties every caller to the driver's shaded names.
