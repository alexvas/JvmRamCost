package jvmram.jmx;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.tools.attach.VirtualMachine;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.invoke.MethodHandles;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.MemoryMXBean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.management.ManagementFactory.getPlatformMXBeans;
import static java.lang.management.ManagementFactory.newPlatformMXBeanProxy;

class JmxBeanFactoryImpl implements JmxBeanFactory {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Map<Integer, MxDatum> mxData = new ConcurrentHashMap<>();

    private JmxBeanFactoryImpl() {
    }

    @Override
    public @Nullable MxDatum getMxDatum(int pid) {
        return mxData.computeIfAbsent(pid, JmxBeanFactoryImpl::initConnection);
    }

    private static @Nullable MxDatum initConnection(int pid) {
        try {
            // Подключаемся к целевой JVM
            var vm = VirtualMachine.attach(String.valueOf(pid));

            try {
                // Получаем свойства агента
                var agentProperties = vm.getAgentProperties();
                var connectorAddress = agentProperties.getProperty(
                        "com.sun.management.jmxremote.localConnectorAddress");

                // Если JMX агент не запущен, запускаем его
                if (connectorAddress == null) {
                    vm.startLocalManagementAgent();
                    agentProperties = vm.getAgentProperties();
                    connectorAddress = agentProperties.getProperty("com.sun.management.jmxremote.localConnectorAddress");
                }

                if (connectorAddress == null) {
                    LOG.warn("Failed to resolve connector address for pid {}", pid);
                    return null;
                }

                // Подключаемся к JMX коннектору
                var serviceUrl = new JMXServiceURL(connectorAddress);
                var jmxConnector = JMXConnectorFactory.connect(serviceUrl, null);
                var connection = jmxConnector.getMBeanServerConnection();

                var memoryMxBean = newPlatformMXBeanProxy(
                        connection,
                        "java.lang:type=Memory",
                        MemoryMXBean.class
                );

                var hotSpotBean = newPlatformMXBeanProxy(
                        connection,
                        "com.sun.management:type=HotSpotDiagnostic",
                        HotSpotDiagnosticMXBean.class
                );

                var bufferPools = getPlatformMXBeans(
                        connection,
                        BufferPoolMXBean.class
                );

                var agentProps = vm.getAgentProperties();
                var sysProps = vm.getSystemProperties();

                return new MxDatum(jmxConnector, memoryMxBean, hotSpotBean, bufferPools, agentProps, sysProps);

            } finally {
                // Отключаемся от виртуальной машины (но оставляем JMX коннектор открытым)
                vm.detach();
            }

        } catch (Exception e) {
            LOG.warn("Failed to obtain JMX data for pid {}", pid, e);
            return null;
        }
    }

    @Override
    public void disconnect(int pid) {
        var jmxDatum = mxData.remove(pid);
        if (jmxDatum == null) {
            return;
        }
        var jmxConnector = jmxDatum.jmxConnector();
        if (jmxConnector == null) {
            return;
        }
        try {
            jmxConnector.close();
        } catch (Exception e) {
            // Игнорируем ошибки закрытия
        }
    }

    static final JmxBeanFactoryImpl INSTANCE = new JmxBeanFactoryImpl();
}
