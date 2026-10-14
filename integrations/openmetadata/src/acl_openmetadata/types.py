"""duckdb's type text, as a node's information_schema.columns answers it, to OpenMetadata's columns.

The nesting is ours (STRUCT, a list `T[]`, an array `T[n]`, MAP, UNION); the scalar names go through
OpenMetadata's own ColumnTypeParser, with duckdb's own names it does not know mapped first. The text as the node
wrote it is always kept (`dataTypeDisplay`): the OpenMetadata type is the nearest one, the display is the truth.
"""

from __future__ import annotations

from dataclasses import dataclass, field

# duckdb names OpenMetadata's parser answers UNKNOWN or too loosely: the nearest type that holds the value
_DUCKDB_SCALARS = {
    "HUGEINT": "NUMERIC",
    "UHUGEINT": "NUMERIC",
    "VARINT": "NUMERIC",
    "BIGNUM": "NUMERIC",
    "UBIGINT": "NUMERIC",
    "UINTEGER": "BIGINT",
    "USMALLINT": "INT",
    "UTINYINT": "SMALLINT",
    "TIMESTAMP WITH TIME ZONE": "TIMESTAMPZ",
    "TIMESTAMPTZ": "TIMESTAMPZ",
    "TIMESTAMP_NS": "TIMESTAMP",
    "TIMESTAMP_MS": "TIMESTAMP",
    "TIMESTAMP_S": "TIMESTAMP",
    "TIME WITH TIME ZONE": "TIME",
    "TIMETZ": "TIME",
    "VARCHAR": "VARCHAR",
    "STRING": "VARCHAR",
    "TEXT": "VARCHAR",
}


@dataclass
class DuckType:
    """A parsed duckdb type: a base name, its arguments, its fields (STRUCT / UNION), its element (a list / array)."""

    name: str  # STRUCT, UNION, MAP, LIST, ARRAY, DECIMAL, ENUM, or a scalar's name
    fields: list[tuple[str, DuckType]] = field(default_factory=list)
    args: list[str] = field(default_factory=list)  # DECIMAL's precision and scale, ENUM's values
    element: DuckType | None = None  # LIST / ARRAY
    key: DuckType | None = None  # MAP
    value: DuckType | None = None  # MAP
    size: int | None = None  # ARRAY's fixed size


class _Parser:
    def __init__(self, text: str):
        self.text = text
        self.pos = 0

    def error(self, what: str) -> ValueError:
        return ValueError(f"cannot read the type {self.text!r} at {self.pos}: {what}")

    def skip(self) -> None:
        while self.pos < len(self.text) and self.text[self.pos].isspace():
            self.pos += 1

    def peek(self) -> str:
        self.skip()
        return self.text[self.pos] if self.pos < len(self.text) else ""

    def expect(self, ch: str) -> None:
        if self.peek() != ch:
            raise self.error(f"expected {ch!r}")
        self.pos += 1

    def name(self) -> str:
        """An identifier: a run of word characters, or a double-quoted one ("" inside is one quote)."""
        self.skip()
        if self.peek() == '"':
            self.pos += 1
            out = []
            while True:
                if self.pos >= len(self.text):
                    raise self.error("an unterminated quoted name")
                ch = self.text[self.pos]
                self.pos += 1
                if ch == '"':
                    if self.pos < len(self.text) and self.text[self.pos] == '"':
                        out.append('"')
                        self.pos += 1
                        continue
                    return "".join(out)
                out.append(ch)
        start = self.pos
        while self.pos < len(self.text) and (self.text[self.pos].isalnum() or self.text[self.pos] in "_$"):
            self.pos += 1
        if start == self.pos:
            raise self.error("expected a name")
        return self.text[start : self.pos]

    def literal_or_number(self) -> str:
        self.skip()
        if self.peek() == "'":
            self.pos += 1
            out = []
            while True:
                if self.pos >= len(self.text):
                    raise self.error("an unterminated literal")
                ch = self.text[self.pos]
                self.pos += 1
                if ch == "'":
                    if self.pos < len(self.text) and self.text[self.pos] == "'":
                        out.append("'")
                        self.pos += 1
                        continue
                    return "".join(out)
                out.append(ch)
        start = self.pos
        while self.pos < len(self.text) and (self.text[self.pos].isalnum() or self.text[self.pos] in "_.-"):
            self.pos += 1
        return self.text[start : self.pos]

    def base_name(self) -> str:
        """A scalar's name may be several words: TIMESTAMP WITH TIME ZONE, DOUBLE PRECISION."""
        words = [self.name()]
        while True:
            save = self.pos
            self.skip()
            if self.pos < len(self.text) and (self.text[self.pos].isalpha()):
                word = self.name()
                if word.upper() in ("WITH", "WITHOUT", "TIME", "ZONE", "PRECISION", "VARYING"):
                    words.append(word)
                    continue
            self.pos = save
            return " ".join(words).upper()

    def type(self) -> DuckType:
        base = self.base_name()
        out: DuckType
        if base in ("STRUCT", "UNION") and self.peek() == "(":
            self.pos += 1
            fields = []
            while True:
                fname = self.name()
                fields.append((fname, self.type()))
                if self.peek() == ",":
                    self.pos += 1
                    continue
                self.expect(")")
                break
            out = DuckType(base, fields=fields)
        elif base == "MAP" and self.peek() == "(":
            self.pos += 1
            key = self.type()
            self.expect(",")
            value = self.type()
            self.expect(")")
            out = DuckType("MAP", key=key, value=value)
        elif self.peek() == "(":
            self.pos += 1
            args = []
            while True:
                args.append(self.literal_or_number())
                if self.peek() == ",":
                    self.pos += 1
                    continue
                self.expect(")")
                break
            out = DuckType(base, args=args)
        else:
            out = DuckType(base)
        # suffixes: T[] a list, T[n] an array - each wraps what came before
        while self.peek() == "[":
            self.pos += 1
            self.skip()
            size = None
            if self.peek() != "]":
                text = self.literal_or_number()
                size = int(text) if text.isdigit() else None
            self.expect("]")
            out = DuckType("ARRAY" if size is not None else "LIST", element=out, size=size)
        return out


def parse_type(text: str) -> DuckType:
    parser = _Parser(text)
    out = parser.type()
    if parser.peek():
        raise parser.error("trailing text")
    return out


def om_type_name(duck: DuckType) -> str:
    """The OpenMetadata DataType's name for a parsed type."""
    if duck.name in ("LIST", "ARRAY"):
        return "ARRAY"
    if duck.name in ("STRUCT", "UNION", "MAP", "DECIMAL", "ENUM"):
        return duck.name
    if duck.name in _DUCKDB_SCALARS:
        return _DUCKDB_SCALARS[duck.name]
    from metadata.ingestion.source.database.column_type_parser import ColumnTypeParser

    return ColumnTypeParser.get_column_type(duck.name)


def _children(duck: DuckType) -> list[tuple[str, DuckType]]:
    # an array of structs carries its struct's fields as the column's children (as OpenMetadata's Hive / BigQuery
    # sources do), a struct its own
    while duck.name in ("LIST", "ARRAY") and duck.element is not None:
        duck = duck.element
    return duck.fields if duck.name in ("STRUCT", "UNION") else []


def to_column(name: str, type_text: str, description: str | None = None, position: int | None = None):
    """An OpenMetadata Column for one of the node's columns; an unreadable type stays the node's text, UNKNOWN."""
    from metadata.generated.schema.entity.data.table import Column, DataType

    try:
        duck = parse_type(type_text)
    except ValueError:
        return Column(
            name=name, dataType=DataType.UNKNOWN, dataTypeDisplay=type_text, description=description,
            ordinalPosition=position,
        )
    return _column(name, duck, type_text, description, position)


def _column(name: str, duck: DuckType, display: str, description: str | None, position: int | None):
    from metadata.generated.schema.entity.data.table import Column, DataType

    data_type = DataType(om_type_name(duck))
    kwargs = {"name": name, "dataType": data_type, "dataTypeDisplay": display, "description": description}
    if position is not None:
        kwargs["ordinalPosition"] = position
    if data_type == DataType.ARRAY and duck.element is not None:
        element = duck.element
        while element.name in ("LIST", "ARRAY") and element.element is not None:
            element = element.element
        kwargs["arrayDataType"] = DataType(om_type_name(element))
    if data_type == DataType.DECIMAL and len(duck.args) == 2 and all(a.isdigit() for a in duck.args):
        kwargs["precision"], kwargs["scale"] = int(duck.args[0]), int(duck.args[1])
    if data_type == DataType.VARCHAR:
        # an unbounded duckdb VARCHAR: OpenMetadata requires a length for VARCHAR, and 1 is what its own SQL
        # sources set when the source declares none (the display says VARCHAR)
        kwargs["dataLength"] = 1
    children = _children(duck)
    if children:
        kwargs["children"] = [
            _column(child_name, child, type_display(child), None, None) for child_name, child in children
        ]
    return Column(**kwargs)


def type_display(duck: DuckType) -> str:
    """A parsed type written back - a child column's display (its parent's text holds the whole)."""
    if duck.name == "LIST" and duck.element is not None:
        return type_display(duck.element) + "[]"
    if duck.name == "ARRAY" and duck.element is not None:
        return f"{type_display(duck.element)}[{duck.size}]"
    if duck.name in ("STRUCT", "UNION"):
        return f"{duck.name}(" + ", ".join(f"{_quote(n)} {type_display(t)}" for n, t in duck.fields) + ")"
    if duck.name == "MAP" and duck.key is not None and duck.value is not None:
        return f"MAP({type_display(duck.key)}, {type_display(duck.value)})"
    if duck.args:
        if duck.name == "ENUM":
            return "ENUM(" + ", ".join("'" + a.replace("'", "''") + "'" for a in duck.args) + ")"
        return f"{duck.name}({', '.join(duck.args)})"
    return duck.name


def _quote(name: str) -> str:
    if name and (name[0].isalpha() or name[0] == "_") and all(c.isalnum() or c == "_" for c in name):
        return name
    return '"' + name.replace('"', '""') + '"'
