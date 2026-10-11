package io.github.hugrlab.acl.dbeaver.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.ui.PlatformUI;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.jkiss.dbeaver.registry.DataSourceProviderRegistry;
import org.jkiss.dbeaver.registry.DataSourceProviderDescriptor;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.junit.Test;
import org.junit.runner.RunWith;

/** The plugin inside a running DBeaver: the connection type and its bundled driver are registered (spec 007.1). */
@RunWith(SWTBotJunit4ClassRunner.class)
public class PluginLoadedTest {
	@Test
	public void theConnectionTypeIsRegistered() {
		DataSourceProviderDescriptor provider = DataSourceProviderRegistry.getInstance().getDataSourceProvider("duckdb_acl");
		assertNotNull("the duckdb-acl provider", provider);
		DBPDriver driver = provider.getDriver("duckdb_acl_jdbc");
		assertNotNull("its driver", driver);
		assertEquals("io.github.hugrlab.acl.jdbc.AclDriver", driver.getDriverClassName());
	}

	@Test
	public void theWorkbenchIsUp() {
		Workbench.waitUp();
		Shell window = UIThreadRunnable.syncExec(() -> PlatformUI.getWorkbench().getWorkbenchWindows()[0].getShell());
		// no activate(): macOS keeps a background process from taking the focus (the tests drive widgets by events)
		assertTrue(new SWTBotShell(window).isVisible());
	}
}
