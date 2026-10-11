package org.jkiss.dbeaver.ext.acl.ui;

import java.util.ArrayList;
import java.util.List;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.ConsoleCopy;
import org.jkiss.dbeaver.model.app.DBPDataSourceRegistry;
import org.jkiss.dbeaver.model.connection.DBPConnectionBootstrap;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.runtime.DBWorkbench;

/**
 * A console's connection: a copy of the user's (its URL, sign-in, settings) with what {@link ConsoleCopy} changes, kept
 * for the session only (temporary - never saved, gone at restart) beside the user's in the navigator.
 */
public final class AclConsoles {
	private AclConsoles() {
	}

	public static DataSourceDescriptor open(DataSourceDescriptor original, ConsoleCopy spec) throws DBException {
		DBPDataSourceRegistry registry = original.getRegistry();
		DataSourceDescriptor copy = new DataSourceDescriptor(original, registry);
		copy.setId(DataSourceDescriptor.generateNewId(original.getDriver()));
		copy.setName(spec.name());
		copy.setTemporary(true);
		if (spec.readOnly()) {
			copy.setConnectionReadOnly(true);
		}
		DBPConnectionConfiguration configuration = copy.getConnectionConfiguration();
		configuration.setProperty("acl.mode", spec.mode());
		if (!spec.initQueries().isEmpty()) {
			DBPConnectionBootstrap bootstrap = configuration.getBootstrap();
			List<String> queries = new ArrayList<>(bootstrap.getInitQueries());
			queries.addAll(spec.initQueries());
			bootstrap.setInitQueries(queries);
		}
		if (spec.connectionType() != null) {
			configuration.setConnectionType(DBWorkbench.getPlatform().getDataSourceProviderRegistry()
			                                    .getConnectionType(spec.connectionType(), configuration.getConnectionType()));
		}
		registry.addDataSource(copy);
		return copy;
	}
}
