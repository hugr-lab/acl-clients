package io.github.hugrlab.acl.jdbc;

import java.io.IOException;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLHandshakeException;

/**
 * The door's TLS certificate is not trusted (spec 003 addendum): said as such, with what to set - not as a discovery
 * failure, which sent a user to the issuer and clientId properties that cannot help.
 */
final class UntrustedDoorException extends IOException {
	UntrustedDoorException(String host, int port, Throwable cause) {
		super("the door at " + host + ":" + port + " presented a certificate this machine does not trust (" + rootOf(cause)
		          + ") - add the CA that signed it to the trust store or name it with tlsRootCerts=<PEM file>; "
		          + "disableCertificateVerification=true is for a development node only",
		    cause);
	}

	/** Whether a failure is the certificate check: a handshake or a certificate path in its causes. */
	static boolean isCertificateFailure(Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof SSLHandshakeException || t instanceof CertPathBuilderException
			    || t instanceof CertPathValidatorException || t instanceof CertificateException) {
				return true;
			}
		}
		return false;
	}

	private static String rootOf(Throwable error) {
		Throwable root = error;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
	}
}
