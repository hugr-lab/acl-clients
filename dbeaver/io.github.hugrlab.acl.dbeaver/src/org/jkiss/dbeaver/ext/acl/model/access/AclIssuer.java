package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A token issuer (platform.issuers, duckdb-acl spec 095). Read: one property per column of its view (spec 007.3).
 * Written (spec 008.2): `CREATE ISSUER n URL '…' [FROM SECRET s [IN c]]`, `ALTER ISSUER n SET URL '…'`, `SET FROM
 * SECRET s [IN c]` / `DROP FROM SECRET`, `DROP ISSUER n` (with its implicit client and mappings). No key here: keys come
 * from the issuer's discovery or the named secret of the secrets service.
 */
public class AclIssuer extends AclPlatformObject {
	public AclIssuer(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
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

	@Property(viewable = true, editable = true, updatable = true, order = 2, name = "Url",
	          description = "The issuer's URL: a token's iss routes to it; its keys by OIDC discovery under it")
	public String getUrl() {
		return edited("url", text("url"));
	}

	public void setUrl(String url) {
		edit("url", url);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 3, name = "Secret",
	          description = "An oidc_issuer secret of the secrets service (its keys), by name - never its content")
	public String getSecret() {
		return edited("secret", text("secret"));
	}

	public void setSecret(String secret) {
		edit("secret", secret);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 4, name = "Secret service",
	          description = "The secrets service the secret is in; empty = the one attached")
	public String getSecretService() {
		return edited("secretService", text("secret_service"));
	}

	public void setSecretService(String service) {
		edit("secretService", service);
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("define_issuer");
			case EDIT -> Set.of("alter_issuer");
			case DELETE -> Set.of("drop_issuer");
		};
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("issuers", "name", getName());
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) {
		return switch (id) {
			case "url" -> text("url");
			case "secret" -> text("secret");
			case "secretService" -> text("secret_service");
			default -> null;
		};
	}

	static String fromSecret(String secret, String service) {
		return "FROM SECRET " + AclGrammar.ident(secret.trim())
		       + (AclGrammar.isBlank(service) ? "" : " IN " + AclGrammar.ident(service.trim()));
	}

	@Override
	public List<String> createStatements() {
		StringBuilder sql = new StringBuilder("CREATE ISSUER ").append(AclGrammar.ident(getName()));
		if (!AclGrammar.isBlank(getUrl())) {
			sql.append(" URL ").append(AclGrammar.literal(getUrl().trim()));
		}
		if (!AclGrammar.isBlank(getSecret())) {
			sql.append(' ').append(fromSecret(getSecret(), getSecretService()));
		}
		return List.of(sql.toString());
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) {
		String alter = "ALTER ISSUER " + AclGrammar.ident(getName());
		List<String> out = new ArrayList<>();
		if (changed.contains("url")) {
			out.add(alter + " SET URL " + AclGrammar.literal(getUrl() == null ? "" : getUrl().trim()));
		}
		if (changed.contains("secret") || changed.contains("secretService")) {
			out.add(AclGrammar.isBlank(getSecret()) ? alter + " DROP FROM SECRET"
			                                        : alter + " SET " + fromSecret(getSecret(), getSecretService()));
		}
		return out;
	}

	@Override
	public List<String> dropStatements() {
		return List.of("DROP ISSUER " + AclGrammar.ident(getName()));
	}

	@Association
	public List<AclClient> getClients(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "Clients", PlatformSql.view("clients", "issuer", getName()), r -> new AclClient(dataSource, this, r));
	}
}
