package org.jkiss.dbeaver.ext.acl.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.junit.Test;

/** Spec 003 addendum: the Main page's "trust the node's certificate" reaches the driver as its property. */
public class TrustCertificateTest {
	@Test
	public void theChoiceBecomesTheDriversProperty() {
		DBPConnectionConfiguration configuration = new DBPConnectionConfiguration();
		assertFalse(AclDataSource.trustsCertificate(configuration));
		assertEquals(Map.of(), AclDataSource.internalProperties(configuration));

		configuration.setProviderProperty(AclDataSource.PROP_TRUST_CERTIFICATE, "true");
		assertTrue(AclDataSource.trustsCertificate(configuration));
		assertEquals(Map.of("disableCertificateVerification", "true"), AclDataSource.internalProperties(configuration));

		configuration.removeProviderProperty(AclDataSource.PROP_TRUST_CERTIFICATE);
		assertEquals(Map.of(), AclDataSource.internalProperties(configuration));
	}
}
