package jvmram.process.iml;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import jvmram.process.JvmProcessInfo;
import jvmram.process.ProcessManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

public class ProcessManagerImpl implements ProcessManager {

    private final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private ProcessManagerImpl() {
    }

    private int getPid(VirtualMachineDescriptor vmd) {
        String id = vmd.id();
        try {
            return Integer.parseInt(id);
        } catch (Exception e) {
            LOG.error("Failed to parse Virtual Machine ID {} of {}", id, vmd, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<JvmProcessInfo> getJvmProcesses() {
        return VirtualMachine.list().stream().map(vmd ->
                new JvmProcessInfo(
                        getPid(vmd),
                        vmd.displayName().isEmpty()
                                ? vmd.id()
                                : vmd.displayName(),
                        List.of()
                )).toList();
    }

    private Integer getPid(ProcessHandle ph) {
        long pid = ph.pid();
        try {
            // И в Линуксе, и в Виндоуз PID должен помещаться в int32.
            return (int) pid;
        } catch (Exception e) {
            LOG.error("Unable to convert pid to int: {}", pid, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Integer> getProcessDescendantIds(int pid) {
        List<Integer> output = new ArrayList<>();
        var processHandle = ProcessHandle.of(pid);
        processHandle.ifPresent(ph ->
                ph.descendants()
                        .map(this::getPid)
                        .forEach(output::add)
        );
        return output;
    }

    public static final ProcessManager INSTANCE = new ProcessManagerImpl();
}
