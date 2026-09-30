package io.github.hugrlab.acl.jdbc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tokens kept between connections, keyed by issuer, client and who signed in. The memory cache lives
 * as long as the JVM (a DBeaver session): the next connection reuses the access token, or refreshes it
 * silently. The file cache also survives a restart - a file only its owner can read, never logged.
 */
abstract class TokenCache {
	private static final Map<String, Tokens> MEMORY = new ConcurrentHashMap<>();

	abstract Tokens get(String key);

	abstract void put(String key, Tokens tokens);

	abstract void remove(String key);

	static TokenCache of(AclConfig config) {
		switch (config.cache) {
		case NONE:
			return new None();
		case FILE:
			return new FileBacked(config.cacheFile);
		default:
			return new Memory();
		}
	}

	static String key(String issuer, String clientId, String who) {
		return issuer + "|" + clientId + "|" + who;
	}

	static void clearMemory() {
		MEMORY.clear();
	}

	static final class None extends TokenCache {
		@Override
		Tokens get(String key) {
			return null;
		}

		@Override
		void put(String key, Tokens tokens) {
		}

		@Override
		void remove(String key) {
		}
	}

	static class Memory extends TokenCache {
		@Override
		Tokens get(String key) {
			return MEMORY.get(key);
		}

		@Override
		void put(String key, Tokens tokens) {
			MEMORY.put(key, tokens);
		}

		@Override
		void remove(String key) {
			MEMORY.remove(key);
		}
	}

	static final class FileBacked extends Memory {
		private static final Object LOCK = new Object();
		private final Path file;

		FileBacked(Path file) {
			this.file = file;
		}

		@Override
		Tokens get(String key) {
			Tokens memory = super.get(key);
			if (memory != null) {
				return memory;
			}
			synchronized (LOCK) {
				Object entry = read().get(key);
				return entry instanceof Map<?, ?> m ? Tokens.fromJson(m) : null;
			}
		}

		@Override
		void put(String key, Tokens tokens) {
			super.put(key, tokens);
			synchronized (LOCK) {
				Map<String, Object> all = read();
				all.put(key, tokens.toJson());
				write(all);
			}
		}

		@Override
		void remove(String key) {
			super.remove(key);
			synchronized (LOCK) {
				Map<String, Object> all = read();
				if (all.remove(key) != null) {
					write(all);
				}
			}
		}

		private Map<String, Object> read() {
			try {
				if (Files.isRegularFile(file)) {
					return new HashMap<>(Http.parse(Files.readAllBytes(file)));
				}
			} catch (IOException e) {
				// an unreadable cache is an empty one: the flow runs again
			}
			return new HashMap<>();
		}

		private void write(Map<String, Object> all) {
			try {
				Files.createDirectories(file.toAbsolutePath().getParent());
				Path tmp = Files.createTempFile(file.toAbsolutePath().getParent(), ".tokens", ".tmp");
				ownerOnly(tmp);
				Files.writeString(tmp, Http.write(all));
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				// the memory cache still holds it; the file is a convenience
			}
		}

		private static void ownerOnly(Path path) {
			try {
				Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
			} catch (UnsupportedOperationException | IOException e) {
				// not POSIX (Windows): the file sits in the user's profile, which only the user reads
			}
		}
	}
}
