package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.jkiss.dbeaver.ext.acl.model.SchemaPath;

/**
 * The management grammar an editor writes (spec 008): one composer, so the preview is what runs. Names come from the
 * canonical keys the `platform` rows carry (duckdb-acl spec 116: parts joined by `.`, a part quoted only when it holds
 * `.` or `"`) and are written as identifiers - a part quoted when it is not a plain lower-case name or is a reserved
 * word; text is a single-quoted literal, quotes doubled.
 */
public final class AclGrammar {
	private AclGrammar() {
	}

	/** Every capability a grant may name (duckdb-acl spec 012 / 016 / 050 / 052 / 082 / 129 / 009), in the node's order. */
	public static final List<String> CAPABILITIES = List.of("select", "insert", "update", "delete", "merge", "create",
	                                                        "drop", "temp", "explain", "secrets", "lineage", "manage");

	private static final Pattern PLAIN = Pattern.compile("[a-z_][a-z0-9_]*");

	/** duckdb's reserved words (`duckdb_keywords()`, category reserved): never written bare. */
	private static final Set<String> RESERVED = Set.of("all", "analyse", "analyze", "and", "any", "array", "as", "asc",
	    "asymmetric", "both", "case", "cast", "check", "collate", "column", "constraint", "create", "default",
	    "deferrable", "desc", "describe", "distinct", "do", "else", "end", "except", "false", "fetch", "for", "foreign",
	    "from", "group", "having", "in", "initially", "intersect", "into", "lambda", "lateral", "leading", "limit", "not",
	    "null", "offset", "on", "only", "or", "order", "pivot", "pivot_longer", "pivot_wider", "placing", "primary",
	    "qualify", "references", "returning", "select", "show", "some", "summarize", "symmetric", "table", "then", "to",
	    "trailing", "true", "union", "unique", "unpivot", "using", "variadic", "when", "where", "window", "with");

	/** One name part as an identifier: bare when plain and not reserved, else in `"`, doubled inside. */
	public static String ident(String part) {
		if (PLAIN.matcher(part).matches() && !RESERVED.contains(part)) {
			return part;
		}
		return "\"" + part.replace("\"", "\"\"") + "\"";
	}

	/** A canonical key (`raw.eu.events`, `"a.b"`) as a dotted path of identifiers; the key as one part if malformed. */
	public static String path(String key) {
		List<String> parts = SchemaPath.parts(key);
		if (parts == null) {
			return ident(key);
		}
		List<String> out = new ArrayList<>();
		for (String part : parts) {
			out.add(ident(part));
		}
		return String.join(".", out);
	}

	/** `<catalog>.<object path>`: a catalog key and a key inside it. */
	public static String inCatalog(String catalog, String key) {
		return path(catalog) + "." + path(key);
	}

	/** A single-quoted literal, quotes doubled. */
	public static String literal(String text) {
		return "'" + (text == null ? "" : text).replace("'", "''") + "'";
	}

	public static boolean isBlank(String text) {
		return text == null || text.trim().isEmpty();
	}

	/**
	 * A grant's capabilities as a GRANT writes them: null (unstated - every data capability, or what the catalog grant
	 * holds for a schema or object grant) is no clause; none held is `CAPS '{}'`; else `WITH (select, insert)`.
	 */
	public static String grantCaps(Collection<String> caps) {
		if (caps == null) {
			return "";
		}
		return caps.isEmpty() ? " CAPS '{}'" : " WITH (" + String.join(", ", ordered(caps)) + ")";
	}

	/** The same as `ALTER GRANT … SET CAPS` takes it: `''` for unstated, else the JSON of the held ones. */
	public static String capsJson(Collection<String> caps) {
		if (caps == null) {
			return "''";
		}
		List<String> items = new ArrayList<>();
		for (String cap : ordered(caps)) {
			items.add("\"" + cap + "\": true");
		}
		return literal("{" + String.join(", ", items) + "}");
	}

	private static List<String> ordered(Collection<String> caps) {
		List<String> out = new ArrayList<>();
		for (String cap : CAPABILITIES) {
			if (caps.contains(cap)) {
				out.add(cap);
			}
		}
		for (String cap : caps) {
			if (!out.contains(cap)) {
				out.add(cap); // a name the node does not know is written as it was stored
			}
		}
		return out;
	}

	/** A comma list as written by a user, trimmed; empty when there is nothing in it. */
	public static List<String> items(String list) {
		List<String> out = new ArrayList<>();
		if (list != null) {
			for (String item : list.split(",")) {
				if (!item.trim().isEmpty()) {
					out.add(item.trim());
				}
			}
		}
		return out;
	}
}
