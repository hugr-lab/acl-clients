// Generates the Forwarding*.java and UnsupportedResultSet.java bases of the driver (spec 006) from the
// JDK's java.sql interfaces. Run from jdbc/ in a JDK container, then copy out/*.java into the package:
//   docker run --rm -v "$PWD/tools":/w -w /w eclipse-temurin:17 bash -c "java GenForwarding.java"

import java.lang.reflect.*;
import java.util.*;
import java.nio.file.*;

public class GenForwarding {
	static String t(Type type) {
		String s = type.getTypeName();
		s = s.replaceAll("java\\.lang\\.([A-Z][A-Za-z]*)", "$1");
		s = s.replace("$", ".");
		return s;
	}

	static boolean withDefaults = true;
	static List<Method> methods(Class<?> c, boolean inherited) {
		List<Method> out = new ArrayList<>();
		Method[] ms = inherited ? c.getMethods() : c.getDeclaredMethods();
		for (Method m : ms) {
			if ((m.isDefault() && !withDefaults) || Modifier.isStatic(m.getModifiers()) || m.isSynthetic()) continue;
			if (!inherited && !m.getDeclaringClass().equals(c)) continue;
			out.add(m);
		}
		out.sort(Comparator.comparing(Method::getName).thenComparing(m -> Arrays.toString(m.getParameterTypes())));
		// dedupe by signature (getMethods may return the same sig from two interfaces)
		Map<String, Method> seen = new LinkedHashMap<>();
		for (Method m : out) seen.putIfAbsent(m.getName() + Arrays.toString(m.getParameterTypes()), m);
		return new ArrayList<>(seen.values());
	}

	static String sig(Method m) {
		StringBuilder b = new StringBuilder("\t@Override\n\tpublic ");
		TypeVariable<Method>[] tv = m.getTypeParameters();
		if (tv.length > 0) {
			b.append("<");
			for (int i = 0; i < tv.length; i++) b.append(i > 0 ? ", " : "").append(tv[i].getName());
			b.append("> ");
		}
		b.append(t(m.getGenericReturnType())).append(" ").append(m.getName()).append("(");
		Type[] ps = m.getGenericParameterTypes();
		for (int i = 0; i < ps.length; i++) b.append(i > 0 ? ", " : "").append(t(ps[i])).append(" p").append(i);
		b.append(")");
		if (m.getExceptionTypes().length > 0) {
			b.append(" throws ");
			Class<?>[] ex = m.getExceptionTypes();
			for (int i = 0; i < ex.length; i++) b.append(i > 0 ? ", " : "").append(t(ex[i]));
		}
		return b.toString();
	}

	static String args(Method m) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < m.getParameterCount(); i++) b.append(i > 0 ? ", " : "").append("p").append(i);
		return b.toString();
	}

	static final String HEADER = "package io.github.hugrlab.acl.jdbc;\n\n// GENERATED from the JDK's java.sql interfaces (spec 006): every method %s.\n// Regenerate rather than edit; the behaviour lives in the subclasses.\n\n";

	static void forwarding(Class<?> iface, String name, String typeParam, String superClass, String delegateType, boolean inherited) throws Exception {
		StringBuilder b = new StringBuilder(String.format(HEADER, "forwards to the delegate"));
		b.append("@SuppressWarnings({\"deprecation\", \"unused\"})\n");
		b.append("abstract class ").append(name);
		if (typeParam != null) b.append("<").append(typeParam).append(">");
		if (superClass != null) b.append(" extends ").append(superClass);
		b.append(" implements ").append(iface.getName()).append(" {\n");
		if (superClass == null) {
			b.append("\tprotected final ").append(delegateType).append(" delegate;\n\n");
			b.append("\tprotected ").append(name).append("(").append(delegateType).append(" delegate) {\n\t\tthis.delegate = delegate;\n\t}\n");
		} else {
			b.append("\tprotected ").append(name).append("(").append(delegateType).append(" delegate) {\n\t\tsuper(delegate);\n\t}\n");
		}
		for (Method m : methods(iface, inherited)) {
			b.append("\n").append(sig(m)).append(" {\n\t\t");
			if (m.getReturnType() != void.class) b.append("return ");
			b.append("delegate.").append(m.getName()).append("(").append(args(m)).append(");\n\t}\n");
		}
		b.append("}\n");
		Files.writeString(Path.of("out", name + ".java"), b.toString());
	}

	static void unsupported(Class<?> iface, String name) throws Exception {
		StringBuilder b = new StringBuilder(String.format(HEADER, "refuses with SQLFeatureNotSupportedException"));
		b.append("@SuppressWarnings({\"deprecation\", \"unused\"})\n");
		b.append("abstract class ").append(name).append(" implements ").append(iface.getName()).append(" {\n");
		b.append("\tprotected static java.sql.SQLFeatureNotSupportedException unsupported(String what) {\n");
		b.append("\t\treturn new java.sql.SQLFeatureNotSupportedException(what + \" is not supported on a metadata result\");\n\t}\n");
		for (Method m : methods(iface, true)) {
			b.append("\n").append(sig(m)).append(" {\n\t\tthrow unsupported(\"").append(m.getName()).append("\");\n\t}\n");
		}
		b.append("}\n");
		Files.writeString(Path.of("out", name + ".java"), b.toString());
	}

	public static void main(String[] a) throws Exception {
		Files.createDirectories(Path.of("out"));
		forwarding(java.sql.Connection.class, "ForwardingConnection", null, null, "java.sql.Connection", true);
		forwarding(java.sql.DatabaseMetaData.class, "ForwardingDatabaseMetaData", null, null, "java.sql.DatabaseMetaData", true);
		forwarding(java.sql.ResultSet.class, "ForwardingResultSet", null, null, "java.sql.ResultSet", true);
		forwarding(java.sql.ResultSetMetaData.class, "ForwardingResultSetMetaData", null, null, "java.sql.ResultSetMetaData", true);
		forwarding(java.sql.Statement.class, "ForwardingStatement", "S extends java.sql.Statement", null, "S", true);
		forwarding(java.sql.PreparedStatement.class, "ForwardingPreparedStatement", "S extends java.sql.PreparedStatement", "ForwardingStatement<S>", "S", false);
		forwarding(java.sql.CallableStatement.class, "ForwardingCallableStatement", "S extends java.sql.CallableStatement", "ForwardingPreparedStatement<S>", "S", false);
		withDefaults = false;
		unsupported(java.sql.ResultSet.class, "UnsupportedResultSet");
	}
}
