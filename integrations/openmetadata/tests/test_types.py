"""duckdb's type text to OpenMetadata columns (acl-clients spec 005)."""

from metadata.generated.schema.entity.data.table import DataType

from acl_openmetadata.types import parse_type, to_column, type_display


def test_scalars_map_to_the_nearest_type_and_keep_the_text():
    for text, expected in [
        ("INTEGER", DataType.INT), ("BIGINT", DataType.BIGINT), ("HUGEINT", DataType.NUMERIC),
        ("UBIGINT", DataType.NUMERIC), ("UINTEGER", DataType.BIGINT), ("DOUBLE", DataType.DOUBLE),
        ("BOOLEAN", DataType.BOOLEAN), ("DATE", DataType.DATE), ("TIMESTAMP", DataType.TIMESTAMP),
        ("TIMESTAMP WITH TIME ZONE", DataType.TIMESTAMPZ), ("TIMESTAMP_NS", DataType.TIMESTAMP),
        ("BLOB", DataType.BLOB), ("UUID", DataType.UUID), ("JSON", DataType.JSON), ("INTERVAL", DataType.INTERVAL),
    ]:
        column = to_column("c", text)
        assert column.dataType == expected, text
        assert column.dataTypeDisplay == text


def test_varchar_has_the_length_openmetadata_requires():
    column = to_column("name", "VARCHAR")
    assert column.dataType == DataType.VARCHAR and column.dataLength == 1


def test_decimal_keeps_precision_and_scale():
    column = to_column("balance", "DECIMAL(18,3)")
    assert column.dataType == DataType.DECIMAL and (column.precision, column.scale) == (18, 3)


def test_a_struct_nests_its_fields():
    column = to_column("address", "STRUCT(city VARCHAR, zip VARCHAR, geo STRUCT(lat DOUBLE, lon DOUBLE))")
    assert column.dataType == DataType.STRUCT
    assert [c.name.root for c in column.children] == ["city", "zip", "geo"]
    geo = column.children[2]
    assert geo.dataType == DataType.STRUCT and [c.name.root for c in geo.children] == ["lat", "lon"]
    assert geo.dataTypeDisplay == "STRUCT(lat DOUBLE, lon DOUBLE)"


def test_lists_and_arrays():
    tags = to_column("tags", "VARCHAR[]")
    assert tags.dataType == DataType.ARRAY and tags.arrayDataType == DataType.VARCHAR
    scores = to_column("scores", "INTEGER[3]")
    assert scores.dataType == DataType.ARRAY and scores.arrayDataType == DataType.INT
    visits = to_column("visits", "STRUCT(city VARCHAR, n INTEGER)[]")
    assert visits.dataType == DataType.ARRAY and visits.arrayDataType == DataType.STRUCT
    assert [c.name.root for c in visits.children] == ["city", "n"]


def test_map_union_enum():
    assert to_column("attrs", "MAP(VARCHAR, INTEGER)").dataType == DataType.MAP
    assert to_column("u", "UNION(a INTEGER, b VARCHAR)").dataType == DataType.UNION
    mood = to_column("mood", "ENUM('calm', 'busy')")
    assert mood.dataType == DataType.ENUM and mood.dataTypeDisplay == "ENUM('calm', 'busy')"


def test_quoted_field_names_and_round_trip():
    duck = parse_type('STRUCT("select" INTEGER, "a b" VARCHAR[])')
    assert [name for name, _ in duck.fields] == ["select", "a b"]
    # a child's display (its parent keeps the node's own text): a name that is not a plain word is quoted
    assert type_display(duck) == 'STRUCT(select INTEGER, "a b" VARCHAR[])'


def test_an_unreadable_type_stays_the_nodes_text():
    column = to_column("x", "STRUCT(a INTEGER")
    assert column.dataType == DataType.UNKNOWN and column.dataTypeDisplay == "STRUCT(a INTEGER"
