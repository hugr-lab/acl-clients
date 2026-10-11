package io.github.hugrlab.acl.dbeaver.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A schema's path as the node lists it (duckdb-acl spec 116): parts joined by `.`, a part quoted (`"`, doubled
 * inside) only when it holds a `.` or a `"` - so `raw.eu` is two parts and `"a.b"` is one.
 */
public final class SchemaPath {
	private SchemaPath() {
	}

	/** The parts, unquoted; null when the text is not a well-formed path. */
	public static List<String> parts(String path) {
		List<String> out = new ArrayList<>();
		StringBuilder part = new StringBuilder();
		boolean quoted = false;
		boolean wasQuoted = false;
		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < path.length() && path.charAt(i + 1) == '"') {
					part.append('"');
					i++;
				} else if (c == '"') {
					quoted = false;
				} else {
					part.append(c);
				}
			} else if (c == '"' && part.length() == 0 && !wasQuoted) {
				quoted = true;
				wasQuoted = true;
			} else if (c == '.') {
				if (part.length() == 0) {
					return null;
				}
				out.add(part.toString());
				part.setLength(0);
				wasQuoted = false;
			} else {
				part.append(c);
			}
		}
		if (quoted || part.length() == 0) {
			return null;
		}
		out.add(part.toString());
		return out;
	}

	/** The path without its last part, as the node writes it; null for a one-part path. */
	public static String parent(String path) {
		List<String> parts = parts(path);
		if (parts == null || parts.size() < 2) {
			return null;
		}
		return join(parts.subList(0, parts.size() - 1));
	}

	/** The last part, unquoted: what the tree shows. */
	public static String leaf(String path) {
		List<String> parts = parts(path);
		return parts == null ? path : parts.get(parts.size() - 1);
	}

	static String join(List<String> parts) {
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (out.length() > 0) {
				out.append('.');
			}
			out.append(part.contains(".") || part.contains("\"") ? "\"" + part.replace("\"", "\"\"") + "\"" : part);
		}
		return out.toString();
	}
}
