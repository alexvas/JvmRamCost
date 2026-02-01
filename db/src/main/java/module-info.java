module jvmram.db {
    requires org.slf4j;
    requires org.jspecify;
    requires java.sql;
    requires com.zaxxer.hikari;
    requires java.management;
    requires com.github.benmanes.caffeine;
    requires ru.dimension.di;
    requires jakarta.inject;

    exports jvmram.db.datasource;
    exports jvmram.db.boot;
    exports jvmram.db.pid;
    exports jvmram.db.config;
}