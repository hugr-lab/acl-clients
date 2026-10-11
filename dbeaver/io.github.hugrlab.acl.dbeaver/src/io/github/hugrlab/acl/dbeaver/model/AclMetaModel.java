package io.github.hugrlab.acl.dbeaver.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.generic.model.GenericCatalog;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;
import org.jkiss.dbeaver.ext.generic.model.GenericSchema;
import org.jkiss.dbeaver.ext.generic.model.meta.GenericMetaModel;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;

/**
 * The tree of a duckdb-acl node: what the driver's DatabaseMetaData answers (spec 006) - nothing decided here. The
 * driver lists a nested schema by its path (`raw`, `raw.eu`); the catalog keeps the top level and each schema its
 * children, so the tree nests them (spec 007.1).
 */
public class AclMetaModel extends GenericMetaModel {
	@Override
	public GenericSchema createSchemaImpl(@NotNull GenericDataSource dataSource, @Nullable GenericCatalog catalog,
	                                      @NotNull String schemaName) throws DBException {
		return new AclSchema(dataSource, catalog, schemaName);
	}

	@Nullable
	@Override
	public List<GenericSchema> loadSchemas(@NotNull JDBCSession session, @NotNull GenericDataSource dataSource,
	                                       @Nullable GenericCatalog catalog) throws DBException {
		List<GenericSchema> all = super.loadSchemas(session, dataSource, catalog);
		return all == null ? null : nest(all);
	}

	/** The top-level schemas, each nested one added to its parent (one whose parent is not listed stays on top). */
	static List<GenericSchema> nest(List<GenericSchema> all) {
		Map<String, AclSchema> byPath = new HashMap<>();
		for (GenericSchema schema : all) {
			if (schema instanceof AclSchema acl) {
				byPath.put(acl.getName(), acl);
			}
		}
		List<GenericSchema> top = new ArrayList<>();
		for (GenericSchema schema : all) {
			String parent = SchemaPath.parent(schema.getName());
			AclSchema holder = parent == null ? null : byPath.get(parent);
			if (holder != null && schema instanceof AclSchema child) {
				holder.addChild(child);
			} else {
				top.add(schema);
			}
		}
		return top;
	}
}
