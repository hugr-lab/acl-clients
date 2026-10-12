package org.jkiss.dbeaver.ext.acl.ui.grant;

import java.util.List;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.jkiss.dbeaver.ext.acl.model.access.AclGrammar;
import org.jkiss.dbeaver.ext.acl.model.access.AclRelation;

/**
 * A virtual table's Row filter (spec 008.2): its own predicate over the physical columns - AND-ed into every read and
 * write of it, by every role - in an editor of its own, beside the source's columns. The node binds it when it is
 * saved (a predicate that does not bind is refused there).
 */
public class RelationRowFilterPage extends PolicyPage {
	private StyledText predicate;
	private Table columns;
	private Label hint;
	private boolean filling;

	private AclRelation relation() {
		return (AclRelation) getDatabaseObject();
	}

	@Override
	public void createPartControl(Composite parent) {
		Composite page = new Composite(parent, SWT.NONE);
		page.setLayout(new GridLayout(1, false));
		Label about = new Label(page, SWT.WRAP);
		about.setText("The table's own predicate over its source's physical columns, AND-ed into every read and write "
		              + "of it by every role (each grant may narrow further). acl_claim('name') is the principal's claim. "
		              + "The node binds it against the source when it is saved.");
		about.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		SashForm split = new SashForm(page, SWT.HORIZONTAL);
		split.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		predicate = new StyledText(split, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		predicate.setFont(JFaceResources.getTextFont());
		predicate.addModifyListener(e -> {
			if (!filling) {
				set("rls", predicate.getText());
			}
		});
		Composite side = new Composite(split, SWT.NONE);
		side.setLayout(new GridLayout(1, false));
		hint = new Label(side, SWT.WRAP);
		hint.setText("Columns of the source (double-click inserts)");
		hint.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		columns = new Table(side, SWT.BORDER | SWT.FULL_SELECTION);
		columns.setHeaderVisible(true);
		columns.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		for (String name : new String[] {"Column", "Type"}) {
			TableColumn column = new TableColumn(columns, SWT.LEFT);
			column.setText(name);
			column.setWidth(140);
		}
		columns.addListener(SWT.DefaultSelection, e -> {
			if (columns.getSelectionCount() == 1 && predicate.getEditable()) {
				predicate.insert(columns.getSelection()[0].getText(0));
			}
		});
		split.setWeights(3, 1);
	}

	@Override
	protected void load() {
		if (relation().isView()) {
			predicate.setEditable(false);
			hint.setText("A view filters in its SELECT; a grant on it filters its output");
			return;
		}
		predicate.setEditable(editable());
		filling = true;
		String rls = relation().getRls();
		predicate.setText(rls == null ? "" : rls);
		filling = false;
		columns.removeAll();
		inBackground("Read the source's columns", relation()::physicalColumns, this::fill, error -> hint.setText(error));
	}

	private void fill(List<String[]> physical) {
		if (columns.isDisposed()) {
			return;
		}
		if (physical.isEmpty()) {
			hint.setText("The source's columns are not listed here (its phys, or a source not granted to you)");
		}
		for (String[] column : physical) {
			TableItem item = new TableItem(columns, SWT.NONE);
			item.setText(0, AclGrammar.ident(column[0]));
			item.setText(1, column[1] == null ? "" : column[1]);
		}
	}
}
