package org.jkiss.dbeaver.ext.acl.model.edit;

import java.util.Map;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.ext.acl.model.access.AclCatalogGrant;
import org.jkiss.dbeaver.ext.acl.model.access.AclPlatformGrant;
import org.jkiss.dbeaver.ext.acl.model.access.AclAdminScope;
import org.jkiss.dbeaver.ext.acl.model.access.AclRoleGroup;
import org.jkiss.dbeaver.ext.acl.model.access.AclResourceGroup;
import org.jkiss.dbeaver.ext.acl.model.access.AclFunctionGrant;
import org.jkiss.dbeaver.ext.acl.model.access.AclCategoryMember;
import org.jkiss.dbeaver.ext.acl.model.access.AclFunctionCategory;
import org.jkiss.dbeaver.ext.acl.model.access.AclRoleMapping;
import org.jkiss.dbeaver.ext.acl.model.access.AclClient;
import org.jkiss.dbeaver.ext.acl.model.access.AclIssuer;
import org.jkiss.dbeaver.ext.acl.model.access.AclFunctionEntry;
import org.jkiss.dbeaver.ext.acl.model.access.AclReferenceEntry;
import org.jkiss.dbeaver.ext.acl.model.access.AclVirtualSchema;
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

	public static class Schemas extends AclObjectManager<AclVirtualSchema> {
		@Override
		protected AclVirtualSchema make(DBSObject container) {
			return new AclVirtualSchema(dataSource(container), container, Map.of());
		}
	}

	public static class Functions extends AclObjectManager<AclFunctionEntry> {
		@Override
		protected AclFunctionEntry make(DBSObject container) {
			return new AclFunctionEntry(dataSource(container), container, Map.of());
		}
	}

	public static class References extends AclObjectManager<AclReferenceEntry> {
		@Override
		protected AclReferenceEntry make(DBSObject container) {
			return new AclReferenceEntry(dataSource(container), container, Map.of());
		}
	}

	public static class Issuers extends AclObjectManager<AclIssuer> {
		@Override
		protected AclIssuer make(DBSObject container) {
			return new AclIssuer(dataSource(container), container, Map.of());
		}
	}

	public static class Clients extends AclObjectManager<AclClient> {
		@Override
		protected AclClient make(DBSObject container) {
			return new AclClient(dataSource(container), container, Map.of());
		}
	}

	public static class RoleMappings extends AclObjectManager<AclRoleMapping> {
		@Override
		protected AclRoleMapping make(DBSObject container) {
			return new AclRoleMapping(dataSource(container), container, Map.of());
		}
	}

	public static class FunctionCategorys extends AclObjectManager<AclFunctionCategory> {
		@Override
		protected AclFunctionCategory make(DBSObject container) {
			return new AclFunctionCategory(dataSource(container), container, Map.of());
		}
	}

	public static class CategoryMembers extends AclObjectManager<AclCategoryMember> {
		@Override
		protected AclCategoryMember make(DBSObject container) {
			return new AclCategoryMember(dataSource(container), container, Map.of());
		}
	}

	public static class FunctionGrants extends AclObjectManager<AclFunctionGrant> {
		@Override
		protected AclFunctionGrant make(DBSObject container) {
			return new AclFunctionGrant(dataSource(container), container, Map.of());
		}
	}

	public static class ResourceGroups extends AclObjectManager<AclResourceGroup> {
		@Override
		protected AclResourceGroup make(DBSObject container) {
			return new AclResourceGroup(dataSource(container), container, Map.of());
		}
	}

	public static class RoleGroups extends AclObjectManager<AclRoleGroup> {
		@Override
		protected AclRoleGroup make(DBSObject container) {
			return new AclRoleGroup(dataSource(container), container, Map.of());
		}
	}

	public static class AdminScopes extends AclObjectManager<AclAdminScope> {
		@Override
		protected AclAdminScope make(DBSObject container) {
			return new AclAdminScope(dataSource(container), container, Map.of());
		}
	}

	public static class PlatformGrants extends AclObjectManager<AclPlatformGrant> {
		@Override
		protected AclPlatformGrant make(DBSObject container) {
			return new AclPlatformGrant(dataSource(container), container, Map.of());
		}
	}
}
