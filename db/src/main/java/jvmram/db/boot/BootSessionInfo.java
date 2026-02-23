package jvmram.db.boot;

import jvmram.model.data.Os;

import java.util.UUID;

public record BootSessionInfo(
        int id,
        Os os,
        String hostname,
        String alias,
        UUID machineId,
        String bootId
) {
}
