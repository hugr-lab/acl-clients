# Spec 006: metadata for tools - the JDBC driver's DatabaseMetaData, types and modes

- **Status**: implemented (accepted by the owner 2026-10-09)
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
- **Lazy by construction** (owner, 2026-10-09): there can be very many schemas and objects, virtual
  and physical. Every listing fetches only what was asked - the catalog / schema / name filters a tool
  passes go into the WHERE the node runs (`=` or `LIKE ... ESCAPE '\'`), never "fetch all and filter
  in Java"; `getSchemas(catalog, pattern)` returns only that catalog's schemas; `getTables` /
  `getColumns` / `getFunctions` are scoped by the catalog + schema the tool passes; no method walks the
  tree by itself, and nothing is cached beyond one call except `getCatalog` / `getSchema`.
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

## As built

- **Wrappers, not subclasses.** Arrow's `ArrowDatabaseMetadata` has a package-private constructor, so
  `AclDatabaseMetaData` cannot extend it: it wraps Arrow's instance and forwards what it does not
  answer itself (the SqlInfo-backed values stay Arrow's). The same holds for the connection, the
  statements and the result sets: `Forwarding*` bases (every `java.sql` method forwarded) and
  `UnsupportedResultSet` (for `RowsResultSet`) are generated from the JDK's interfaces by
  `jdbc/tools/GenForwarding.java`; the behaviour lives in `AclConnection`, `AclStatement`,
  `AclPreparedStatement`, `AclCallableStatement`, `AclDatabaseMetaData`, `AclResultSet`,
  `AclResultSetMetaData`. `unwrap` reaches Arrow's objects.
- **The metadata SQL** is composed in `MetadataSql` (one static method per listing, unit-tested for
  the filters it carries and for calling nothing but the listing table functions). The filters go into
  the SQL as the owner required. One bounded exception: `getFunctionColumns` /
  `getProcedureColumns` with a column pattern also fetch the asked functions' parameters
  (`column_kind = 'param' OR column_name LIKE ...`), because they make SPECIFIC_NAME; the ones the
  pattern does not name are dropped in Java. `acl_function_columns()` is filtered by WHERE rather than
  by its arguments, so patterns work.
- **Keys** come from `acl_keys()` / `acl_references()` as specified; the object is the path in its
  catalog (`orders`, `raw.eu.events`), composed from (schema, table) in the WHERE (`main` = the root;
  a null schema matches the name at any depth by `LIKE '%.<name>'`) and split back in Java. A
  reference counts as a foreign key when it goes to a relation by column pairs (`to_kind = 'relation'
  AND expression IS NULL`, pairs aligned in Java); `PK_NAME` = `<object>_pk` as the door's
  GetPrimaryKeys names it, rules `importedKeyNoAction`, `importedKeyNotDeferrable`.
- **Functions**: `getProcedures` answers the same rows as `getFunctions` (PROCEDURE_TYPE
  `procedureReturnsResult`) with the same SPECIFIC_NAME `name(TYPE, ...)`, so a tool that reads only
  procedures shows the functions and one that reads both can tell they are one object. A function's
  return value is a column named `returnValue` (ORDINAL_POSITION 0). The engine's functions a principal
  may call are listed too when no catalog is given (that is what `duckdb_functions()` answers); their
  columns are not (`acl_function_columns()` covers virtual functions).
- **`getTableTypes`** is static (`BASE TABLE`, `LOCAL TEMPORARY`, `VIEW`); `getTables` also accepts
  `TABLE` for `BASE TABLE` and puts the types into the SQL (a branch of the UNION per kind).
  `getTypeInfo` lists `duckdb_types()` of the system catalog, TYPE_NAME upper-cased, aliases included,
  sorted by DATA_TYPE.
- **`setSchema` with a nested schema** is `USE SCHEMA "raw.eu"` - the dotted path as ONE quoted name,
  which is how the node reads it (`"raw"."eu"` is a syntax error there). A `USE SCHEMA` needs a
  **schema grant** on the node: a schema the principal sees through its catalog grant only is refused
  ("no schema the principal holds", duckdb-acl spec 114's `HeldSchema`), so the dev seed grants
  `sales.raw.eu` to analyst.
- **Before the first USE** a connection sends `SELECT 1` (only if nothing has gone out yet), so the
  USE lands in the cookie session (duckdb-acl spec 050). The `catalog` / `schema` URL properties use
  the same path at connect; a failure closes Arrow's connection and fails the connect.
- **`acl.mode`**: leading whitespace and comments are dropped before the prefix (the node's prefix
  scanner reads none); a text that already starts with `ACL` is sent as written; in `manage` mode a
  `USE` is sent as written (the client's session statement, not a management one). A principal
  without passthrough gets "the principal has no ACL administration scope" for `ACL NATIVE ...` (the
  node's first check), not the passthrough sentence.
- **Result types**: `getColumnType` for every column the node names comes from `DuckTypes`; the
  class name of a scalar is Arrow's, and where Arrow leaves it null (it does) `DuckTypes.javaClass`
  (null for unsigned types - Arrow's accessors decide those). A LIST from `getObject` is Arrow's own
  `java.sql.Array`; STRUCT values are `AclStruct` at every depth; `getString` of a nested value is its
  JSON. Arrow's `Text` values inside nested ones become Strings (matched by simple name, no shaded
  link). The schema is read by reflection from Arrow's result set (`schema`, else
  `vectorSchemaRoot.getSchema()`), names only.
- **DBeaver**: `examples/dbeaver/drivers.xml` uses `USE "?"` (quoted, so a mixed-case or keyword
  catalog works) instead of `USE ?`, `active-entity-type = catalog`, `query-get-active-db`,
  `split-procedures-and-functions`, `supports-references`, and `supports-indexes = false` (no index
  listing).
- **Licence**: `jdbc/THIRD-PARTY.md` (duckdb-java's MIT text) ships as `META-INF/THIRD-PARTY.md` in
  the thin and the `-all` jar (a pom resource); `DuckTypes`, `AclStruct`, `AclDatabaseMetaData` and
  `MetadataSql` carry headers naming their source.
- **Tests**: units `DuckTypesTest`, `NestedValuesTest` (`AclStruct`, JSON), `AclSqlTest` (prefixes,
  USE, quoting), `MetadataSqlTest` (the filters in the SQL, no calls but the listings, JDBC filter
  rules), `RowsResultSetTest` (empty shape), `AclConnectionTest` (every statement path prefixed, USE
  and its cache, URL catalog/schema, metadata never prefixed and every empty listing with its JDBC
  columns, Java type mapping) over a scripted fake of Arrow's connection, `AclConfigTest` (the four
  keys consumed). Live: `LiveMetadataE2ETest` as analyst1 (an ordinary role) against
  `ACL_METADATA=1 dev/node.sh`, wired into `dev/jdbc-e2e.sh` (`ACL_E2E_METADATA=1`); 84 tests green
  on 2026-10-09 with the live ones.
- **Seen on the node, not fixed here**: `duckdb_views().comment` is NULL for a view created with
  `COMMENT` (so a view's REMARKS are empty); `USE SCHEMA` refuses a schema reachable only through the
  catalog grant (above).
