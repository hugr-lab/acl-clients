package io.github.hugrlab.acl.dbeaver.tests;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.junit.Test;
import org.junit.runner.RunWith;

/** What each expansion of the tree asks the node: the driver's metadata listings per level (FINE log). */
@RunWith(SWTBotJunit4ClassRunner.class)
public class LiveExpansionTest {
	static final List<String> LISTINGS = new ArrayList<>();

	static DBNNode child(DBNNode node, String name) throws Exception {
		for (DBNNode each : node.getChildren(new VoidProgressMonitor())) {
			if (each.getNodeDisplayName().equals(name)) {
				return each;
			}
		}
		throw new AssertionError("no " + name + " under " + node.getNodeDisplayName());
	}

	static String step(StringBuilder out, String title, DBNNode node) throws Exception {
		LISTINGS.clear();
		long t0 = System.nanoTime();
		DBNNode[] children = node.getChildren(new VoidProgressMonitor());
		long ms = (System.nanoTime() - t0) / 1_000_000;
		out.append("== ").append(title).append(": ").append(ms).append(" ms, ").append(children.length)
		    .append(" children, ").append(LISTINGS.size()).append(" listings\n");
		for (String l : LISTINGS) {
			out.append("   ").append(l).append("\n");
		}
		return title;
	}

	@Test
	public void eachLevelAsksForItself() throws Exception {
		LiveTreeTest.assumeNode();
		Logger log = Logger.getLogger("io.github.hugrlab.acl.jdbc.AclDatabaseMetaData");
		log.setLevel(Level.FINE);
		Handler capture = new Handler() {
			@Override
			public void publish(LogRecord record) {
				synchronized (LISTINGS) {
					LISTINGS.add(record.getMessage());
				}
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		};
		capture.setLevel(Level.FINE);
		log.addHandler(capture);
		StringBuilder out = new StringBuilder();
		LISTINGS.clear();
		long t0 = System.nanoTime();
		DBPDataSourceContainer container = LiveTreeTest.connect("analyst1", "analyst1-pass");
		out.append("== connect: ").append((System.nanoTime() - t0) / 1_000_000).append(" ms, ").append(LISTINGS.size())
		    .append(" listings\n");
		for (String l : LISTINGS) {
			out.append("   ").append(l).append("\n");
		}
		DBNNode root = DBWorkbench.getPlatform().getNavigatorModel().getNodeByObject(container);
		step(out, "catalogs", root);
		DBNNode sales = child(root, "sales");
		step(out, "sales: schemas", sales);
		DBNNode main = child(sales, "main");
		step(out, "sales.main: folders", main);
		DBNNode tables = child(main, "Tables");
		step(out, "sales.main Tables", tables);
		DBNNode customers = child(tables, "customers");
		step(out, "customers: folders", customers);
		step(out, "customers Columns", child(customers, "Columns"));
		step(out, "customers Keys", child(customers, "Keys"));
		step(out, "customers Foreign Keys", child(customers, "Foreign Keys"));
		step(out, "customers References", child(customers, "References"));
		step(out, "sales.main Views", child(main, "Views"));
		step(out, "sales.main Functions", child(main, "Functions"));
		DBNNode orders = child(tables, "orders");
		step(out, "orders: folders", orders);
		step(out, "orders Columns", child(orders, "Columns"));
		log.removeHandler(capture);
		Files.writeString(Path.of("target", "expansion.txt"), out.toString(), StandardCharsets.UTF_8);
	}
}
