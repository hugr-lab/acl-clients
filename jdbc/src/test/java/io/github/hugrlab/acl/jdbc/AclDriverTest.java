package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class AclDriverTest {
	static final class Capture implements Driver {
		String url;
		Properties props;

		@Override
		public Connection connect(String url, Properties info) {
			this.url = url;
			this.props = info;
			return null;
		}

		@Override
		public boolean acceptsURL(String url) {
			return true;
		}

		@Override
		public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
			return new DriverPropertyInfo[0];
		}

		@Override
		public int getMajorVersion() {
			return 0;
		}

		@Override
		public int getMinorVersion() {
			return 0;
		}

		@Override
		public boolean jdbcCompliant() {
			return false;
		}

		@Override
		public Logger getParentLogger() {
			return null;
		}
	}

	@Test
	void connectsThroughArrowsDriverWithTheToken() throws SQLException {
		Capture arrow = new Capture();
		AclDriver driver = new AclDriver(arrow, new TokenProvider(c -> {
			throw new AssertionError("a token needs no discovery");
		}, new TestInteraction()));
		Properties info = new Properties();
		info.setProperty("token", "jwt");
		info.setProperty("disableCertificateVerification", "true");
		driver.connect("jdbc:acl://door:32800", info);
		assertEquals("jdbc:arrow-flight-sql://door:32800", arrow.url);
		assertEquals("jwt", arrow.props.getProperty("token"));
		assertEquals("true", arrow.props.getProperty("disableCertificateVerification"));
		assertNull(driver.connect("jdbc:postgresql://x/y", info), "another driver's URL is not ours");
	}

	@Test
	void registeredWithDriverManager() throws SQLException {
		assertTrue(DriverManager.getDriver("jdbc:acl://door:1") instanceof AclDriver);
	}

	@Test
	void describesItsProperties() {
		DriverPropertyInfo[] props = new AclDriver().getPropertyInfo("jdbc:acl://d:1", new Properties());
		assertTrue(props.length >= 10);
		assertEquals("flow", props[0].name);
	}
}
