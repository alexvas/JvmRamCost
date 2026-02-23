package jvmram.conf;

import jvmram.model.data.Os;
import jvmram.model.ui.metrics.MetricType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import static jvmram.model.data.Os.LINUX;
import static jvmram.model.data.Os.WINDOWS;

public final class Config {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // Интервалы опроса (в секундах)
    public static final Map<MetricType, Duration> PRODUCTION_POLL_INTERVALS = new EnumMap<>(MetricType.class);
    public static final Map<MetricType, Duration> DEV_POLL_INTERVALS = new EnumMap<>(MetricType.class);
    public static final Map<MetricType, Duration> LEAK_HUNT_POLL_INTERVALS = new EnumMap<>(MetricType.class);

    // Настройки отображения метрик по умолчанию
    public static final Map<MetricType, Boolean> DEFAULT_METRIC_VISIBILITY = new EnumMap<>(MetricType.class);

    // Определение платформы
    public static final Os os;

    static {
        // Инициализация интервалов опроса
        Arrays.stream(MetricType.values()).forEach(type -> {

            int devDurationInSeconds = switch (type) {
                case RSS, WS, HEAP_USED, HEAP_COMMITTED, OLD_GEN_MAX, OLD_GEN_COMMITTED, OLD_GEN_USED, NMT_USED,
                     NMT_COMMITTED, BUFFER_TOTAL -> 1;
                case PSS, USS, PB -> 10;
            };
            var devDuration = Duration.ofSeconds(devDurationInSeconds);
            DEV_POLL_INTERVALS.put(type, devDuration);

            int productionDurationInSeconds = switch (type) {
                case RSS, WS, HEAP_USED, HEAP_COMMITTED, OLD_GEN_MAX, OLD_GEN_COMMITTED, OLD_GEN_USED, NMT_USED,
                     NMT_COMMITTED, BUFFER_TOTAL -> 2;
                case PB -> 15;
                case PSS, USS -> 30;
            };
            var productionDuration = Duration.ofSeconds(productionDurationInSeconds);
            PRODUCTION_POLL_INTERVALS.put(type, productionDuration);

            int leakHuntDurationInSeconds = switch (type) {
                case RSS, WS -> 2;
                case PSS, USS, PB, HEAP_USED, HEAP_COMMITTED, OLD_GEN_MAX, OLD_GEN_COMMITTED, OLD_GEN_USED, NMT_USED,
                     NMT_COMMITTED, BUFFER_TOTAL -> 5;
            };
            var leakHuntDuration = Duration.ofSeconds(leakHuntDurationInSeconds);
            LEAK_HUNT_POLL_INTERVALS.put(type, leakHuntDuration);
        });


        // Инициализация видимости метрик по умолчанию
        DEFAULT_METRIC_VISIBILITY.put(MetricType.RSS, true);
        DEFAULT_METRIC_VISIBILITY.put(MetricType.PSS, true);
        DEFAULT_METRIC_VISIBILITY.put(MetricType.USS, false);
        DEFAULT_METRIC_VISIBILITY.put(MetricType.WS, true);
        DEFAULT_METRIC_VISIBILITY.put(MetricType.PB, false);
        DEFAULT_METRIC_VISIBILITY.put(MetricType.HEAP_USED, true);
        DEFAULT_METRIC_VISIBILITY.put(MetricType.HEAP_COMMITTED, true);
        DEFAULT_METRIC_VISIBILITY.put(MetricType.OLD_GEN_MAX, true);
        DEFAULT_METRIC_VISIBILITY.put(MetricType.OLD_GEN_COMMITTED, true);
        DEFAULT_METRIC_VISIBILITY.put(MetricType.OLD_GEN_USED, true);
        DEFAULT_METRIC_VISIBILITY.put(MetricType.NMT_USED, true);
        DEFAULT_METRIC_VISIBILITY.put(MetricType.NMT_COMMITTED, true);

        // Определение платформы
        var osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("linux")) {
            os = LINUX;
        } else if (osName.contains("windows")) {
            os = WINDOWS;
        } else {
            LOG.error("Unsupported OS {}", osName);
            System.exit(2);
            // never reach the next line
            os = null;
        }
    }

    private Config() {
        // Утилитный класс
    }
}

