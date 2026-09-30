package io.github.hugrlab.acl.jdbc;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.URI;

/**
 * What the interactive flows need from the user's desktop: a browser to open, and somewhere to show a
 * device code. The default opens the system browser and a small non-modal window (DBeaver, any GUI);
 * without a display both go to stderr. Tests substitute their own.
 */
interface Interaction {
	/** Whether a browser can be opened on this machine - decides {@code flow=auto}. */
	boolean browserAvailable();

	void openBrowser(URI uri) throws IOException;

	/** Shows the device code until the returned handle is closed. */
	AutoCloseable showDeviceCode(String userCode, String verificationUri, String verificationUriComplete);

	static Interaction desktop() {
		return new DesktopInteraction();
	}
}

final class DesktopInteraction implements Interaction {
	@Override
	public boolean browserAvailable() {
		return !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
		    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
	}

	@Override
	public void openBrowser(URI uri) throws IOException {
		if (browserAvailable()) {
			Desktop.getDesktop().browse(uri);
			return;
		}
		System.err.println("duckdb-acl: open this address in a browser to sign in:\n  " + uri);
	}

	@Override
	public AutoCloseable showDeviceCode(String userCode, String verificationUri, String verificationUriComplete) {
		String link = verificationUriComplete != null ? verificationUriComplete : verificationUri;
		System.err.println("duckdb-acl: to sign in, open " + verificationUri + " and enter the code " + userCode
		                   + (verificationUriComplete != null ? "\n  (or open " + verificationUriComplete + ")" : ""));
		if (GraphicsEnvironment.isHeadless()) {
			return () -> {};
		}
		return DeviceCodeWindow.show(userCode, verificationUri, link, this);
	}
}
