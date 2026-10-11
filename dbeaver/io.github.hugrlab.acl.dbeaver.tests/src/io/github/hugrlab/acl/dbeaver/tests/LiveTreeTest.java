package io.github.hugrlab.acl.dbeaver.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.jkiss.dbeaver.ext.generic.model.GenericCatalog;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;
import org.jkiss.dbeaver.ext.generic.model.GenericSchema;
import org.jkiss.dbeaver.ext.generic.model.GenericTableBase;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.app.DBPDataSourceRegistry;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.model.navigator.DBNDataSource;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.registry.DataSourceProviderRegistry;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The node's tree in a running DBeaver (spec 007.1), against dev/node.sh (ACL_METADATA=1): gated by ACL_E2E_URL like
 * the driver's live tests. Dumps the navigator tree to target/tree-dump.txt for review.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class LiveTreeTest {
	static final String URL = System.getenv("ACL_E2E_URL");
	private static DBPDataSourceContainer analyst;

	/** A dev node named by ACL_E2E_URL (an unset variable may reach the test as text, never as a jdbc:acl: URL). */
	static void assumeNode() {
		assumeTrue("ACL_E2E_URL names a dev node", URL != null && URL.startsWith("jdbc:acl:"));
	}

	/** One connection for the class: analyst1, the ordinary role of dev/node.sh. */
	static synchronized DBPDataSourceContainer analyst() throws Exception {
		if (analyst == null) {
			Workbench.waitUp();
			analyst = connect("analyst1", "analyst1-pass");
		}
		return analyst;
	}

	static DBPDataSourceContainer connect(String user, String password) throws Exception {
		DBPProject project = DBWorkbench.getPlatform().getWorkspace().getActiveProject();
		DBPDataSourceRegistry registry = project.getDataSourceRegistry();
		DBPDriver driver = DataSourceProviderRegistry.getInstance().getDataSourceProvider("duckdb_acl")
		    .getDriver("duckdb_acl_jdbc");
		DBPConnectionConfiguration configuration = new DBPConnectionConfiguration();
		configuration.setUrl(URL);
		configuration.setUserName(user);
		configuration.setUserPassword(password);
		DataSourceDescriptor container = new DataSourceDescriptor(registry, DataSourceDescriptor.generateNewId(driver),
		    driver, configuration);
		container.setName("node as " + user);
		container.setSavePassword(true); // else DBeaver prompts for the password it was given
		registry.addDataSource(container);
		container.connect(new VoidProgressMonitor(), true, true);
		return container;
	}

	static void dump(DBNNode node, int depth, int maxDepth, StringBuilder out) throws Exception {
		out.append("  ".repeat(depth)).append(node.getNodeDisplayName()).append("  [").append(node.getNodeType())
		    .append("]\n");
		if (depth >= maxDepth || !node.hasChildren(false)) {
			return;
		}
		DBNNode[] children = node.getChildren(new VoidProgressMonitor());
		if (children != null) {
			for (DBNNode child : children) {
				dump(child, depth + 1, maxDepth, out);
			}
		}
	}

	@Test
	public void aQueryReadsTheRolesRows() throws Exception {
		assumeNode();
		DBPDataSourceContainer container = analyst();
		DBCExecutionContext context = DBUtils.getDefaultContext(container.getDataSource(), false);
		try (JDBCSession session = (JDBCSession) context.openSession(new VoidProgressMonitor(), DBCExecutionPurpose.USER,
		    "test");
		     JDBCPreparedStatement statement = session.prepareStatement("SELECT DISTINCT tenant FROM sales.main.orders");
		     JDBCResultSet rows = statement.executeQuery()) {
			// the node's RLS (dev/node.sh): analyst1 is acme's
			assertTrue(rows.next());
			assertEquals("acme", rows.getString(1));
			assertFalse(rows.next());
		}
	}

	@Test
	public void aNestedSchemasObjectsAreNamedAndReadByDBeaver() throws Exception {
		assumeNode();
		DBPDataSourceContainer container = analyst();
		VoidProgressMonitor monitor = new VoidProgressMonitor();
		GenericDataSource source = (GenericDataSource) container.getDataSource();
		GenericCatalog sales = source.getCatalog("sales");
		// found by its path at any depth (a foreign key, a search, the editor look it up so)
		GenericSchema eu = sales.getSchema(monitor, "raw.eu");
		assertNotNull("sales.raw.eu by its path", eu);
		assertEquals("raw", ((GenericSchema) eu.getParentObject()).getName());
		GenericTableBase events = eu.getTable(monitor, "events");
		assertNotNull(events);
		// the name DBeaver writes into the SQL it generates (Data tab, Generate SQL): each part on its own
		String name = events.getFullyQualifiedName(DBPEvaluationContext.DML);
		assertEquals("sales.raw.eu.events", name);
		DBCExecutionContext context = DBUtils.getDefaultContext(container.getDataSource(), false);
		try (JDBCSession session = (JDBCSession) context.openSession(monitor, DBCExecutionPurpose.USER, "test");
		     JDBCPreparedStatement statement = session.prepareStatement("SELECT count(*) FROM " + name);
		     JDBCResultSet rows = statement.executeQuery()) {
			assertTrue(rows.next());
		}
	}

	@Test
	public void theTreeOfAnOrdinaryRole() throws Exception {
		assumeNode();
		DBPDataSourceContainer container = analyst();
		DBNDataSource node = DBWorkbench.getPlatform().getNavigatorModel().getNodeByObject(container) instanceof DBNDataSource
		    data ? data : null;
		assertNotNull("the connection's navigator node", node);
		StringBuilder out = new StringBuilder();
		dump(node, 0, 8, out);
		Path file = Path.of("target", "tree-dump.txt");
		Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
		String tree = out.toString();
		// the nested schema sales.raw.eu under raw, by its last part (its own tables below it); the types are not
		// repeated per schema
		List<String> lines = tree.lines().toList();
		int raw = lines.indexOf("    raw  [Schema]");
		assertTrue(tree, raw > 0);
		int eu = -1;
		for (int i = raw + 1; i < lines.size() && lines.get(i).startsWith("      "); i++) {
			if (lines.get(i).equals("      eu  [Schema]")) {
				eu = i;
			}
		}
		assertTrue("eu is a child of raw\n" + tree, eu > raw);
		assertTrue(tree, lines.contains("          events  [Table]"));
		assertTrue(tree, lines.contains("              kind  [Column]"));
		assertFalse(tree, tree.contains("raw.eu"));
		assertFalse(tree, tree.contains("[Data Types]"));
		// the role's objects, from the driver's listings (spec 006)
		assertTrue(tree, tree.contains("orders_over  [Function]"));
		assertTrue(tree, tree.contains("customers_pk  [Key]"));
		assertTrue(tree, tree.contains("big_orders  [View]"));
	}
}
