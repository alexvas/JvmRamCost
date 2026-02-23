package jvmram.db.boot.impl;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jvmram.db.boot.BootSessionFacade;
import jvmram.db.boot.BootSessionInfo;
import jvmram.db.datasource.DataSourceFacade;
import jvmram.db.machine.MachineIdFacade;
import jvmram.model.data.Os;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static jvmram.db.utils.Utils.invokeExact;
import static jvmram.db.utils.Utils.readContent;

@Singleton
public class BootSessionFacadeImpl implements BootSessionFacade {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final DataSourceFacade dataSourceFacade;
    private final BootSessionInfo bootSessionInfo;

    @Inject
    BootSessionFacadeImpl(
            MachineIdFacade machineIdFacade,
            DataSourceFacade dataSourceFacade
    ) {
        this.dataSourceFacade = dataSourceFacade;
        var machineId = machineIdFacade.getMachineId(Os.current());
        bootSessionInfo = createAndSaveInfo(machineId);
    }

    @Override
    public BootSessionInfo info() {
        return bootSessionInfo;
    }

    private BootSessionInfo createAndSaveInfo(UUID machineId) {
        var os = Os.current();
        var hostname = getHostname(os);
        var bootId = getBootId(os);
        final int id;
        try {
            id = saveInDb(os, hostname, machineId, bootId);
        } catch (SQLException e) {
            LOG.error("Failed to save bootId {} in os {} with hostname {} and machineId {}", bootId, os, hostname, machineId, e);
            throw new IllegalStateException("Failed to save boot info", e);
        }
        return new BootSessionInfo(id, os, hostname, "", machineId, bootId);
    }

    private String getHostname(Os os) {
        return switch (os) {
            case LINUX -> getLinuxHostname();
            case WINDOWS -> System.getenv("COMPUTERNAME");
        };
    }

    private String getLinuxHostname() {
        // 1. Пробуем InetAddress
        try {
            var host = InetAddress.getLocalHost().getHostName();
            if (host != null && !host.equals("localhost")) {
                return host;
            }
        } catch (UnknownHostException ignored) {
        }

        // 2. Пробуем /etc/hostname
        var hostnamePath = Path.of("/etc/hostname");
        var fromFile = readContent(hostnamePath);
        if (fromFile != null) {
            return fromFile;
        }

        // 3. Fallback на переменную окружения
        String env = System.getenv("HOSTNAME");
        return env != null ? env : "unknown";
    }

    private String getBootId(Os os) {
        return switch (os) {
            case LINUX -> getLinuxBootId();
            case WINDOWS -> getWindowsBootId();
        };
    }

    private String getLinuxBootId() {
        return readContent(Path.of("/proc/sys/kernel/random/boot_id"));
    }

    private static final DateTimeFormatter BOOT_ID_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm");

    private String getWindowsBootId() {
        //GetTickCount64 возвращает миллисекунды с момента загрузки Windows
        var linker = Linker.nativeLinker();
        var kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());

        var getTickCount64 = linker.downcallHandle(
                kernel32.find("GetTickCount64").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG)
        );

        long uptimeMs = (long) invokeExact(getTickCount64);
        long bootTimeMs = System.currentTimeMillis() - uptimeMs;
        var bootTime = Instant.ofEpochMilli(bootTimeMs);

        return BOOT_ID_FORMAT.format(bootTime);
    }

    private int saveInDb(Os os, String hostname, UUID machineId, String bootId) throws SQLException {
        try (var c = dataSourceFacade.getConnection()) {
            if (c == null) {
                throw new IllegalStateException(
                        "No connection to DB saving boot info os %s, hostname %s, machineId %s, bootId %s".formatted(
                                os,
                                hostname,
                                machineId,
                                bootId
                        )
                );
            }
            // Сначала проверяем, есть ли уже запись с такой парой machine_id и boot_id
            try (var selectStmt = c.prepareStatement(
                    // language=SQL
                    "SELECT id FROM jvm_ram_cost_boot_session WHERE machine_id = ? AND boot_id = ?"
            )
            ) {
                selectStmt.setObject(1, machineId);
                selectStmt.setString(2, bootId);
                try (var rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            // Не нашли — вставляем новую запись
            try (var insertStmt = c.prepareStatement(
                    // language=SQL
                    "INSERT INTO jvm_ram_cost_boot_session (os_type, hostname, alias, machine_id, boot_id) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, os.name().toLowerCase());
                insertStmt.setString(2, hostname);
                insertStmt.setString(3, "");
                insertStmt.setObject(4, machineId);
                insertStmt.setString(5, bootId);
                insertStmt.executeUpdate();
                try (var rs = insertStmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    throw new SQLException("Failed to get generated key for boot session");
                }
            }
        }
    }
}
