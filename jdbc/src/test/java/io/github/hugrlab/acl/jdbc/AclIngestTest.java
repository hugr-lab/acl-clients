package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hugrlab.acl.jdbc.AclIngest.Mode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.arrow.driver.jdbc.ArrowFlightJdbcDriver;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.CallHeaders;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.CallStatus;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.FlightMethod;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.FlightServerMiddleware;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.FlightServer;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.FlightStream;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.Location;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.PutResult;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.sql.NoOpFlightSqlProducer;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.sql.impl.FlightSql;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.sql.impl.FlightSql.CommandStatementIngest;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.sql.impl.FlightSql.CommandStatementIngest.TableDefinitionOptions.TableExistsOption;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.flight.sql.impl.FlightSql.CommandStatementIngest.TableDefinitionOptions.TableNotExistOption;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** spec 009: the ingest call as a Flight SQL server receives it, through Arrow's own connection. */
class AclIngestTest {
	/** What the server was sent: one command and its rows per call; a table named "refused" is refused. */
	static final class Recorder extends NoOpFlightSqlProducer {
		final List<CommandStatementIngest> commands = new ArrayList<>();
		final List<Long> rows = new ArrayList<>();
		/** A stream that ended in an error: the client cancelled it (or it broke) - the call is over either way. */
		final java.util.concurrent.CountDownLatch failed = new java.util.concurrent.CountDownLatch(1);
		final BufferAllocator allocator;

		Recorder(BufferAllocator allocator) {
			this.allocator = allocator;
		}

		@Override
		public Runnable acceptPutStatementBulkIngest(CommandStatementIngest command, CallContext context,
		                                             FlightStream stream, StreamListener<PutResult> ack) {
			return () -> {
				if (command.getTable().equals("refused")) {
					ack.onError(CallStatus.INVALID_ARGUMENT.withDescription("acl: a bulk load needs the ingest capability")
					                .toRuntimeException());
					return;
				}
				long n = 0;
				try {
					while (stream.next()) {
						n += stream.getRoot().getRowCount();
					}
				} catch (RuntimeException e) {
					failed.countDown();
					ack.onError(e);
					return;
				}
				commands.add(command);
				rows.add(n);
				byte[] result = FlightSql.DoPutUpdateResult.newBuilder().setRecordCount(n).build().toByteArray();
				try (ArrowBuf buf = allocator.buffer(result.length)) {
					buf.writeBytes(result);
					ack.onNext(PutResult.metadata(buf));
				}
				ack.onCompleted();
			};
		}
	}

	private RootAllocator allocator;
	private Recorder recorder;
	private FlightServer server;
	private Connection arrow;
	private final List<String> probes = new java.util.concurrent.CopyOnWriteArrayList<>();
	private final List<String> cookies = new java.util.concurrent.CopyOnWriteArrayList<>();

	@BeforeEach
	void serve() throws Exception {
		allocator = new RootAllocator();
		recorder = new Recorder(allocator);
		server = FlightServer.builder(allocator, Location.forGrpcInsecure("localhost", 0), recorder)
		             .middleware(FlightServerMiddleware.Key.of("probe"), (info, incoming, context) -> {
			             if (info.method() == FlightMethod.DO_PUT) {
				             probes.add(String.valueOf(incoming.get("x-acl-probe")));
				             cookies.add(String.valueOf(incoming.get("cookie")));
			             }
			             return new FlightServerMiddleware() {
				             @Override
				             public void onBeforeSendingHeaders(CallHeaders outgoing) {
					             outgoing.insert("set-cookie", "acl_session=s1"); // as the door hands out its session
				             }

				             @Override
				             public void onCallCompleted(CallStatus status) {
				             }

				             @Override
				             public void onCallErrored(Throwable err) {
				             }
			             };
		             })
		             .build()
		             .start();
		Properties p = new Properties();
		p.setProperty("useEncryption", "false");
		p.setProperty("x-acl-probe", "carried"); // Arrow sends a property it does not know as a header
		arrow = new ArrowFlightJdbcDriver().connect("jdbc:arrow-flight-sql://localhost:" + server.getPort(), p);
	}

	@AfterEach
	void stop() throws Exception {
		arrow.close();
		server.close();
		allocator.close();
	}

	/** Two batches of the ids, cut in the middle of the second: the schema and the first batch read, then a failure. */
	private static byte[] truncated(long... ids) throws Exception {
		byte[] one = stream(ids);
		byte[] two = streamTwice(ids);
		return java.util.Arrays.copyOf(two, one.length + (two.length - one.length) / 2);
	}

	private static byte[] streamTwice(long... ids) throws Exception {
		try (RootAllocator a = new RootAllocator(); BigIntVector id = new BigIntVector("id", a)) {
			id.allocateNew(ids.length);
			for (int i = 0; i < ids.length; i++) {
				id.set(i, ids[i]);
			}
			id.setValueCount(ids.length);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (VectorSchemaRoot root = VectorSchemaRoot.of(id); ArrowStreamWriter w = new ArrowStreamWriter(root, null, out)) {
				w.start();
				w.writeBatch();
				w.writeBatch();
				w.end();
			}
			return out.toByteArray();
		}
	}

	private static byte[] stream(long... ids) throws Exception {
		try (RootAllocator a = new RootAllocator(); BigIntVector id = new BigIntVector("id", a)) {
			id.allocateNew(ids.length);
			for (int i = 0; i < ids.length; i++) {
				id.set(i, ids[i]);
			}
			id.setValueCount(ids.length);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (VectorSchemaRoot root = VectorSchemaRoot.of(id); ArrowStreamWriter w = new ArrowStreamWriter(root, null, out)) {
				w.start();
				w.writeBatch();
				w.end();
			}
			return out.toByteArray();
		}
	}

	@Test
	void eachModeIsTheProtocolsOptionsAndOurs() throws Exception {
		AclConnection conn = new AclConnection(arrow, AclConfig.Mode.DATA, false);
		AclIngest ingest = conn.unwrap(AclIngest.class);
		assertSame(conn, ingest);
		assertTrue(conn.isWrapperFor(AclIngest.class));

		assertEquals(3, ingest.ingest("sales", "main", "stock", new ByteArrayInputStream(stream(1, 2, 2)), Mode.MERGE));
		assertEquals(1, ingest.ingest("stock", new ByteArrayInputStream(stream(1)), Mode.REMOVE));
		assertEquals(2, ingest.ingest("stock", new ByteArrayInputStream(stream(1, 2)), Mode.APPEND));
		ingest.ingest("made", new ByteArrayInputStream(stream(1)), Mode.CREATE);
		ingest.ingest("made", new ByteArrayInputStream(stream(1)), Mode.REPLACE);

		CommandStatementIngest merge = recorder.commands.get(0);
		assertEquals("stock", merge.getTable());
		assertEquals("sales", merge.getCatalog());
		assertEquals("main", merge.getSchema());
		assertEquals(Map.of("acl.mode", "merge"), merge.getOptionsMap());
		assertEquals(TableNotExistOption.TABLE_NOT_EXIST_OPTION_FAIL, merge.getTableDefinitionOptions().getIfNotExist());
		assertEquals(TableExistsOption.TABLE_EXISTS_OPTION_APPEND, merge.getTableDefinitionOptions().getIfExists());
		assertEquals(false, merge.getTemporary());

		CommandStatementIngest remove = recorder.commands.get(1);
		assertEquals(Map.of("acl.mode", "remove"), remove.getOptionsMap());
		assertEquals(false, remove.hasCatalog());
		assertEquals(false, remove.hasSchema());
		assertEquals(Map.of(), recorder.commands.get(2).getOptionsMap());
		assertEquals(TableExistsOption.TABLE_EXISTS_OPTION_APPEND,
		             recorder.commands.get(2).getTableDefinitionOptions().getIfExists());
		assertEquals(TableExistsOption.TABLE_EXISTS_OPTION_FAIL,
		             recorder.commands.get(3).getTableDefinitionOptions().getIfExists());
		assertEquals(TableNotExistOption.TABLE_NOT_EXIST_OPTION_CREATE,
		             recorder.commands.get(3).getTableDefinitionOptions().getIfNotExist());
		assertEquals(TableExistsOption.TABLE_EXISTS_OPTION_REPLACE,
		             recorder.commands.get(4).getTableDefinitionOptions().getIfExists());
		assertEquals(List.of(3L, 1L, 2L, 1L, 1L), recorder.rows);
		// the connection's call options travel: its headers (the bearer, x-acl-view-as) ride every put
		assertEquals(List.of("carried", "carried", "carried", "carried", "carried"), probes);
		// and the session's cookie: the first put earns it, every later one carries it (the connection's middleware)
		assertEquals(List.of("null", "acl_session=s1", "acl_session=s1", "acl_session=s1", "acl_session=s1"), cookies);
	}

	/** The review's finding: a stream that breaks after the put began cancels the call, so the session is free. */
	@Test
	void aStreamThatBreaksMidwayCancelsTheCall() throws Exception {
		AclIngest ingest = new AclConnection(arrow, AclConfig.Mode.DATA, false);
		SQLException e = assertThrows(SQLException.class,
		                              () -> ingest.ingest("stock", new ByteArrayInputStream(truncated(1, 2, 3)), Mode.MERGE));
		assertTrue(String.valueOf(e.getMessage()).startsWith("ingest: "), e.getMessage());
		assertTrue(recorder.failed.await(5, java.util.concurrent.TimeUnit.SECONDS), "the server saw the call end");
		assertEquals(List.of(), recorder.rows);
		assertEquals(2, ingest.ingest("stock", new ByteArrayInputStream(stream(1, 2)), Mode.APPEND));
	}

	@Test
	void aRefusalIsAnSqlExceptionWithTheNodesReason() throws Exception {
		AclIngest ingest = new AclConnection(arrow, AclConfig.Mode.DATA, false);
		SQLException e = assertThrows(SQLException.class,
		                              () -> ingest.ingest("refused", new ByteArrayInputStream(stream(1)), Mode.MERGE));
		assertTrue(String.valueOf(e.getMessage()).contains("needs the ingest capability"), e.getMessage());
		assertThrows(SQLException.class, () -> ingest.ingest("", new ByteArrayInputStream(stream(1)), Mode.APPEND));
		assertThrows(SQLException.class, () -> ingest.ingest("t", null, Mode.APPEND));
		assertThrows(SQLException.class, () -> ingest.ingest("t", new ByteArrayInputStream(new byte[] {1, 2}), Mode.APPEND));
	}
}
