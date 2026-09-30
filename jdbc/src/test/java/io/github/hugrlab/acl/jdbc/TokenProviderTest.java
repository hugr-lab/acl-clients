package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenProviderTest {
	FakeIdp idp;
	TestInteraction ui;
	TokenProvider provider;

	@BeforeEach
	void start() throws IOException {
		TokenCache.clearMemory();
		idp = new FakeIdp();
		ui = new TestInteraction();
		provider = new TokenProvider(idp.discovery(), ui);
		Flows.slowDownStepMillis = 10;
	}

	@AfterEach
	void stop() {
		idp.close();
	}

	private AclConfig config(String... kv) throws SQLException {
		Properties p = new Properties();
		for (int i = 0; i < kv.length; i += 2) {
			p.setProperty(kv[i], kv[i + 1]);
		}
		return AclConfig.parse("jdbc:acl://door:32800", p);
	}

	@Test
	void browserSignInWithPkce() throws SQLException {
		String token = provider.acquire(config("flow", "authcode", "loginTimeout", "10"));
		assertTrue(token.startsWith("at-"));
		assertEquals(List.of("authcode"), idp.grants, "the code was redeemed with the verifier it was issued for");
		assertEquals(1, ui.browsersOpened.get());
	}

	@Test
	void browserSignInRefusedByTheIdp() {
		idp.authorizeError = "access_denied";
		AuthException e = assertThrows(AuthException.class,
		    () -> provider.acquire(config("flow", "authcode", "loginTimeout", "10")));
		assertTrue(e.getMessage().contains("access_denied"), e.getMessage());
		assertEquals("28000", e.getSQLState());
	}

	@Test
	void browserSignInNobodyCompletesTimesOut() {
		ui.follow = false;
		AuthException e = assertThrows(AuthException.class,
		    () -> provider.acquire(config("flow", "authcode", "loginTimeout", "1")));
		assertTrue(e.getMessage().contains("not completed within 1 s"), e.getMessage());
	}

	@Test
	void deviceSignInPollsUntilApproved() throws SQLException {
		idp.devicePending = 2;
		String token = provider.acquire(config("flow", "device"));
		assertTrue(token.startsWith("at-"));
		assertEquals(List.of("ABCD-EFGH"), ui.deviceCodes);
		assertEquals(3, idp.devicePolls.get(), "two pending answers, then the token");
		assertTrue(ui.deviceClosed, "the code is taken off the screen when the sign-in ends");
	}

	@Test
	void deviceSignInSlowsDownWhenAsked() throws SQLException {
		idp.devicePending = 0;
		idp.deviceError = "slow_down";
		Thread flip = new Thread(() -> {
			try {
				Thread.sleep(100);
			} catch (InterruptedException ignored) {
			}
			idp.deviceError = null;
		});
		flip.start();
		assertTrue(provider.acquire(config("flow", "device")).startsWith("at-"));
	}

	@Test
	void deviceSignInDenied() {
		idp.deviceError = "access_denied";
		AuthException e = assertThrows(AuthException.class, () -> provider.acquire(config("flow", "device")));
		assertTrue(e.getMessage().contains("access_denied"), e.getMessage());
		assertTrue(ui.deviceClosed);
	}

	@Test
	void autoWithoutBrowserFallsBackToDevice() throws SQLException {
		ui.browser = false;
		idp.devicePending = 0;
		provider.acquire(config());
		assertEquals(List.of("device"), idp.grants);
	}

	@Test
	void passwordAndItsRefusal() throws SQLException {
		assertTrue(provider.acquire(config("user", "alice", "password", "secret")).startsWith("at-"));
		assertEquals(List.of("password:alice"), idp.grants);
		AuthException e = assertThrows(AuthException.class,
		    () -> provider.acquire(config("user", "bob", "password", "wrong")));
		assertTrue(e.getMessage().contains("Invalid user credentials"), e.getMessage());
		assertTrue(!e.getMessage().contains("wrong"), "the password is never in a message");
	}

	@Test
	void aCachedTokenIsReusedAcrossConnections() throws SQLException {
		String first = provider.acquire(config("flow", "authcode"));
		String second = provider.acquire(config("flow", "authcode"));
		assertEquals(first, second);
		assertEquals(1, ui.browsersOpened.get(), "one browser sign-in for both connections");
	}

	@Test
	void anExpiredTokenIsRefreshedSilently() throws SQLException {
		idp.accessLifetime = 1; // under the margin: never handed out again
		String first = provider.acquire(config("flow", "authcode"));
		String second = provider.acquire(config("flow", "authcode"));
		assertTrue(!first.equals(second));
		assertEquals(List.of("authcode", "refresh"), idp.grants);
		String third = provider.acquire(config("flow", "authcode"));
		assertEquals(List.of("authcode", "refresh", "refresh"), idp.grants,
		    "the refresh token survives a refresh answer that carries none");
		assertTrue(!third.equals(second));
		assertEquals(1, ui.browsersOpened.get());
	}

	@Test
	void aRefusedRefreshSignsInAgain() throws SQLException {
		idp.accessLifetime = 1;
		provider.acquire(config("flow", "authcode"));
		idp.refuseRefresh = true;
		provider.acquire(config("flow", "authcode"));
		assertEquals(List.of("authcode", "refresh", "authcode"), idp.grants);
		assertEquals(2, ui.browsersOpened.get());
	}

	@Test
	void aWrongPasswordNeverGetsTheCachedToken() throws SQLException {
		provider.acquire(config("user", "alice", "password", "secret"));
		AuthException e = assertThrows(AuthException.class,
		    () -> provider.acquire(config("user", "alice", "password", "guess")));
		assertTrue(e.getMessage().contains("Invalid user credentials"), e.getMessage());
		assertEquals(List.of("password:alice", "password:alice"), idp.grants, "the IdP judged the second password");
	}

	@Test
	void aPasswordSignInNeverReachesTheFileCache(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("tokens.json");
		provider.acquire(config("user", "alice", "password", "secret", "tokenCache", "file", "tokenCacheFile",
		    file.toString()));
		assertTrue(!Files.exists(file), "nothing derived from a password is written to disk");
	}

	@Test
	void theCacheKeepsSignInsApart() throws SQLException {
		provider.acquire(config("user", "alice", "password", "secret"));
		provider.acquire(config("user", "carol", "password", "secret"));
		provider.acquire(config("user", "alice", "password", "secret"));
		assertEquals(List.of("password:alice", "password:carol"), idp.grants,
		    "carol never gets alice's token; alice's second connection reuses hers");
	}

	@Test
	void noCacheMeansEveryConnectionSignsIn() throws SQLException {
		provider.acquire(config("user", "alice", "password", "secret", "tokenCache", "none"));
		provider.acquire(config("user", "alice", "password", "secret", "tokenCache", "none"));
		assertEquals(2, idp.grants.size());
	}

	@Test
	void concurrentConnectionsShareOneBrowserSignIn() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Future<String>> all = pool.invokeAll(List.of(() -> provider.acquire(config("flow", "authcode")),
			    () -> provider.acquire(config("flow", "authcode")), () -> provider.acquire(config("flow", "authcode")),
			    () -> provider.acquire(config("flow", "authcode"))));
			String first = all.get(0).get();
			for (Future<String> f : all) {
				assertEquals(first, f.get());
			}
		} finally {
			pool.shutdown();
		}
		assertEquals(1, ui.browsersOpened.get());
	}

	@Test
	void theFileCacheSurvivesTheProcessAndOnlyItsOwnerReadsIt(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("sub").resolve("tokens.json");
		String first = provider.acquire(config("flow", "authcode", "tokenCache", "file", "tokenCacheFile", file.toString()));
		TokenCache.clearMemory(); // a new DBeaver process
		String second = provider.acquire(config("flow", "authcode", "tokenCache", "file", "tokenCacheFile", file.toString()));
		assertEquals(first, second);
		assertEquals(1, ui.browsersOpened.get());
		if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
			assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
		}
	}

	@Test
	void aTokenIsUsedAsGiven() throws SQLException {
		assertEquals("raw-jwt", provider.acquire(config("token", "raw-jwt")));
		assertTrue(idp.grants.isEmpty());
		assertThrows(AuthException.class, () -> provider.acquire(config("flow", "token")));
	}

	@Test
	void theIssuerIsTheDoorsUnlessNamed() throws SQLException {
		AuthException e = assertThrows(AuthException.class,
		    () -> provider.acquire(config("issuer", "https://other/realms/x", "user", "a", "password", "secret")));
		assertTrue(e.getMessage().contains("does not trust"), e.getMessage());
		provider.acquire(config("issuer", idp.issuer + "/", "user", "alice", "password", "secret"));
		assertEquals(List.of("password:alice"), idp.grants, "a trailing slash names the same issuer");
	}

	@Test
	void withoutDiscoveryTheConnectionNamesIssuerAndClient() throws SQLException {
		TokenProvider blind = new TokenProvider(c -> {
			throw new IOException("no discovery here");
		}, ui);
		AuthException e = assertThrows(AuthException.class,
		    () -> blind.acquire(config("user", "alice", "password", "secret")));
		assertTrue(e.getMessage().contains("set the issuer and clientId"), e.getMessage());
		blind.acquire(config("issuer", idp.issuer, "clientId", FakeIdp.CLIENT, "user", "alice", "password", "secret"));
		assertEquals(List.of("password:alice"), idp.grants);
	}

	@Test
	void discoveryAnswerParses() throws IOException {
		List<DoorIssuer> issuers = FlightDiscovery.parse(("{\"issuers\":[{\"issuer\":\"https://a\"},"
		                                                  + "{\"issuer\":\"https://b\",\"client_id\":\"app\","
		                                                  + "\"token_endpoint\":\"https://b/token\"}]}")
		                                                     .getBytes());
		assertEquals(2, issuers.size());
		assertEquals("app", issuers.get(1).clientId());
		assertTrue(FlightDiscovery.parse(new byte[0]).isEmpty(), "an older door answers nothing");
	}
}
