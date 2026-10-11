package io.github.hugrlab.acl.dbeaver.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import io.github.hugrlab.acl.dbeaver.model.SchemaPath;
import org.junit.Test;

/** Schema paths as the node lists them (duckdb-acl spec 116). */
public class SchemaPathTest {
	@Test
	public void partsAndParents() {
		assertEquals(List.of("raw", "eu"), SchemaPath.parts("raw.eu"));
		assertEquals("raw", SchemaPath.parent("raw.eu"));
		assertEquals("eu", SchemaPath.leaf("raw.eu"));
		assertNull(SchemaPath.parent("raw"));
	}

	@Test
	public void aQuotedPartIsOnePart() {
		assertEquals(List.of("a.b"), SchemaPath.parts("\"a.b\""));
		assertNull(SchemaPath.parent("\"a.b\""));
		assertEquals("\"a.b\"", SchemaPath.parent("\"a.b\".c"));
		assertEquals("c", SchemaPath.leaf("\"a.b\".c"));
		assertEquals(List.of("say \"hi\"", "x"), SchemaPath.parts("\"say \"\"hi\"\"\".x"));
	}

	@Test
	public void malformedPathsAreNotSplit() {
		assertNull(SchemaPath.parts("a..b"));
		assertNull(SchemaPath.parts("\"open"));
		assertEquals("a..b", SchemaPath.leaf("a..b"));
	}
}
