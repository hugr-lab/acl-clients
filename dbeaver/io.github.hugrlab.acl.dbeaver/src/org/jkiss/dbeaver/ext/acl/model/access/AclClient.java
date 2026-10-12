package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.IPropertyValueListProvider;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * An issuer's client (platform.clients, duckdb-acl spec 095): which of its tokens count and what they become. Read: one
 * property per column of its view (spec 007.3). Written (spec 008.2): `CREATE CLIENT n ISSUER i <clause> … [FROM
 * SECRET s [IN c]]`; a change is one `ALTER CLIENT n SET <clause>` per changed clause - for the implicit client of a
 * short-form issuer `ALTER ISSUER i SET <clause>` (the node refuses ALTER CLIENT on it) -, a cleared list clause `SET
 * X ()`, a cleared secret `DROP FROM SECRET`; `DROP CLIENT n` (its mappings with it). Each clause is a property in the
 * grammar's own syntax (`REQUIRE (tier = 'gold', 'x' IN groups)`), read back from the view's typed columns. No
 * credential here: a client secret is the secrets service's, named.
 */
public class AclClient extends AclPlatformObject {
	/** The list clauses, by property id: the grammar's keyword and how the view's column reads back. */
	private static final Map<String, String> LIST_CLAUSES = new LinkedHashMap<>();
	static {
		LIST_CLAUSES.put("audiences", "AUDIENCES");
		LIST_CLAUSES.put("azp", "AZP");
		LIST_CLAUSES.put("requires", "REQUIRE");
		LIST_CLAUSES.put("rolesFrom", "ROLES FROM");
		LIST_CLAUSES.put("rolesConstant", "ROLES CONSTANT");
		LIST_CLAUSES.put("attributes", "ATTRIBUTES");
		LIST_CLAUSES.put("subject", "SUBJECT");
		LIST_CLAUSES.put("flows", "FLOWS");
	}

	public AclClient(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "name";
	}

	@Override
	@Property(viewable = true, editable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	/** The issuer it is of: the row's, or - a new client - the issuer it is made under. */
	public String issuer() {
		String issuer = text("issuer");
		if (issuer == null && getParentObject() instanceof AclIssuer parentIssuer) {
			return parentIssuer.getName();
		}
		return issuer;
	}

	public boolean isImplicit() {
		return Boolean.TRUE.equals(row.get("implicit"));
	}

	private String storedClause(String id) {
		return switch (id) {
			case "audiences" -> PlatformValues.quotedList(row.get("audiences"));
			case "azp" -> PlatformValues.quotedList(row.get("azp"));
			case "requires" -> requires(row.get("requires"));
			case "rolesFrom" -> PlatformValues.quotedList(row.get("roles_from"));
			case "rolesConstant" -> PlatformValues.quotedList(row.get("roles_constant"));
			case "attributes" -> attributes(row.get("attributes"));
			case "subject" -> PlatformValues.quotedList(row.get("subject"));
			case "flows" -> PlatformValues.bareList(row.get("flows"));
			case "unmapped" -> text("unmapped") == null ? "" : text("unmapped");
			case "tokenType" -> text("token_type") == null ? "" : text("token_type");
			case "clientId" -> text("client_id") == null ? "" : text("client_id");
			case "secret" -> text("secret") == null ? "" : text("secret");
			case "secretService" -> text("secret_service") == null ? "" : text("secret_service");
			default -> null;
		};
	}

	/** REQUIRE's conditions as the grammar writes them: `tier = 'gold'`, `'x' IN groups`, `region IN ('eu')`, `mail LIKE '%@x'`. */
	static String requires(Object value) {
		List<String> out = new ArrayList<>();
		for (Object item : PlatformValues.items(value)) {
			Map<?, ?> condition = PlatformValues.fieldsOf(item);
			String path = String.valueOf(condition.get("path"));
			String op = String.valueOf(condition.get("op"));
			String values = PlatformValues.quotedList(condition.get("values"));
			out.add(switch (op) {
				case "eq" -> path + " = " + values;
				case "contains" -> values + " IN " + path;
				case "in" -> path + " IN (" + values + ")";
				case "like" -> path + " LIKE " + values;
				default -> path + " " + op + " " + values;
			});
		}
		return String.join(", ", out);
	}

	/** ATTRIBUTES as the grammar writes them: `tenant = 'tenant'`, `units = ('a.b', 'c')`, `fixed = CONSTANT 'v'`. */
	static String attributes(Object value) {
		List<String> out = new ArrayList<>();
		for (Object item : PlatformValues.items(value)) {
			Map<?, ?> attribute = PlatformValues.fieldsOf(item);
			String name = String.valueOf(attribute.get("name"));
			Object constant = attribute.get("constant");
			List<Object> paths = PlatformValues.items(attribute.get("paths"));
			if (constant != null) {
				out.add(name + " = CONSTANT " + AclGrammar.literal(constant.toString()));
			} else if (paths.size() == 1) {
				out.add(name + " = " + AclGrammar.literal(String.valueOf(paths.get(0))));
			} else {
				out.add(name + " = (" + PlatformValues.quotedList(attribute.get("paths")) + ")");
			}
		}
		return String.join(", ", out);
	}

	private String clause(String id) {
		return edited(id, storedClause(id));
	}

	private void setClause(String id, String value) {
		edit(id, value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 2, name = "Audiences", description = "'account', 'api'")
	public String getAudiences() {
		return clause("audiences");
	}

	public void setAudiences(String value) {
		setClause("audiences", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 3, name = "Azp", description = "'acl-desktop'")
	public String getAzp() {
		return clause("azp");
	}

	public void setAzp(String value) {
		setClause("azp", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 4, name = "Require",
	          description = "tier = 'gold', 'admins' IN groups, region IN ('eu', 'us'), mail LIKE '%@corp.com'")
	public String getRequires() {
		return clause("requires");
	}

	public void setRequires(String value) {
		setClause("requires", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 5, name = "Roles from",
	          description = "The claim paths a token's roles are read from: 'realm_access.roles'")
	public String getRolesFrom() {
		return clause("rolesFrom");
	}

	public void setRolesFrom(String value) {
		setClause("rolesFrom", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 6, name = "Roles constant",
	          description = "Roles every token of the client gets: 'reader'")
	public String getRolesConstant() {
		return clause("rolesConstant");
	}

	public void setRolesConstant(String value) {
		setClause("rolesConstant", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 7, name = "Unmapped", listProvider = Unmapped.class,
	          description = "A role value with no mapping: ignore it, or take it as a role of that name")
	public String getUnmapped() {
		return clause("unmapped");
	}

	public void setUnmapped(String value) {
		setClause("unmapped", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 8, name = "Attributes",
	          description = "The claims RLS reads: tenant = 'tenant', units = ('a.b', 'c'), fixed = CONSTANT 'v'")
	public String getAttributes() {
		return clause("attributes");
	}

	public void setAttributes(String value) {
		setClause("attributes", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 9, name = "Subject", description = "'sub'")
	public String getSubject() {
		return clause("subject");
	}

	public void setSubject(String value) {
		setClause("subject", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 10, name = "Token type", description = "JWT")
	public String getTokenType() {
		return clause("tokenType");
	}

	public void setTokenType(String value) {
		setClause("tokenType", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 11, name = "Client id",
	          description = "The OAuth client id a driver signs in as")
	public String getClientId() {
		return clause("clientId");
	}

	public void setClientId(String value) {
		setClause("clientId", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 12, name = "Flows",
	          description = "password, authcode, device, client_credentials (needs a client id)")
	public String getFlows() {
		return clause("flows");
	}

	public void setFlows(String value) {
		setClause("flows", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 13, name = "Secret",
	          description = "An oidc_client secret of the secrets service (audiences, client id, client secret), by name")
	public String getSecret() {
		return clause("secret");
	}

	public void setSecret(String value) {
		setClause("secret", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 14, name = "Secret service")
	public String getSecretService() {
		return clause("secretService");
	}

	public void setSecretService(String value) {
		setClause("secretService", value);
	}

	@Property(viewable = true, order = 15, name = "Implicit", description = "The client a short-form issuer made")
	public String getImplicit() {
		return text("implicit");
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("define_client");
			case EDIT -> Set.of(isImplicit() ? "alter_issuer" : "alter_client");
			case DELETE -> Set.of("drop_client");
		};
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("clients", "name", getName());
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) {
		return storedClause(id);
	}

	/** One clause as the grammar writes it; null when it says nothing. */
	private String clauseSql(String id, boolean clearing) {
		String value = clause(id);
		boolean blank = AclGrammar.isBlank(value);
		if (LIST_CLAUSES.containsKey(id)) {
			return blank ? (clearing ? LIST_CLAUSES.get(id) + " ()" : null) : LIST_CLAUSES.get(id) + " (" + value.trim() + ")";
		}
		return switch (id) {
			case "unmapped" -> blank ? null : "UNMAPPED " + ("as_role".equals(value.trim()) ? "AS ROLE" : "IGNORE");
			case "tokenType" -> blank && !clearing ? null : "TOKEN TYPE " + AclGrammar.literal(blank ? "" : value.trim());
			case "clientId" -> blank && !clearing ? null : "CLIENT ID " + AclGrammar.literal(blank ? "" : value.trim());
			default -> null;
		};
	}

	private static final List<String> ORDER = List.of("audiences", "azp", "requires", "rolesFrom", "rolesConstant",
	                                                  "unmapped", "attributes", "subject", "tokenType", "clientId", "flows");

	@Override
	public List<String> createStatements() {
		StringBuilder sql = new StringBuilder("CREATE CLIENT ").append(AclGrammar.ident(getName())).append(" ISSUER ")
		    .append(AclGrammar.ident(issuer()));
		for (String id : ORDER) {
			String clause = clauseSql(id, false);
			if (clause != null) {
				sql.append(' ').append(clause);
			}
		}
		if (!AclGrammar.isBlank(getSecret())) {
			sql.append(' ').append(AclIssuer.fromSecret(getSecret(), getSecretService()));
		}
		return List.of(sql.toString());
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) {
		String alter = isImplicit() ? "ALTER ISSUER " + AclGrammar.ident(issuer()) : "ALTER CLIENT " + AclGrammar.ident(getName());
		List<String> out = new ArrayList<>();
		for (String id : ORDER) {
			if (changed.contains(id)) {
				String clause = clauseSql(id, true);
				if (clause != null) {
					out.add(alter + " SET " + clause);
				}
			}
		}
		if (changed.contains("secret") || changed.contains("secretService")) {
			out.add(AclGrammar.isBlank(getSecret())
			            ? alter + (isImplicit() ? " DROP CLIENT FROM SECRET" : " DROP FROM SECRET")
			            : alter + " SET " + (isImplicit() ? "CLIENT " : "") + AclIssuer.fromSecret(getSecret(), getSecretService()));
		}
		return out;
	}

	@Override
	public List<String> dropStatements() {
		return List.of("DROP CLIENT " + AclGrammar.ident(getName()));
	}

	public static class Unmapped implements IPropertyValueListProvider<AclClient> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclClient object) {
			return new Object[] {"ignore", "as_role"};
		}
	}
}
