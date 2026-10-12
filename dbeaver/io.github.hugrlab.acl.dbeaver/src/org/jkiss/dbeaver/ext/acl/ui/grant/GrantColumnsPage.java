package org.jkiss.dbeaver.ext.acl.ui.grant;

import java.util.List;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.jkiss.dbeaver.ext.acl.model.access.AclObjectGrant;
import org.jkiss.dbeaver.ext.acl.model.access.GrantColumns;
import org.jkiss.dbeaver.ext.acl.model.access.GrantColumns.Node;
import org.jkiss.dbeaver.ext.acl.model.access.GrantColumns.Shown;

/**
 * Columns (spec 008.1 addendum): what the role reads of each column the object exposes - shown, masked (the expression
 * replaces the value) or hidden - a STRUCT's fields as children (duckdb-acl spec 102), the `COLUMNS (…)` it writes
 * under the tree, and Check. A catalog grant's list names columns across the catalog: written as text.
 */
public class GrantColumnsPage extends GrantPage {
	private static final String[] SHOWN = {"Visible", "Masked", "Hidden"};

	private TreeViewer tree;
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
			hint.setText("What the role reads of each column: Visible, Masked (the expression replaces the value - "
			             + "NULL, '***', left(ssn, 1) || '***'), Hidden. A STRUCT's fields narrow it. Nothing hidden or "
			             + "masked = the grant narrows nothing.");
			createTree(page);
			new Label(page, SWT.NONE).setText("COLUMNS (…) as the grant writes it:");
			written = new Text(page, SWT.BORDER | SWT.READ_ONLY | SWT.WRAP);
			written.setFont(JFaceResources.getTextFont());
			written.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		} else {
			hint.setText("A catalog grant's column list - names (and masks) across every object of the catalog, "
			             + "intersected with each object's own: id, amount, ssn = NULL. The node does not probe it.");
			catalogList = new Text(page, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
			catalogList.setFont(JFaceResources.getTextFont());
			catalogList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			catalogList.addModifyListener(e -> {
				if (!filling) {
					set("columns", catalogList.getText());
				}
			});
		}
		new CheckPanel(page, this, () -> new String[] {grant().getRls(), currentList()});
	}

	private String currentList() {
		if (catalogList != null) {
			return catalogList.getText();
		}
		return model != null ? model.compose() : grant().getColumns();
	}

	private void createTree(Composite page) {
		tree = new TreeViewer(page, SWT.BORDER | SWT.FULL_SELECTION);
		tree.getTree().setHeaderVisible(true);
		tree.getTree().setLinesVisible(true);
		tree.getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tree.setContentProvider(new ITreeContentProvider() {
			@Override
			public Object[] getElements(Object input) {
				return ((GrantColumns) input).columns().toArray();
			}

			@Override
			public Object[] getChildren(Object parent) {
				return ((Node) parent).children.toArray();
			}

			@Override
			public Object getParent(Object element) {
				return null;
			}

			@Override
			public boolean hasChildren(Object element) {
				return !((Node) element).children.isEmpty();
			}
		});
		column("Column", 200, node -> node.name, null);
		column("Type", 220, node -> node.type, null);
		column("Shown", 100, node -> SHOWN[node.shown.ordinal()], new EditingSupport(tree) {
			@Override
			protected CellEditor getCellEditor(Object element) {
				return new ComboBoxCellEditor(tree.getTree(), SHOWN, SWT.READ_ONLY);
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
		column("Mask", 280, node -> node.shown == Shown.MASKED ? node.mask : "", new EditingSupport(tree) {
			@Override
			protected CellEditor getCellEditor(Object element) {
				return new TextCellEditor(tree.getTree());
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
		TreeViewerColumn column = new TreeViewerColumn(tree, SWT.LEFT);
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

	/** An edit of the tree: the list it writes now, into the grant (Save previews it). */
	private void changed() {
		tree.refresh();
		String list = model.compose();
		written.setText(list.isEmpty() ? "(none - every column, whole)" : list);
		String now = grant().getColumns();
		if (!list.equals(now == null ? "" : now)) {
			set("columns", list);
		}
	}

	@Override
	protected void load() {
		if (catalogList != null) {
			catalogList.setEditable(editable());
			filling = true;
			String list = grant().getColumns();
			catalogList.setText(list == null ? "" : list);
			filling = false;
			return;
		}
		loadColumns(this::fill, error -> {
			if (!hint.isDisposed()) {
				hint.setText("The object's columns could not be read: " + error);
			}
		});
	}

	private void fill(List<String[]> exposed) {
		if (tree.getTree().isDisposed()) {
			return;
		}
		model = new GrantColumns(exposed);
		model.apply(grant().getColumns());
		tree.setInput(model);
		tree.expandToLevel(2);
		String list = model.compose();
		written.setText(list.isEmpty() ? "(none - every column, whole)" : list);
	}
}
