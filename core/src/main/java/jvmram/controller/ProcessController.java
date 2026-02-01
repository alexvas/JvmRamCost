package jvmram.controller;

import jvmram.process.JvmProcessInfo;

import java.util.Collection;
import java.util.function.Consumer;

public interface ProcessController {

    /**
     * Следим не только за памятью указанного процесса,
     * но и за памятью всех его процессов-потомков.
     */
    void includeChildrenProcesses();

    /**
     * Следим только за памятью указанного процесса.
     * За памятью процессов-потомков не следим.
     */
    void excludeChildrenProcesses();

    /**
     * Получить список процессов, за которыми явно поручено следить.
     *
     * @return список отслеживаемых процессов
     */
    Collection<Integer> getExplicitlyFollowingPids();

    /**
     * Получить список процессов, за которыми поручено следить
     * вместе с их процессами-потомками.
     *
     * @return список отслеживаемых процессов
     */
    Collection<Integer> getPidsWithDescendants();

    void refreshAvailableJvmProcesses();

    void addAvailableJvmProcessesListener(Consumer<Collection<JvmProcessInfo>> onProcessInfoChanged);

    void setCurrentlySelectedPids(Collection<Integer> pids);

    boolean areChildrenProcessesIncluded();
}
