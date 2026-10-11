package io.github.hugrlab.acl.dbeaver.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.PlatformUI;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.ext.acl.model.ConsoleCopy;
import org.jkiss.dbeaver.ext.acl.model.ConsoleInfo;
import org.jkiss.dbeaver.ext.acl.model.ConsoleKind;
import org.jkiss.dbeaver.ext.acl.ui.AclConsoles;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Spec 007.2 in a running DBeaver, against dev/node.sh: steward1 (the policy bundle) is offered a management console
 * and view-as, never a native console; analyst1 nothing; a view-as connection's tree is the role's own tree.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class LiveConsoleTest {
	private static final String NAVIGATOR = "org.jkiss.dbeaver.core.databaseNavigator";
	private static DBPDataSourceContainer steward;

	static synchronized DBPDataSourceContainer steward() throws Exception {
		if (steward == null) {
			Workbench.waitUp();
			steward = LiveTreeTest.connect("steward1", "steward1-pass");
		}
		return steward;
	}

	private static ConsoleInfo console(DBPDataSourceContainer container) {
		return ((AclDataSource) container.getDataSource()).console();
	}

	@Test
	public void whatEachPrincipalIsOffered() throws Exception {
		LiveTreeTest.assumeNode();
		assertNull("an ordinary role is refused console_info", console(LiveTreeTest.analyst()));
		ConsoleInfo info = console(steward());
		assertNotNull(info);
		assertTrue(info.offers(ConsoleKind.MANAGE));
		assertTrue(info.offers(ConsoleKind.VIEW_AS));
		assertFalse("native is passthrough's", info.offers(ConsoleKind.NATIVE));
	}

	@Test
	public void theMenuOffersWhatConsoleInfoSays() throws Exception {
		LiveTreeTest.assumeNode();
		String stewardName = steward().getName();
		String analystName = LiveTreeTest.analyst().getName();
		SWTWorkbenchBot bot = new SWTWorkbenchBot();
		UIThreadRunnable.syncExec(() -> {
			try {
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(NAVIGATOR);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
		SWTBotTreeItem item = bot.viewById(NAVIGATOR).bot().tree().getTreeItem(stewardName).select();
		List<String> items = item.contextMenu("duckdb-acl").menuItems();
		assertTrue(items.toString(), items.contains("Management console"));
		assertTrue(items.toString(), items.contains("View as role…"));
		assertFalse(items.toString(), items.contains("Native console"));
		SWTBotTreeItem other = bot.viewById(NAVIGATOR).bot().tree().getTreeItem(analystName).select();
		assertFalse(other.contextMenu().menuItems().contains("duckdb-acl"));

		// the actions themselves: a management console is an SQL editor on a connection of its own
		bot.viewById(NAVIGATOR).bot().tree().getTreeItem(stewardName).select().contextMenu("duckdb-acl")
		    .menu("Management console").click();
		DataSourceDescriptor manage = waitFor(bot, stewardName + " · manage");
		SWTWorkbenchBot workbench = bot;
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return workbench.editors().stream().anyMatch(e -> e.getTitle().contains("· manage"));
			}

			@Override
			public String getFailureMessage() {
				return "no SQL console on the management connection: "
				    + workbench.editors().stream().map(e -> e.getTitle()).toList();
			}
		}, 30_000);
		bot.editors().stream().filter(e -> e.getTitle().contains("· manage")).forEach(e -> e.close());
		close(manage);
		// view-as: the role chosen from platform.roles, then a read-only connection in the navigator
		bot.viewById(NAVIGATOR).bot().tree().getTreeItem(stewardName).select().contextMenu("duckdb-acl")
		    .menu("View as role…").click();
		// the dialog's own widgets: the test runtime never takes the focus, so its shell is not activated
		SWTBot dialog = bot.shell("View as role").bot();
		dialog.text().setText("analyst");
		dialog.table().select("analyst");
		dialog.button("OK").click();
		DataSourceDescriptor viewAs = waitFor(bot, stewardName + " · as analyst");
		assertTrue(viewAs.isConnectionReadOnly());
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return viewAs.isConnected();
			}

			@Override
			public String getFailureMessage() {
				return "the action did not connect the view-as copy";
			}
		}, 30_000);
		close(viewAs);
		// a role the viewer may not view (steward holds administration - passthrough's to view): the node refuses
		// it at connect, the plugin says so and leaves no connection behind
		bot.viewById(NAVIGATOR).bot().tree().getTreeItem(stewardName).select().contextMenu("duckdb-acl")
		    .menu("View as role…").click();
		SWTBot again = bot.shell("View as role").bot();
		again.text().setText("steward");
		again.table().select("steward");
		again.button("OK").click();
		bot.shell("duckdb-acl").close();
		for (DBPDataSourceContainer each : steward().getRegistry().getDataSources()) {
			assertFalse("a refused view-as left a connection", each.getName().equals(stewardName + " · as steward"));
		}
	}

	/** The temporary connection an action added, by its name. */
	private static DataSourceDescriptor waitFor(SWTWorkbenchBot bot, String name) {
		DataSourceDescriptor[] found = new DataSourceDescriptor[1];
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() throws Exception {
				for (DBPDataSourceContainer each : steward().getRegistry().getDataSources()) {
					if (name.equals(each.getName()) && each instanceof DataSourceDescriptor descriptor) {
						found[0] = descriptor;
						return descriptor.isTemporary();
					}
				}
				return false;
			}

			@Override
			public String getFailureMessage() {
				return "no connection " + name;
			}
		}, 30_000);
		return found[0];
	}

	@Test
	public void viewAsShowsTheRolesTree() throws Exception {
		LiveTreeTest.assumeNode();
		VoidProgressMonitor monitor = new VoidProgressMonitor();
		DataSourceDescriptor copy = AclConsoles.open((DataSourceDescriptor) steward(),
		    ConsoleCopy.of(ConsoleKind.VIEW_AS, steward().getName(), "analyst"));
		try {
			assertTrue(copy.isTemporary());
			assertTrue(copy.isConnectionReadOnly());
			copy.connect(monitor, true, true);
			assertNull("a view-as copy reads no console_info (no denial recorded for it)", console(copy));
			// the same tree as analyst1's own connection: the node answers the role's listings
			assertEquals(tree(LiveTreeTest.analyst()), tree(copy));
			// and no data: a view-as session reads metadata only (duckdb-acl spec 120.3)
			DBCExecutionContext context = DBUtils.getDefaultContext(copy.getDataSource(), false);
			try (JDBCSession session = (JDBCSession) context.openSession(monitor, DBCExecutionPurpose.USER, "test");
			     JDBCPreparedStatement statement = session.prepareStatement("SELECT * FROM sales.main.orders");
			     JDBCResultSet rows = statement.executeQuery()) {
				fail("a view-as session read rows");
			} catch (Exception e) {
				StringBuilder chain = new StringBuilder();
				for (Throwable t = e; t != null; t = t.getCause()) {
					chain.append(t.getMessage()).append(" / ");
				}
				assertTrue(chain.toString(), chain.toString().contains("metadata only"));
			}
		} finally {
			close(copy);
		}
	}

	@Test
	public void aRefusedViewAsDoesNotConnect() throws Exception {
		LiveTreeTest.assumeNode();
		DataSourceDescriptor copy = AclConsoles.open((DataSourceDescriptor) steward(),
		    ConsoleCopy.of(ConsoleKind.VIEW_AS, steward().getName(), "steward"));
		try {
			copy.connect(new VoidProgressMonitor(), true, true);
			fail("a view-as the node refuses connected - as the viewer, under the role's label");
		} catch (Exception e) {
			StringBuilder chain = new StringBuilder();
			for (Throwable t = e; t != null; t = t.getCause()) {
				chain.append(t.getMessage()).append(" / ");
			}
			assertTrue(chain.toString(), chain.toString().contains("holds administration"));
			assertFalse(copy.isConnected());
		} finally {
			close(copy);
		}
	}

	@Test
	public void aManagementConsoleRunsManagement() throws Exception {
		LiveTreeTest.assumeNode();
		VoidProgressMonitor monitor = new VoidProgressMonitor();
		DataSourceDescriptor copy = AclConsoles.open((DataSourceDescriptor) steward(),
		    ConsoleCopy.of(ConsoleKind.MANAGE, steward().getName(), null));
		try {
			assertEquals("manage", copy.getConnectionConfiguration().getProperty("acl.mode"));
			copy.connect(monitor, true, true);
			assertNull("console_info is read in data mode only", console(copy));
			DBCExecutionContext context = DBUtils.getDefaultContext(copy.getDataSource(), false);
			try (JDBCSession session = (JDBCSession) context.openSession(monitor, DBCExecutionPurpose.USER, "test");
			     JDBCPreparedStatement statement = session.prepareStatement("CHECK VIRTUAL CATALOG sales");
			     JDBCResultSet rows = statement.executeQuery()) {
				assertNotNull(rows);
			}
		} finally {
			close(copy);
		}
	}

	private static String tree(DBPDataSourceContainer container) throws Exception {
		DBNNode node = DBWorkbench.getPlatform().getNavigatorModel().getNodeByObject(container);
		StringBuilder out = new StringBuilder();
		for (DBNNode child : node.getChildren(new VoidProgressMonitor())) {
			LiveTreeTest.dump(child, 0, 6, out);
		}
		return out.toString();
	}

	private static void close(DataSourceDescriptor copy) throws Exception {
		// a console's editor may still be connecting it: wait, or the connection completes after removal and leaks
		for (int i = 0; i < 300 && copy.isConnecting(); i++) {
			Thread.sleep(100);
		}
		if (copy.isConnected()) {
			copy.disconnect(new VoidProgressMonitor());
		}
		copy.getRegistry().removeDataSource(copy);
	}
}
