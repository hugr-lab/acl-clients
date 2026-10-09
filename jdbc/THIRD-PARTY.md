# Third-party code in the duckdb-acl JDBC driver

## duckdb-java

Parts of this driver are adapted from [duckdb-java](https://github.com/duckdb/duckdb-java), the DuckDB
JDBC driver: the type table of `DuckTypes` (after `DuckDBResultSetMetaData`), `AclStruct` (after
`DuckDBStruct`), and the static values, filter rules and listing shapes of `AclDatabaseMetaData` /
`MetadataSql` (after `DuckDBDatabaseMetaData`). Each adapted file names its source in its header.

```text
MIT License

Copyright 2018-2025 Stichting DuckDB Foundation

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial
portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

## Apache Arrow

The driver runs on Apache Arrow's Flight SQL JDBC driver (Apache License 2.0), bundled unchanged in
the `-all` jar with its own notices.
