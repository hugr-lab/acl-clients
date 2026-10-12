package org.jkiss.dbeaver.ext.acl.ui;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.handlers.HandlerUtil;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.ext.acl.model.access.AclPlatformObject;
import org.jkiss.dbeaver.ext.acl.model.access.Operable;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.runtime.AbstractJob;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.navigator.NavigatorUtils;

/**
 * An operation of an object of the branch ({@link Operable}, spec 008.2 / 008.3 - a repair, a refresh, a session's kill
 * and levels, drain and resume, a drift entry into the profile): its statements in {@link StatementDialog} - chosen,
 * completed, then run on the connection; the object's parent is read again after it.
 */
public class AclRunHandler extends AbstractHandler {
	static final String KIND = "io.github.hugrlab.acl.run.kind";

	@Override
	public Object execute(ExecutionEvent event) {
		String kind = event.getParameter(KIND);
		DBNNode node = NavigatorUtils.getSelectedNode(HandlerUtil.getCurrentSelection(event));
		if (kind == null || !(node instanceof DBNDatabaseNode databaseNode)
		    || !(databaseNode.getObject() instanceof AclPlatformObject object) || !(object instanceof Operable operable)) {
			return null;
		}
		List<String> alternatives = operable.operation(kind);
		String title = operable.operationTitle(kind);
		String explanation = operable.operationExplanation(kind);
		if (alternatives.isEmpty()) {
			return null;
		}
		StatementDialog dialog = new StatementDialog(HandlerUtil.getActiveShell(event), title, explanation, alternatives);
		if (dialog.open() != Window.OK || dialog.chosen() == null) {
			return null;
		}
		String statement = dialog.chosen();
		DBSObject catalog = object.getParentObject();
		new AbstractJob(title) {
			@NotNull
			@Override
			protected IStatus run(@NotNull DBRProgressMonitor monitor) {
				try (JDBCSession session = DBUtils.openUtilSession(monitor, object.getDataSource(), title);
				     JDBCStatement run = session.createStatement()) {
					run.execute(statement);
				} catch (Exception e) {
					UIUtils.asyncExec(() -> DBWorkbench.getPlatformUI().showError(title, "The node refused " + statement, e));
					return Status.OK_STATUS;
				}
				try {
					DBNDatabaseNode parent = DBWorkbench.getPlatform().getNavigatorModel().getNodeByObject(catalog);
					if (parent != null) {
						parent.refreshNode(monitor, this);
					}
				} catch (Exception e) {
					// the statement ran; the tree shows it on the next refresh
				}
				return Status.OK_STATUS;
			}
		}.schedule();
		return null;
	}
}
