package io.github.hugrlab.acl.jdbc;

import java.io.IOException;
import java.util.List;

/** Asks the door which issuers it trusts and with which client id - before any credential exists. */
interface Discovery {
	List<DoorIssuer> discover(AclConfig config) throws IOException;
}
