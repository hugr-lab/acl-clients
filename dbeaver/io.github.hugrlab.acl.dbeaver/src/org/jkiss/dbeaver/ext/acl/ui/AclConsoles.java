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
 * copy of the same connection, kind and role is reused.
 */
public final class AclConsoles {
	static final String OF = "acl.console.of";
	static final String KIND = "acl.console.kind";
	static final String ROLE = "acl.console.role";

	private AclConsoles() {
	}

	/** A URL without its `catalog` / `schema` query parameters (a view-as session's catalog is the role's). */
	static String withoutStart(String url) {
		int q = url.indexOf('?');
		if (q < 0) {
			return url;
		}
		StringBuilder kept = new StringBuilder();
		for (String pair : url.substring(q + 1).split("&")) {
			String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
			if (pair.isEmpty() || key.equalsIgnoreCase("catalog") || key.equalsIgnoreCase("schema")) {
				continue;
			}
			kept.append(kept.length() == 0 ? "" : "&").append(pair);
		}
		return kept.length() == 0 ? url.substring(0, q) : url.substring(0, q + 1) + kept;
	}

	public static DataSourceDescriptor open(DataSourceDescriptor original, ConsoleCopy spec) throws DBException {
		DBPDataSourceRegistry registry = original.getRegistry();
		String role = spec.viewAs() == null ? "" : spec.viewAs();
		for (DBPDataSourceContainer each : registry.getDataSources()) {
			// the copy of THIS connection, for this kind and role (provider properties - never sent to the driver)
			DBPConnectionConfiguration other = each.getConnectionConfiguration();
			if (each instanceof DataSourceDescriptor existing && existing.isTemporary()
			    && original.getId().equals(other.getProviderProperty(OF)) && spec.mode().equals(
			        other.getProviderProperty(KIND)) && role.equals(other.getProviderProperty(ROLE))) {
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
		configuration.setProviderProperty(OF, original.getId());
		configuration.setProviderProperty(KIND, spec.mode());
		configuration.setProviderProperty(ROLE, role);
		configuration.setProperty("acl.mode", spec.mode());
		if (spec.viewAs() != null) {
			configuration.setProperty("acl.viewAs", spec.viewAs());
			// the role's catalog: the driver takes no catalog / schema with it, and the mode refuses a USE
			configuration.removeProperty("catalog");
			configuration.removeProperty("schema");
			if (configuration.getUrl() != null) {
				configuration.setUrl(withoutStart(configuration.getUrl()));
			}
			configuration.getBootstrap().setDefaultCatalogName(null);
			configuration.getBootstrap().setDefaultSchemaName(null);
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
