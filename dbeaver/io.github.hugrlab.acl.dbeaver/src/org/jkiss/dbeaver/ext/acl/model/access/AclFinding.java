package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A finding of the catalog check (platform.check_catalog, spec 039). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclFinding extends AclPlatformObject implements Operable {
	public AclFinding(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "object";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return text("problem") + ": " + (text("object") == null ? "" : text("object"))
		    + (text("role") == null ? "" : " (" + text("role") + ")");
	}

	@Property(viewable = true, order = 2, name = "Kind")
	public String getKind() {
		return text("kind");
	}

	@Property(viewable = true, order = 3, name = "Role")
	public String getRole() {
		return text("role");
	}

	@Property(viewable = true, order = 4, name = "Problem")
	public String getProblem() {
		return text("problem");
	}

	@Property(viewable = true, order = 5, name = "Detail")
	public String getDetail() {
		return text("detail");
	}

	@Property(viewable = true, order = 6, name = "Repair")
	public String getRepair() {
		return text("repair");
	}

	/** The object it is about, as the catalog names it. */
	public String object() {
		return text("object");
	}

	/** The repair's statements one may run (spec 008.2): the node's own, split at its alternatives. */
	public java.util.List<String> repairs() {
		String vcat = text("vcat");
		String object = text("object");
		return RepairStatements.alternatives(getRepair(), vcat == null || object == null ? null
		                                                                                  : AclGrammar.inCatalog(vcat, object));
	}

	@Override
	public List<String> operation(String kind) {
		return "repair".equals(kind) ? repairs() : List.of();
	}

	@Override
	public String operationTitle(String kind) {
		return "Repair";
	}

	@Override
	public String operationExplanation(String kind) {
		return "The node's repair for " + getName() + ". Choose one, fill in what is in <…>, and run it: it changes the "
		       + "policy (the finding is checked again after).";
	}

	@Override
	public String operationFunction(String kind) {
		return null; // the repair is any of the grammar's statements: the node judges it
	}
}
