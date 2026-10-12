package io.github.hugrlab.acl.dbeaver.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCheckBox;
import org.eclipse.ui.PlatformUI;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.app.DBPDataSourceRegistry;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.registry.DataSourceProviderRegistry;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.dialogs.connection.EditConnectionDialog;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The Main page's TLS choice (spec 003 addendum), against dev/node.sh's self-signed door: the box is on the page and
 * shows what the connection holds, and a connection with the box on - and no driver property - connects.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class LiveConnectionPageTest {
	static final String LABEL = "Trust the node's certificate without checking it";

	/** The dev node's URL with the development switch taken off: the box alone must get past the certificate. */
	private static String strictUrl() {
		return LiveTreeTest.URL.replace("?disableCertificateVerification=true", "")
		    .replace("&disableCertificateVerification=true", "").replace("disableCertificateVerification=true&", "");
	}

	private static DataSourceDescriptor container(boolean trust) throws Exception {
		DBPProject project = DBWorkbench.getPlatform().getWorkspace().getActiveProject();
		DBPDataSourceRegistry registry = project.getDataSourceRegistry();
		DBPDriver driver = DataSourceProviderRegistry.getInstance().getDataSourceProvider("duckdb_acl")
		    .getDriver("duckdb_acl_jdbc");
		DBPConnectionConfiguration configuration = new DBPConnectionConfiguration();
		configuration.setUrl(strictUrl());
		configuration.setUserName("analyst1");
		configuration.setUserPassword("analyst1-pass");
		if (trust) {
			configuration.setProviderProperty(AclDataSource.PROP_TRUST_CERTIFICATE, "true");
		}
		DataSourceDescriptor container = new DataSourceDescriptor(registry, DataSourceDescriptor.generateNewId(driver),
		    driver, configuration);
		container.setName("tls " + trust);
		container.setSavePassword(true);
		registry.addDataSource(container);
		return container;
	}

	/** The connection's settings dialog: its title names the connection (DBeaver's own wording around it). */
	private static org.eclipse.swtbot.swt.finder.widgets.SWTBotShell shellNaming(SWTWorkbenchBot bot, String name)
	    throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			for (org.eclipse.swtbot.swt.finder.widgets.SWTBotShell shell : bot.shells()) {
				if (shell.getText().contains(name)) {
					return shell;
				}
			}
			Thread.sleep(100);
		}
		throw new AssertionError("no settings dialog for " + name);
	}

	@Test
	public void theBoxAloneConnectsToASelfSignedNode() throws Exception {
		LiveTreeTest.assumeNode();
		Workbench.waitUp();
		DataSourceDescriptor trusting = container(true);
		try {
			assertTrue("connected", trusting.connect(new VoidProgressMonitor(), true, true));
		} finally {
			trusting.disconnect(new VoidProgressMonitor());
			trusting.getRegistry().removeDataSource(trusting);
		}
		DataSourceDescriptor strict = container(false);
		try {
			boolean connected;
			try {
				connected = strict.connect(new VoidProgressMonitor(), true, true);
			} catch (Exception e) {
				connected = false;
				assertTrue(String.valueOf(e.getMessage()), String.valueOf(e.getMessage()).contains("does not trust"));
			}
			assertFalse("without the box the certificate is checked", connected);
		} finally {
			strict.getRegistry().removeDataSource(strict);
		}
	}

	@Test
	public void theBoxIsOnTheMainPage() throws Exception {
		LiveTreeTest.assumeNode();
		Workbench.waitUp();
		SWTWorkbenchBot bot = new SWTWorkbenchBot();
		for (boolean trust : new boolean[] {true, false}) {
			DataSourceDescriptor container = container(trust);
			try {
				Display.getDefault().asyncExec(() -> EditConnectionDialog.openEditConnectionDialog(
				    PlatformUI.getWorkbench().getWorkbenchWindows()[0], container, null));
				org.eclipse.swtbot.swt.finder.widgets.SWTBotShell shell = shellNaming(bot, container.getName());
				SWTBotCheckBox box = shell.bot().checkBox(LABEL);
				assertTrue("the box shows what the connection holds", box.isChecked() == trust);
				shell.close(); // nothing saved
				assertFalse("closing saved nothing", trust != AclDataSource.trustsCertificate(container.getConnectionConfiguration()));
			} finally {
				container.getRegistry().removeDataSource(container);
			}
		}
	}
}
