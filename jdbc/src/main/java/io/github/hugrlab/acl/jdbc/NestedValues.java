package io.github.hugrlab.acl.jdbc;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arrow's driver hands nested values as its own collections - a STRUCT as a map, text as Arrow's
 * {@code Text} - so they are turned here into what a JDBC client expects: {@link AclStruct} for a
 * STRUCT (at any depth), plain lists and maps, strings for text; or, with {@code nested=json}, one
 * JSON text.
 */
final class NestedValues {
	private NestedValues() {
	}

	/** The value as JDBC objects, guided by the column's duckdb type where it is known. */
	static Object normalize(Object raw, DuckTypes.Type type) throws SQLException {
		if (raw == null) {
			return null;
		}
		if (raw instanceof Array array) {
			raw = toList(array.getArray());
		}
		if (type != null && type.isStruct() && raw instanceof Map<?, ?> map) {
			List<String> names = new ArrayList<>();
			List<Object> values = new ArrayList<>();
			if (!type.fields().isEmpty() && type.fields().size() == map.size()) {
				// by name; by position when the map's keys are spelled otherwise
				List<Object> byPosition = new ArrayList<>(map.values());
				for (int i = 0; i < type.fields().size(); i++) {
					DuckTypes.Field field = type.fields().get(i);
					Object value = map.containsKey(field.name()) ? map.get(field.name()) : byPosition.get(i);
					names.add(field.name());
					values.add(normalize(value, field.type()));
				}
			} else {
				for (Map.Entry<?, ?> e : map.entrySet()) {
					names.add(String.valueOf(plain(e.getKey())));
					values.add(normalize(e.getValue(), null));
				}
			}
			return new AclStruct(type.text(), names, values.toArray());
		}
		if (raw instanceof List<?> list) {
			DuckTypes.Type element = type != null && type.isList() ? type.element() : null;
			List<Object> out = new ArrayList<>(list.size());
			for (Object item : list) {
				out.add(normalize(item, element));
			}
			return out;
		}
		if (raw instanceof Map<?, ?> map) {
			DuckTypes.Type key = type != null && type.isMap() ? type.key() : null;
			DuckTypes.Type value = type != null && type.isMap() ? type.value() : null;
			Map<Object, Object> out = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : map.entrySet()) {
				out.put(normalize(e.getKey(), key), normalize(e.getValue(), value));
			}
			return out;
		}
		return plain(raw);
	}

	/** Arrow's {@code Text} (a shaded class) as a String; anything else unchanged. */
	static Object plain(Object raw) {
		if (raw != null && raw.getClass().getSimpleName().equals("Text")) {
			return raw.toString();
		}
		return raw;
	}

	private static List<Object> toList(Object array) {
		List<Object> out = new ArrayList<>();
		if (array instanceof Object[] items) {
			for (Object item : items) {
				out.add(item);
			}
		} else if (array != null && array.getClass().isArray()) {
			int n = java.lang.reflect.Array.getLength(array);
			for (int i = 0; i < n; i++) {
				out.add(java.lang.reflect.Array.get(array, i));
			}
		}
		return out;
	}

	/** A normalized value as JSON text. */
	static String json(Object value) {
		StringBuilder out = new StringBuilder();
		writeJson(value, out);
		return out.toString();
	}

	private static void writeJson(Object value, StringBuilder out) {
		if (value == null) {
			out.append("null");
		} else if (value instanceof AclStruct struct) {
			writeJson(struct.getMap(), out);
		} else if (value instanceof Map<?, ?> map) {
			out.append('{');
			boolean first = true;
			for (Map.Entry<?, ?> e : map.entrySet()) {
				if (!first) {
					out.append(',');
				}
				first = false;
				quote(String.valueOf(plain(e.getKey())), out);
				out.append(':');
				writeJson(e.getValue(), out);
			}
			out.append('}');
		} else if (value instanceof List<?> list) {
			out.append('[');
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) {
					out.append(',');
				}
				writeJson(list.get(i), out);
			}
			out.append(']');
		} else if (value instanceof Object[] items) {
			writeJson(java.util.Arrays.asList(items), out);
		} else if (value instanceof byte[] bytes) {
			quote(Base64.getEncoder().encodeToString(bytes), out);
		} else if (value instanceof Boolean || value instanceof Integer || value instanceof Long
		           || value instanceof Short || value instanceof Byte || value instanceof java.math.BigInteger
		           || value instanceof java.math.BigDecimal) {
			out.append(value);
		} else if (value instanceof Double || value instanceof Float) {
			double d = ((Number) value).doubleValue();
			if (Double.isFinite(d)) {
				out.append(value);
			} else {
				quote(value.toString(), out);
			}
		} else {
			quote(plain(value).toString(), out);
		}
	}

	private static void quote(String s, StringBuilder out) {
		out.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				case '\b' -> out.append("\\b");
				case '\f' -> out.append("\\f");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					} else {
						out.append(c);
					}
				}
			}
		}
		out.append('"');
	}
}
