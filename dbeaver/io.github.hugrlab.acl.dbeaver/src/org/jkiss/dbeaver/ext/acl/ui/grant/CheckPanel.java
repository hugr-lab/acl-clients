package org.jkiss.dbeaver.ext.acl.ui.grant;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

/**
 * Check: what writing the grant with the tab's values would say - asked of the node (platform.check_grant, duckdb-acl
 * spec 122), nothing written. One row per finding; a refusal is the write's own words, in red.
 */
final class CheckPanel {
	private final Label said;
	private final Table table;

	/** `values` = the RLS and the column list to check, as the tabs hold them now. */
	CheckPanel(Composite parent, PolicyPage page, Supplier<String[]> values) {
		Composite panel = new Composite(parent, SWT.NONE);
		panel.setLayout(new GridLayout(2, false));
		panel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		Button check = new Button(panel, SWT.PUSH);
		check.setText("Check with the node");
		check.setToolTipText("What writing the grant would say - the predicate, the columns the role would read, with "
		                     + "their types - asked of the node; nothing is written");
		said = new Label(panel, SWT.NONE);
		said.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		table = new Table(panel, SWT.BORDER | SWT.FULL_SELECTION);
		table.setHeaderVisible(true);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.horizontalSpan = 2;
		data.heightHint = 120;
		table.setLayoutData(data);
		for (String name : new String[] {"Item", "Name", "Type", "Status", "Message"}) {
			TableColumn column = new TableColumn(table, SWT.LEFT);
			column.setText(name);
			column.setWidth(name.equals("Message") ? 420 : 120);
		}
		check.addListener(SWT.Selection, e -> {
			String[] now = values.get();
			said.setText("Checking...");
			page.ask("Check the grant", now[0], now[1], this::show, error -> {
				table.removeAll();
				said.setText(error == null ? "the node did not answer" : error);
			});
		});
	}

	void show(List<Map<String, Object>> rows) {
		if (table.isDisposed()) {
			return;
		}
		table.removeAll();
		boolean refused = false;
		for (Map<String, Object> row : rows) {
			TableItem item = new TableItem(table, SWT.NONE);
			for (int i = 0; i < 5; i++) {
				String value = PolicyPage.text(row, new String[] {"item", "name", "type", "status", "message"}[i]);
				item.setText(i, value == null ? "" : value);
			}
			if ("refused".equals(PolicyPage.text(row, "status"))) {
				refused = true;
				item.setForeground(table.getDisplay().getSystemColor(SWT.COLOR_RED));
			}
		}
		said.setText(refused ? "The write would be refused" : "The write would be accepted");
	}
}
