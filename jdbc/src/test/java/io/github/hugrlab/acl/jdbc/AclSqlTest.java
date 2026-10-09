package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hugrlab.acl.jdbc.AclConfig.Mode;
import java.util.List;
import org.junit.jupiter.api.Test;

class AclSqlTest {
	@Test
	void modesPrefix() {
		assertEquals("SELECT 1", AclSql.prefix(Mode.DATA, "SELECT 1"));
		assertEquals("ACL CREATE VIRTUAL CATALOG c", AclSql.prefix(Mode.MANAGE, "CREATE VIRTUAL CATALOG c"));
		assertEquals("ACL NATIVE   SELECT 1", AclSql.prefix(Mode.NATIVE, "  SELECT 1"));
	}

	// native: the prefix goes in front of the text as written - duckdb's parser reads the comments
	@Test
	void nativeKeepsTheTextAsWritten() {
		assertEquals("ACL NATIVE -- why\n/* how */ SELECT 1", AclSql.prefix(Mode.NATIVE, "-- why\n/* how */ SELECT 1"));
		assertEquals("ACL NATIVE /* c */ UPDATE t SET x = 1", AclSql.prefix(Mode.NATIVE, "/* c */ UPDATE t SET x = 1"));
	}

	// manage: the node's management grammar reads no comment - whole leading comments go
	@Test
	void manageDropsWholeLeadingComments() {
		assertEquals("ACL CREATE VIRTUAL CATALOG c", AclSql.prefix(Mode.MANAGE, "-- why\n/* how */ CREATE VIRTUAL CATALOG c"));
		assertEquals("ACL CREATE ROLE r", AclSql.prefix(Mode.MANAGE, "/* a /* nested */ comment */ CREATE ROLE r"));
	}

	// a `--` comment ends at \r as well as \n (duckdb's lexer)
	@Test
	void lineCommentEndsAtCarriageReturn() {
		assertEquals("ACL UPDATE t SET x=1", AclSql.prefix(Mode.MANAGE, "-- note\rUPDATE t SET x=1"));
		assertEquals("ACL NATIVE -- note\rUPDATE t SET x=1", AclSql.prefix(Mode.NATIVE, "-- note\rUPDATE t SET x=1"));
		assertEquals("-- note\rACL x", AclSql.prefix(Mode.NATIVE, "-- note\rACL x"), "ACL after the comment");
		assertEquals("-- note\rUSE sales", AclSql.prefix(Mode.MANAGE, "-- note\rUSE sales"));
	}

	// block comments nest: what a flat skipper would read as `ACL NATIVE DROP TABLE t` is commented out
	@Test
	void nestedCommentsNeverUnhideText() {
		String hidden = "/* /* */ ACL NATIVE DROP TABLE t; /* */ SELECT 1";
		assertEquals("ACL NATIVE " + hidden, AclSql.prefix(Mode.NATIVE, hidden));
		assertEquals("ACL " + hidden, AclSql.prefix(Mode.MANAGE, hidden), "nothing to strip: prefixed as written");
		assertFalse(AclSql.containsUse("/* /* */ USE x; /* */"));
		assertEquals(List.of(), AclSql.statementStarts(hidden));
		// a statement of only comments is still sent, prefixed
		assertEquals("ACL NATIVE -- only a note", AclSql.prefix(Mode.NATIVE, "-- only a note"));
	}

	@Test
	void anExplicitPrefixIsKept() {
		assertEquals("ACL NATIVE SELECT 1", AclSql.prefix(Mode.MANAGE, "ACL NATIVE SELECT 1"));
		assertEquals("acl GRANT x", AclSql.prefix(Mode.NATIVE, "acl GRANT x"));
		assertEquals("/* c */ ACL NATIVE SELECT 1", AclSql.prefix(Mode.MANAGE, "/* c */ ACL NATIVE SELECT 1"));
		assertEquals("ACL NATIVE acl_x()", AclSql.prefix(Mode.NATIVE, "acl_x()"), "a name starting acl is no prefix");
	}

	// owner, 2026-10-09: the session's catalog is virtual in every mode; a physical USE is written ACL NATIVE USE
	@Test
	void aSingleUseIsSentAsWrittenInEveryMode() {
		for (Mode mode : Mode.values()) {
			assertEquals("USE \"sales\"", AclSql.prefix(mode, "USE \"sales\""));
			assertEquals("use sales.raw;", AclSql.prefix(mode, "use sales.raw;"));
			assertEquals("USE SCHEMA \"raw.eu\"", AclSql.prefix(mode, "USE SCHEMA \"raw.eu\""));
		}
		assertEquals("ACL NATIVE USE memory", AclSql.prefix(Mode.MANAGE, "ACL NATIVE USE memory"));
		// only as the one statement: a batch is prefixed as a whole
		assertEquals("ACL USE x; GRANT CATALOG c TO ROLE r", AclSql.prefix(Mode.MANAGE, "USE x; GRANT CATALOG c TO ROLE r"));
		assertEquals("ACL NATIVE USE x; DROP TABLE t", AclSql.prefix(Mode.NATIVE, "USE x; DROP TABLE t"));
		assertEquals("ACL NATIVE user_table", AclSql.prefix(Mode.NATIVE, "user_table"));
	}

	@Test
	void statements() {
		assertEquals(List.of(0, 10), AclSql.statementStarts("SELECT 1; SELECT ';' -- ;\n"));
		assertEquals(List.of(0), AclSql.statementStarts("SELECT 'a;b', \"c;d\", $$e;f$$, $t$g;$t$ ;;"));
		assertEquals(List.of(0), AclSql.statementStarts("SELECT E'it\\'s;'"));
		assertEquals(2, AclSql.statementStarts("SELECT $1; SELECT 2").size(), "a parameter is no dollar quote");
	}

	@Test
	void recognisesUse() {
		assertTrue(AclSql.containsUse("use sales"));
		assertTrue(AclSql.containsUse(" -- x\nUSE SCHEMA raw"));
		assertTrue(AclSql.containsUse("-- x\rUSE SCHEMA raw"));
		assertTrue(AclSql.containsUse("ACL NATIVE USE memory"));
		assertTrue(AclSql.containsUse("SELECT 1; USE inventory"), "anywhere in a batch");
		assertFalse(AclSql.containsUse("SELECT 'USE x'"));
		assertFalse(AclSql.containsUse("SELECT 1; SELECT ';USE x'"));
		assertFalse(AclSql.containsUse("user_table"));
		assertFalse(AclSql.containsUse(null));
	}

	@Test
	void quoting() {
		assertEquals("\"sales\"", AclSql.quoteIdentifier("sales"));
		assertEquals("\"a\"\"b\"", AclSql.quoteIdentifier("a\"b"));
		assertEquals("'it''s'", AclSql.literal("it's"));
		assertEquals("a\\_b\\%c\\\\", AclSql.escapeLike("a_b%c\\"));
	}

	@Test
	void aTrailingLoneBackslashIsLiteral() {
		assertEquals("ab\\\\", AclSql.likePattern("ab\\"));
		assertEquals("ab\\\\", AclSql.likePattern("ab\\\\"), "a pair is already an escaped backslash");
		assertEquals("a\\_b", AclSql.likePattern("a\\_b"));
		assertEquals("\\\\\\\\", AclSql.likePattern("\\\\\\"));
	}
}
