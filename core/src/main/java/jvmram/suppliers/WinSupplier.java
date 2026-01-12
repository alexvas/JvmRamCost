package jvmram.suppliers;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import jvmram.conf.Config;
import jvmram.suppliers.data.WinData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

import static jvmram.model.metrics.Os.WINDOWS;

class WinSupplier extends AbstractDataSupplier<WinData> {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private interface Psapi extends com.sun.jna.Library {
        Psapi INSTANCE = Native.load("psapi", Psapi.class);

        boolean GetProcessMemoryInfo(WinNT.HANDLE hProcess, ProcessMemoryCountersEx2 ppsmemCounters, int cb);
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
        var hProcess = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_QUERY_INFORMATION | WinNT.PROCESS_VM_READ,
                false,
                (int) pid
        );
        if (hProcess == null) {
            LOG.warn("Failed to open process handle for pid {}", pid);
            return null;
        }
        try {
            var pmc = new ProcessMemoryCountersEx2();
            int size = pmc.size();
            pmc.cb = new WinDef.DWORD(size);
            boolean success = Psapi.INSTANCE.GetProcessMemoryInfo(hProcess, pmc, size);
            if (success) {
                return new WinData(
                        pmc.WorkingSetSize.longValue(),
                        pmc.PrivateUsage.longValue()
                );
            } else {
                LOG.warn("Failed to get process memory info for pid {}", pid);
                return null;
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(hProcess);
        }
    }
}
