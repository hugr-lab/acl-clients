package org.jkiss.dbeaver.ext.acl.model;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;
import org.jkiss.dbeaver.ext.generic.model.meta.GenericMetaModel;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.ext.acl.model.access.*;

/**
 * A connection to a node: Generic's, plus what `platform.console_info()` answered at connect (spec 007.2) - read once,
 * on the user's own connection (data mode, no view-as); a principal with no administration is refused it (one
 * recorded denial per connect), and then no console action is shown. Our dialect is passed here - Generic's
 * createDataSourceImpl makes its own, so a connected session used Generic's until 007.2.
 */
public class AclDataSource extends GenericDataSource {
	private static final Log log = Log.getLog(AclDataSource.class);
	static final String CONSOLE_SQL = "SELECT bundles, admin_catalogs, my_views, my_functions FROM platform.console_info()";

	/** The Main page's "trust the node's certificate" (a provider property - spec 003 addendum). */
	public static final String PROP_TRUST_CERTIFICATE = "acl.trustCertificate";

	private volatile ConsoleInfo console;

	public AclDataSource(@NotNull DBRProgressMonitor monitor, @NotNull DBPDataSourceContainer container,
	                     @NotNull GenericMetaModel metaModel) throws DBException {
		super(monitor, container, metaModel, new AclSQLDialect());
	}

	@Override
	public void initialize(@NotNull DBRProgressMonitor monitor) throws DBException {
		super.initialize(monitor);
		// a console's copy is prefixed and a view-as one is the role's: neither reads it (nor records a denial)
		DBPConnectionConfiguration configuration = getContainer().getConnectionConfiguration();
		String mode = configuration.getProperty("acl.mode");
		boolean data = mode == null || mode.isEmpty() || "data".equals(mode);
		String viewAs = configuration.getProperty("acl.viewAs");
		console = data && (viewAs == null || viewAs.isEmpty()) ? readConsole(monitor) : null;
	}

	/** Whether the connection trusts the node's certificate unchecked (the Main page's TLS choice). */
	public static boolean trustsCertificate(DBPConnectionConfiguration configuration) {
		return "true".equals(configuration.getProviderProperty(PROP_TRUST_CERTIFICATE));
	}

	/**
	 * What the Main page's choices add to the driver's properties: {@code disableCertificateVerification=true} when it
	 * trusts the certificate - before the user's own driver properties, which win (DBeaver fills them after these).
	 */
	public static java.util.Map<String, String> internalProperties(DBPConnectionConfiguration configuration) {
		return trustsCertificate(configuration) ? java.util.Map.of("disableCertificateVerification", "true")
		                                        : java.util.Map.of();
	}

	@Override
	protected java.util.Map<String, String> getInternalConnectionProperties(@NotNull DBRProgressMonitor monitor,
	    @NotNull org.jkiss.dbeaver.model.connection.DBPDriver driver,
	    @NotNull org.jkiss.dbeaver.model.impl.jdbc.JDBCExecutionContext context, @NotNull String purpose,
	    @NotNull DBPConnectionConfiguration connectionInfo) throws org.jkiss.dbeaver.model.exec.DBCException {
		java.util.Map<String, String> out = new java.util.HashMap<>();
		java.util.Map<String, String> inherited = super.getInternalConnectionProperties(monitor, driver, context, purpose,
		                                                                                connectionInfo);
		if (inherited != null) {
			out.putAll(inherited);
		}
		out.putAll(internalProperties(connectionInfo));
		return out;
	}

	/** What console_info answered at connect; null when it was not read or was refused. */
	@Nullable
	public ConsoleInfo console() {
		return console;
	}

	// --- spec 007.3: the Access control branch - shown only where console_info answered, each folder only where the
	// principal reads its view (the node judges every read again) ------------------------------------------------

	/** Whether the Access control branch is shown: the node answered console_info for this principal. */
	public boolean isAccessControl() {
		return console != null;
	}

	/** Whether the principal reads platform's view (a folder's visibleIf). */
	public boolean shows(String view) {
		return console != null && console.myViews().contains(view);
	}

	/** Whether the principal may call platform's function (a folder's visibleIf). */
	public boolean calls(String function) {
		return console != null && console.myFunctions().contains(function);
	}

	private final java.util.Map<String, List<?>> access = new java.util.HashMap<>();

	@SuppressWarnings("unchecked")
	private synchronized <T> List<T> accessRows(DBRProgressMonitor monitor, String key, String sql,
	                                             java.util.function.Function<java.util.Map<String, Object>, T> make)
	    throws DBException {
		List<?> cached = access.get(key);
		if (cached == null) {
			List<T> made = new java.util.ArrayList<>();
			for (java.util.Map<String, Object> each : platformRows(monitor, sql)) {
				made.add(make.apply(each));
			}
			cached = made;
			access.put(key, cached);
		}
		return (List<T>) cached;
	}

	/** The rows of one platform statement, by lower-case column name. */
	public List<java.util.Map<String, Object>> platformRows(DBRProgressMonitor monitor, String sql) throws DBException {
		List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
		try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Read the access control");
		     JDBCPreparedStatement statement = session.prepareStatement(sql);
		     JDBCResultSet rs = statement.executeQuery()) {
			java.sql.ResultSetMetaData meta = rs.getMetaData();
			while (rs.next()) {
				java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
				for (int i = 1; i <= meta.getColumnCount(); i++) {
					row.put(meta.getColumnLabel(i).toLowerCase(java.util.Locale.ROOT), rs.getObject(i));
				}
				rows.add(row);
			}
		} catch (java.sql.SQLException e) {
			throw new DBException("duckdb-acl: " + e.getMessage(), e);
		}
		return rows;
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclRole> getAccessRoles(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "roles", PlatformSql.view("roles"), r -> new AclRole(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclVirtualCatalog> getAccessCatalogs(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "catalogs", PlatformSql.view("catalogs"), r -> new AclVirtualCatalog(this, this, r));
	}

	/**
	 * The node's attached sources, as it lists them to an administrator (duckdb-acl spec 118.3: `platform.attached.<alias>`
	 * - never the policy store or a secrets service) - never derived here.
	 */
	@org.jkiss.dbeaver.model.meta.Association
	public List<AclSource> getAccessSources(DBRProgressMonitor monitor) throws DBException {
		List<AclSource> sources = accessRows(monitor, "sources",
		    "SELECT schema_name AS source FROM information_schema.schemata WHERE catalog_name = 'platform'"
		        + " AND schema_name LIKE 'attached.%' ORDER BY schema_name",
		    r -> {
			    // `attached.<alias>` (its schemas are `attached.<alias>.<schema>`): the alias, a part of the key
			    List<String> parts = SchemaPath.parts(String.valueOf(r.get("source")));
			    return parts != null && parts.size() == 2 ? new AclSource(this, this, java.util.Map.of("source", parts.get(1)))
			                                                : null;
		    });
		return sources.stream().filter(java.util.Objects::nonNull).toList();
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclIssuer> getAccessIssuers(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "issuers", PlatformSql.view("issuers"), r -> new AclIssuer(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclRoleMapping> getAccessRoleMappings(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "role_mappings", PlatformSql.view("role_mappings"), r -> new AclRoleMapping(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclFunctionCategory> getAccessFunctionCategories(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "function_categories", PlatformSql.view("function_categories"),
		                  r -> new AclFunctionCategory(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclFunctionGrant> getAccessFunctionGrants(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "function_grants", PlatformSql.view("function_grants"),
		                  r -> new AclFunctionGrant(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclResourceGroup> getAccessResourceGroups(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "resource_groups", PlatformSql.view("resource_groups"),
		                  r -> new AclResourceGroup(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclSession> getAccessSessions(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "sessions", PlatformSql.view("sessions"), r -> new AclSession(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclNodeLoad> getAccessNodeLoad(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "node_load", PlatformSql.view("node_load"), r -> new AclNodeLoad(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclNodeDoor> getAccessNodeDoors(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "node_doors", PlatformSql.view("node_doors"), r -> new AclNodeDoor(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclClusterItem> getAccessClusterItems(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "cluster_items", PlatformSql.view("cluster_items"), r -> new AclClusterItem(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclClusterItem> getAccessClusterEffective(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "cluster_effective", PlatformSql.view("cluster_effective"),
		                  r -> new AclClusterItem(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclDriftEntry> getAccessDrift(DBRProgressMonitor monitor) throws DBException {
		try {
			return accessRows(monitor, "drift", PlatformSql.view("drift"), r -> new AclDriftEntry(this, this, r));
		} catch (DBException e) {
			// a standalone node has no profile to drift from (duckdb-acl spec 118.2 refuses the view there): nothing
			if (String.valueOf(e.getMessage()).contains("this node is standalone")) {
				return List.of();
			}
			throw e;
		}
	}

	@Override
	public org.jkiss.dbeaver.model.struct.DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
		synchronized (this) {
			access.clear();
		}
		return super.refreshObject(monitor);
	}

	private ConsoleInfo readConsole(DBRProgressMonitor monitor) {
		try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Read the console's rights");
		     JDBCPreparedStatement statement = session.prepareStatement(CONSOLE_SQL);
		     JDBCResultSet rs = statement.executeQuery()) {
			if (rs.next()) {
				return new ConsoleInfo(strings(rs.getObject(1)), strings(rs.getObject(2)), strings(rs.getObject(3)),
				                       strings(rs.getObject(4)));
			}
		} catch (Exception e) {
			log.debug("duckdb-acl: no console info (" + e.getMessage() + ")");
		}
		return null;
	}

	/** A VARCHAR[] as the driver answers it (a java.sql.Array, a list or an array) as a set of strings. */
	static Set<String> strings(Object value) throws SQLException {
		Object[] items = value instanceof Array array ? (Object[]) array.getArray()
		                 : value instanceof Collection<?> list   ? list.toArray()
		                 : value instanceof Object[] plain        ? plain
		                                                          : new Object[0];
		Set<String> out = new LinkedHashSet<>();
		for (Object item : items) {
			if (item != null) {
				out.add(item.toString());
			}
		}
		return out;
	}
}
