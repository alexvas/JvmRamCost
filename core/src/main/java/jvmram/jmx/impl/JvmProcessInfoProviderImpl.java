package jvmram.jmx.impl;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jvmram.jmx.JmxBeanFactory;
import jvmram.jmx.JvmProcessInfoProvider;
import jvmram.jmx.MxDatum;
import jvmram.model.data.JvmProcessInfo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.regex.Pattern;

@Singleton
public class JvmProcessInfoProviderImpl implements JvmProcessInfoProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final Pattern CONTAINER_ID_PATTERN = Pattern.compile("([a-f0-9]{64})");
    private static final Path C_GROUP_PATH = Path.of("/proc/self/cgroup");

    private final JmxBeanFactory jmxBeanFactory;

    @Inject
    JvmProcessInfoProviderImpl(JmxBeanFactory jmxBeanFactory) {
        this.jmxBeanFactory = jmxBeanFactory;
    }

    @Override
    public @Nullable JvmProcessInfo provideProcessInfo(int pid) {
        var datum = jmxBeanFactory.getMxDatum(pid);
        if (datum == null) {
            return null;
        }
        var sysProps = datum.sysProps();
        var sunJavaCommand = sysProps.getProperty("sun.java.command");
        var mainClass = sunJavaCommand == null
                ? null
                : sunJavaCommand.split(" ", 2)[0];
        Instant processStartTime = getProcessStartTime(pid);

        var javaHome = sysProps.getProperty("java.home");
        var javaVersion = sysProps.getProperty("java.version");
        var javaSpecificationVersion = sysProps.getProperty("java.specification.version");
        int jvmMajorVersion = Integer.parseInt(javaSpecificationVersion);
        var garbageCollector = extractGarbageCollectorUsed(datum);
        var containerId = extractContainerIdOrHostname();

        long directMaxBytes = extractVmOptionInBytes(datum, pid, "MaxDirectMemorySize");
        long directMaxKibs = directMaxBytes <= 0
                ? directMaxBytes
                : directMaxBytes / 1024;

        long mxBytes = extractVmOptionInBytes(datum, pid, "mx");
        long mxKibs = mxBytes <= 0
                ? mxBytes
                : mxBytes / 1024;

        long msBytes = extractVmOptionInBytes(datum, pid, "ms");
        long msKibs = msBytes <= 0
                ? msBytes
                : msBytes / 1024;

        // Metaspace, Code Cache, Compressed Class Space
        long nmtMaxBytes = datum.memory().getNonHeapMemoryUsage().getMax();
        long nmtMaxKibs = nmtMaxBytes <= 0
                ? nmtMaxBytes
                : nmtMaxBytes / 1024;

        return new JvmProcessInfo(
                pid,
                mainClass,
                processStartTime,
                Path.of(javaHome),
                jvmMajorVersion,
                javaVersion,
                garbageCollector,
                containerId,
                directMaxKibs,
                nmtMaxKibs,
                mxKibs,
                msKibs
        );
    }

    /**
     * Extract the value as if it is set via the command-line option
     * e.g. -XX:MaxDirectMemorySize
     *
     * @param datum jmx misc data
     * @param pid - process id
     * @param vmOptionName an option name to extract
     * @return max direct memory in bytes or -1 in case of error
     */
    private static long extractVmOptionInBytes(MxDatum datum, int pid, String vmOptionName) {
        try {
            var option = datum.hotSpot().getVMOption(vmOptionName);
            if (option == null) {
                return 0;
            }
            // для числовых опций getValue() возвращает строку
            // с уже сконвертированным значением в байтах
            var strValue = option.getValue();
            if (strValue == null) {
                return 0;
            }
            return Long.parseLong(strValue);
        } catch (Exception e) {
            LOG.warn("Failed to extract {} for pid {}", vmOptionName, pid, e);
            return -1;
        }
    }

    /**
     * Extract container ID from Docker / k8s or other container environment
     *
     * @return Container ID / Container name or hostname if JVM runs in host mode.
     */
    private @Nullable String extractContainerIdOrHostname() {
        var containerId = doExtractContainerId();
        if (containerId != null) {
            return containerId;
        }
        return doExtractHostname();
    }

    private @Nullable String doExtractContainerId() {
        var cgroupFile = C_GROUP_PATH.toFile();
        if (!cgroupFile.exists() || !cgroupFile.canRead()) {
            return null;
        }
        String cgroupContent = null;
        try {
            cgroupContent = Files.readString(C_GROUP_PATH);
        } catch (IOException | OutOfMemoryError e) {
            LOG.debug("Could not read container ID from {}", C_GROUP_PATH, e);
        }
        if (cgroupContent == null) {
            return null;
        }
        var matcher = CONTAINER_ID_PATTERN.matcher(cgroupContent);
        return matcher.find()
                ? matcher.group(1)
                : null;
    }

    private static String doExtractHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            LOG.warn("Failed to resolve hostname", e);
            return null;
        }
    }

    private @Nullable String extractGarbageCollectorUsed(MxDatum datum) {
        try {
            var connection = datum.jmxConnector().getMBeanServerConnection();
            return ManagementFactory.getPlatformMXBeans(connection, GarbageCollectorMXBean.class)
                    .stream()
                    .findFirst()
                    .map(GarbageCollectorMXBean::getName)
                    .orElse(null);
        } catch (Exception e) {
            LOG.warn("Failed to extract garbage collector info", e);
            return null;
        }
    }

    private Instant getProcessStartTime(int pid) {
        try {
            return ProcessHandle.of(pid)
                    .flatMap(it ->
                            it.info()
                                    .startInstant()
                    )
                    .orElse(null);
        } catch (Exception e) {
            LOG.error("Failed to get process start time for pid {}", pid, e);
            return null;
        }
    }
}
