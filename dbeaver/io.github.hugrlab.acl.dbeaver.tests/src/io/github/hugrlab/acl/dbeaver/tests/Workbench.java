package io.github.hugrlab.acl.dbeaver.tests;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.ui.PlatformUI;

/** The workbench comes up after the tests start (useUIThread=false): every test class waits for its window. */
final class Workbench {
	private Workbench() {
	}

	static void waitUp() {
		new SWTWorkbenchBot().waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return PlatformUI.isWorkbenchRunning() && PlatformUI.getWorkbench().getWorkbenchWindowCount() > 0;
			}

			@Override
			public String getFailureMessage() {
				return "no workbench window";
			}
		}, 180_000);
	}
}
