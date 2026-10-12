package org.jkiss.dbeaver.ext.acl.ui.grant;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CheckboxCellEditor;
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
import org.jkiss.dbeaver.ext.acl.model.access.AclRelation;
import org.jkiss.dbeaver.ext.acl.model.access.RelationColumns;
import org.jkiss.dbeaver.ext.acl.model.access.RelationColumns.Column;
import org.jkiss.dbeaver.ext.acl.model.access.RelationColumns.Computed;

/**
 * A virtual table's Columns (spec 008.2): the source's physical columns, each exposed or not, under a name of the
 * table's, through an expression (a mask over the physical row), with a nullability mark - and the computed columns the
 * source does not have; the `COLUMNS (…)` it writes under the table. Nothing changed = every column as it is (the
 * alias form - writable); a mask, a computed column or a predicate make the table read-only (the node's rule).
 */
public class RelationColumnsPage extends PolicyPage {
	private static final String[] NULLABLE = {"", "NOT NULL", "NULL"};

	private TableViewer table;
	private Text written;
	private Label hint;
	private Button add;
	private Button remove;
	private RelationColumns model;

	private AclRelation relation() {
		return (AclRelation) getDatabaseObject();
	}

	@Override
	public void createPartControl(Composite parent) {
		Composite page = new Composite(parent, SWT.NONE);
		page.setLayout(new GridLayout(1, false));
		hint = new Label(page, SWT.WRAP);
		hint.setText("Which columns of the source the table exposes, and how: a name of its own (a rename), an "
		             + "expression over the physical row (a mask: NULL, '***'), NOT NULL (a promise a key may rest on). "
		             + "Computed columns are the table's own. Nothing changed = every column as it is (writable).");
		hint.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		table = new TableViewer(page, SWT.BORDER | SWT.FULL_SELECTION);
		table.getTable().setHeaderVisible(true);
		table.getTable().setLinesVisible(true);
		table.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		table.setContentProvider(ArrayContentProvider.getInstance());
		column("Exposed", 70, e -> e instanceof Column c ? (c.exposed ? "yes" : "no") : "computed",
		       edit(e -> e instanceof Column, e -> new CheckboxCellEditor(table.getTable()),
		            e -> ((Column) e).exposed, (e, v) -> ((Column) e).exposed = Boolean.TRUE.equals(v)));
		column("Source column", 160, e -> e instanceof Column c ? c.name : "", null);
		column("Type", 160, e -> e instanceof Column c ? c.type : "", null);
		column("Name in the table", 160, e -> e instanceof Column c ? c.as : ((Computed) e).name,
		       edit(e -> true, e -> new TextCellEditor(table.getTable()), e -> e instanceof Column c ? c.as : ((Computed) e).name,
		            (e, v) -> {
			            String name = v == null ? "" : v.toString().trim();
			            if (name.isEmpty()) {
				            return;
			            }
			            if (e instanceof Column c) {
				            c.as = name;
			            } else {
				            ((Computed) e).name = name;
			            }
		            }));
		column("Expression", 240, e -> e instanceof Column c ? c.expression : ((Computed) e).expression,
		       edit(e -> true, e -> new TextCellEditor(table.getTable()),
		            e -> e instanceof Column c ? c.expression : ((Computed) e).expression, (e, v) -> {
			            String text = v == null ? "" : v.toString().trim();
			            if (e instanceof Column c) {
				            c.expression = text;
			            } else if (!text.isEmpty()) {
				            ((Computed) e).expression = text;
			            }
		            }));
		column("Nullability", 100, e -> e instanceof Column c ? c.nullable : ((Computed) e).nullable,
		       edit(e -> true, e -> new ComboBoxCellEditor(table.getTable(), NULLABLE, SWT.READ_ONLY), e -> {
			       String now = e instanceof Column c ? c.nullable : ((Computed) e).nullable;
			       return Math.max(0, java.util.Arrays.asList(NULLABLE).indexOf(now));
		       }, (e, v) -> {
			       String mark = v instanceof Integer i && i >= 0 ? NULLABLE[i] : "";
			       if (e instanceof Column c) {
				       c.nullable = mark;
			       } else {
				       ((Computed) e).nullable = mark;
			       }
		       }));
		Composite buttons = new Composite(page, SWT.NONE);
		buttons.setLayout(new GridLayout(2, false));
		add = new Button(buttons, SWT.PUSH);
		add.setText("Add computed column");
		add.addListener(SWT.Selection, e -> {
			model.computed().add(new Computed("new_column", "NULL"));
			changed();
		});
		remove = new Button(buttons, SWT.PUSH);
		remove.setText("Remove computed column");
		remove.addListener(SWT.Selection, e -> {
			Object selected = table.getStructuredSelection().getFirstElement();
			if (selected instanceof Computed made) {
				model.computed().remove(made);
				changed();
			}
		});
		new Label(page, SWT.NONE).setText("COLUMNS (…) as the table writes it:");
		written = new Text(page, SWT.BORDER | SWT.READ_ONLY | SWT.WRAP);
		written.setFont(JFaceResources.getTextFont());
		written.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
	}

	interface Get {
		Object get(Object element);
	}

	interface Put {
		void put(Object element, Object value);
	}

	private EditingSupport edit(java.util.function.Predicate<Object> can, java.util.function.Function<Object, CellEditor> editor,
	                            Get get, Put put) {
		return new EditingSupport(table) {
			@Override
			protected CellEditor getCellEditor(Object element) {
				return editor.apply(element);
			}

			@Override
			protected boolean canEdit(Object element) {
				return model != null && editable() && can.test(element);
			}

			@Override
			protected Object getValue(Object element) {
				return get.get(element);
			}

			@Override
			protected void setValue(Object element, Object value) {
				put.put(element, value);
				changed();
			}
		};
	}

	private void column(String title, int width, java.util.function.Function<Object, String> text, EditingSupport editing) {
		TableViewerColumn column = new TableViewerColumn(table, SWT.LEFT);
		column.getColumn().setText(title);
		column.getColumn().setWidth(width);
		column.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				String value = text.apply(element);
				return value == null ? "" : value;
			}
		});
		if (editing != null) {
			column.setEditingSupport(editing);
		}
	}

	private List<Object> rows() {
		List<Object> out = new ArrayList<>(model.columns());
		out.addAll(model.computed());
		return out;
	}

	private void changed() {
		table.setInput(rows());
		String list = model.compose();
		written.setText(list.isEmpty() ? "(none - every column as it is)" : list);
		try {
			String now = relation().getDeclaredColumns(new org.jkiss.dbeaver.model.runtime.VoidProgressMonitor());
			if (!list.equals(now == null ? "" : now)) {
				set("declaredColumns", list);
			}
		} catch (Exception e) {
			set("declaredColumns", list);
		}
	}

	@Override
	protected void load() {
		boolean can = editable() && !relation().isView();
		add.setEnabled(can);
		remove.setEnabled(can);
		if (relation().isView()) {
			hint.setText("A view's columns are its SELECT's (a grant on it narrows them)");
			return;
		}
		inBackground("Read the source's columns", monitor -> new Object[] {relation().physicalColumns(monitor),
		                                                                  relation().getDeclaredColumns(monitor)},
		             this::fill, error -> hint.setText(error));
	}

	@SuppressWarnings("unchecked")
	private void fill(Object[] read) {
		if (table.getTable().isDisposed()) {
			return;
		}
		List<String[]> physical = (List<String[]>) read[0];
		if (physical.isEmpty()) {
			hint.setText("The source's columns are not listed here (its phys, or a source not granted to you): the "
			             + "list is kept as written");
		}
		model = new RelationColumns(physical);
		model.apply((String) read[1]);
		table.setInput(rows());
		String list = model.compose();
		written.setText(list.isEmpty() ? "(none - every column as it is)" : list);
	}
}
