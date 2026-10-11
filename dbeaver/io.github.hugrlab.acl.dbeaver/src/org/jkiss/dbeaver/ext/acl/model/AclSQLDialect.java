package org.jkiss.dbeaver.ext.acl.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.ext.generic.model.GenericSQLDialect;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCDatabaseMetaData;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSource;
import org.jkiss.dbeaver.model.sql.parser.rules.SQLDollarQuoteRule;
import org.jkiss.dbeaver.model.text.parser.TPRule;
import org.jkiss.dbeaver.model.text.parser.TPRuleProvider;

/** duckdb SQL and the words of the ACL management grammar; `$$ ... $$` is a string, as in DBeaver's DuckDB dialect. */
public class AclSQLDialect extends GenericSQLDialect implements TPRuleProvider {
	@Override
	public void initDriverSettings(JDBCSession session, JDBCDataSource dataSource, JDBCDatabaseMetaData metaData) {
		super.initDriverSettings(session, dataSource, metaData);
		addSQLKeywords(AclKeywords.WORDS);
	}

	/** Our grammar's words are highlighted, not reserved: a plain lower-case name of one is written as is. */
	@Override
	public boolean mustBeQuoted(@NotNull String str, boolean forceCaseSensitive) {
		if (AclKeywords.contains(str) && str.matches("[a-z_][a-z0-9_]*")) {
			return false;
		}
		return super.mustBeQuoted(str, forceCaseSensitive);
	}

	@NotNull
	@Override
	public TPRule[] extendRules(@Nullable DBPDataSourceContainer dataSource, @NotNull RulePosition position) {
		if (position == RulePosition.INITIAL || position == RulePosition.PARTITION) {
			return new TPRule[] {new SQLDollarQuoteRule(position == RulePosition.PARTITION, true, false, false)};
		}
		return new TPRule[0];
	}

	@Override
	public boolean supportsAliasInSelect() {
		return true;
	}
}
