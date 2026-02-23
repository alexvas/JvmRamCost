package jvmram.db.pid.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jvmram.db.boot.BootSessionInfo;
import jvmram.db.datasource.DataSourceFacade;
import jvmram.db.pid.ProcessInfo;
import jvmram.db.pid.ProcessInfoFacade;
import jvmram.model.data.ProcessInfoInput;
import jvmram.db.pid.ProcessState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.function.Supplier;

@Singleton
public class ProcessInfoFacadeImpl implements ProcessInfoFacade {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Cache<@NonNull Long, ProcessInfo> infos = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(5))
            .build();

    private final DataSourceFacade dataSourceFacade;

    @Inject
    ProcessInfoFacadeImpl(DataSourceFacade dataSourceFacade) {
        this.dataSourceFacade = dataSourceFacade;
    }

    @Override
    public ProcessInfo getProcessInfo(
            BootSessionInfo bootSessionInfo,
            int pid,
            Supplier<@Nullable ProcessInfoInput> inputSupplier
    ) {
        long key = (((long) bootSessionInfo.id()) << 32) | pid;
        return infos.get(key, _ -> createProcessInfo(bootSessionInfo, pid, inputSupplier));
    }

    private @Nullable ProcessInfo createProcessInfo(
            BootSessionInfo bootSessionInfo,
            int pid,
            Supplier<@Nullable ProcessInfoInput> inputSupplier
    ) {
        var input = inputSupplier.get();
        if (input == null) {
            return null;
        }
        int id = saveProcessInfo(bootSessionInfo, pid, input);
        if (id < 0) {
            return null;
        }
        return new ProcessInfo(
                id,
                bootSessionInfo,
                input.pid(),
                input.processName(),
                "",
                ProcessState.RUNNING,
                input.start(),
                input.homeDirectory(),
                input.jvmMajorVersion(),
                input.jvmVersion(),
                input.gcType(),
                input.containerId(),
                input.maxDirectMemoryKib(),
                input.metaspaceMaxKib(),
                input.xmxKib(),
                input.xmsKib()
        );
    }

    private int saveProcessInfo(BootSessionInfo bootSessionInfo, int pid, ProcessInfoInput input) {
        try (var c = dataSourceFacade.getConnection()) {
            if (c == null) {
                throw new IllegalStateException(
                        "No connection to DB saving process info bootSessionInfo %s, pid %d, input %s".formatted(
                                bootSessionInfo,
                                pid,
                                input
                        )
                );
            }
            return doSaveProcessInfo(c, bootSessionInfo, pid, input);
        } catch (SQLException e) {
            LOG.error("failed to save Process Info bootSessionInfo {}, pid {}, input {}", bootSessionInfo, pid, input, e);
            return -1;
        }
    }

    private static int doSaveProcessInfo(
            Connection c,
            BootSessionInfo bootSessionInfo,
            int pid,
            ProcessInfoInput input
    ) throws SQLException {
        // Сначала проверяем, есть ли уже запись с такой парой boot_session_id и pid
        try (var selectStmt = c.prepareStatement(
                // language=SQL
                "SELECT id FROM jvm_ram_cost_process_info WHERE boot_session_id = ? AND pid = ?"
        )) {
            selectStmt.setInt(1, bootSessionInfo.id());
            selectStmt.setInt(2, pid);
            try (var rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        // Не нашли — вставляем новую запись
        try (var insertStmt = c.prepareStatement(
                // language=SQL
                "INSERT INTO jvm_ram_cost_process_info (boot_session_id, pid, process_name, comment, process_state, " +
                        "process_start_time, process_home_directory, jvm_major_version, jvm_version, gc_type, container_id, " +
                        "max_direct_memory_kib, metaspace_max_kib, xmx_kib, xms_kib) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insertStmt.setInt(1, bootSessionInfo.id());
            insertStmt.setInt(2, pid);
            insertStmt.setString(3, input.processName());
            insertStmt.setString(4, "");
            insertStmt.setString(5, ProcessState.RUNNING.name().toLowerCase());
            insertStmt.setTimestamp(6, Timestamp.from(input.start()));
            insertStmt.setString(7, input.homeDirectory().toString());
            insertStmt.setInt(8, input.jvmMajorVersion());
            insertStmt.setString(9, input.jvmVersion());
            insertStmt.setString(10, input.gcType());
            insertStmt.setString(11, input.containerId());
            insertStmt.setLong(12, input.maxDirectMemoryKib());
            insertStmt.setLong(13, input.metaspaceMaxKib());
            insertStmt.setLong(14, input.xmxKib());
            insertStmt.setLong(15, input.xmsKib());
            insertStmt.executeUpdate();
            try (var rs = insertStmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                throw new SQLException("Failed to get generated key for process info");
            }
        }
    }
}
