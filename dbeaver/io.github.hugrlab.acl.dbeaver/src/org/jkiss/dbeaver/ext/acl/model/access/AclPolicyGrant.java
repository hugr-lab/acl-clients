package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

/**
 * A grant that carries a policy - a catalog grant or an object grant: its row filter, and an object grant's columns and
 * masks, which the Row filter and Columns tabs edit and check (spec 008.1 addendum, spec 011; a schema grant carries
 * capabilities only).
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

	/** The grant's masks (`col AS <expr>, …`); a catalog grant has none (duckdb-acl spec 158). */
	default String getMasks() {
		return "";
	}

	/**
	 * What writing it with these values would say - `platform.check_grant` (duckdb-acl spec 122 / 158), written nowhere:
	 * rows (item, name, type, status, message). With no columns: every column the object exposes.
	 */
	default List<Map<String, Object>> check(DBRProgressMonitor monitor, String rls, String columns, String masks)
	    throws DBException {
		return getDataSource().platformRows(monitor, checkSql(rls, columns, masks));
	}

	/**
	 * `SELECT * FROM platform.check_grant(role, catalog, object, rls, columns[, caps[, masks]])` - positional, `''` caps
	 * = unstated (duckdb-acl spec 170).
	 */
	default String checkSql(String rls, String columns, String masks) {
		List<String> arguments = new ArrayList<>(List.of(role(), grantCatalog(), grantObject(), rls == null ? "" : rls.trim(),
		                                                 columns == null ? "" : columns.trim()));
		boolean masked = masks != null && !masks.trim().isEmpty();
		if (capsJson() != null || masked) {
			arguments.add(capsJson() == null ? "" : capsJson());
		}
		if (masked) {
			arguments.add(masks.trim());
		}
		return PlatformSql.call("check_grant", arguments.toArray(new String[0]));
	}
}
