module jvmram.core {
    requires java.management;
    requires jdk.attach;
    requires static org.jspecify;
    requires org.slf4j;
    requires jvmram.model;
    requires jdk.management;
    requires ru.dimension.di;
    requires jakarta.inject;

    exports jvmram.conf;
    exports jvmram.metrics;
    exports jvmram.controller;
    exports jvmram.process;
    exports jvmram.visibility;
    exports jvmram.di;
}