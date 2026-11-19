package io.github.hugrlab.acl.jdbc;

import org.apache.arrow.driver.jdbc.ArrowFlightConnection;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.CallOption;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.sql.FlightSqlClient;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.sql.FlightSqlClient.ExecuteIngestOptions;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.sql.impl.FlightSql.CommandStatementIngest.TableDefinitionOptions;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.sql.impl.FlightSql.CommandStatementIngest.TableDefinitionOptions.TableExistsOption;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.sql.impl.FlightSql.CommandStatementIngest.TableDefinitionOptions.TableNotExistOption;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.vector.ipc.ArrowStreamReader;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * The bulk ingest call (spec 009), on the Flight SQL client Arrow's connection already holds: its middleware keeps
 * the session's cookie and its call options carry the bearer and the headers ({@code x-acl-view-as}), so the load
 * is the connection's own. Arrow keeps that client private (its JDBC driver has no ingest), so it is reached by
 * reflection - pinned with the driver's Arrow version like {@link FlightDiscovery}'s shaded names, and failing by
 * name if Arrow moves it.
 */
final class FlightIngest {
	/** duckdb-acl spec 173's option: a load by the target's key. */
	static final String MODE_OPTION = "acl.mode";

	private FlightIngest() {
	}

	/** The protocol's table options and the node's option for a mode. */
	record Command(TableDefinitionOptions table, Map<String, String> options) {
	}

	static Command command(AclIngest.Mode mode) {
		TableDefinitionOptions.Builder table = TableDefinitionOptions.newBuilder();
		Map<String, String> options = Map.of();
		switch (mode) {
		case CREATE -> table.setIfNotExist(TableNotExistOption.TABLE_NOT_EXIST_OPTION_CREATE)
		                   .setIfExists(TableExistsOption.TABLE_EXISTS_OPTION_FAIL);
		case REPLACE -> table.setIfNotExist(TableNotExistOption.TABLE_NOT_EXIST_OPTION_CREATE)
		                    .setIfExists(TableExistsOption.TABLE_EXISTS_OPTION_REPLACE);
		default -> {
			// append, and the loads by key: the node takes them only over an existing table, appended to
			table.setIfNotExist(TableNotExistOption.TABLE_NOT_EXIST_OPTION_FAIL)
			    .setIfExists(TableExistsOption.TABLE_EXISTS_OPTION_APPEND);
			if (mode == AclIngest.Mode.MERGE) {
				options = Map.of(MODE_OPTION, "merge");
			} else if (mode == AclIngest.Mode.REMOVE) {
				options = Map.of(MODE_OPTION, "remove");
			}
		}
		}
		return new Command(table.build(), options);
	}

	static long ingest(Connection arrow, String catalog, String schema, String table, InputStream rows,
	                   AclIngest.Mode mode) throws SQLException {
		if (table == null || table.isEmpty()) {
			throw new SQLException("ingest: a table name is required");
		}
		if (rows == null || mode == null) {
			throw new SQLException("ingest: an Arrow stream and a mode are required");
		}
		Object handler = handlerOf(arrow);
		FlightSqlClient client = (FlightSqlClient) read(handler, "sqlClient");
		CallOption[] options = (CallOption[]) call(handler, "getOptions");
		Command command = command(mode);
		ExecuteIngestOptions ingest = new ExecuteIngestOptions(table, command.table(), false, blankToNull(catalog),
		                                                       blankToNull(schema), command.options());
		try (RootAllocator allocator = new RootAllocator(); ArrowStreamReader reader = new ArrowStreamReader(rows,
		                                                                                                    allocator)) {
			return client.executeIngest(reader, ingest, options);
		} catch (RuntimeException e) {
			// a node's refusal arrives as Arrow's FlightRuntimeException: its message is the door's reason
			throw new SQLException(e.getMessage(), e);
		} catch (Exception e) {
			throw new SQLException("ingest: " + e.getMessage(), e);
		}
	}

	private static String blankToNull(String s) {
		return s == null || s.isEmpty() ? null : s;
	}

	private static Object handlerOf(Connection arrow) throws SQLException {
		ArrowFlightConnection connection = arrow.unwrap(ArrowFlightConnection.class);
		return call(connection, "getClientHandler");
	}

	private static Object read(Object target, String name) throws SQLException {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException | RuntimeException e) {
			throw moved(target, name, e);
		}
	}

	private static Object call(Object target, String name) throws SQLException {
		Method method;
		try {
			method = target.getClass().getDeclaredMethod(name);
			method.setAccessible(true);
		} catch (ReflectiveOperationException | RuntimeException e) {
			throw moved(target, name, e);
		}
		try {
			return method.invoke(target);
		} catch (InvocationTargetException e) {
			throw new SQLException("ingest: " + e.getCause().getMessage(), e.getCause());
		} catch (ReflectiveOperationException e) {
			throw moved(target, name, e);
		}
	}

	private static SQLException moved(Object target, String name, Exception e) {
		return new SQLException("ingest: Arrow's Flight SQL driver no longer has " + target.getClass().getSimpleName()
		                        + "." + name + " - the driver is built against another Arrow version", e);
	}
}
