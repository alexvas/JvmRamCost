package jvmram.jmx;

import org.jspecify.annotations.Nullable;

public interface JmxBeanFactory {

    /**
     * Возвращаем JMX handle для получения данных о JVM-процессе.
     *
     * @param pid процесса
     * @return данные о нём или null в случае ошибки получения данных
     */
    @Nullable
    MxDatum getMxDatum(int pid);

    /**
     * Разрываем JMX-соединение с процессом
     *
     * @param pid процесса
     */
    void disconnect(int pid);
}
