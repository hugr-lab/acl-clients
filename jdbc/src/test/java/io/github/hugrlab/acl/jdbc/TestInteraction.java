package io.github.hugrlab.acl.jdbc;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** A "browser" that follows the IdP's redirect back to the loopback, and a device-code screen. */
final class TestInteraction implements Interaction {
	final AtomicInteger browsersOpened = new AtomicInteger();
	final List<String> deviceCodes = new CopyOnWriteArrayList<>();
	volatile boolean browser = true;
	volatile boolean deviceClosed;
	/** When false, the browser opens but nobody signs in - the flow must time out. */
	volatile boolean follow = true;

	@Override
	public boolean browserAvailable() {
		return browser;
	}

	@Override
	public void openBrowser(URI uri) throws IOException {
		browsersOpened.incrementAndGet();
		if (!follow) {
			return;
		}
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
		try {
			client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.discarding());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}
	}

	@Override
	public AutoCloseable showDeviceCode(String userCode, String verificationUri, String verificationUriComplete) {
		deviceCodes.add(userCode);
		deviceClosed = false;
		return () -> deviceClosed = true;
	}
}
