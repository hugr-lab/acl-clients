package io.github.hugrlab.acl.dbeaver.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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

	public static synchronized DBPDataSourceContainer steward() throws Exception {
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
		List<String> items = item.contextMenu("Access control").menuItems();
		assertTrue(items.toString(), items.contains("Management console"));
		assertTrue(items.toString(), items.contains("View as role…"));
		assertFalse(items.toString(), items.contains("Native console"));
		SWTBotTreeItem other = bot.viewById(NAVIGATOR).bot().tree().getTreeItem(analystName).select();
		assertFalse(other.contextMenu().menuItems().contains("Access control"));

		// the actions themselves: a management console is an SQL editor on a connection of its own
		bot.viewById(NAVIGATOR).bot().tree().getTreeItem(stewardName).select().contextMenu("Access control")
		    .menu("Management console").click();
		DataSourceDescriptor manage = waitFor(bot, stewardName + " · manage");
		// what the copy is (AclConsoles.open): manage mode, auto-commit, no bootstrap statements of the user's, the
		// type's colour; asked again, the same copy
		assertEquals("manage", manage.getConnectionConfiguration().getProperty("acl.mode"));
		assertTrue(manage.isDefaultAutoCommit());
		assertTrue(manage.getConnectionConfiguration().getBootstrap().getInitQueries().isEmpty());
		assertSame(manage, AclConsoles.open((DataSourceDescriptor) steward(),
		    ConsoleCopy.of(ConsoleKind.MANAGE, stewardName, null)));
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
		bot.viewById(NAVIGATOR).bot().tree().getTreeItem(stewardName).select().contextMenu("Access control")
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
		bot.viewById(NAVIGATOR).bot().tree().getTreeItem(stewardName).select().contextMenu("Access control")
		    .menu("View as role…").click();
		SWTBot again = bot.shell("View as role").bot();
		again.text().setText("steward");
		again.table().select("steward");
		again.button("OK").click();
		// the error dialog names the action (the node's text is its cause - aRefusedViewAsDoesNotConnect reads it)
		String said = textsOf(bot.shell("duckdb-acl"));
		assertTrue("the error dialog: " + said, said.contains("view_as connection"));
		bot.shell("duckdb-acl").close();
		for (DBPDataSourceContainer each : steward().getRegistry().getDataSources()) {
			assertFalse("a refused view-as left a connection", each.getName().equals(stewardName + " · as steward"));
		}
	}

	/** Every text a dialog shows (labels, texts, links), joined. */
	/** Spec 008.1: View as this role, on a role of the Access control branch - that role's copy, no chooser. */
	@Test
	public void viewAsThisRoleFromTheBranch() throws Exception {
		LiveTreeTest.assumeNode();
		SWTWorkbenchBot bot = new SWTWorkbenchBot();
		String stewardName = steward().getName();
		org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem connection = bot.viewById(NAVIGATOR).bot().tree()
		    .getTreeItem(stewardName).expand();
		org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem roles = expandTo(bot, connection, "Access control", "Roles");
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return roles.getNodes().contains("analyst");
			}

			@Override
			public String getFailureMessage() {
				return "no role analyst under Roles: " + roles.getNodes();
			}
		}, 30_000);
		roles.getNode("analyst").select().contextMenu("View as this role").click();
		DataSourceDescriptor viewAs = waitFor(bot, stewardName + " · as analyst");
		assertTrue(viewAs.isConnectionReadOnly());
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return viewAs.isConnected();
			}

			@Override
			public String getFailureMessage() {
				return "View as this role did not connect the copy";
			}
		}, 30_000);
		close(viewAs);
	}

	/**
	 * Spec 008.1 through DBeaver's own UI: Create New Role on the branch's Roles opens the object's editor; Save shows
	 * DBeaver's Persist Changes with our grammar and Execute runs it; an edit saves as one ALTER; Delete drops it.
	 * The property values are set through the editor's own command context - what its properties panel does.
	 */
	@Test
	public void aRoleThroughDbeaversEditor() throws Exception {
		LiveTreeTest.assumeNode();
		SWTWorkbenchBot bot = new SWTWorkbenchBot();
		org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem connection = bot.viewById(NAVIGATOR).bot().tree()
		    .getTreeItem(steward().getName()).expand();
		dropRole("t008 ui"); // a run that stopped half way
		org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem roles = expandTo(bot, connection, "Access control", "Roles");
		roles.select().contextMenu("Create New Role").click();
		org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor editor = waitForEditor(bot);
		org.eclipse.ui.IEditorPart part = UIThreadRunnable.syncExec(() -> editor.getReference().getEditor(true));
		org.jkiss.dbeaver.ui.editors.IDatabaseEditorInput input =
		    (org.jkiss.dbeaver.ui.editors.IDatabaseEditorInput) part.getEditorInput();
		org.jkiss.dbeaver.ext.acl.model.access.AclRole role = (org.jkiss.dbeaver.ext.acl.model.access.AclRole) input.getDatabaseObject();
		setInEditor(input, role, "name", "t008 ui");
		setInEditor(input, role, "comment", "from the ui");
		String preview = saveAndExecute(bot, part, "t008 ui");
		assertTrue(preview, preview.contains("CREATE ROLE \"t008 ui\";") && preview.contains("ALTER ROLE \"t008 ui\" SET COMMENT 'from the ui';"));
		org.jkiss.dbeaver.ext.acl.model.AclDataSource acl = (org.jkiss.dbeaver.ext.acl.model.AclDataSource) steward().getDataSource();
		waitForRows(bot, acl, org.jkiss.dbeaver.ext.acl.model.access.PlatformSql.view("roles", "role", "t008 ui", "comment", "from the ui"), 1);

		// as a user would: close the new object's editor, open the role from the tree, edit there
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return !role.isCreating() && !UIThreadRunnable.syncExec(part::isDirty);
			}

			@Override
			public String getFailureMessage() {
				return "the saved role was not read again";
			}
		}, 30_000);
		assertEquals("read again after the save", "from the ui", role.getComment());
		UIThreadRunnable.syncExec(() -> part.getSite().getPage().closeEditor(part, false));
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return roles.getNodes().contains("t008 ui");
			}

			@Override
			public String getFailureMessage() {
				return "the new role is not in the tree: " + roles.getNodes();
			}
		}, 30_000);
		roles.getNode("t008 ui").select().contextMenu("View Role").click();
		org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor reopened = waitForEditor(bot);
		org.eclipse.ui.IEditorPart part2 = UIThreadRunnable.syncExec(() -> reopened.getReference().getEditor(true));
		org.jkiss.dbeaver.ui.editors.IDatabaseEditorInput input2 =
		    (org.jkiss.dbeaver.ui.editors.IDatabaseEditorInput) part2.getEditorInput();
		org.jkiss.dbeaver.ext.acl.model.access.AclRole stored = (org.jkiss.dbeaver.ext.acl.model.access.AclRole) input2.getDatabaseObject();
		assertEquals("the editor shows what the node holds", "from the ui", stored.getComment());
		setInEditor(input2, stored, "comment", "two");
		preview = saveAndExecute(bot, part2, "t008 ui");
		assertEquals("ALTER ROLE \"t008 ui\" SET COMMENT 'two';", preview.trim());
		waitForRows(bot, acl, org.jkiss.dbeaver.ext.acl.model.access.PlatformSql.view("roles", "role", "t008 ui", "comment", "two"), 1);
		UIThreadRunnable.syncExec(() -> part2.getSite().getPage().closeEditor(part2, false));

		// Delete from the navigator: DBeaver confirms, then the role is dropped
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return roles.getNodes().contains("t008 ui");
			}

			@Override
			public String getFailureMessage() {
				return "the new role is not in the tree: " + roles.getNodes();
			}
		}, 30_000);
		roles.getNode("t008 ui").select().contextMenu("Delete").click();
		org.eclipse.swtbot.swt.finder.widgets.SWTBotShell confirm = bot.shell("Delete object");
		java.util.Set<String> before = new java.util.HashSet<>();
		for (org.eclipse.swtbot.swt.finder.widgets.SWTBotShell sh : bot.shells()) {
			before.add(sh.getText());
		}
		confirm.bot().button("View Script").click();
		String script = null;
		for (int i = 0; i < 50 && script == null; i++) {
			for (org.eclipse.swtbot.swt.finder.widgets.SWTBotShell sh : bot.shells()) {
				if (!before.contains(sh.getText())) {
					script = textsOf(sh);
					sh.close();
				}
			}
			Thread.sleep(100);
		}
		assertTrue("the delete's script: " + script, script != null && script.contains("DROP ROLE \"t008 ui\""));
		confirm = bot.shell("Delete object"); // DBeaver asks again once the script is closed
		confirm.bot().button("Yes").click();
		waitForRows(bot, acl, org.jkiss.dbeaver.ext.acl.model.access.PlatformSql.view("roles", "role", "t008 ui"), 0);
	}

	private static void dropRole(String role) throws Exception {
		org.jkiss.dbeaver.ext.acl.model.AclDataSource acl = (org.jkiss.dbeaver.ext.acl.model.AclDataSource) steward().getDataSource();
		try (org.jkiss.dbeaver.model.exec.jdbc.JDBCSession session = org.jkiss.dbeaver.model.DBUtils.openUtilSession(
		         new org.jkiss.dbeaver.model.runtime.VoidProgressMonitor(), acl, "t008");
		     org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement statement = session.createStatement()) {
			statement.execute("DROP ROLE IF EXISTS " + org.jkiss.dbeaver.ext.acl.model.access.AclGrammar.ident(role));
		}
	}

	private static org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor waitForEditor(SWTWorkbenchBot workbench) {
		workbench.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				try {
					return workbench.activeEditor() != null;
				} catch (Exception e) {
					return false;
				}
			}

			@Override
			public String getFailureMessage() {
				return "Create New Role opened no editor";
			}
		}, 30_000);
		return workbench.activeEditor();
	}

	private static void setInEditor(org.jkiss.dbeaver.ui.editors.IDatabaseEditorInput input, Object object, String id,
	                                Object value) {
		org.jkiss.dbeaver.runtime.properties.PropertySourceEditable source =
		    new org.jkiss.dbeaver.runtime.properties.PropertySourceEditable(input.getCommandContext(), object, object);
		source.collectProperties();
		UIThreadRunnable.syncExec(() -> source.setPropertyValue(new org.jkiss.dbeaver.model.runtime.VoidProgressMonitor(), id, value));
	}

	/** Save the editor: DBeaver's Persist Changes shows the script; Execute runs it. Answers the script shown. */
	private static String saveAndExecute(SWTWorkbenchBot bot, org.eclipse.ui.IEditorPart part, String name) {
		UIThreadRunnable.asyncExec(() -> part.doSave(new org.eclipse.core.runtime.NullProgressMonitor()));
		org.eclipse.swtbot.swt.finder.widgets.SWTBotShell persist = bot.shell(name + " - Persist Changes");
		String shown = UIThreadRunnable.syncExec(() -> {
			java.util.ArrayDeque<org.eclipse.swt.widgets.Control> todo = new java.util.ArrayDeque<>();
			todo.add(persist.widget);
			while (!todo.isEmpty()) {
				org.eclipse.swt.widgets.Control c = todo.poll();
				if (c instanceof org.eclipse.swt.custom.StyledText t) {
					return t.getText();
				}
				if (c instanceof org.eclipse.swt.widgets.Composite composite) {
					todo.addAll(java.util.Arrays.asList(composite.getChildren()));
				}
			}
			return "";
		});
		persist.bot().button("Execute").click();
		return shown;
	}

	private static void waitForRows(SWTWorkbenchBot bot, org.jkiss.dbeaver.ext.acl.model.AclDataSource acl, String sql,
	                                int count) {
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() throws Exception {
				return acl.platformRows(new org.jkiss.dbeaver.model.runtime.VoidProgressMonitor(), sql).size() == count;
			}

			@Override
			public String getFailureMessage() {
				return "the node does not hold it yet: " + sql;
			}
		}, 30_000);
	}

	/**
	 * Spec 008.1 addendum: a grant's Row filter and Columns tabs - the predicate in its own editor, the object's columns
	 * read from the node, Check answering - and an edit there previewed by Save as the grant's statement.
	 */
	@Test
	public void aGrantsRowFilterAndColumnsTabs() throws Exception {
		LiveTreeTest.assumeNode();
		SWTWorkbenchBot bot = new SWTWorkbenchBot();
		org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem connection = bot.viewById(NAVIGATOR).bot().tree()
		    .getTreeItem(steward().getName()).expand();
		org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem grants = expandTo(bot, connection, "Access control", "Roles",
		                                                                      "analyst", "Object grants");
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return grants.getNodes().contains("sales.orders");
			}

			@Override
			public String getFailureMessage() {
				return "no grant sales.orders: " + grants.getNodes();
			}
		}, 30_000);
		grants.getNode("sales.orders").doubleClick();
		org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor editor = waitForEditor(bot);
		org.eclipse.ui.IEditorPart part = UIThreadRunnable.syncExec(() -> editor.getReference().getEditor(true));
		org.jkiss.dbeaver.ui.editors.entity.EntityEditor entity = (org.jkiss.dbeaver.ui.editors.entity.EntityEditor) part;
		UIThreadRunnable.syncExec(() -> entity.switchFolder("io.github.hugrlab.acl.grant.rowfilter"));
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				String all = editorTexts(entity);
				return all.contains("tenant = acl_claim('tenant')") && all.contains("amount");
			}

			@Override
			public String getFailureMessage() {
				return "the Row filter tab shows no predicate or no columns: " + editorTexts(entity);
			}
		}, 30_000);
		clickButton(entity, "Check with the node");
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return editorTexts(entity).contains("The write would be accepted");
			}

			@Override
			public String getFailureMessage() {
				return "Check did not answer: " + editorTexts(entity);
			}
		}, 30_000);
		UIThreadRunnable.syncExec(() -> entity.switchFolder("io.github.hugrlab.acl.grant.columns"));
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return editorTexts(entity).contains("(none - every column, whole)");
			}

			@Override
			public String getFailureMessage() {
				return "the Columns tab shows no tree: " + editorTexts(entity);
			}
		}, 30_000);
		// an edit of the predicate there: Save previews the grant's statement with it (cancelled - the dev policy stays)
		UIThreadRunnable.syncExec(() -> entity.switchFolder("io.github.hugrlab.acl.grant.rowfilter"));
		// typed at its end, a key at a time: each keystroke is a command, and the text must stay where it is typed
		org.eclipse.swt.custom.StyledText field = UIThreadRunnable.syncExec(() -> predicateEditor(entity, "tenant = acl_claim('tenant')"));
		org.eclipse.swtbot.swt.finder.widgets.SWTBotStyledText typed = new org.eclipse.swtbot.swt.finder.widgets.SWTBotStyledText(field);
		UIThreadRunnable.syncExec(() -> field.setCaretOffset(field.getCharCount()));
		for (char c : " AND amount > 0".toCharArray()) {
			UIThreadRunnable.syncExec(() -> {
				int at = field.getCaretOffset();
				field.replaceTextRange(at, 0, String.valueOf(c)); // what a key does: insert at the caret, then move it
				field.setCaretOffset(at + 1);
			});
			Thread.sleep(30); // let the command's refresh run between keys, as it does between a person's keys
		}
		assertEquals("tenant = acl_claim('tenant') AND amount > 0", typed.getText());
		UIThreadRunnable.asyncExec(() -> part.doSave(new org.eclipse.core.runtime.NullProgressMonitor()));
		org.eclipse.swtbot.swt.finder.widgets.SWTBotShell persist = bot.shell("sales.orders - Persist Changes");
		String preview = textsOf(persist);
		assertTrue(preview, preview.contains("GRANT OBJECT sales.orders TO ROLE analyst WITH (select) RLS (tenant = "
		                                     + "acl_claim('tenant') AND amount > 0)"));
		persist.bot().button("Cancel").click();
		UIThreadRunnable.syncExec(() -> part.getSite().getPage().closeEditor(part, false));
	}

	private static String editorTexts(org.eclipse.ui.IEditorPart part) {
		return UIThreadRunnable.syncExec(() -> {
			StringBuilder out = new StringBuilder();
			java.util.ArrayDeque<org.eclipse.swt.widgets.Control> todo = new java.util.ArrayDeque<>();
			todo.add(control(part));
			while (!todo.isEmpty()) {
				org.eclipse.swt.widgets.Control c = todo.poll();
				if (c == null || c.isDisposed()) {
					continue;
				}
				if (c instanceof org.eclipse.swt.widgets.Label l) {
					out.append(l.getText()).append(" | ");
				} else if (c instanceof org.eclipse.swt.widgets.Text t) {
					out.append(t.getText()).append(" | ");
				} else if (c instanceof org.eclipse.swt.custom.StyledText t) {
					out.append(t.getText()).append(" | ");
				} else if (c instanceof org.eclipse.swt.widgets.Table t) {
					for (org.eclipse.swt.widgets.TableItem item : t.getItems()) {
						out.append(item.getText(0)).append(" ");
					}
				} else if (c instanceof org.eclipse.swt.widgets.Tree t) {
					for (org.eclipse.swt.widgets.TreeItem item : t.getItems()) {
						out.append(item.getText(0)).append(" ");
					}
				}
				if (c instanceof org.eclipse.swt.widgets.Composite composite) {
					todo.addAll(java.util.Arrays.asList(composite.getChildren()));
				}
			}
			return out.toString();
		});
	}

	private static org.eclipse.swt.widgets.Control control(org.eclipse.ui.IEditorPart part) {
		org.eclipse.swt.widgets.Shell shell = part.getSite().getShell();
		return shell;
	}

	/** The Row filter tab's editor: the StyledText holding the grant's predicate. */
	private static org.eclipse.swt.custom.StyledText predicateEditor(org.eclipse.ui.IEditorPart part, String predicate) {
		java.util.ArrayDeque<org.eclipse.swt.widgets.Control> todo = new java.util.ArrayDeque<>();
		todo.add(control(part));
		while (!todo.isEmpty()) {
			org.eclipse.swt.widgets.Control c = todo.poll();
			if (c instanceof org.eclipse.swt.custom.StyledText t && predicate.equals(t.getText())) {
				return t;
			}
			if (c instanceof org.eclipse.swt.widgets.Composite composite) {
				todo.addAll(java.util.Arrays.asList(composite.getChildren()));
			}
		}
		throw new AssertionError("no predicate editor in the editor");
	}

	private static void clickButton(org.eclipse.ui.IEditorPart part, String label) {
		UIThreadRunnable.syncExec(() -> {
			java.util.ArrayDeque<org.eclipse.swt.widgets.Control> todo = new java.util.ArrayDeque<>();
			todo.add(control(part));
			while (!todo.isEmpty()) {
				org.eclipse.swt.widgets.Control c = todo.poll();
				if (c instanceof org.eclipse.swt.widgets.Button b && label.equals(b.getText()) && b.isVisible()) {
					b.notifyListeners(org.eclipse.swt.SWT.Selection, new org.eclipse.swt.widgets.Event());
					return;
				}
				if (c instanceof org.eclipse.swt.widgets.Composite composite) {
					todo.addAll(java.util.Arrays.asList(composite.getChildren()));
				}
			}
			throw new AssertionError("no button " + label);
		});
	}

	/** Spec 008.2: a virtual table's Column list tab, and Repair… on a finding of Check through its dialog. */
	@Test
	public void aTablesColumnListAndARepair() throws Exception {
		LiveTreeTest.assumeNode();
		SWTWorkbenchBot bot = new SWTWorkbenchBot();
		runAsSteward("DROP VIRTUAL CATALOG IF EXISTS t008ui CASCADE");
		runAsSteward("CREATE VIRTUAL CATALOG t008ui");
		runAsSteward("CREATE VIRTUAL TABLE t008ui.o AS memory.main.orders");
		runAsSteward("CREATE VIRTUAL TABLE t008ui.ghost AS memory.main.t008_none");
		try {
			org.jkiss.dbeaver.model.navigator.DBNDatabaseNode root = DBWorkbench.getPlatform().getNavigatorModel()
			    .getNodeByObject(steward().getDataSource());
			root.refreshNode(new org.jkiss.dbeaver.model.runtime.VoidProgressMonitor(), this);
			org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem connection = bot.viewById(NAVIGATOR).bot().tree()
			    .getTreeItem(steward().getName()).expand();
			org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem relations = expandTo(bot, connection, "Access control",
			                                                                         "Virtual catalogs", "t008ui", "Relations");
			bot.waitUntil(new DefaultCondition() {
				@Override
				public boolean test() {
					return relations.getNodes().contains("o");
				}

				@Override
				public String getFailureMessage() {
					return "no relation o: " + relations.getNodes();
				}
			}, 30_000);
			relations.getNode("o").doubleClick();
			org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor editor = waitForEditor(bot);
			org.eclipse.ui.IEditorPart part = UIThreadRunnable.syncExec(() -> editor.getReference().getEditor(true));
			org.jkiss.dbeaver.ui.editors.entity.EntityEditor entity = (org.jkiss.dbeaver.ui.editors.entity.EntityEditor) part;
			UIThreadRunnable.syncExec(() -> entity.switchFolder("io.github.hugrlab.acl.relation.columns"));
			bot.waitUntil(new DefaultCondition() {
				@Override
				public boolean test() {
					String all = editorTexts(entity);
					return all.contains("(none - every column as it is)") && all.contains("yes");
				}

				@Override
				public String getFailureMessage() {
					return "the Column list tab shows no source columns: " + editorTexts(entity);
				}
			}, 30_000);
			UIThreadRunnable.syncExec(() -> part.getSite().getPage().closeEditor(part, false));

			org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem check = expandTo(bot, connection, "Access control",
			                                                                     "Virtual catalogs", "t008ui", "Check");
			bot.waitUntil(new DefaultCondition() {
				@Override
				public boolean test() {
					return check.getNodes().stream().anyMatch(n -> n.contains("ghost"));
				}

				@Override
				public String getFailureMessage() {
					return "no finding on ghost: " + check.getNodes();
				}
			}, 30_000);
			String finding = check.getNodes().stream().filter(n -> n.contains("ghost")).findFirst().orElseThrow();
			check.getNode(finding).select().contextMenu("Repair…").click();
			org.eclipse.swtbot.swt.finder.widgets.SWTBotShell repair = bot.shell("Repair");
			repair.bot().radio("DROP VIRTUAL TABLE t008ui.ghost").click();
			repair.bot().button("Execute").click();
			org.jkiss.dbeaver.ext.acl.model.AclDataSource acl = (org.jkiss.dbeaver.ext.acl.model.AclDataSource) steward().getDataSource();
			waitForRows(bot, acl, org.jkiss.dbeaver.ext.acl.model.access.PlatformSql.view("relations", "catalog", "t008ui", "name", "ghost"), 0);
		} finally {
			runAsSteward("DROP VIRTUAL CATALOG IF EXISTS t008ui CASCADE");
		}
	}

	private static void runAsSteward(String sql) throws Exception {
		org.jkiss.dbeaver.ext.acl.model.AclDataSource acl = (org.jkiss.dbeaver.ext.acl.model.AclDataSource) steward().getDataSource();
		try (org.jkiss.dbeaver.model.exec.jdbc.JDBCSession session = org.jkiss.dbeaver.model.DBUtils.openUtilSession(
		         new org.jkiss.dbeaver.model.runtime.VoidProgressMonitor(), acl, "t008");
		     org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement statement = session.createStatement()) {
			statement.execute(sql);
		}
	}

	/** Expand a path of the navigator, waiting at each level for its children (DBeaver loads them in the background). */
	private static org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem expandTo(SWTWorkbenchBot bot,
	    org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem from, String... path) {
		org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem at = from;
		for (String name : path) {
			org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem parent = at.expand();
			bot.waitUntil(new DefaultCondition() {
				@Override
				public boolean test() {
					return parent.getNodes().contains(name);
				}

				@Override
				public String getFailureMessage() {
					return "no " + name + " under " + parent.getText() + ": " + parent.getNodes();
				}
			}, 30_000);
			at = parent.getNode(name);
		}
		return at.expand();
	}

	static String textsOf(org.eclipse.swtbot.swt.finder.widgets.SWTBotShell shell) {
		return UIThreadRunnable.syncExec(() -> {
			StringBuilder out = new StringBuilder();
			java.util.ArrayDeque<org.eclipse.swt.widgets.Control> todo = new java.util.ArrayDeque<>();
			todo.add(shell.widget);
			while (!todo.isEmpty()) {
				org.eclipse.swt.widgets.Control c = todo.poll();
				if (c instanceof org.eclipse.swt.widgets.Label l) {
					out.append(l.getText()).append(" | ");
				} else if (c instanceof org.eclipse.swt.widgets.Text t) {
					out.append(t.getText()).append(" | ");
				} else if (c instanceof org.eclipse.swt.custom.StyledText t) {
					out.append(t.getText()).append(" | ");
				} else if (c instanceof org.eclipse.swt.widgets.Link l) {
					out.append(l.getText()).append(" | ");
				}
				if (c instanceof org.eclipse.swt.widgets.Composite composite) {
					todo.addAll(java.util.List.of(composite.getChildren()));
				}
			}
			return out.toString();
		});
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
		// a console's editor connects it on a job of its own: let that job start and end (up to 3 s idle, 30 s in
		// all), or the connection completes after the removal and leaks
		for (int i = 0, idle = 0; i < 300 && idle < 30; i++) {
			idle = copy.isConnecting() || copy.isConnected() ? 30 : idle + 1;
			Thread.sleep(100);
		}
		for (int i = 0; i < 300 && copy.isConnecting(); i++) {
			Thread.sleep(100);
		}
		if (copy.isConnected()) {
			copy.disconnect(new VoidProgressMonitor());
		}
		copy.getRegistry().removeDataSource(copy);
	}
}
