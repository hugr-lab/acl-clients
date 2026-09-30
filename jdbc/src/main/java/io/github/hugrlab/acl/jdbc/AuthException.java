package io.github.hugrlab.acl.jdbc;

import java.sql.SQLInvalidAuthorizationSpecException;

/** A sign-in that did not produce a token. SQLSTATE 28000; the message never carries a credential. */
final class AuthException extends SQLInvalidAuthorizationSpecException {
	private static final long serialVersionUID = 1L;

	AuthException(String message) {
		super("duckdb-acl: " + message, "28000");
	}

	AuthException(String message, Throwable cause) {
		super("duckdb-acl: " + message, "28000", cause);
	}
}
