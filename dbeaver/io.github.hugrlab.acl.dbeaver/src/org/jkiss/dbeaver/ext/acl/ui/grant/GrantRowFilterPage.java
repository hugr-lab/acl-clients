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
import org.jkiss.dbeaver.ext.acl.model.access.AclObjectGrant;

/**
 * Row filter (spec 008.1 addendum): the grant's predicate in an editor of its own, beside the columns it can name, and
 * Check. A catalog grant's predicate is AND-ed onto every object of the catalog.
 */
public class GrantRowFilterPage extends GrantPage {
	private StyledText predicate;
	private Table columns;
	private Label hint;
	private boolean filling;

	@Override
	public void createPartControl(Composite parent) {
		Composite page = new Composite(parent, SWT.NONE);
		page.setLayout(new GridLayout(1, false));
		Label about = new Label(page, SWT.WRAP);
		about.setText("A predicate over the object's columns, AND-ed into every read and write of it by this role "
		              + "(and onto the catalog grant's). acl_claim('name') is the principal's claim: tenant = "
		              + "acl_claim('tenant'). A catalog grant's predicate applies to every object of the catalog.");
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
		hint.setText("Columns of the object (double-click inserts)");
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
			if (columns.getSelectionCount() == 1) {
				predicate.insert(columns.getSelection()[0].getText(0));
			}
		});
		split.setWeights(3, 1);
		new CheckPanel(page, this, () -> new String[] {predicate.getText(), grant().getColumns()});
	}

	@Override
	protected void load() {
		predicate.setEditable(editable());
		filling = true;
		String rls = grant().getRls();
		predicate.setText(rls == null ? "" : rls);
		filling = false;
		columns.removeAll();
		if (!(getDatabaseObject() instanceof AclObjectGrant)) {
			hint.setText("A catalog grant: every object of the catalog");
			return;
		}
		loadColumns(this::fill, error -> hint.setText(error));
	}

	private void fill(List<String[]> exposed) {
		if (columns.isDisposed()) {
			return;
		}
		for (String[] column : exposed) {
			TableItem item = new TableItem(columns, SWT.NONE);
			item.setText(0, org.jkiss.dbeaver.ext.acl.model.access.AclGrammar.ident(column[0]));
			item.setText(1, column[1] == null ? "" : column[1]);
		}
	}
}
