package io.github.hugrlab.acl.dbeaver.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Spec 007.3 in a running DBeaver, against dev/node.sh: steward1 (the policy bundle) gets the Access control branch -
 * roles with their grants and effective rights, catalogs with their objects and check, sources with their exposures,
 * identity, function categories; analyst1 does not. Dumps the branch to target/access-dump.txt.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class LiveAccessControlTest {
	private static DBNNode child(DBNNode node, String name) throws Exception {
		for (DBNNode each : node.getChildren(new VoidProgressMonitor())) {
			if (each.getNodeDisplayName().equals(name)) {
				return each;
			}
		}
		return null;
	}

	private static DBNNode root(DBPDataSourceContainer container) {
		return DBWorkbench.getPlatform().getNavigatorModel().getNodeByObject(container);
	}

	@Test
	public void anAdministratorSeesTheBranch() throws Exception {
		LiveTreeTest.assumeNode();
		DBNNode access = child(root(LiveConsoleTest.steward()), "Access control");
		assertNotNull("steward1 has the Access control branch", access);
		StringBuilder out = new StringBuilder();
		LiveTreeTest.dump(access, 0, 5, out);
		Files.writeString(Path.of("target", "access-dump.txt"), out.toString(), StandardCharsets.UTF_8);
		String tree = out.toString();
		// a role, its grant and its effective rights in a catalog: what analyst reads of customers (spec 120.2)
		assertTrue(tree, tree.contains("  Roles  [Roles]"));
		assertTrue(tree, tree.contains("    analyst  [Role]"));
		assertTrue(tree, tree.contains("Catalog grants"));
		assertTrue(tree, tree.contains("Effective rights"));
		DBNNode analyst = child(child(access, "Roles"), "analyst");
		DBNNode rights = child(child(child(analyst, "Effective rights"), "sales"), "customers");
		assertNotNull("analyst's effective rights on sales.customers", rights);
		// the masked ssn, as the node decided it (spec 120.2) - the node's own answer, shown as a property
		Object object = ((org.jkiss.dbeaver.model.navigator.DBNDatabaseNode) rights).getObject();
		String masks = ((org.jkiss.dbeaver.ext.acl.model.access.AclEffectiveRight) object).getMasks();
		assertTrue(masks, masks != null && masks.contains("ssn"));
		// a grant's capabilities by name (the node's STRUCT - spec 120.2's caps), the exposure row named so it stands alone
		DBNNode salesGrant = child(child(analyst, "Catalog grants"), "sales");
		String caps = ((org.jkiss.dbeaver.ext.acl.model.access.AclCatalogGrant)
		    ((org.jkiss.dbeaver.model.navigator.DBNDatabaseNode) salesGrant).getObject()).getCaps();
		assertTrue(caps, caps.equals("select"));
		DBNNode exposures = child(child(child(access, "Sources"), "memory"), "Exposures");
		assertNotNull(tree, child(exposures, "customers (sales) - analyst"));
		// a catalog's objects and its check; a source and who reads it
		assertTrue(tree, tree.contains("Virtual catalogs"));
		assertTrue(tree, tree.contains("    sales  [Catalog]"));
		assertTrue(tree, tree.contains("Check"));
		assertTrue(tree, tree.contains("    memory  [Source]"));
		assertTrue(tree, tree.contains("Exposures"));
		assertTrue(tree, tree.contains("Issuers"));
		assertTrue(tree, tree.contains("Function categories"));
		// no node or cluster views for the policy bundle: those folders are not shown
		assertFalse(tree, tree.contains("  Node  ["));
		assertFalse(tree, tree.contains("  Cluster  ["));
	}

	@Test
	public void anOperatorSeesTheNodeAndTheCluster() throws Exception {
		LiveTreeTest.assumeNode();
		DBNNode access = child(root(LiveTreeTest.connect("ops1", "ops1-pass")), "Access control");
		assertNotNull("ops1 (the cluster bundle) has the Access control branch", access);
		StringBuilder out = new StringBuilder();
		LiveTreeTest.dump(access, 0, 3, out);
		String tree = out.toString();
		assertTrue(tree, tree.contains("  Node  ["));
		assertTrue(tree, tree.contains("    Sessions  ["));
		assertTrue("a session (ops1's own at least)",
		           child(child(access, "Node"), "Sessions").getChildren(new VoidProgressMonitor()).length > 0);
		assertNotNull("the load report, named", child(child(access, "Node"), "Load").getChildren(new VoidProgressMonitor())[0]);
		assertTrue(child(child(access, "Node"), "Load").getChildren(new VoidProgressMonitor())[0].getNodeDisplayName()
		    .startsWith("node"));
		assertTrue(tree, tree.contains("  Cluster  ["));
		assertTrue(tree, tree.contains("    Drift  ["));
		assertFalse("the cluster bundle reads no policy view", tree.contains("  Roles  ["));
	}

	@Test
	public void anOrdinaryRoleDoesNot() throws Exception {
		LiveTreeTest.assumeNode();
		assertFalse("analyst1 has no Access control branch", child(root(LiveTreeTest.analyst()), "Access control") != null);
	}
}
