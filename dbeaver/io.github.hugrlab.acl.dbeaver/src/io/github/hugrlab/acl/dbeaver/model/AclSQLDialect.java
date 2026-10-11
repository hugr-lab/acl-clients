package io.github.hugrlab.acl.dbeaver.model;

import org.jkiss.dbeaver.ext.generic.model.GenericSQLDialect;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCDatabaseMetaData;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSource;

/** duckdb SQL and the words of the ACL management grammar (duckdb-acl's management SQL). */
public class AclSQLDialect extends GenericSQLDialect {
	@Override
	public void initDriverSettings(JDBCSession session, JDBCDataSource dataSource, JDBCDatabaseMetaData metaData) {
		super.initDriverSettings(session, dataSource, metaData);
		addSQLKeywords(AclKeywords.WORDS);
	}

	@Override
	public boolean supportsAliasInSelect() {
		return true;
	}
}
