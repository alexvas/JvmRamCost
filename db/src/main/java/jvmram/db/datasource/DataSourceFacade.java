package jvmram.db.datasource;

import jvmram.db.datasource.impl.DataSourceFacadeImpl;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;

/**
 * Это фасад, который обеспечивает соединение с инициализированной БД.
 */
public interface DataSourceFacade {

    /**
     * Возвращает соединение к инициализированной БД либо null, если соединение поломано.
     * @return соединение к БД или null
     */
    @Nullable Connection getConnection();
}
