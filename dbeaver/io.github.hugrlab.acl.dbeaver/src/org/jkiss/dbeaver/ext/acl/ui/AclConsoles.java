package org.jkiss.dbeaver.ext.acl.ui;

import java.util.List;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.ConsoleCopy;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.app.DBPDataSourceRegistry;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.runtime.DBWorkbench;

/**
 * A console's connection: a copy of the user's (its URL, sign-in, settings) with what {@link ConsoleCopy} changes, kept
 * for the session only (temporary - never saved, gone at restart) beside the user's in the navigator. Asked again, the
 * copy already there is reused.
 */
public final class AclConsoles {
	private AclConsoles() {
	}

	public static DataSourceDescriptor open(DataSourceDescriptor original, ConsoleCopy spec) throws DBException {
		DBPDataSourceRegistry registry = original.getRegistry();
		for (DBPDataSourceContainer each : registry.getDataSources()) {
			if (each instanceof DataSourceDescriptor existing && existing.isTemporary()
			    && spec.name().equals(existing.getName())) {
				return existing;
			}
		}
		DataSourceDescriptor copy = new DataSourceDescriptor(original, registry);
		copy.setId(DataSourceDescriptor.generateNewId(original.getDriver()));
		copy.setName(spec.name());
		copy.setTemporary(true);
		if (spec.readOnly()) {
			copy.setConnectionReadOnly(true);
		}
		DBPConnectionConfiguration configuration = copy.getConnectionConfiguration();
		configuration.setProperty("acl.mode", spec.mode());
		if (spec.viewAs() != null) {
			configuration.setProperty("acl.viewAs", spec.viewAs());
		} else {
			configuration.removeProperty("acl.viewAs");
		}
		if (spec.dropInitQueries()) {
			configuration.getBootstrap().setInitQueries(List.of());
		}
		if (spec.autoCommit() != null) {
			configuration.getBootstrap().setDefaultAutoCommit(spec.autoCommit());
		}
		if (spec.connectionType() != null) {
			configuration.setConnectionType(DBWorkbench.getPlatform().getDataSourceProviderRegistry()
			                                    .getConnectionType(spec.connectionType(), configuration.getConnectionType()));
			configuration.setConnectionColor(null); // the type's colour is the marker, not the user's own
		}
		registry.addDataSource(copy);
		return copy;
	}
}
