package org.jkiss.dbeaver.ext.acl.ui.grant;

import java.util.List;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.jkiss.dbeaver.ext.acl.model.access.AclObjectGrant;
import org.jkiss.dbeaver.ext.acl.model.access.GrantColumns;
import org.jkiss.dbeaver.ext.acl.model.access.GrantColumns.Node;
import org.jkiss.dbeaver.ext.acl.model.access.GrantColumns.Shown;

/**
 * Columns (spec 008.1 addendum, spec 011 / duckdb-acl spec 158): what the role reads of each column the object exposes
 * - shown, masked (the expression the role reads instead) or hidden - and whether a column the object gains later is
 * shown; the `COLUMNS` test and the `MASK` items it writes under the table, and Check. A catalog grant states no columns:
 * a list an older build stored is shown, to be cleared.
 */
public class GrantColumnsPage extends PolicyPage {
	private static final String[] SHOWN = {"Visible", "Masked", "Hidden"};

	private TableViewer table;
	private Button laterShown;
	private Text written;
	private Text catalogList;
	private Label hint;
	private GrantColumns model;
	private boolean filling;

	@Override
	public void createPartControl(Composite parent) {
		Composite page = new Composite(parent, SWT.NONE);
		page.setLayout(new GridLayout(1, false));
		hint = new Label(page, SWT.WRAP);
		hint.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		if (getDatabaseObject() instanceof AclObjectGrant) {
			hint.setText("What the role reads of each column: Visible, Masked (the expression it reads instead - NULL, "
			             + "'***', left(ssn, 1) || '***' - over the object's columns), Hidden. Nothing hidden or masked "
			             + "= the grant narrows nothing.");
			createTable(page);
			laterShown = new Button(page, SWT.CHECK);
			laterShown.setText("Show columns the object gains later (COLUMNS (* EXCLUDE (…)); unticked: only the "
			                   + "columns shown here)");
			laterShown.addListener(SWT.Selection, e -> {
				if (!filling && model != null) {
					model.setLaterShown(laterShown.getSelection());
					changed();
				}
			});
			new Label(page, SWT.NONE).setText("What the grant writes:");
			written = new Text(page, SWT.BORDER | SWT.READ_ONLY | SWT.MULTI | SWT.WRAP);
			written.setFont(JFaceResources.getTextFont());
			written.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
			new CheckPanel(page, this, () -> new String[] {grant().getRls(), grant().getColumns(), grant().getMasks()});
		} else {
			hint.setText("A catalog grant states no columns (duckdb-acl spec 158): which columns a role sees, and "
			             + "masked how, is said on the object's grant. A list an older build stored refuses every read "
			             + "under this grant - clear it and save.");
			catalogList = new Text(page, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
			catalogList.setFont(JFaceResources.getTextFont());
			catalogList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			catalogList.addModifyListener(e -> {
				if (!filling) {
					set("columns", catalogList.getText());
				}
			});
		}
	}

	private void createTable(Composite page) {
		table = new TableViewer(page, SWT.BORDER | SWT.FULL_SELECTION);
		table.getTable().setHeaderVisible(true);
		table.getTable().setLinesVisible(true);
		table.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		table.setContentProvider(ArrayContentProvider.getInstance());
		column("Column", 200, node -> node.name, null);
		column("Type", 220, node -> node.type, null);
		column("Shown", 100, node -> SHOWN[node.shown.ordinal()], new EditingSupport(table) {
			@Override
			protected CellEditor getCellEditor(Object element) {
				return new ComboBoxCellEditor(table.getTable(), SHOWN, SWT.READ_ONLY);
			}

			@Override
			protected boolean canEdit(Object element) {
				return editable();
			}

			@Override
			protected Object getValue(Object element) {
				return ((Node) element).shown.ordinal();
			}

			@Override
			protected void setValue(Object element, Object value) {
				if (value instanceof Integer index && index >= 0) {
					model.setShown((Node) element, Shown.values()[index]);
					changed();
				}
			}
		});
		column("Mask", 280, node -> node.shown == Shown.MASKED ? node.mask : "", new EditingSupport(table) {
			@Override
			protected CellEditor getCellEditor(Object element) {
				return new TextCellEditor(table.getTable());
			}

			@Override
			protected boolean canEdit(Object element) {
				return editable() && ((Node) element).shown == Shown.MASKED;
			}

			@Override
			protected Object getValue(Object element) {
				return ((Node) element).mask;
			}

			@Override
			protected void setValue(Object element, Object value) {
				((Node) element).mask = value == null ? "" : value.toString();
				changed();
			}
		});
	}

	private void column(String title, int width, java.util.function.Function<Node, String> text, EditingSupport editing) {
		TableViewerColumn column = new TableViewerColumn(table, SWT.LEFT);
		column.getColumn().setText(title);
		column.getColumn().setWidth(width);
		column.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				String value = text.apply((Node) element);
				return value == null ? "" : value;
			}
		});
		if (editing != null) {
			column.setEditingSupport(editing);
		}
	}

	/** An edit of the table: the test and the masks it writes now, into the grant (Save previews them). */
	private void changed() {
		table.refresh();
		String columns;
		String masks;
		try {
			columns = model.composeColumns();
			masks = model.composeMasks();
		} catch (IllegalStateException e) {
			written.setText("Not written: " + e.getMessage());
			return;
		}
		show(columns, masks);
		if (!columns.equals(nonNull(grant().getColumns()))) {
			set("columns", columns);
		}
		if (!masks.equals(nonNull(grant().getMasks()))) {
			set("masks", masks);
		}
	}

	private static String nonNull(String text) {
		return text == null ? "" : text;
	}

	private void show(String columns, String masks) {
		written.setText((columns.isEmpty() ? "COLUMNS: every column" : "COLUMNS " + columns)
		                + (masks.isEmpty() ? "" : "\nMASK " + masks));
	}

	@Override
	protected void load() {
		if (catalogList != null) {
			catalogList.setEditable(editable());
			filling = true;
			String list = nonNull(grant().getColumns());
			if (!catalogList.getText().equals(list)) { // never put back what the user is typing (the caret stays)
				catalogList.setText(list);
			}
			filling = false;
			return;
		}
		if (((AclObjectGrant) getDatabaseObject()).unreadable()) {
			hint.setText("The node could not read this grant's columns or masks: written again it would show what "
			             + "they hide - revoke it, or write it in the SQL editor.");
			table.getTable().setEnabled(false);
			laterShown.setEnabled(false);
			return;
		}
		loadColumns(this::fill, error -> {
			if (!hint.isDisposed()) {
				hint.setText("The object's columns could not be read: " + error);
			}
		});
	}

	private void fill(List<String[]> exposed) {
		if (table.getTable().isDisposed()) {
			return;
		}
		model = new GrantColumns(exposed);
		model.apply(grant().getColumns(), grant().getMasks());
		table.setInput(model.columns());
		filling = true;
		laterShown.setSelection(model.laterShown());
		laterShown.setEnabled(editable());
		filling = false;
		show(nonNull(grant().getColumns()), nonNull(grant().getMasks()));
	}
}
