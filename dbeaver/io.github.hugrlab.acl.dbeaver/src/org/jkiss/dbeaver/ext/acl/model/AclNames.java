package org.jkiss.dbeaver.ext.acl.model;

import java.util.ArrayList;
import java.util.List;

import org.jkiss.dbeaver.ext.generic.model.GenericCatalog;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;
import org.jkiss.dbeaver.ext.generic.model.GenericSchema;
import org.jkiss.dbeaver.model.DBUtils;

/**
 * The SQL name of an object: the catalog, each part of the schema's path, the object - each quoted on its own.
 * DBeaver quotes the schema's name whole (`sales."raw.eu".events`), which is a one-part schema named `raw.eu` to the
 * node (duckdb-acl spec 116), not the nested `raw.eu`.
 */
public final class AclNames {
	private AclNames() {
	}

	public static String qualified(GenericDataSource dataSource, GenericCatalog catalog, GenericSchema schema,
	                               String name) {
		List<String> parts = new ArrayList<>();
		if (catalog != null) {
			parts.add(DBUtils.getQuotedIdentifier(dataSource, catalog.getName()));
		}
		if (schema != null && !DBUtils.isVirtualObject(schema)) {
			parts.addAll(schemaParts(dataSource, schema.getName()));
		}
		if (name != null) {
			parts.add(DBUtils.getQuotedIdentifier(dataSource, name));
		}
		return String.join(".", parts);
	}

	/** A schema path's parts, each quoted as needed; a path that does not parse is one part. */
	static List<String> schemaParts(GenericDataSource dataSource, String path) {
		List<String> raw = SchemaPath.parts(path);
		List<String> out = new ArrayList<>();
		for (String part : raw == null ? List.of(path) : raw) {
			out.add(DBUtils.getQuotedIdentifier(dataSource, part));
		}
		return out;
	}
}
