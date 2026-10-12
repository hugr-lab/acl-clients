package io.github.hugrlab.acl.dbeaver.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.ext.acl.model.access.AclClusterItem;
import org.jkiss.dbeaver.ext.acl.model.access.AclDriftEntry;
import org.jkiss.dbeaver.ext.acl.model.access.AclNodeLoad;
import org.jkiss.dbeaver.ext.acl.model.access.AclSession;
import org.jkiss.dbeaver.ext.acl.model.access.PlatformSql;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectMaker;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.edit.AbstractCommandContext;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.runtime.properties.PropertySourceEditable;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Spec 008.3 against dev/node.sh as ops1 (the cluster bundle - operate inside it): a session's levels and its kill,
 * drain and resume - the statements the operations offer, run as the dialog runs them - and, on a cluster node
 * (ACL_CLUSTER=1), the profile's items through the editor's command path and a drift entry into the profile.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class LiveOperateTest {
	private static final VoidProgressMonitor MONITOR = new VoidProgressMonitor();
	private static DBPDataSourceContainer ops;

	static synchronized AclDataSource ops() throws Exception {
		if (ops == null) {
			Workbench.waitUp();
			ops = LiveTreeTest.connect("ops1", "ops1-pass");
		}
		return (AclDataSource) ops.getDataSource();
	}

	private static void run(AclDataSource acl, String sql) throws Exception {
		try (JDBCSession session = DBUtils.openUtilSession(MONITOR, acl, "t008");
		     JDBCStatement statement = session.createStatement()) {
			statement.execute(sql);
		}
	}

	@Test
	public void aSessionsLevelsAndItsKill() throws Exception {
		LiveTreeTest.assumeNode();
		AclDataSource acl = ops();
		java.util.Set<Object> before = new java.util.HashSet<>();
		for (Map<String, Object> row : acl.platformRows(MONITOR, PlatformSql.view("sessions"))) {
			before.add(row.get("id"));
		}
		DBPDataSourceContainer victim = LiveTreeTest.connect("analyst2", "analyst2-pass");
		try {
			acl.refreshObject(MONITOR);
			AclSession session = null;
			for (AclSession each : acl.getAccessSessions(MONITOR)) {
				if (!before.contains(each.getName()) && String.valueOf(each.getRoles()).contains("analyst")) {
					session = each; // the session the connection just opened (its subject is the token's sub)
				}
			}
			assertNotNull("analyst2's session is listed", session);
			run(acl, session.operation("audit").get(0)); // AUDIT LEVEL ALL
			run(acl, session.operation("profile").get(0)); // PROFILE … ON
			Map<String, Object> row = acl.platformRows(MONITOR, PlatformSql.view("sessions", "id", session.getName())).get(0);
			assertEquals("all", String.valueOf(row.get("audit_level")).toLowerCase());
			run(acl, session.operation("kill").get(0));
			assertTrue("killed", acl.platformRows(MONITOR, PlatformSql.view("sessions", "id", session.getName())).isEmpty());
		} finally {
			victim.disconnect(MONITOR);
			victim.getRegistry().removeDataSource(victim);
		}
	}

	@Test
	public void drainAndResume() throws Exception {
		LiveTreeTest.assumeNode();
		AclDataSource acl = ops();
		AclNodeLoad node = acl.getAccessNodeLoad(MONITOR).get(0);
		try {
			run(acl, node.operation("drain").get(0));
			assertEquals(Boolean.TRUE, acl.platformRows(MONITOR, PlatformSql.view("node_load")).get(0).get("draining"));
		} finally {
			run(acl, node.operation("resume").get(0));
		}
		assertEquals(Boolean.FALSE, acl.platformRows(MONITOR, PlatformSql.view("node_load")).get(0).get("draining"));
	}

	private static boolean clusterNode(AclDataSource acl) {
		try {
			acl.platformRows(MONITOR, PlatformSql.view("drift"));
			return true;
		} catch (Exception standalone) {
			return false;
		}
	}

	@Test
	public void theClusterProfile() throws Exception {
		LiveTreeTest.assumeNode();
		AclDataSource acl = ops();
		assumeTrue("a cluster node (ACL_CLUSTER=1 dev/node.sh)", clusterNode(acl));
		DBECommandContext context = new AbstractCommandContext(DBUtils.getDefaultContext(acl, true), false) {
		};
		@SuppressWarnings("unchecked")
		DBEObjectMaker<AclClusterItem, DBSObject> maker = DBWorkbench.getPlatform().getEditorsRegistry()
		    .getObjectManager(AclClusterItem.class, DBEObjectMaker.class);
		assertTrue("the cluster bundle is offered the profile", maker.canCreateObject(acl));
		AclClusterItem item = maker.createNewObject(MONITOR, context, acl, null, new HashMap<>());
		PropertySourceEditable source = new PropertySourceEditable(context, item, item);
		source.collectProperties();
		source.setPropertyValue(MONITOR, "name", "threads");
		source.setPropertyValue(MONITOR, "value", "3");
		context.saveChanges(MONITOR, new HashMap<>());
		Map<String, Object> stored = acl.platformRows(MONITOR, PlatformSql.view("cluster_items", "name", "threads")).get(0);
		assertTrue(String.valueOf(stored.get("spec")), String.valueOf(stored.get("spec")).contains("3"));
		// the drift says threads differs from this node's: into the profile is the SET of the node's value
		AclDriftEntry threads = null;
		for (AclDriftEntry entry : acl.getAccessDrift(MONITOR)) {
			if ("threads".equals(entry.getName())) {
				threads = entry;
			}
		}
		assertNotNull(threads);
		List<String> into = threads.operation("into_profile");
		assertFalse(into.isEmpty());
		assertTrue(into.get(0), into.get(0).startsWith("CLUSTER SET threads = "));
		item.setPersisted(true);
		DBECommandContext drop = new AbstractCommandContext(DBUtils.getDefaultContext(acl, true), false) {
		};
		maker.deleteObject(drop, item, new HashMap<>());
		drop.saveChanges(MONITOR, new HashMap<>());
		assertTrue(acl.platformRows(MONITOR, PlatformSql.view("cluster_items", "name", "threads")).isEmpty());
	}
}
