package io.github.hugrlab.acl.jdbc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The duckdb type text of each column of a statement's result: the field metadata
 * {@code ARROW:FLIGHT:SQL:TYPE_NAME} the node sets (duckdb-acl spec 115). Arrow's driver keeps the
 * result's Arrow schema to itself (a private field of a class whose Arrow types are shaded), so it is
 * read by reflection, and by name only - no shaded type is linked here. Anything missing gives
 * nulls, and the caller falls back to Arrow's generic names; a change on an Arrow bump shows in the
 * live e2e, not as a failure here.
 */
final class ResultTypes {
	static final String TYPE_NAME = "ARROW:FLIGHT:SQL:TYPE_NAME";
	private static final Logger LOG = Logger.getLogger(ResultTypes.class.getName());

	private ResultTypes() {
	}

	/** One entry per column (null where unknown), or null when the schema cannot be reached at all. */
	static String[] of(Object arrow, int columnCount) {
		// a result: the flight's schema (set at execute), else the current batch's; a prepared statement:
		// the dataset schema the node answered the prepare with - each tried on its own
		String[] out = attempt(arrow, columnCount, () -> field(arrow, "schema"));
		if (out == null) {
			out = attempt(arrow, columnCount, () -> {
				Object prepared = field(arrow, "preparedStatement");
				return prepared == null ? null : call(prepared, "getDataSetSchema");
			});
		}
		if (out == null) {
			out = attempt(arrow, columnCount, () -> {
				Object root = field(arrow, "vectorSchemaRoot");
				return root == null ? null : call(root, "getSchema");
			});
		}
		return out;
	}

	private interface SchemaSource {
		Object get() throws ReflectiveOperationException;
	}

	private static String[] attempt(Object arrow, int columnCount, SchemaSource source) {
		try {
			return fromSchema(source.get(), columnCount);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOG.log(Level.FINE, "no Arrow schema there on " + arrow.getClass().getName(), e);
			return null;
		}
	}

	private static String[] fromSchema(Object schema, int columnCount) throws ReflectiveOperationException {
		if (schema == null) {
			return null;
		}
		Object fields = call(schema, "getFields");
		if (!(fields instanceof List<?> list) || list.size() != columnCount) {
			return null;
		}
		String[] out = new String[columnCount];
		for (int i = 0; i < columnCount; i++) {
			Object metadata = call(list.get(i), "getMetadata");
			if (metadata instanceof Map<?, ?> m && m.get(TYPE_NAME) != null) {
				out[i] = m.get(TYPE_NAME).toString();
			}
		}
		return out;
	}

	private static Object field(Object target, String name) throws IllegalAccessException {
		for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(target);
			} catch (NoSuchFieldException e) {
				// up the hierarchy
			}
		}
		return null;
	}

	private static Object call(Object target, String method) throws ReflectiveOperationException {
		Method m = target.getClass().getMethod(method);
		m.setAccessible(true);
		return m.invoke(target);
	}
}
