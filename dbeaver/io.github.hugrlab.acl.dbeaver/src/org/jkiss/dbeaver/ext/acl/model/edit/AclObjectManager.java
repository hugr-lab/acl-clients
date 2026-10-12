package org.jkiss.dbeaver.ext.acl.model.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.ext.acl.model.access.AclPlatformObject;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.edit.DBECommand;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.SQLObjectEditor;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;
import org.jkiss.dbeaver.runtime.DBWorkbench;

/**
 * How DBeaver writes an object of the Access control branch (spec 008.1): its own create / edit / delete and Save
 * (which shows the script first), the statements always the object's own composition in the management grammar - the
 * preview is what runs. The node decides; this only offers what can work (a connection that is not read-only - a
 * view-as copy is - and that read console_info) and reports honestly:
 * - before a save of a stored object, a property the user changed whose value on the node moved meanwhile is shown
 *   and asked about (best effort, on the client - spec 008 section 3);
 * - statements run one at a time in auto-commit; at the first refusal the error says which ran before it (a
 *   management batch is not atomic on the node, and a management write is not part of a client transaction).
 */
public abstract class AclObjectManager<T extends AclPlatformObject> extends SQLObjectEditor<T, DBSObject> {
	/** What each command has run so far, for the error of the statement that fails after them. */
	private final Map<DBECommand<?>, List<String>> applied = Collections.synchronizedMap(new WeakHashMap<>());

	/** A new object under its parent (the folder's owner): the role a grant is to, the catalog an object is in. */
	protected abstract T make(DBSObject container);

	@Override
	public long getMakerOptions(@NotNull DBPDataSource dataSource) {
		return FEATURE_EDITOR_ON_CREATE;
	}

	@Nullable
	@Override
	public DBSObjectCache<? extends DBSObject, T> getObjectsCache(T object) {
		return null; // the branch keeps its lists itself; DBeaver adds and removes the node
	}

	static boolean writable(DBPDataSource dataSource) {
		return dataSource instanceof AclDataSource acl && acl.isAccessControl()
		       && !acl.getContainer().isConnectionReadOnly();
	}

	@Override
	public boolean canCreateObject(@NotNull Object container) {
		DBSObject owner = owner(container);
		return owner != null && writable(owner.getDataSource()) && super.canCreateObject(container);
	}

	@Override
	public boolean canEditObject(@NotNull T object) {
		return object.isEditable() && writable(object.getDataSource()) && super.canEditObject(object);
	}

	@Override
	public boolean canDeleteObject(@NotNull T object) {
		return object.isEditable() && writable(object.getDataSource()) && super.canDeleteObject(object);
	}

	@Nullable
	static DBSObject owner(Object container) {
		if (container instanceof DBNDatabaseNode node) {
			return node.getObject();
		}
		return container instanceof DBSObject object ? object : null;
	}

	@Override
	protected T createDatabaseObject(@NotNull DBRProgressMonitor monitor, @NotNull DBECommandContext context,
	                                 @NotNull Object container, @Nullable Object copyFrom,
	                                 @NotNull Map<String, Object> options) throws DBException {
		DBSObject owner = owner(container);
		if (owner == null) {
			throw new DBException("duckdb-acl: nothing to create it in");
		}
		T made = make(owner);
		made.markCreating();
		return made;
	}

	private static void add(List<DBEPersistAction> actions, String title, List<String> statements) {
		for (String statement : statements) {
			actions.add(new SQLDatabasePersistAction(title, statement));
		}
	}

	@Override
	protected void addObjectCreateActions(@NotNull DBRProgressMonitor monitor, @NotNull DBCExecutionContext executionContext,
	                                      @NotNull List<DBEPersistAction> actions, @NotNull ObjectCreateCommand command,
	                                      @NotNull Map<String, Object> options) {
		add(actions, "Create " + command.getObject().getName(), command.getObject().createStatements());
	}

	@Override
	protected void addObjectModifyActions(@NotNull DBRProgressMonitor monitor, @NotNull DBCExecutionContext executionContext,
	                                      @NotNull List<DBEPersistAction> actions, @NotNull ObjectChangeCommand command,
	                                      @NotNull Map<String, Object> options) throws DBException {
		T object = command.getObject();
		if (object.isCreating()) {
			return; // a new object's properties are in its create statement
		}
		add(actions, "Change " + object.getName(), object.alterStatements(monitor, changed(command)));
	}

	@Override
	protected void addObjectDeleteActions(@NotNull DBRProgressMonitor monitor, @NotNull DBCExecutionContext executionContext,
	                                      @NotNull List<DBEPersistAction> actions, @NotNull ObjectDeleteCommand command,
	                                      @NotNull Map<String, Object> options) {
		add(actions, "Drop " + command.getObject().getName(), command.getObject().dropStatements());
	}

	private Set<String> changed(ObjectChangeCommand command) {
		Set<String> ids = new LinkedHashSet<>();
		for (Object id : command.getProperties().keySet()) {
			ids.add(String.valueOf(id));
		}
		return ids;
	}

	@Override
	protected void validateObjectProperties(@NotNull DBRProgressMonitor monitor, @NotNull ObjectChangeCommand command,
	                                        @NotNull Map<String, Object> options) throws DBException {
		options.put(DBECommandContext.OPTION_AVOID_TRANSACTIONS, true);
		T object = command.getObject();
		if (object.isCreating() || !object.isPersisted()) {
			return;
		}
		Map<String, String[]> moved = object.changedOnTheNode(monitor, changed(command));
		if (moved.isEmpty()) {
			return;
		}
		StringBuilder said = new StringBuilder();
		if (moved.containsKey("")) {
			throw new DBException("duckdb-acl: " + object.getName() + " is no longer on the node - refresh the branch");
		}
		for (Map.Entry<String, String[]> each : moved.entrySet()) {
			said.append("\n  ").append(each.getKey()).append(": was ").append(shown(each.getValue()[0])).append(", now ")
			    .append(shown(each.getValue()[1]));
		}
		boolean overwrite = DBWorkbench.getPlatformUI().confirmAction("Changed on the node",
		    object.getName() + " was changed on the node since it was opened:" + said
		        + "\n\nSave your values over them anyway?");
		if (!overwrite) {
			throw new DBException("duckdb-acl: not saved - " + object.getName()
			                      + " was changed on the node meanwhile; refresh it and edit again");
		}
	}

	private static String shown(String value) {
		return value == null || value.isEmpty() ? "(empty)" : "'" + value + "'";
	}

	@Override
	public void executePersistAction(@NotNull DBCSession session, @NotNull DBECommand<T> command,
	                                 @NotNull DBEPersistAction action) throws DBException {
		List<String> before = applied.computeIfAbsent(command, c -> new ArrayList<>());
		try {
			super.executePersistAction(session, command, action);
		} catch (DBException e) {
			if (before.isEmpty()) {
				throw e;
			}
			throw new DBException(e.getMessage() + "\n\nApplied before it (a management batch is not atomic):\n  "
			                      + String.join(";\n  ", before), e);
		}
		before.add(action.getScript());
	}
}
