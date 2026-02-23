package jvmram.model.ui.graph.impl;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jvmram.model.ui.graph.GraphKey;
import jvmram.model.ui.graph.GraphPoint;
import jvmram.model.ui.graph.GraphPointQueuesWritable;
import jvmram.model.ui.metrics.MetricType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

@Singleton
public class GraphPointQueuesImpl implements GraphPointQueuesWritable {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int SIZE_LIMIT = 1_000;

    private final Map<GraphKey, Deque<GraphPoint>> data = new ConcurrentHashMap<>();

    private final Instant applicationStart = Instant.now();

    @Inject
    GraphPointQueuesImpl() {
    }

    @Override
    public List<GraphPoint> add(int pid, MetricType metricType, GraphPoint graphPoint) {

        var bytes = graphPoint.bytes();
        if (bytes < 0) {
            throw new IllegalArgumentException("Bytes in GraphPoint must be positive: %s".formatted(graphPoint));
        }

        var key = new GraphKey(metricType, pid);
        var deque = data.computeIfAbsent(key, ignored -> {
            LOG.debug("creating entry for a {}", key);
            return new LinkedBlockingDeque<>();
        });
        var exceed = new ArrayList<GraphPoint>();
        while (deque.size() >= SIZE_LIMIT) {
            exceed.add(deque.pollFirst());
        }
        deque.offer(graphPoint);
        return exceed.isEmpty()
                ? List.of()
                : exceed;
    }

    @Override
    public void handleExceed(Collection<GraphPoint> exceeds) {
        if (exceeds.isEmpty()) {
            return;
        }
        var maxExceedInstant = exceeds.stream()
                .map(GraphPoint::moment)
                .max(Instant::compareTo)
                .orElse(Instant.MIN);
        data.values().forEach(deque -> trim(deque, maxExceedInstant));
    }

    private static void trim(Deque<GraphPoint> deque, Instant maxExceed) {
        var point = deque.peekFirst();
        if (point != null && point.moment().isBefore(maxExceed)) {
            deque.pollFirst();
        }
    }

    @Override
    public Collection<GraphKey> keys() {
        return data.keySet();
    }

    @Override
    public Collection<GraphPoint> getPoints(GraphKey key) {
        return data.get(key);
    }

    @Override
    public Instant getApplicationStart() {
        return applicationStart;
    }
}
