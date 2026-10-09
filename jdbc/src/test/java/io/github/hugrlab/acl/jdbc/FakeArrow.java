package io.github.hugrlab.acl.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A stand-in for Arrow's connection: records every text it is handed and answers queries from a
 * script (columns + rows per statement). Enough JDBC to drive {@link AclConnection} and
 * {@link AclDatabaseMetaData} without a node.
 */
final class FakeArrow {
	/** A scripted answer: column labels and rows. */
	record Answer(List<String> columns, List<Object[]> rows) {
		static Answer empty() {
			return new Answer(List.of(), List.of());
		}
	}

	final List<String> sent = new ArrayList<>();
	final List<String> prepared = new ArrayList<>();
	final List<String> nativeSql = new ArrayList<>();
	private final Function<String, Answer> script;

	FakeArrow(Function<String, Answer> script) {
		this.script = script;
	}

	Connection connection() {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
			case "createStatement" -> statement(null, Statement.class);
			case "prepareStatement", "prepareCall" -> {
				prepared.add((String) a[0]);
				yield statement((String) a[0], m.getName().equals("prepareCall") ? CallableStatement.class
				                                                                  : PreparedStatement.class);
			}
			case "getMetaData" -> proxy(DatabaseMetaData.class, (q, n, b) -> {
				throw new UnsupportedOperationException("Arrow's metadata: " + n.getName());
			});
			case "nativeSQL" -> {
				nativeSql.add((String) a[0]);
				yield a[0];
			}
			case "isClosed", "isReadOnly" -> false;
			case "close" -> null;
			default -> throw new UnsupportedOperationException(m.getName());
		});
	}

	private Object statement(String preparedSql, Class<?> type) {
		ResultSet[] last = new ResultSet[1];
		return proxy(type, (p, m, a) -> switch (m.getName()) {
			case "executeQuery" -> last[0] = resultSet(run(a == null ? preparedSql : (String) a[0]));
			case "execute" -> {
				last[0] = resultSet(run(a == null ? preparedSql : (String) a[0]));
				yield true;
			}
			case "executeUpdate" -> {
				run(a == null ? preparedSql : (String) a[0]);
				yield 0;
			}
			case "addBatch" -> {
				sent.add((String) a[0]);
				yield null;
			}
			case "getResultSet" -> last[0];
			case "close" -> null;
			default -> throw new UnsupportedOperationException(m.getName());
		});
	}

	private Answer run(String sql) {
		sent.add(sql);
		Answer answer = script.apply(sql);
		return answer == null ? Answer.empty() : answer;
	}

	private static ResultSet resultSet(Answer answer) {
		int[] row = {-1};
		ResultSetMetaData meta = proxy(ResultSetMetaData.class, (p, m, a) -> switch (m.getName()) {
			case "getColumnCount" -> answer.columns().size();
			case "getColumnLabel", "getColumnName" -> answer.columns().get((Integer) a[0] - 1);
			default -> throw new UnsupportedOperationException(m.getName());
		});
		return proxy(ResultSet.class, (p, m, a) -> switch (m.getName()) {
			case "next" -> ++row[0] < answer.rows().size();
			case "getMetaData" -> meta;
			case "getObject" -> answer.rows().get(row[0])[(Integer) a[0] - 1];
			case "getString" -> {
				Object v = answer.rows().get(row[0])[(Integer) a[0] - 1];
				yield v == null ? null : v.toString();
			}
			case "close" -> null;
			default -> throw new UnsupportedOperationException(m.getName());
		});
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(FakeArrow.class.getClassLoader(), new Class<?>[] {type}, handler);
	}
}
