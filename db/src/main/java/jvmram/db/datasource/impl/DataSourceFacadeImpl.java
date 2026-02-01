package jvmram.db.datasource.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jvmram.db.boot.Os;
import jvmram.db.datasource.DataSourceFacade;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static java.nio.file.Files.createDirectories;
import static jvmram.db.utils.Utils.readResource;

public class DataSourceFacadeImpl implements DataSourceFacade {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String DB_FILE_DIR = "h2_db";
    private static final String DIR_PLACEHOLDER = "{DIR_CANONICAL_PATH}";
    private static final String ADDRESS = "jdbc:h2:file:" + DIR_PLACEHOLDER + ";MODE=PostgreSQL;AUTO_SERVER=TRUE;WRITE_DELAY=10;DB_CLOSE_DELAY=-1";

    private final javax.sql.DataSource ds = new HikariDataSource(createConfig());

    private final AtomicReference<State> state = new AtomicReference<>(State.INITIAL);

    @Override
    public @Nullable Connection getConnection() {
        var s = state.get();
        if (s == State.READY) {
            return doGetConnection();
        }
        if (s == State.BROKEN) {
            return null;
        }

        while (true) {
            if (state.compareAndSet(State.INITIAL, State.INITIALIZING)) {
                boolean success = initDb();
                if (success) {
                    state.set(State.READY);
                    return doGetConnection();
                } else {
                    state.set(State.BROKEN);
                    return null;
                }
            }

            waitABit();

            s = state.get();
            if (s == State.READY) {
                return doGetConnection();
            }
            if (s == State.BROKEN) {
                return null;
            }
        }
    }

    private static final Duration WAIT_A_BIT = Duration.ofMillis(2);

    private void waitABit() {
        LockSupport.parkNanos(WAIT_A_BIT.toNanos());
    }

    private Connection doGetConnection() {
        try {
            return ds.getConnection();
        } catch (SQLException e) {
            state.set(State.BROKEN);
            LOG.error("Failed to get connection to db {}", getJdbcUri(), e);
            return null;
        }
    }

    private boolean initDb() {
        try (var c = doGetConnection()) {
            if (c == null) {
                return false;
            }
            return initDb(c);
        } catch (SQLException e) {
            LOG.error("Failed to init DB", e);
            return false;
        }
    }

    private static final String INIT = "/db1/init.sql";

    private boolean initDb(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            var result = statement.executeQuery(
                    // lang = sql
                    "SELECT max(version) FROM jvm_ram_cost_metadata"
            );
            while (result.next()) {
                var version = result.getInt(0);
                if (version == 1) {
                    return true;
                }
            }
        } catch (SQLException e) {
            LOG.info("Db was not initialized, initializing");
        }

        var initSql = readResource(INIT);
        if (initSql == null) {
            LOG.error("No init file found in {}", INIT);
            return false;
        }
        try (var statement = connection.createStatement()) {
            return statement.execute(initSql);
        }
    }

    private HikariConfig createConfig() {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(getJdbcUri());
        cfg.setUsername("sa");
        cfg.setPassword("");
        return cfg;
    }

    private String getJdbcUri() {
        var dbDir = jvmRamCostDataPath().resolve(DB_FILE_DIR).toFile();
        if (!dbDir.exists()) {
            createDbDir(dbDir);
        }
        if (!dbDir.canRead()) {
            throw new IllegalStateException("Directory %s is not readable".formatted(dbDir));
        }
        if (!dbDir.canWrite()) {
            throw new IllegalStateException("Directory %s is not writable".formatted(dbDir));
        }
        final String canonical;
        try {
            canonical = dbDir.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to get canonical path from directory %s".formatted(dbDir), e);
        }

        return ADDRESS.replace(DIR_PLACEHOLDER, canonical);
    }

    private void createDbDir(File dir) {
        try {
            createDirectories(dir.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create directory %s".formatted(dir), e);
        }
    }

    private Path jvmRamCostDataPath() {
        var home = Path.of(System.getProperty("user.home"));
        return switch (Os.current()) {
            case LINUX -> home.resolve(".jvm-ram-cost");
            case WINDOWS -> getWindowsAppDataPath(home).resolve("jvm-ram-cost");
        };
    }

    private Path getWindowsAppDataPath(Path home) {
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            return Path.of(appData);
        }
        // fallback (редко, но бывает в сервисах)
        return home.resolve("AppData").resolve("Roaming");
    }

    private enum State {
        INITIAL,
        INITIALIZING,
        READY,
        BROKEN
    }

    DataSourceFacadeImpl() {
    }
}
