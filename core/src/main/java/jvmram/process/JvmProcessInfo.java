package jvmram.process;

import org.jspecify.annotations.NonNull;

import java.util.Collection;

public record JvmProcessInfo(
        int pid,
        String displayName,
        Collection<Integer> children
) implements Comparable<JvmProcessInfo> {

    @Override
    public int compareTo(@NonNull JvmProcessInfo other) {
        return Integer.compare(pid, other.pid);
    }

    public JvmProcessInfo withChildren(Collection<Integer> newChildren) {
        return new JvmProcessInfo(pid, displayName, newChildren);
    }
}

