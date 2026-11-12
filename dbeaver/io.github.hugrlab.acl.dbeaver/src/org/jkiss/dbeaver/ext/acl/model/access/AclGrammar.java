package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.jkiss.dbeaver.ext.acl.model.SchemaPath;

/**
 * The management grammar an editor writes (spec 008): one composer, so the preview is what runs. Names come from the
 * canonical keys the `platform` rows carry (duckdb-acl spec 116: parts joined by `.`, a part quoted only when it holds
 * `.` or `"`) and are written as identifiers - a part quoted as duckdb's writer quotes one (not a plain word, or a
 * keyword); text is a single-quoted literal, quotes doubled.
 */
public final class AclGrammar {
	private AclGrammar() {
	}

	/** Every capability a grant may name (duckdb-acl spec 012 / 016 / 050 / 052 / 082 / 129 / 009), in the node's order. */
	public static final List<String> CAPABILITIES = List.of("select", "insert", "update", "delete", "merge", "create",
	                                                        "drop", "temp", "explain", "secrets", "lineage", "manage");

	/** A word duckdb's own writer leaves bare (`KeywordHelper::RequiresQuotes`, capitals allowed). */
	private static final Pattern PLAIN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	/** A column's or a function's word as it has always been written (lower-case). */
	private static final Pattern PLAIN_FIELD = Pattern.compile("[a-z_][a-z0-9_]*");

	/** A word the node reads as a word, never a name (a scope, a setting): ASCII letters, digits, `_`. */
	private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	/** duckdb's reserved words (`duckdb_keywords()`, category reserved): never written bare in a column list. */
	private static final Set<String> RESERVED = Set.of("all", "analyse", "analyze", "and", "any", "array", "as", "asc",
	    "asymmetric", "both", "case", "cast", "check", "collate", "column", "constraint", "create", "default",
	    "deferrable", "desc", "describe", "distinct", "do", "else", "end", "except", "false", "fetch", "for", "foreign",
	    "from", "group", "having", "in", "initially", "intersect", "into", "lambda", "lateral", "leading", "limit", "not",
	    "null", "offset", "on", "only", "or", "order", "pivot", "pivot_longer", "pivot_wider", "placing", "primary",
	    "qualify", "references", "returning", "select", "show", "some", "summarize", "symmetric", "table", "then", "to",
	    "trailing", "true", "union", "unique", "unpivot", "using", "variadic", "when", "where", "window", "with");

	/**
	 * Every duckdb keyword (`SELECT DISTINCT keyword_name FROM duckdb_keywords()` at duckdb v2.0.0, all categories): never written
	 * bare - duckdb-acl spec 150 4a reads a name as duckdb reads an identifier, which refuses a reserved word and a word
	 * only a function or a type may be (`full`, `left`), and the node quotes every keyword where it writes a name back
	 * (a repair statement), so a name this composer writes is the node's spelling too.
	 */
	private static final String KEYWORD_LIST =
	    "abort absolute access action add admin after aggregate all also alter always analyse analyze and anti " +
	    "any approx array as asc asof assertion assignment asymmetric at attach attribute authorization backward " +
	    "before begin between bigint binary bit boolean both by cache call called cascade cascaded case cast " +
	    "catalog centuries century chain char character characteristics check checkpoint class close cluster " +
	    "coalesce collate collation column columns comment comments commit committed compression concurrently " +
	    "configuration conflict connect connection constraint constraints content continue conversion copy cost " +
	    "create cross csv cube current cursor cycle data database day days deallocate dec decade decades decimal " +
	    "declare default defaults deferrable deferred definer delete delimiter delimiters depends desc describe " +
	    "destroy detach dictionary disable discard disconnect distance distinct do document domain double drop " +
	    "each else enable encoding encrypted end enum error escape event exact except exclude excluding exclusive " +
	    "execute exists explain export export_state extension extensions external extract false family fetch " +
	    "filter first float following for force foreign forward freeze from full function functions generated " +
	    "glob global grant granted group grouping grouping_id groups handler having header hold hour hours " +
	    "identity if ignore ilike immediate immutable implicit import in include including increment index " +
	    "indexes inherit inherits initially inline inner inout input insensitive insert install instead int " +
	    "integer intersect interval into invoker is isnull isolation join json key label lambda language large " +
	    "last lateral leading leakproof left level like limit listen load local location lock locked logged macro " +
	    "map mapping match matched materialized maxvalue merge method microsecond microseconds millennia " +
	    "millennium millisecond milliseconds minute minutes minvalue mode month months move name names national " +
	    "natural nchar nearest new next no none not nothing notify notnull nowait null nullif nulls numeric " +
	    "object of off offset oids old on only operator option options or order ordinality others out outer over " +
	    "overlaps overlay overriding owned owner parallel parser partial partition partitioned passing password " +
	    "percent persistent pivot pivot_longer pivot_wider placing plans policy position positional pragma " +
	    "preceding precision prepare prepared preserve primary prior privileges procedural procedure program " +
	    "publication qualify quarter quarters quote range read real reassign recheck recursive ref references " +
	    "referencing refresh register reindex relative release rename repeatable replace replica reset resource " +
	    "resources respect restart restrict returning returns revoke right role rollback rollup row rows rule " +
	    "sample savepoint schema schemas scope scroll search second seconds secret secure security select semi " +
	    "sequence sequences serializable server session set setof sets share show similar similarity simple skip " +
	    "smallint snapshot some sorted source sql stable standalone start statement statistics stdin stdout " +
	    "storage stored strict strip struct subscription substring summarize symmetric sysid system table tables " +
	    "tablesample tablespace target temp template temporary text then ties time timestamp to trailing " +
	    "transaction transform treat trigger trim true truncate trusted try_cast tuple type types unbounded " +
	    "uncommitted unencrypted union unique unknown unlisten unlogged unpack unpivot until update use user " +
	    "using vacuum valid validate validator value values varchar variable variadic varying verbose version " +
	    "view views virtual volatile week weeks when where whitespace window with within without work wrapper " +
	    "write xml xmlattributes xmlconcat xmlelement xmlexists xmlforest xmlnamespaces xmlparse xmlpi xmlroot " +
	    "xmlserialize xmltable year years yes zone";
	private static final Set<String> KEYWORDS = Set.of(KEYWORD_LIST.split(" "));

	/**
	 * The keywords duckdb takes as no identifier - a reserved word or one only a function or a type may be (no
	 * `unreserved` / `column_name` row in `duckdb_keywords()` at duckdb v2.0.0): what the node refuses bare first in a
	 * qualified name.
	 */
	private static final String NOT_NAME_LIST =
	    "all analyse analyze and anti any array as asc asof asymmetric at authorization binary both by case cast " +
	    "check collate collation column concurrently constraint create cross default deferrable desc describe " +
	    "distinct do else end except false fetch for foreign freeze from full glob group having ilike in " +
	    "initially inner intersect into is isnull join lambda lateral leading left like limit natural not notnull " +
	    "null offset on only or order outer overlaps pivot pivot_longer pivot_wider placing positional primary " +
	    "qualify references returning right select semi show similar some summarize symmetric table tablesample " +
	    "then to trailing true union unique unpack unpivot using variadic verbose when where window with";
	private static final Set<String> NOT_NAMES = Set.of(NOT_NAME_LIST.split(" "));

	/** One name part as an identifier, as duckdb writes one: bare when plain and no keyword, else in `"`, doubled inside. */
	public static String ident(String part) {
		if (PLAIN.matcher(part).matches() && !KEYWORDS.contains(part.toLowerCase(Locale.ROOT))) {
			return part;
		}
		return "\"" + part.replace("\"", "\"\"") + "\"";
	}

	/**
	 * A column's name in a column list (a grant's, a relation's - duckdb-acl spec 102's path reader and duckdb's parser
	 * read it, not the management grammar), as it has always been written:
	 * lower-case words bare, anything else quoted. Here a spelling changes meaning - a quoted physical column in a
	 * relation's list is a computed reading (the relation read-only), a bare one a rename (writable) - so a composer
	 * that re-spells a stored list must spell it as it was.
	 */
	public static String fieldIdent(String part) {
		// duckdb-acl spec 150 4e / 4f: a list's name is duckdb's column name - a reserved word and one only a function
		// or a type may be (`left`, `full`, `at`) are quoted
		if (PLAIN_FIELD.matcher(part).matches() && !NOT_NAMES.contains(part)) {
			return part;
		}
		return "\"" + part.replace("\"", "\"\"") + "\"";
	}

	/** A physical function's key (`system.main.upper`, `range`) as {@link #functionParts}. */
	public static String functionPath(String key) {
		List<String> parts = SchemaPath.parts(key);
		return parts == null ? fieldIdent(key) : functionParts(parts);
	}

	/**
	 * A physical function's name, its parts quoted only where the node does not read them bare (its spelling changes
	 * nothing but how it shows): lower-case words bare - a lone name may be a keyword a function may be (`left`), the
	 * first part of a qualified one no keyword duckdb takes as no identifier (`"left".main.f`); anything else quoted.
	 */
	public static String functionParts(List<String> parts) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < parts.size(); i++) {
			String part = parts.get(i);
			boolean bare = PLAIN_FIELD.matcher(part).matches()
			               && !(i == 0 && (parts.size() == 1 ? RESERVED : NOT_NAMES).contains(part));
			out.add(bare ? part : "\"" + part.replace("\"", "\"\"") + "\"");
		}
		return String.join(".", out);
	}

	/**
	 * A word the node reads as a word - an admin scope, a setting's name (duckdb-acl spec 150: the scanner's words, never
	 * a quoted name): as it is, when it is one; quoted otherwise, which the node refuses where it reads a word.
	 */
	public static String word(String text) {
		return text != null && WORD.matcher(text).matches() ? text : ident(text == null ? "" : text);
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
