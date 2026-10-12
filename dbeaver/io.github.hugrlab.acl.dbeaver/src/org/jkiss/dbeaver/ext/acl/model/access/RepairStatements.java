package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A finding's repair as the node writes it (duckdb-acl spec 039): a statement, often a template with places to fill
 * (`<column>`, `<path>`), and alternatives after `-- or` - a whole statement (`DROP VIRTUAL TABLE c.b`) or the tail of
 * the first (`DROP MISSING COLUMNS` - of `REPAIR VIRTUAL TABLE c.a`). Split into the statements one may run (spec
 * 008.2); a statement with a place left to fill is not one to run yet.
 */
public final class RepairStatements {
	private static final Pattern PLACE = Pattern.compile("<[a-z_ ]+>");
	private static final List<String> VERBS = List.of("CREATE", "ALTER", "DROP", "REPAIR", "GRANT", "REVOKE", "COMMENT",
	                                                  "REFRESH", "ANALYZE", "CHECK");

	private RepairStatements() {
	}

	/** The alternatives, each a whole statement; `target` = the object as the statements name it (`c.a`). */
	public static List<String> alternatives(String repair, String target) {
		List<String> out = new ArrayList<>();
		if (repair == null || repair.isBlank()) {
			return out;
		}
		String[] parts = repair.split("\\s*--\\s*or\\s+");
		String first = parts[0].trim();
		out.add(first);
		int at = target == null ? -1 : first.indexOf(target);
		String prefix = at < 0 ? null : first.substring(0, at + target.length());
		for (int i = 1; i < parts.length; i++) {
			String alternative = parts[i].trim();
			String word = alternative.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
			boolean whole = VERBS.contains(word) && !(prefix != null && "DROP".equals(word)
			                                           && !alternative.toUpperCase(Locale.ROOT).startsWith("DROP VIRTUAL")
			                                           && !alternative.toUpperCase(Locale.ROOT).startsWith("DROP ROLE"));
			out.add(whole || prefix == null ? alternative : prefix + " " + alternative);
		}
		return out;
	}

	/** Whether a statement still has a place to fill. */
	public static boolean hasPlaces(String statement) {
		return PLACE.matcher(statement).find();
	}
}
