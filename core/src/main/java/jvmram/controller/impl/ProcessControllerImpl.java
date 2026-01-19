package jvmram.controller.impl;

import jvmram.controller.ProcessController;
import jvmram.model.util.RwGuarded;
import jvmram.process.JvmProcessInfo;
import jvmram.process.ProcessManager;

import java.util.*;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toSet;
import static jvmram.controller.impl.Utils.callActionOrGetRidOfListener;

public class ProcessControllerImpl implements ProcessController {

    private final RwGuarded guarded = RwGuarded.create();

    private boolean includeChildrenProcesses = false;

    private final Collection<Integer> explicitlyFollowingPids = new TreeSet<>();
    private final Map<Integer, Collection<Integer>> descendantPids = new HashMap<>();

    private final ProcessManager processManager = ProcessManager.getInstance();

    private final List<Consumer<Collection<JvmProcessInfo>>> onProcessInfoChangedListeners = new ArrayList<>();

    @Override
    public boolean areChildrenProcessesIncluded() {
        return guarded.read(() -> includeChildrenProcesses);
    }

    @Override
    public void includeChildrenProcesses() {
        guarded.write(() ->
                includeChildrenProcesses = true
        );
    }

    @Override
    public void excludeChildrenProcesses() {
        guarded.write(() ->
                includeChildrenProcesses = false
        );
    }

    @Override
    public Collection<Integer> getExplicitlyFollowingPids() {
        return guarded.read(() -> explicitlyFollowingPids);
    }

    @Override
    public Collection<Integer> getPidsWithDescendants() {
        return guarded.read(() -> {
                    List<Integer> output = new ArrayList<>();
                    for (Integer pid : explicitlyFollowingPids) {
                        output.add(pid);
                        output.addAll(descendantPids.getOrDefault(pid, List.of()));
                    }
                    return output;
                }
        );
    }

    @Override
    public void setCurrentlySelectedPids(Collection<Integer> pids) {
        guarded.write(() -> {
            var pidsGone = new HashSet<>(explicitlyFollowingPids);
            pidsGone.removeAll(pids);
            pidsGone.forEach(this::doUnfollowPid);
            pids.forEach(this::doFollowPid);
        });
    }

    private void doUnfollowPid(Integer pid) {
        explicitlyFollowingPids.remove(pid);
        descendantPids.remove(pid);
    }

    private void doFollowPid(Integer pid) {
        explicitlyFollowingPids.add(pid);
        if (!includeChildrenProcesses) {
            return;
        }
        descendantPids.put(pid, processManager.getProcessDescendantIds(pid));
    }

    @Override
    public void refreshAvailableJvmProcesses() {
        var jvmProcesses = processManager.getJvmProcesses();
        var actualPids = jvmProcesses.stream().map(JvmProcessInfo::pid).collect(toSet());
        guarded.write(() -> {

                    var pidsGone = new HashSet<>(explicitlyFollowingPids);
                    pidsGone.removeAll(actualPids);
                    pidsGone.forEach(this::doUnfollowPid);
                    var fixedJvmProcesses = jvmProcesses.stream()
                            .map(it -> {
                                var children = descendantPids.get(it.pid());
                                return children == null
                                        ? it
                                        : it.withChildren(children);
                            })
                            .toList();

                    callActionOrGetRidOfListener(
                            onProcessInfoChangedListeners,
                            listener -> listener.accept(fixedJvmProcesses)
                    );
                }
        );
    }

    @Override
    public void addAvailableJvmProcessesListener(Consumer<Collection<JvmProcessInfo>> onProcessInfoChanged) {
        guarded.write(() -> onProcessInfoChangedListeners.add(onProcessInfoChanged));
    }

    private ProcessControllerImpl() {
    }

    public static final ProcessControllerImpl INSTANCE = new ProcessControllerImpl();
}
