package org.jkiss.dbeaver.ext.acl.ui;

import org.eclipse.core.expressions.PropertyTester;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.ext.acl.model.ConsoleInfo;
import org.jkiss.dbeaver.ext.acl.model.ConsoleKind;
import org.jkiss.dbeaver.model.navigator.DBNDataSource;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.ext.acl.model.access.AclRole;

/**
 * `io.github.hugrlab.acl.console`: whether a connection's console_info offers a console kind (`any`: one of them);
 * `io.github.hugrlab.acl.role`: whether a role of the Access control branch can be viewed as (spec 008.1 - the view-as
 * offer of its connection).
 */
public class AclConsoleTester extends PropertyTester {
	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		if ("role".equals(property)) {
			return receiver instanceof DBNDatabaseNode node && node.getObject() instanceof AclRole role
			       && role.getDataSource() != null && role.getDataSource().console() != null
			       && role.getDataSource().console().offers(ConsoleKind.VIEW_AS);
		}
		if (!(receiver instanceof DBNDataSource node)
		    || !(node.getDataSourceContainer().getDataSource() instanceof AclDataSource acl)) {
			return false;
		}
		ConsoleInfo console = acl.console();
		if (console == null) {
			return false;
		}
		String kind = args.length > 0 ? String.valueOf(args[0]) : "any";
		return "any".equals(kind) ? console.offersAny() : console.offers(ConsoleKind.of(kind));
	}
}
