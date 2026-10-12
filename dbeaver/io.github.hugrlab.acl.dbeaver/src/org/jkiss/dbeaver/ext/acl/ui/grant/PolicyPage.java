package org.jkiss.dbeaver.ext.acl.ui.grant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.ext.acl.model.access.AclPlatformObject;
import org.jkiss.dbeaver.ext.acl.model.access.AclPolicyGrant;
import org.jkiss.dbeaver.ext.acl.model.access.PlatformValues;
import org.jkiss.dbeaver.model.runtime.AbstractJob;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.runtime.properties.PropertySourceEditable;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.editors.AbstractDatabaseObjectEditor;

/**
 * A tab of a policy object's editor - a grant's (spec 008.1 addendum) or a virtual table's (spec 008.2) Row filter and
 * Columns: it edits the object's property through the editor's own command context - what the properties panel does,
 * so Save previews and writes it - and reads the node off the UI thread.
 */
public abstract class PolicyPage extends AbstractDatabaseObjectEditor<AclPlatformObject> {
	private PropertySourceEditable source;
	protected boolean loaded;

	protected AclPolicyGrant grant() {
		return (AclPolicyGrant) getDatabaseObject();
	}

	/**
	 * One property, as the panel sets it - a command in the editor's context (consecutive edits of one property make
	 * one). By its descriptor, not its id: the tabs' properties are hidden from the panel, and DBeaver's by-id setter
	 * only knows the properties it shows.
	 */
	protected void set(String id, Object value) {
		set(getEditorInput().getCommandContext(), getDatabaseObject(), id, value);
	}

	private PropertySourceEditable sourceFor(org.jkiss.dbeaver.model.edit.DBECommandContext context, Object object) {
		if (source == null) {
			source = new PropertySourceEditable(context, object, object);
		}
		return source;
	}

	/** {@link #set} for any object of the branch - also what the tests use. */
	public void set(org.jkiss.dbeaver.model.edit.DBECommandContext context, Object object, String id, Object value) {
		PropertySourceEditable editable = sourceFor(context, object);
		editable.setPropertyValue(new VoidProgressMonitor(), object, descriptor(editable, object, id), value);
	}

	public static org.jkiss.dbeaver.runtime.properties.ObjectPropertyDescriptor descriptor(
	    PropertySourceEditable source, Object object, String id) {
		for (org.jkiss.dbeaver.runtime.properties.ObjectPropertyDescriptor each :
		     org.jkiss.dbeaver.runtime.properties.ObjectAttributeDescriptor.extractAnnotations(source, object.getClass(),
		                                                                                     null, null, false)) {
			if (each.getId().equals(id)) {
				return each;
			}
		}
		throw new IllegalArgumentException("no property " + id + " on " + object.getClass().getSimpleName());
	}

	/** The object's columns as the node answers them (name, type); empty when it cannot (a new grant, no object). */
	protected void loadColumns(Consumer<List<String[]>> then, Consumer<String> failed) {
		ask("Read the object's columns", "", "", rows -> {
			List<String[]> columns = new ArrayList<>();
			String refused = null;
			for (Map<String, Object> row : rows) {
				if ("column".equals(text(row, "item"))) {
					columns.add(new String[] {text(row, "name"), text(row, "type")});
				} else if ("refused".equals(text(row, "status"))) {
					refused = text(row, "message");
				}
			}
			if (refused != null && columns.isEmpty()) {
				failed.accept(refused);
			} else {
				then.accept(columns);
			}
		}, failed);
	}

	/** Something read from the node in the background; the answer (or the failure's text) on the UI thread. */
	protected <T> void inBackground(String title, Read<T> read, Consumer<T> then, Consumer<String> failed) {
		new AbstractJob(title) {
			@NotNull
			@Override
			protected IStatus run(@NotNull DBRProgressMonitor monitor) {
				try {
					T value = read.read(monitor);
					UIUtils.asyncExec(() -> then.accept(value));
				} catch (Exception e) {
					UIUtils.asyncExec(() -> failed.accept(e.getMessage()));
				}
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	public interface Read<T> {
		T read(DBRProgressMonitor monitor) throws Exception;
	}

	/** platform.check_grant with these values, in the background; the answer on the UI thread. */
	protected void ask(String title, String rls, String columns, Consumer<List<Map<String, Object>>> then,
	                   Consumer<String> failed) {
		new AbstractJob(title) {
			@NotNull
			@Override
			protected IStatus run(@NotNull DBRProgressMonitor monitor) {
				try {
					List<Map<String, Object>> rows = grant().check(monitor, rls, columns);
					UIUtils.asyncExec(() -> then.accept(rows));
				} catch (Exception e) {
					UIUtils.asyncExec(() -> failed.accept(e.getMessage()));
				}
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	static String text(Map<String, Object> row, String key) {
		return PlatformValues.text(row.get(key));
	}

	@Override
	public void setFocus() {
	}

	/** Whether this principal may change the grant (its object manager's answer - the node judges regardless). */
	@SuppressWarnings("unchecked")
	protected boolean editable() {
		org.jkiss.dbeaver.model.edit.DBEObjectEditor<AclPlatformObject> manager =
		    org.jkiss.dbeaver.runtime.DBWorkbench.getPlatform().getEditorsRegistry()
		        .getObjectManager(getDatabaseObject().getClass(), org.jkiss.dbeaver.model.edit.DBEObjectEditor.class);
		return manager != null && manager.canEditObject(getDatabaseObject());
	}

	@Override
	public void activatePart() {
		// a new grant's object is chosen on the Properties tab: read its columns again each time the tab is shown
		if (!loaded || !getDatabaseObject().isPersisted()) {
			loaded = true;
			load();
		}
	}

	/** Read what the tab shows (the object's columns, the grant's current values). */
	protected abstract void load();

	@Override
	public org.jkiss.dbeaver.ui.IRefreshablePart.RefreshResult refreshPart(Object source, boolean force) {
		loaded = false;
		UIUtils.asyncExec(this::activatePart);
		return org.jkiss.dbeaver.ui.IRefreshablePart.RefreshResult.REFRESHED;
	}
}
