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
				return text(struct.getAttributes());
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
