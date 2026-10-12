package org.jkiss.dbeaver.ext.acl.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.ext.generic.views.GenericConnectionPage;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.ui.UIUtils;

/**
 * The connection's Main page: Generic's, plus the one TLS choice a dev node needs on it (spec 003 addendum) - trust the
 * node's certificate without checking it. Kept as a provider property that the data source passes to the driver as
 * {@code disableCertificateVerification}, so the Driver properties tab never overwrites it (a value set there wins).
 */
public class AclConnectionPage extends GenericConnectionPage {
	private Button trustCertificate;

	@Override
	public void createAdvancedSettingsGroup(Composite composite) {
		Composite group = UIUtils.createTitledComposite(composite, "TLS", 1, GridData.FILL_HORIZONTAL);
		trustCertificate = UIUtils.createCheckbox(group, "Trust the node's certificate without checking it",
		    "A self-signed development node only: a node in production has a certificate your machine trusts (or name "
		        + "its CA with the driver property tlsRootCerts)",
		    false, 1);
		trustCertificate.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
	}

	@Override
	public void loadSettings() {
		super.loadSettings();
		if (trustCertificate != null) {
			DBPConnectionConfiguration configuration = site.getActiveDataSource().getConnectionConfiguration();
			trustCertificate.setSelection(AclDataSource.trustsCertificate(configuration));
		}
	}

	@Override
	public void saveSettings(@NotNull DBPDataSourceContainer dataSource) {
		super.saveSettings(dataSource);
		if (trustCertificate != null) {
			DBPConnectionConfiguration configuration = dataSource.getConnectionConfiguration();
			if (trustCertificate.getSelection()) {
				configuration.setProviderProperty(AclDataSource.PROP_TRUST_CERTIFICATE, "true");
			} else {
				configuration.removeProviderProperty(AclDataSource.PROP_TRUST_CERTIFICATE);
			}
		}
	}
}
