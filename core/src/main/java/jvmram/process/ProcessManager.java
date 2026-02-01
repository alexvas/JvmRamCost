package jvmram.process;

import java.util.List;

public interface ProcessManager {

    /**
     * Получить список запущенных локально Java-процессов.
     * Возвращает pid и displayName для каждого процесса.
     *
     * @return список Java-процессов.
     */
    List<JvmProcessInfo> getJvmProcesses();

    /**
     * Отдаёт список идентификаторов потомков процесса (дочерних процессов, внучатых процессов и так далее).
     * Список не включает в себя родительский pid.
     *
     * @param pid - родительский PID, относительно которого строится список потомков
     * @return список потомков.
     */
    List<Integer> getProcessDescendantIds(int pid);
}
