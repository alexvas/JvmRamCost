package jvmram.controller;

public interface JmxService {

    /**
     * Провести Garbage Collection для JDK-процесса с определённым PID
     *
     * @param pid кому провести GC
     */
    void gc(int pid);

    void createHeapDump(int pid, String outputHprofFilePath);

    void createThreadDump(int pid, String outputThreadDumpPath);
}
