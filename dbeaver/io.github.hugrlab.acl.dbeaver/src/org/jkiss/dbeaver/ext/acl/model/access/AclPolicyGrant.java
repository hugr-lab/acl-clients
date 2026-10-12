package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

/**
 * A grant that carries a policy - a catalog grant or an object grant: its row filter and column list, which the Row
 * filter and Columns tabs edit and check (spec 008.1 addendum; a schema grant carries capabilities only).
 */
public interface AclPolicyGrant {
	String getRls();

	void setRls(String rls);

	String getColumns();

	void setColumns(String columns);

	/** The grant's catalog. */
	String grantCatalog();

	/** The object it is on; empty for a catalog grant. */
	String grantObject();

	/** The role it is to. */
	String role();

	/** The capabilities as `CAPS` writes them (the JSON), or null when unstated. */
	String capsJson();

	org.jkiss.dbeaver.ext.acl.model.AclDataSource getDataSource();

	/**
	 * What writing it with these values would say - `platform.check_grant` (duckdb-acl spec 122), written nowhere:
	 * rows (item, name, type, status, message). With no columns: every column the object exposes.
	 */
	default List<Map<String, Object>> check(DBRProgressMonitor monitor, String rls, String columns) throws DBException {
		List<String> arguments = new ArrayList<>(List.of(role(), grantCatalog(), grantObject(), rls == null ? "" : rls.trim(),
		                                                 columns == null ? "" : columns.trim()));
		if (capsJson() != null) {
			arguments.add(capsJson());
		}
		return getDataSource().platformRows(monitor, PlatformSql.call("check_grant", arguments.toArray(new String[0])));
	}
}
