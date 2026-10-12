package org.jkiss.dbeaver.ext.acl.model.edit;

import java.util.Map;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.ext.acl.model.access.AclCatalogGrant;
import org.jkiss.dbeaver.ext.acl.model.access.AclObjectGrant;
import org.jkiss.dbeaver.ext.acl.model.access.AclRelation;
import org.jkiss.dbeaver.ext.acl.model.access.AclSchemaGrant;
import org.jkiss.dbeaver.ext.acl.model.access.AclVirtualCatalog;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** The editable classes of the branch besides roles (spec 008.1): each made under the object its folder belongs to. */
final class AclManagers {
	private AclManagers() {
	}

	static AclDataSource dataSource(DBSObject container) {
		return (AclDataSource) container.getDataSource();
	}

	public static class Catalogs extends AclObjectManager<AclVirtualCatalog> {
		@Override
		protected AclVirtualCatalog make(DBSObject container) {
			return new AclVirtualCatalog(dataSource(container), container, Map.of());
		}
	}

	public static class Relations extends AclObjectManager<AclRelation> {
		@Override
		protected AclRelation make(DBSObject container) {
			return new AclRelation(dataSource(container), container, Map.of());
		}
	}

	public static class CatalogGrants extends AclObjectManager<AclCatalogGrant> {
		@Override
		protected AclCatalogGrant make(DBSObject container) {
			return new AclCatalogGrant(dataSource(container), container, Map.of());
		}
	}

	public static class SchemaGrants extends AclObjectManager<AclSchemaGrant> {
		@Override
		protected AclSchemaGrant make(DBSObject container) {
			return new AclSchemaGrant(dataSource(container), container, Map.of());
		}
	}

	public static class ObjectGrants extends AclObjectManager<AclObjectGrant> {
		@Override
		protected AclObjectGrant make(DBSObject container) {
			return new AclObjectGrant(dataSource(container), container, Map.of());
		}
	}
}
