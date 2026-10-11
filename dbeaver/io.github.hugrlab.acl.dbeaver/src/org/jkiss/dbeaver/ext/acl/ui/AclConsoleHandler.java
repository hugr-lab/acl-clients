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
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.editors.sql.handlers.SQLEditorHandlerOpenEditor;
import org.jkiss.dbeaver.ui.editors.sql.handlers.SQLNavigatorContext;
import org.jkiss.dbeaver.ui.navigator.NavigatorUtils;

/**
 * duckdb-acl ▸ Management console | Native console | View as role… on a connection (spec 007.2): a connection of its
 * own; a console opens an SQL editor on it, view-as leaves it in the navigator - its tree is the role's.
 */
public class AclConsoleHandler extends AbstractHandler {
	static final String KIND = "io.github.hugrlab.acl.console.kind";
	static final String ROLES_SQL = "SELECT role FROM platform.roles ORDER BY role";

	@Override
	public Object execute(ExecutionEvent event) {
		ConsoleKind kind = ConsoleKind.of(event.getParameter(KIND));
		DBNNode node = NavigatorUtils.getSelectedNode(HandlerUtil.getCurrentSelection(event));
		if (!(node instanceof DBNDataSource source)
		    || !(source.getDataSourceContainer() instanceof DataSourceDescriptor original)) {
			return null;
		}
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
		try {
			String role = null;
			if (kind == ConsoleKind.VIEW_AS) {
				role = chooseRole(window.getShell(), roles(original.getDataSource()));
				if (role == null) {
					return null;
				}
			}
			DataSourceDescriptor copy = AclConsoles.open(original, ConsoleCopy.of(kind, original.getName(), role));
			if (kind != ConsoleKind.VIEW_AS) {
				SQLEditorHandlerOpenEditor.openSQLConsole(window, new SQLNavigatorContext(copy, null), copy.getName(), "");
			}
		} catch (Exception e) {
			Throwable cause = e instanceof InvocationTargetException wrapped ? wrapped.getTargetException() : e;
			DBWorkbench.getPlatformUI().showError("duckdb-acl", "Could not open the " + kind.id() + " connection", cause);
		}
		return null;
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
				throw new InvocationTargetException(e);
			}
		});
		return roles;
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
