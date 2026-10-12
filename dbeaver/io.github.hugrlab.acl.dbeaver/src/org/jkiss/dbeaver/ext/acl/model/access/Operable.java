package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;

/**
 * An object of the Access control branch with operations beyond its properties (spec 008.2 / 008.3): a finding's repair,
 * a schema's refresh, a session's kill and levels, the node's drain, a drift entry into the profile. Each operation is
 * the statements it offers - shown, chosen, completed and run by one dialog - and the platform function they compile
 * to (offered where it is callable; the node judges regardless).
 */
public interface Operable {
	/** The statements the operation offers, the first the default; empty = not this object's. */
	List<String> operation(String kind);

	String operationTitle(String kind);

	String operationExplanation(String kind);

	/** The platform function the operation compiles to; null = judged by the node alone. */
	String operationFunction(String kind);
}
