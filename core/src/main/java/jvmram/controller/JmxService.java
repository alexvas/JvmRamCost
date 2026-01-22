package jvmram.controller;

import jvmram.controller.impl.JmxServiceImpl;

public interface JmxService {

    /**
     * Провести Garbage Collection для JDK-процесса с определённым PID
     *
     * @param pid кому провести GC
     */
    void gc(int pid);

    void createHeapDump(int pid, String outputHprofFilePath);

    void createThreadDump(int pid, String outputThreadDumpPath);

    static JmxService getInstance() {
        return JmxServiceImpl.INSTANCE;
    }
}
