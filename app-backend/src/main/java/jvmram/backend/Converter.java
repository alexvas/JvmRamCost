package jvmram.backend;

import com.google.protobuf.Timestamp;
import jvmram.model.graph.GraphKey;
import jvmram.model.graph.GraphPoint;
import jvmram.process.JvmProcessInfo;
import jvmram.proto.*;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;

import static jvmram.model.metrics.MetricType.*;

class Converter {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    static ProcInfo convert2Grpc(JvmProcessInfo input) {
        return ProcInfo.newBuilder()
                .setPid(input.pid())
                .setDisplayName(input.displayName())
                .build();
    }

    static GraphQueue convert2Grpc(GraphKey k, Collection<GraphPoint> points) {
        return GraphQueue.newBuilder()
                .setMetricType(convert2Grpc(k.type()))
                .addAllPoints(
                        points.stream()
                                .map(Converter::convert2Grpc)
                                .toList()
                )
                .build();
    }

    static jvmram.proto.GraphPoint convert2Grpc(GraphPoint input) {
        var moment = input.moment();
        int kilobytes = Integer.MAX_VALUE;
        long bytes = input.bytes();
        long kb = Math.round(bytes / 1024.0);
        try {
            kilobytes = (int) kb;
        } catch (Exception e) {
            LOG.warn("Kilobyte value too large: {}", kb);
        }

        return jvmram.proto.GraphPoint.newBuilder()
                .setKilobytes(kilobytes)
                .setMoment(
                        Timestamp.newBuilder()
                                .setSeconds(moment.getEpochSecond())
                                .setNanos(moment.getNano())
                                .build()
                )
                .build();
    }

    static PidList convert2Grpc(Collection<Integer> pids) {
        return PidList.newBuilder()
                .addAllPids(pids.stream().map(Converter::convert2Grpc).toList())
                .build();
    }

    static Pid convert2Grpc(Integer input) {
        return Pid.newBuilder().setPid(input).build();
    }

    static MetricType convert2Grpc(jvmram.model.metrics.MetricType input) {
        return switch (input) {
            case RSS -> MetricType.RSS;
            case PSS -> MetricType.PSS;
            case USS -> MetricType.USS;
            case WS -> MetricType.WS;
            case PB -> MetricType.PB;
            case HEAP_USED -> MetricType.HEAP_USED;
            case HEAP_COMMITTED -> MetricType.HEAP_COMMITTED;
            case NMT_USED -> MetricType.NMT_USED;
            case NMT_COMMITTED -> MetricType.NMT_COMMITTED;
        };
    }

    static jvmram.model.metrics.@Nullable MetricType fromGrpc(MetricType input) {
        return switch (input) {
            case RSS -> RSS;
            case PSS -> PSS;
            case USS -> USS;
            case WS -> WS;
            case PB -> PB;
            case HEAP_USED -> HEAP_USED;
            case HEAP_COMMITTED -> HEAP_COMMITTED;
            case NMT_USED -> NMT_USED;
            case NMT_COMMITTED -> NMT_COMMITTED;
            case UNRECOGNIZED -> null;
        };
    }

    static Integer fromGrpc(Pid pid) {
        return pid.getPid();
    }


    private Converter() {
    }
}
