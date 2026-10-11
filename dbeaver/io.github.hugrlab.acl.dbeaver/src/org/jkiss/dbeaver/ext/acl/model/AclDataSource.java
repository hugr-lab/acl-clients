package org.jkiss.dbeaver.ext.acl.model;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;
import org.jkiss.dbeaver.ext.generic.model.meta.GenericMetaModel;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

/**
 * A connection to a node: Generic's, plus what `platform.console_info()` answered at connect (spec 007.2) - read once,
 * on a connection in data mode; a principal with no administration is refused it (one recorded denial per connect),
 * and then no console action is shown.
 */
public class AclDataSource extends GenericDataSource {
	private static final Log log = Log.getLog(AclDataSource.class);
	static final String CONSOLE_SQL = "SELECT bundles, admin_catalogs, my_views, my_functions FROM platform.console_info()";

	private volatile ConsoleInfo console;

	public AclDataSource(@NotNull DBRProgressMonitor monitor, @NotNull DBPDataSourceContainer container,
	                     @NotNull GenericMetaModel metaModel) throws DBException {
		super(monitor, container, metaModel, new AclSQLDialect());
	}

	@Override
	public void initialize(@NotNull DBRProgressMonitor monitor) throws DBException {
		super.initialize(monitor);
		String mode = getContainer().getConnectionConfiguration().getProperty("acl.mode");
		console = mode == null || mode.isEmpty() || "data".equals(mode) ? readConsole(monitor) : null;
	}

	/** What console_info answered at connect; null when it was not read or was refused. */
	@Nullable
	public ConsoleInfo console() {
		return console;
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
