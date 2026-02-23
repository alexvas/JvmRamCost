module jvmram.jmx {
    requires jakarta.inject;
    requires jdk.management;
    requires jdk.attach;
    requires org.jspecify;
    requires org.slf4j;
    requires jvmram.model.data;
    exports jvmram.jmx;
}