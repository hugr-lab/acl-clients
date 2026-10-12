package org.jkiss.dbeaver.ext.acl.ui;

import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.jkiss.dbeaver.ext.acl.model.access.RepairStatements;

/**
 * One management statement before it runs (spec 008.2: a finding's repair, a schema's refresh): its alternatives to
 * choose from, the statement editable - a repair is often a template (`<column>`, `<path>`) - and Execute only when no
 * place is left to fill. What is shown is what runs.
 */
public class StatementDialog extends Dialog {
	private final String title;
	private final String explanation;
	private final List<String> alternatives;
	private Text statement;
	private String chosen;

	public StatementDialog(Shell shell, String title, String explanation, List<String> alternatives) {
		super(shell);
		this.title = title;
		this.explanation = explanation;
		this.alternatives = alternatives;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(title);
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		area.setLayout(new GridLayout(1, false));
		Label about = new Label(area, SWT.WRAP);
		about.setText(explanation);
		GridData wide = new GridData(SWT.FILL, SWT.TOP, true, false);
		wide.widthHint = 640;
		about.setLayoutData(wide);
		if (alternatives.size() > 1) {
			for (int i = 0; i < alternatives.size(); i++) {
				Button choice = new Button(area, SWT.RADIO);
				choice.setText(alternatives.get(i));
				choice.setSelection(i == 0);
				String text = alternatives.get(i);
				choice.addListener(SWT.Selection, e -> {
					if (choice.getSelection()) {
						statement.setText(text);
					}
				});
			}
		}
		statement = new Text(area, SWT.BORDER | SWT.MULTI | SWT.WRAP);
		statement.setFont(JFaceResources.getTextFont());
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.heightHint = 80;
		statement.setLayoutData(data);
		statement.setText(alternatives.isEmpty() ? "" : alternatives.get(0));
		statement.addModifyListener(e -> update());
		return area;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "Execute", true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
		update();
	}

	private void update() {
		Button execute = getButton(IDialogConstants.OK_ID);
		if (execute != null && statement != null) {
			String text = statement.getText().trim();
			execute.setEnabled(!text.isEmpty() && !RepairStatements.hasPlaces(text));
		}
	}

	@Override
	protected void okPressed() {
		chosen = statement.getText().trim();
		super.okPressed();
	}

	/** The statement to run, or null when cancelled. */
	public String chosen() {
		return chosen;
	}
}
