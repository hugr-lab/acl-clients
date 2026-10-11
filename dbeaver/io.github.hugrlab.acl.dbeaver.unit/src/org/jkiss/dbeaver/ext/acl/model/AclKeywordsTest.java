package org.jkiss.dbeaver.ext.acl.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The grammar's words: highlighted, never new reserved words that would retype DBeaver's own. */
public class AclKeywordsTest {
	@Test
	public void ourWordsOnly() {
		assertTrue(AclKeywords.contains("acl"));
		assertTrue(AclKeywords.contains("PLATFORM"));
		// DBeaver's own keywords and types are not re-added (addSQLKeyword would retype them)
		for (String word : new String[] {"AS", "VIEW", "MAP", "GRANT", "REVOKE", "CATALOG", "ROLE", "GROUP", "CHECK"}) {
			assertFalse(word, AclKeywords.contains(word));
		}
	}
}
