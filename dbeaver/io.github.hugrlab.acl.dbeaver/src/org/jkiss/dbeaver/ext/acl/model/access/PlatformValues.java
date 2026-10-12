package org.jkiss.dbeaver.ext.acl.model.access;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A value of a `platform` row as the properties panel shows it: a list joined, a capabilities STRUCT as the names of
 * the capabilities it holds (`select, insert`), another STRUCT as `name=value` pairs.
 */
public final class PlatformValues {
	private PlatformValues() {
	}

	public static String text(Object value) {
		if (value == null) {
			return null;
		}
		try {
			if (value instanceof Array array) {
				value = array.getArray();
			}
			if (value instanceof Struct struct) {
				Map<?, ?> named = fields(struct);
				return named != null ? text(named) : text(struct.getAttributes());
			}
		} catch (SQLException e) {
			return value.toString();
		}
		if (value instanceof Map<?, ?> map) {
			boolean flags = !map.isEmpty() && map.values().stream().allMatch(v -> v == null || v instanceof Boolean);
			List<String> parts = new ArrayList<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (flags) {
					if (Boolean.TRUE.equals(entry.getValue())) {
						parts.add(String.valueOf(entry.getKey()));
					}
				} else if (entry.getValue() != null) {
					parts.add(entry.getKey() + "=" + text(entry.getValue()));
				}
			}
			return String.join(", ", parts);
		}
		if (value instanceof Object[] items) {
			return join(java.util.Arrays.asList(items));
		}
		if (value instanceof Collection<?> items) {
			return join(items);
		}
		return value.toString();
	}

	/** A LIST value's items (a java.sql.Array, a Java list or array); empty for NULL. */
	public static List<Object> items(Object value) {
		List<Object> out = new ArrayList<>();
		try {
			if (value instanceof Array array) {
				value = array.getArray();
			}
		} catch (SQLException e) {
			return out;
		}
		if (value instanceof Object[] array) {
			out.addAll(java.util.Arrays.asList(array));
		} else if (value instanceof Collection<?> list) {
			out.addAll(list);
		}
		return out;
	}

	/** A STRUCT value's fields by name (a java.sql.Struct the driver names, or a map); empty when it has none. */
	public static Map<?, ?> fieldsOf(Object value) {
		if (value instanceof Struct struct) {
			Map<?, ?> named = fields(struct);
			return named == null ? Map.of() : named;
		}
		return value instanceof Map<?, ?> map ? map : Map.of();
	}

	/** A LIST of text as the grammar writes it in a list clause: `'a', 'b'`. */
	public static String quotedList(Object value) {
		List<String> out = new ArrayList<>();
		for (Object item : items(value)) {
			if (item != null) {
				out.add(AclGrammar.literal(item.toString()));
			}
		}
		return String.join(", ", out);
	}

	/** A LIST of words as the grammar writes it bare: `authcode, device`. */
	public static String bareList(Object value) {
		List<String> out = new ArrayList<>();
		for (Object item : items(value)) {
			if (item != null) {
				out.add(item.toString());
			}
		}
		return String.join(", ", out);
	}

	/** A STRUCT's fields by name - the driver's struct carries them (getMap); null when a struct does not. */
	static Map<?, ?> fields(Struct struct) {
		try {
			Object map = struct.getClass().getMethod("getMap").invoke(struct);
			return map instanceof Map<?, ?> named ? named : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/**
	 * A grant's capabilities as the node means them (spec 012): unstated (NULL) is every data capability - or, for an
	 * object or schema grant, what the catalog grant holds; a struct with nothing true is none.
	 */
	public static String caps(Object value, String unstated) {
		if (value == null) {
			return unstated;
		}
		String held = text(value);
		return held == null || held.isEmpty() ? "none" : held;
	}

	private static String join(Collection<?> items) {
		List<String> parts = new ArrayList<>();
		for (Object item : items) {
			if (item != null) {
				parts.add(text(item));
			}
		}
		return String.join(", ", parts);
	}
}
