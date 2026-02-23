package jvmram.jmx;

import jvmram.model.data.JvmProcessInfo;
import org.jspecify.annotations.Nullable;

public interface JvmProcessInfoProvider {

    /**
     * Возвращаем информацию о процессе виртуальной машины Java по его ID
     *
     * @param pid ID jvm-процесса
     * @return информация о процессе или null в случае ошибки
     */
    @Nullable JvmProcessInfo provideProcessInfo(int pid);
}
