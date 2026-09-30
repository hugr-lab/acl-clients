package example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Properties;

/**
 * Connect to a duckdb-acl node's Flight SQL door with plain JDBC and read what the principal may see.
 * Authenticate with a bearer token (ACL_TOKEN) or a user name and password (ACL_USER / ACL_PASSWORD):
 * with a password the door runs the IdP's password grant for you and hands the token back.
 *
 * <pre>
 *   ACL_TOKEN=$(../../dev/token.sh) mvn -q compile exec:java
 *   ACL_USER=analyst2 ACL_PASSWORD=analyst2-pass mvn -q compile exec:java
 * </pre>
 *
 * ACL_HOST / ACL_PORT name the door (localhost:32800); ACL_INSECURE=1 skips certificate verification
 * (the dev node's self-signed certificate) - never in production.
 */
public final class Connect {
	public static void main(String[] args) throws Exception {
		String host = env("ACL_HOST", "localhost");
		String port = env("ACL_PORT", "32800");
		String sql = env("ACL_SQL", "SELECT id, tenant, amount FROM orders ORDER BY id");

		Properties props = new Properties();
		props.setProperty("useEncryption", "true");
		if ("1".equals(env("ACL_INSECURE", ""))) {
			props.setProperty("disableCertificateVerification", "true");
		}
		String token = env("ACL_TOKEN", "");
		if (!token.isEmpty()) {
			props.setProperty("token", token);
		} else {
			props.setProperty("user", env("ACL_USER", ""));
			props.setProperty("password", env("ACL_PASSWORD", ""));
		}

		String url = "jdbc:arrow-flight-sql://" + host + ":" + port;
		try (Connection conn = DriverManager.getConnection(url, props);
		     Statement stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery(sql)) {
			ResultSetMetaData meta = rs.getMetaData();
			while (rs.next()) {
				StringBuilder line = new StringBuilder();
				for (int c = 1; c <= meta.getColumnCount(); c++) {
					line.append(c > 1 ? "\t" : "").append(rs.getString(c));
				}
				System.out.println(line);
			}
		}
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.isEmpty() ? fallback : value;
	}
}
