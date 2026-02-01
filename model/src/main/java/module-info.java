module jvmram.model {
    requires org.slf4j;
    requires java.desktop;
    requires ru.dimension.di;
    requires jakarta.inject;

    exports jvmram.model.metrics;
    exports jvmram.model.graph;
    exports jvmram.model.util;
}