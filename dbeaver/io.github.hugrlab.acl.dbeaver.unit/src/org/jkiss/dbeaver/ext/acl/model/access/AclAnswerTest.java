package org.jkiss.dbeaver.ext.acl.model.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * Spec 010: a management statement's answer {@code (ok, message)} (duckdb-acl spec 179) as the plugin reads and shows
 * it - a false and a message are said, a plain success is silent, a result that is no answer is none.
 */
public class AclAnswerTest {
	private static final List<String> PAIR = List.of("ok", "message");

	@Test
	public void read() {
		assertEquals(new AclAnswer(true, ""), AclAnswer.of(PAIR, true, ""));
		assertEquals(new AclAnswer(true, "2 sessions still open"), AclAnswer.of(PAIR, true, "2 sessions still open"));
		assertEquals(new AclAnswer(false, "nothing changed"), AclAnswer.of(PAIR, false, "nothing changed"));
		// a NULL or non-boolean ok is not read as success; a NULL message is empty
		assertEquals(new AclAnswer(false, ""), AclAnswer.of(PAIR, null, null));
		assertEquals(new AclAnswer(false, "x"), AclAnswer.of(PAIR, "true", "x"));
		// a STRUCT answer keeps its own fields after the pair (hugr-node's CLUSTER forms)
		assertEquals(new AclAnswer(true, "applied"),
		             AclAnswer.of(List.of("ok", "message", "version", "class", "applied_here"), true, "applied"));
		// not a management answer: a CHECK's rows, a query
		assertNull(AclAnswer.of(List.of("item", "name", "status", "message"), null, "x"));
		assertNull(AclAnswer.of(List.of("ok"), true, null));
	}

	@Test
	public void said() {
		assertFalse(new AclAnswer(true, "").says());
		assertTrue(new AclAnswer(true, "3 entries changed").says());
		assertTrue(new AclAnswer(false, "the node was not draining").says());
	}

	@Test
	public void report() {
		String hint = "group g's source set leaves out pg, which role analyst now reads; to add it: "
		              + "ALTER RESOURCE GROUP g SET SOURCES (pg)";
		List<String> statements = List.of("CREATE ROLE analyst", "GRANT CATALOG sales TO ROLE analyst", "RESUME NODE");
		List<AclAnswer> answers = List.of(new AclAnswer(true, ""), new AclAnswer(true, hint),
		                                  new AclAnswer(false, "the node was not draining"));
		assertEquals("GRANT CATALOG sales TO ROLE analyst\n  " + hint + "\n\nRESUME NODE\n  not done: the node was not draining",
		             AclAnswer.report(statements, answers));
		assertFalse(AclAnswer.allOk(answers));
		// nothing said: nothing to show
		assertEquals("", AclAnswer.report(List.of("CREATE ROLE r"), List.of(new AclAnswer(true, ""))));
		// a statement that answered no pair (not a management form) is neither said nor a failure
		assertEquals("", AclAnswer.report(List.of("SELECT 1"), Arrays.asList((AclAnswer) null)));
		assertTrue(AclAnswer.allOk(Arrays.asList(null, new AclAnswer(true, "x"))));
		assertEquals("DROP ROLE r\n  not done: the node answered nothing more",
		             AclAnswer.report(List.of("DROP ROLE r"), List.of(new AclAnswer(false, ""))));
	}
}
