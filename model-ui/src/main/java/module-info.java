module jvmram.model.ui {
    requires org.slf4j;
    requires ru.dimension.di;
    requires jakarta.inject;
    requires jvmram.model.data;

    exports jvmram.model.ui.metrics;
    exports jvmram.model.ui.graph;
    exports jvmram.model.ui.util;
}