package jvmram.process;

import org.jspecify.annotations.NonNull;

public record JvmProcessInfo(int pid, String displayName) implements Comparable<JvmProcessInfo> {
    @Override
    public int compareTo(@NonNull JvmProcessInfo other) {
        return Integer.compare(pid, other.pid);
    }
}

