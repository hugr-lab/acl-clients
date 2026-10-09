package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hugrlab.acl.jdbc.AclConfig.Mode;
import org.junit.jupiter.api.Test;

class AclSqlTest {
	@Test
	void modesPrefix() {
		assertEquals("SELECT 1", AclSql.prefix(Mode.DATA, "SELECT 1"));
		assertEquals("ACL CREATE VIRTUAL CATALOG c", AclSql.prefix(Mode.MANAGE, "CREATE VIRTUAL CATALOG c"));
		assertEquals("ACL NATIVE SELECT 1", AclSql.prefix(Mode.NATIVE, "  SELECT 1"));
		// the node's prefix scanner reads no comment: leading ones go
		assertEquals("ACL NATIVE SELECT 1", AclSql.prefix(Mode.NATIVE, "-- why\n/* how */ SELECT 1"));
	}

	@Test
	void anExplicitPrefixIsKept() {
		assertEquals("ACL NATIVE SELECT 1", AclSql.prefix(Mode.MANAGE, "ACL NATIVE SELECT 1"));
		assertEquals("acl GRANT x", AclSql.prefix(Mode.NATIVE, "acl GRANT x"));
		assertEquals("ACL NATIVE acl_x()", AclSql.prefix(Mode.NATIVE, "acl_x()"), "a name starting acl is no prefix");
	}

	@Test
	void useIsTheSessionsNotAManagementStatement() {
		assertEquals("USE \"sales\"", AclSql.prefix(Mode.MANAGE, "USE \"sales\""));
		assertEquals("ACL NATIVE USE memory", AclSql.prefix(Mode.NATIVE, "USE memory"));
	}

	@Test
	void recognisesUse() {
		assertTrue(AclSql.isUse("use sales"));
		assertTrue(AclSql.isUse(" -- x\nUSE SCHEMA raw"));
		assertTrue(AclSql.isUse("ACL NATIVE USE memory"));
		assertFalse(AclSql.isUse("SELECT 'USE x'"));
		assertFalse(AclSql.isUse("user_table"));
		assertFalse(AclSql.isUse(null));
	}

	@Test
	void quoting() {
		assertEquals("\"sales\"", AclSql.quoteIdentifier("sales"));
		assertEquals("\"a\"\"b\"", AclSql.quoteIdentifier("a\"b"));
		assertEquals("'it''s'", AclSql.literal("it's"));
		assertEquals("a\\_b\\%c\\\\", AclSql.escapeLike("a_b%c\\"));
	}
}
