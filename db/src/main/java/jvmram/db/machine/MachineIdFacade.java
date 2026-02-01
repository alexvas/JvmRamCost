package jvmram.db.machine;

import jvmram.db.boot.Os;

import java.util.UUID;

public interface MachineIdFacade {
    UUID getMachineId(Os os);
}
