package jvmram.db.machine;

import jvmram.model.data.Os;

import java.util.UUID;

public interface MachineIdFacade {
    UUID getMachineId(Os os);
}
