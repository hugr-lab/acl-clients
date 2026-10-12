package org.jkiss.dbeaver.ext.acl.ui.grant;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.jkiss.dbeaver.ext.acl.model.access.AclFunctionEntry;

/**
 * A virtual function's Definition (spec 008.2, the owner's ask): its parameters and result - written at Create (there
 * is no ALTER for them) - and its template in an editor of its own (`SET MACRO`), or an alias's physical function
 * (`SET ALIAS OF`).
 */
public class FunctionDefinitionPage extends PolicyPage {
	private static final String RETURNS_TIP = "a table function's columns (id INTEGER, amount INTEGER), a scalar's type; empty = probed";

	private Text params;
	private Text returns;
	private Text target;
	private StyledText template;
	private Label hint;
	private boolean filling;

	private AclFunctionEntry function() {
		return (AclFunctionEntry) getDatabaseObject();
	}

	@Override
	public void createPartControl(Composite parent) {
		Composite page = new Composite(parent, SWT.NONE);
		page.setLayout(new GridLayout(2, false));
		hint = new Label(page, SWT.WRAP);
		GridData wide = new GridData(SWT.FILL, SWT.TOP, true, false);
		wide.horizontalSpan = 2;
		hint.setLayoutData(wide);
		params = field(page, "Parameters", "threshold INTEGER, tenant VARCHAR - they type the probe", "params");
		returns = field(page, "Returns", RETURNS_TIP, "returns");
		target = field(page, "Alias of", "an alias's physical function", "target");
		Label about = new Label(page, SWT.NONE);
		about.setText("Template");
		about.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
		template = new StyledText(page, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		template.setFont(JFaceResources.getTextFont());
		template.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		template.addModifyListener(e -> {
			if (!filling) {
				set("template", template.getText());
			}
		});
	}

	private Text field(Composite page, String label, String tip, String id) {
		Label name = new Label(page, SWT.NONE);
		name.setText(label);
		Text text = new Text(page, SWT.BORDER);
		text.setToolTipText(tip);
		text.setMessage(tip);
		text.setFont(JFaceResources.getTextFont());
		text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		text.addModifyListener(e -> {
			if (!filling) {
				set(id, text.getText());
			}
		});
		return text;
	}

	private static void put(Text field, String value) {
		String text = value == null ? "" : value;
		if (!field.getText().equals(text)) {
			field.setText(text);
		}
	}

	@Override
	protected void load() {
		AclFunctionEntry function = function();
		boolean can = editable();
		boolean creating = !function.isPersisted();
		boolean alias = function.isAlias();
		hint.setText(alias ? "An alias: the call is retargeted to a physical function in place."
		                   : "A macro: a template in physical names - acl_arg(n) is the caller's n-th argument, "
		                     + "acl_claim('name') a claim. A table function's is a SELECT, a scalar's an expression. "
		                     + "Parameters and result are written at Create (no ALTER changes them).");
		filling = true;
		put(params, function.getParams());
		put(returns, function.getReturns());
		put(target, function.getTarget());
		fill(template, function.getTemplate());
		filling = false;
		params.setEditable(can && creating && !alias);
		returns.setEditable(can && creating && !alias);
		// the node's functions view does not hold the result: a stored function's is the console's (duckdb_functions)
		returns.setMessage(creating ? RETURNS_TIP : "written at Create - the node lists it in duckdb_functions()");
		target.setEditable(can && alias);
		template.setEditable(can && !alias);
	}
}
