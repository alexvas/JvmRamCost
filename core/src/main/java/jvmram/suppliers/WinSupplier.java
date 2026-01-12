package jvmram.suppliers;

import jvmram.conf.Config;
import jvmram.suppliers.data.WinData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static java.lang.foreign.ValueLayout.*;
import static jvmram.model.metrics.Os.WINDOWS;

class WinSupplier extends AbstractDataSupplier<WinData> {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // Windows API constants
    private static final int PROCESS_QUERY_INFORMATION = 0x0400;
    private static final int PROCESS_VM_READ = 0x0010;

    // Linker and symbol lookups
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32;
    private static final SymbolLookup PSAPI;

    // Method handles
    private static final MethodHandle OPEN_PROCESS;
    private static final MethodHandle CLOSE_HANDLE;
    private static final MethodHandle GET_PROCESS_MEMORY_INFO;

    // PROCESS_MEMORY_COUNTERS_EX2 structure layout
    // DWORD = 4 bytes, SIZE_T = 8 bytes (64-bit), ULONGLONG = 8 bytes
    private static final MemoryLayout PMC_EX2_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("cb"),                    // DWORD
            JAVA_INT.withName("PageFaultCount"),        // DWORD
            JAVA_LONG.withName("PeakWorkingSetSize"),   // SIZE_T
            JAVA_LONG.withName("WorkingSetSize"),        // SIZE_T
            JAVA_LONG.withName("QuotaPeakPagedPoolUsage"), // SIZE_T
            JAVA_LONG.withName("QuotaPagedPoolUsage"),  // SIZE_T
            JAVA_LONG.withName("QuotaPeakNonPagedPoolUsage"), // SIZE_T
            JAVA_LONG.withName("QuotaNonPagedPoolUsage"), // SIZE_T
            JAVA_LONG.withName("PagefileUsage"),        // SIZE_T
            JAVA_LONG.withName("PeakPagefileUsage"),    // SIZE_T
            JAVA_LONG.withName("PrivateUsage"),         // SIZE_T
            JAVA_LONG.withName("PrivateWorkingSetSize"), // SIZE_T
            JAVA_LONG.withName("SharedCommitUsage")     // ULONGLONG
    );

    static {
        try {
            KERNEL32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            PSAPI = SymbolLookup.libraryLookup("psapi", Arena.global());

            OPEN_PROCESS = LINKER.downcallHandle(
                    KERNEL32.find("OpenProcess").orElseThrow(),
                    FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_BOOLEAN, JAVA_INT)
            );

            CLOSE_HANDLE = LINKER.downcallHandle(
                    KERNEL32.find("CloseHandle").orElseThrow(),
                    FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS)
            );

            GET_PROCESS_MEMORY_INFO = LINKER.downcallHandle(
                    PSAPI.find("GetProcessMemoryInfo").orElseThrow(),
                    FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, JAVA_INT)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Windows API bindings", e);
        }
    }

    WinSupplier(int pid) {
        super(pid);
        if (Config.os != WINDOWS) {
            LOG.error("The supplier is intended for use in Windows OS only");
        } else {
            setInitialized();
        }
    }

    @Override
    @Nullable WinData doGetData() {
        try (Arena arena = Arena.ofConfined()) {
            // OpenProcess(dwDesiredAccess, bInheritHandle, dwProcessId)
            MemorySegment hProcess;
            try {
                hProcess = (MemorySegment) OPEN_PROCESS.invokeExact(
                        PROCESS_QUERY_INFORMATION | PROCESS_VM_READ,
                        false,
                        (int) pid
                );
            } catch (Throwable e) {
                LOG.warn("Failed to open process handle for pid {}", pid, e);
                return null;
            }

            if (hProcess == null || hProcess.address() == 0) {
                LOG.warn("Failed to open process handle for pid {}: returned null handle", pid);
                return null;
            }

            try {
                // Allocate memory for PROCESS_MEMORY_COUNTERS_EX2
                MemorySegment pmc = arena.allocate(PMC_EX2_LAYOUT);
                
                // Set cb field (size of structure)
                int size = (int) PMC_EX2_LAYOUT.byteSize();
                pmc.set(JAVA_INT, 0, size);

                // GetProcessMemoryInfo(hProcess, ppsmemCounters, cb)
                boolean success;
                try {
                    success = (boolean) GET_PROCESS_MEMORY_INFO.invokeExact(hProcess, pmc, size);
                } catch (Throwable e) {
                    LOG.warn("Failed to call GetProcessMemoryInfo for pid {}", pid, e);
                    return null;
                }

                if (success) {
                    // Extract WorkingSetSize using var handle
                    var workingSetSizeHandle = PMC_EX2_LAYOUT.varHandle(
                            MemoryLayout.PathElement.groupElement("WorkingSetSize")
                    );
                    long workingSetSize = (long) workingSetSizeHandle.get(pmc);
                    
                    // Extract PrivateUsage using var handle
                    var privateUsageHandle = PMC_EX2_LAYOUT.varHandle(
                            MemoryLayout.PathElement.groupElement("PrivateUsage")
                    );
                    long privateUsage = (long) privateUsageHandle.get(pmc);

                    return new WinData(workingSetSize, privateUsage);
                } else {
                    LOG.warn("GetProcessMemoryInfo returned false for pid {}", pid);
                    return null;
                }
            } finally {
                // CloseHandle(hProcess)
                try {
                    CLOSE_HANDLE.invokeExact(hProcess);
                } catch (Throwable e) {
                    LOG.warn("Failed to close process handle", e);
                }
            }
        }
    }
}
