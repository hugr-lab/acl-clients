package org.jkiss.dbeaver.ext.acl.ui;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.handlers.HandlerUtil;
import org.jkiss.dbeaver.ext.acl.model.ConsoleCopy;
import org.jkiss.dbeaver.ext.acl.model.ConsoleKind;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.navigator.DBNDataSource;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.ext.acl.model.access.AclRole;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.editors.sql.handlers.SQLEditorHandlerOpenEditor;
import org.jkiss.dbeaver.ui.editors.sql.handlers.SQLNavigatorContext;
import org.jkiss.dbeaver.ui.navigator.NavigatorUtils;

/**
 * duckdb-acl ▸ Management console | Native console | View as role… on a connection (spec 007.2): a connection of its
 * own; a console opens an SQL editor on it, view-as leaves it in the navigator - its tree is the role's. A role the
 * viewer may not view is listed too (platform.roles is the viewer's listing): the node refuses it at the copy's
 * connect, and the copy does not connect.
 */
public class AclConsoleHandler extends AbstractHandler {
	static final String KIND = "io.github.hugrlab.acl.console.kind";
	static final String ROLES_SQL = "SELECT role FROM platform.roles ORDER BY role";

	@Override
	public Object execute(ExecutionEvent event) {
		String which = event.getParameter(KIND);
		if (which == null) {
			return null; // the command without its kind (Quick Access): nothing to open
		}
		ConsoleKind kind = ConsoleKind.of(which);
		DBNNode node = NavigatorUtils.getSelectedNode(HandlerUtil.getCurrentSelection(event));
		// spec 008.1: View as this role - on a role of the Access control branch, that role, no chooser
		String chosen = null;
		DataSourceDescriptor original;
		if (node instanceof DBNDatabaseNode roleNode && roleNode.getObject() instanceof AclRole aclRole
		    && aclRole.getDataSource().getContainer() instanceof DataSourceDescriptor roleSource) {
			kind = ConsoleKind.VIEW_AS;
			chosen = aclRole.getName();
			original = roleSource;
		} else if (node instanceof DBNDataSource source
		           && source.getDataSourceContainer() instanceof DataSourceDescriptor connection) {
			original = connection;
		} else {
			return null;
		}
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
		try {
			String role = chosen;
			if (kind == ConsoleKind.VIEW_AS) {
				if (role == null) {
					role = chooseRole(window.getShell(), roles(original.getDataSource()));
				}
				if (role == null) {
					return null;
				}
				String why = ConsoleCopy.unviewable(role);
				if (why != null) {
					DBWorkbench.getPlatformUI().showError("View as role", why);
					return null;
				}
			}
			DataSourceDescriptor copy = AclConsoles.open(original, ConsoleCopy.of(kind, original.getName(), role));
			if (kind == ConsoleKind.VIEW_AS && !copy.isConnected()) {
				connectOrDrop(copy);
			}
			if (kind != ConsoleKind.VIEW_AS) {
				SQLEditorHandlerOpenEditor.openSQLConsole(window, new SQLNavigatorContext(copy, null), copy.getName(), "");
			}
		} catch (InterruptedException e) {
			return null; // the user cancelled reading the roles
		} catch (Exception e) {
			Throwable cause = e instanceof InvocationTargetException wrapped ? wrapped.getTargetException() : e;
			DBWorkbench.getPlatformUI().showError("duckdb-acl", "Could not open the " + kind.id() + " connection", cause);
		}
		return null;
	}

	/** A view-as copy connects now: a refusal (a role the viewer may not view) leaves no connection behind. */
	private static void connectOrDrop(DataSourceDescriptor copy) throws Exception {
		try {
			UIUtils.runInProgressService(monitor -> {
				try {
					copy.connect(monitor, true, true);
				} catch (Exception e) {
					throw cancelledOr(monitor, e);
				}
				if (monitor.isCanceled()) {
					throw new InterruptedException();
				}
			});
		} catch (Exception e) {
			copy.getRegistry().removeDataSource(copy);
			throw e;
		}
	}

	/** The roles the principal may view, as `platform.roles` lists them (the node narrows them to its rights). */
	private static List<String> roles(DBPDataSource dataSource) throws Exception {
		List<String> roles = new ArrayList<>();
		UIUtils.runInProgressService(monitor -> {
			try (JDBCSession session = DBUtils.openMetaSession(monitor, dataSource, "Read the roles");
			     JDBCPreparedStatement statement = session.prepareStatement(ROLES_SQL);
			     JDBCResultSet rs = statement.executeQuery()) {
				while (rs.next()) {
					roles.add(rs.getString(1));
				}
			} catch (Exception e) {
				throw cancelledOr(monitor, e);
			}
			if (monitor.isCanceled()) {
				throw new InterruptedException();
			}
		});
		return roles;
	}

	/** A cancel (the monitor's, which cancels the statement) is no failure: the action just ends. */
	private static InvocationTargetException cancelledOr(org.jkiss.dbeaver.model.runtime.DBRProgressMonitor monitor,
	                                                     Exception e) throws InterruptedException {
		if (monitor.isCanceled() || e instanceof InterruptedException) {
			throw new InterruptedException();
		}
		return new InvocationTargetException(e);
	}

	private static String chooseRole(Shell shell, List<String> roles) {
		ElementListSelectionDialog dialog = new ElementListSelectionDialog(shell, new LabelProvider());
		dialog.setTitle("View as role");
		dialog.setMessage("The catalog as this role sees it - metadata only, read-only:");
		dialog.setElements(roles.toArray());
		dialog.setMultipleSelection(false);
		return dialog.open() == Window.OK && dialog.getFirstResult() != null ? dialog.getFirstResult().toString() : null;
	}
}
