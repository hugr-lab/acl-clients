// Adapted from duckdb-java (MIT, Copyright 2018-2025 Stichting DuckDB Foundation): DuckDBStruct.
// See THIRD-PARTY.md.
package io.github.hugrlab.acl.jdbc;

import java.sql.Struct;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A duckdb STRUCT value as {@link java.sql.Struct}: the attributes in the type's field order,
 * {@link #getSQLTypeName()} the type text ({@code STRUCT(city VARCHAR, ...)}), and the names kept for
 * {@link #getMap()} - what a tool shows and what {@code nested=json} writes.
 */
public final class AclStruct implements Struct {
	private final String typeName;
	private final List<String> names;
	private final Object[] attributes;

	AclStruct(String typeName, List<String> names, Object[] attributes) {
		this.typeName = typeName;
		this.names = List.copyOf(names);
		this.attributes = attributes.clone();
	}

	@Override
	public String getSQLTypeName() {
		return typeName;
	}

	@Override
	public Object[] getAttributes() {
		return attributes.clone();
	}

	@Override
	public Object[] getAttributes(Map<String, Class<?>> map) {
		return getAttributes();
	}

	/** The fields by name, in order. */
	public Map<String, Object> getMap() {
		Map<String, Object> out = new LinkedHashMap<>();
		for (int i = 0; i < names.size(); i++) {
			out.put(names.get(i), attributes[i]);
		}
		return Collections.unmodifiableMap(out);
	}

	@Override
	public String toString() {
		return NestedValues.json(this);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof AclStruct s && s.typeName.equals(typeName) && s.names.equals(names)
		    && Arrays.deepEquals(s.attributes, attributes);
	}

	@Override
	public int hashCode() {
		return typeName.hashCode() * 31 + Arrays.deepHashCode(attributes);
	}
}
