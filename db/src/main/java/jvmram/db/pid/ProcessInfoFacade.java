package jvmram.db.pid;

import jvmram.db.boot.BootSessionInfo;
import jvmram.model.data.ProcessInfoInput;

import java.util.function.Supplier;

public interface ProcessInfoFacade {

    ProcessInfo getProcessInfo(
            BootSessionInfo bootSessionInfo,
            int pid,
            Supplier<ProcessInfoInput> inputSupplier
    );
}
