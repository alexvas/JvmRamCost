package jvmram.jmx;

import com.sun.tools.attach.VirtualMachine;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class JmxBeanFactoryImpl implements JmxBeanFactory {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Map<Integer, MemoryMXBean> memoryMxBeans = new ConcurrentHashMap<>();
    private final Map<Integer, JMXConnector> jmxConnectors = new ConcurrentHashMap<>();

    private JmxBeanFactoryImpl() {
    }

    @Override
    public @Nullable MemoryMXBean getMemoryMxBean(int pid) {
        // Возвращаем кэшированный bean, если он есть
        if (memoryMxBeans.containsKey(pid)) {
            return memoryMxBeans.get(pid);
        }
        
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
                var mbsc = jmxConnector.getMBeanServerConnection();
                
                // Получаем MemoryMXBean через MBeanServerConnection
                var memoryMxBean = ManagementFactory.newPlatformMXBeanProxy(
                    mbsc,
                    "java.lang:type=Memory",
                    MemoryMXBean.class
                );
                
                // Кэшируем bean и коннектор
                memoryMxBeans.put(pid, memoryMxBean);
                jmxConnectors.put(pid, jmxConnector);
                
                return memoryMxBean;
                
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
        var jmxConnector = jmxConnectors.remove(pid);
        if (jmxConnector != null) {
            try {
                jmxConnector.close();
            } catch (Exception e) {
                // Игнорируем ошибки закрытия
            }
        }
        
        memoryMxBeans.remove(pid);
    }

    static final JmxBeanFactoryImpl INSTANCE = new JmxBeanFactoryImpl();
}
