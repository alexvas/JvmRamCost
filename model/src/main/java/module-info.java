module jvmram.model {
    requires org.slf4j;
    requires java.desktop;
    requires ru.dimension.di;

    exports jvmram.model.metrics;
    exports jvmram.model.graph;
    exports jvmram.model.util;
    exports jvmram.model.di;
}