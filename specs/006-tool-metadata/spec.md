# Spec 006: metadata for tools - the JDBC driver's DatabaseMetaData, types and modes

- **Status**: accepted (by the owner 2026-10-09)
- **Date**: 2026-10-09
- **Node side**: duckdb-acl spec 115 (SqlInfo, `ARROW:FLIGHT:SQL:TYPE_NAME`, XdbcTypeInfo, functions in
  every held catalog, `acl_function_columns`, `duckdb_schemas.parent_schema`), spec 114 (`USE`), spec 116
  (quoted names, in parallel)
- **Design**: duckdb-acl design/079 (DBeaver as the management console) - phase 0, the client half
- **Borrowed from**: duckdb-java (MIT) `DuckDBDatabaseMetaData` / `DuckDBStruct` - behaviour and
  type mapping, credited (see Licence)

## Problem

Our driver returns Arrow's flight-sql-jdbc 19.0.0 connection unchanged. Measured 2026-10-09 (live
dump against a seeded node) and read in Arrow's source at v19.0.0:

- **Functions are nowhere**: Arrow does not implement `getFunctions` / `getProcedures` /
  `getFunctionColumns` / `getTypeInfo` (Avatica's empty answers) - a tool's tree has no functions.
- **Empty metadata answers have no columns**: Arrow builds a metadata ResultSet's columns in the batch
  transformer, which never runs on a stream with no rows - an empty `getPrimaryKeys` / `getImportedKeys`
  / `getColumns` has 0 columns, `getString("COLUMN_NAME")` throws (DBeaver's Keys/Columns tabs).
- **Types**: `getColumns.DATA_TYPE` maps STRUCT / MAP / UNION to `JAVA_OBJECT`, LIST to `ARRAY`;
  `ResultSetMetaData.getColumnTypeName` always gives that generic name (Arrow ignores TYPE_NAME
  there); a STRUCT's `getObject` is a Map, never a `java.sql.Struct`.
- **REMARKS** are always NULL on tables (Arrow adds them as empty fields).
- **`setCatalog` / `setSchema`** go to Flight `SetSessionOptions{catalog}`, which the door refuses (only
  TimeZone / Calendar, spec 068) - and the `catalog` URL property fails the same way at connect.
- **No way to run management or native SQL as a mode**: every statement must carry `ACL ` / `ACL NATIVE `
  by hand.
- Now present from the node (spec 115) and read by Arrow already: quote string, keywords, search
  escape, terms, catalog-at-start, `supportsTransactions`; `getColumns.TYPE_NAME` from the field
  metadata. Kept as is.

## Design

### 1. `AclConnection` (wraps Arrow's)
- `AclDriver.connect` returns it; `unwrap` reaches Arrow's.
- `setCatalog(c)` → `USE "<c>"`, `setSchema(s)` → `USE SCHEMA "<s>"` (spec 114), identifiers quoted the
  duckdb way (`""` doubling); `getCatalog` / `getSchema` → `SELECT current_database(), current_schema()`,
  cached, invalidated by a USE through any statement of the connection.
- `catalog` / `schema` URL properties become ours (never handed to Arrow): applied with `USE` right after
  connect - after a first call has earned the session cookie (a USE on a cookie-less, per-call session
  would be lost; spec 050).
- `acl.mode` = `data` (default) | `manage` | `native`: every statement (`createStatement`,
  `prepareStatement`, `prepareCall`, `nativeSQL`) is prefixed with nothing / `ACL ` / `ACL NATIVE `. The
  node decides by scope (`native` without `passthrough` is the node's refusal, never the driver's).
  A tool's "management" or "native" connection is then a second connection with this property.

### 2. `AclDatabaseMetaData` (extends Arrow's, delegates what is right)
- **Kept from Arrow** (spec 115's SqlInfo): product, quote string, keywords, escape, terms,
  catalog-at-start, `supportsTransactions`, `isReadOnly`.
- **Static** (duckdb-java's values): `getDefaultTransactionIsolation` (REPEATABLE_READ),
  `supportsTransactionIsolationLevel`, `storesMixedCase*` true / `storesUpper|LowerCase*` false,
  `getCatalogSeparator` `.`, `getTableTypes` (`BASE TABLE`, `VIEW`, `LOCAL TEMPORARY`).
- **Listings by SQL** on the same connection, answered as an in-memory **`RowsResultSet`** (fixed
  `ColumnSpec`s + rows - its fixed columns fix the empty-shape bug for every method):

  | Method | Source (principal surfaces) |
  |---|---|
  | `getCatalogs` / `getSchemas` | `duckdb_schemas()` (nested paths, `parent_schema`) |
  | `getTables` | `duckdb_tables()` + `duckdb_views()` - REMARKS from `comment` |
  | `getColumns` | `duckdb_columns()` - TYPE_NAME = `data_type`, DATA_TYPE / COLUMN_SIZE / DECIMAL_DIGITS from `DuckTypes`, REMARKS, COLUMN_DEF, NULLABLE |
  | `getFunctions` / `getProcedures` | `duckdb_functions()` - CAT = catalog, SCHEM = schema path, TYPE by `function_type` (table → `functionReturnsTable`), SPECIFIC_NAME = name + signature, the JDBC column order (duckdb-java's is wrong there) |
  | `getFunctionColumns` / `getProcedureColumns` | `acl_function_columns(cat, schema, fn)` - `param` → In, `column` → Result, `return` → Return |
  | `getTypeInfo` | `duckdb_types()` (system catalog) + a static JDBC code table |
  | `getPrimaryKeys` / `getImportedKeys` / `getExportedKeys` / `getCrossReference` | `acl_keys()` / `acl_references()` (spec 048 / 022 - not `duckdb_constraints`, which is not a principal surface) |

  Rule for the metadata SQL: only column references, `=`, `LIKE … ESCAPE '\'`, `IS NULL`, `ORDER BY`
  and literals (quotes doubled) - every type mapping and CASE in Java: under a principal every
  function call passes the function gate (spec 072), and an ordinary role must be able to read its own
  tree. Literals, not `?` (two round trips instead of four). JDBC filter rules as duckdb-java: null =
  no filter, `""` = IS NULL, null pattern = `%`.
- **Mode** (owner, 2026-10-09): the metadata SQL is never prefixed - the tree is always the
  principal's virtual catalog, whatever `acl.mode`. The physical engine is shown to an admin *inside*
  the virtual tree, as nested schemas of the system catalog (`platform.attached.<alias>.<schema>.<table>`
  - duckdb-acl spec 117), metadata only; `native` is a kind of session (every statement `ACL NATIVE`),
  or a `ACL NATIVE` prefix written in an ordinary session by a principal that holds the right - never a
  second tree.

### 3. `AclResultSet` / `AclResultSetMetaData` (wrap statement results)
- `getColumnTypeName` = the field's TYPE_NAME (spec 115), `getColumnType` / `getColumnClassName` from
  `DuckTypes`; read off the Arrow schema of the result - Arrow exposes none, so by reflection on
  `ArrowFlightJdbcFlightStreamResultSet.schema` (shaded type), falling back to Arrow's generic name.
- `getObject`: STRUCT → `AclStruct implements java.sql.Struct` (attributes in field order,
  `getSQLTypeName` = the type text, after duckdb-java's `DuckDBStruct`), MAP → `Map`, LIST → a
  `java.sql.Array`. Property `nested=json`: STRUCT / MAP / LIST as a JSON string (BI tools, Spark).

### 4. `DuckTypes`
One parser of duckdb type text for both: `…[]` / `…[n]` → ARRAY, STRUCT → STRUCT, MAP / UNION →
OTHER, DECIMAL(p,s) → DECIMAL with size/digits, ENUM → VARCHAR (it arrives as dictionary utf8),
TIMESTAMP WITH TIME ZONE → TIMESTAMP_WITH_TIMEZONE, the scalars - duckdb-java's table, with its list
bug fixed (`INTEGER[]` never matched `LIST` there).

### 5. DBeaver without the plugin
`examples/dbeaver/drivers.xml`: the Generic provider, our driver class, `split-procedures-and-functions`,
`query-set-active-db` → `USE ?` - and the plugin (spec 007) builds on the same driver.

## Licence

duckdb-java is MIT ("Copyright 2018-2025 Stichting DuckDB Foundation"), like this repository. A file
adapted from it carries a header naming the source; `THIRD-PARTY.md` carries its MIT text and ships in
the jar (`META-INF/`, shade plugin).

## Testing

- Units: `DuckTypes` (STRUCT nested, `INTEGER[]`, `INTEGER[3]`, MAP, UNION, `DECIMAL(18,3)`, ENUM,
  TIMESTAMPTZ); `RowsResultSet` shape when empty; `acl.mode` prefixing; `catalog` / `schema` consumed by
  `AclConfig` (never passed to Arrow); `AclStruct`.
- `dev/node.sh` seed (`ACL_METADATA=1`): a table with STRUCT / LIST / MAP / DECIMAL / ENUM and a COMMENT,
  a view, table functions (one with a parameter), a nested schema `sales.raw.eu`, a second catalog, a
  grant-masked column.
- `LiveMetadataE2ETest` (gated like `LiveDoorE2ETest`), as an **ordinary role** (not an admin): quote
  string, catalog-at-start, terms, transactions; `getColumns` TYPE_NAME / DATA_TYPE of each nested
  column; `getTypeInfo` non-empty; `getFunctions` / `getFunctionColumns` of the table function;
  `getSchemas` with `raw` and `raw.eu`; an empty `getPrimaryKeys` with its 6 columns; `getObject` of a
  struct `instanceof Struct`; `setCatalog` then `getCatalog`; `acl.mode=manage` runs a management
  statement; a metadata dump written to a file for review.

## Alternatives considered

- **Arrow's RPC listings with an empty-shape fallback**: keeps two code paths and still lacks
  functions, comments and parents.
- **GetXdbcTypeInfo through the shaded `FlightSqlClient`** (as `FlightDiscovery` does): more shaded API
  surface for a static table.
- **A fork of Arrow's driver**: the version DBeaver needs moves (arrow-java#964, DBeaver #41638); a
  wrapper keeps us on upstream.

## Risks

- Reflection on Arrow's private `schema` field and shaded names can break on an Arrow bump - the live
  e2e is the catch.
- The metadata SQL is subject to the function gate - tested as an ordinary role.
- `getSQLKeywords` includes our grammar words (cosmetic for JDBC's "non-SQL:2003" contract).
