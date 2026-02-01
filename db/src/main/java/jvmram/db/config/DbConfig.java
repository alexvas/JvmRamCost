package jvmram.db.config;

import jvmram.db.boot.BootSessionFacade;
import jvmram.db.boot.impl.BootSessionFacadeImpl;
import jvmram.db.datasource.DataSourceFacade;
import jvmram.db.datasource.impl.DataSourceFacadeImpl;
import jvmram.db.machine.MachineIdFacade;
import jvmram.db.machine.impl.MachineIdFacadeImpl;
import jvmram.db.pid.ProcessInfoFacade;
import jvmram.db.pid.impl.ProcessInfoFacadeImpl;
import ru.dimension.di.DimensionDI.Builder;

public class DbConfig {

    private DbConfig() {
    }

    public static Builder configure(Builder builder) {
        return builder
                .bind(MachineIdFacade.class, MachineIdFacadeImpl.class)
                .bind(DataSourceFacade.class, DataSourceFacadeImpl.class)
                .bind(BootSessionFacade.class, BootSessionFacadeImpl.class)
                .bind(ProcessInfoFacade.class, ProcessInfoFacadeImpl.class)
                ;
    }
}
