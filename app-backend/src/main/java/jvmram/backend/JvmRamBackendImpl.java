package jvmram.backend;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import jvmram.conf.Config;
import jvmram.controller.GraphController;
import jvmram.controller.JmxService;
import jvmram.controller.ProcessController;
import jvmram.model.graph.GraphKey;
import jvmram.model.graph.GraphPointQueues;
import jvmram.model.metrics.MetricType;
import jvmram.proto.*;
import jvmram.visibility.MetricVisibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import static java.util.stream.Collectors.groupingBy;
import static jvmram.backend.Converter.convert2Grpc;

class JvmRamBackendImpl extends AppBackendGrpc.AppBackendImplBase {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final Empty EMPTY = Empty.newBuilder().build();

    private final ProcessController processController;
    private final GraphController graphController;
    private final GraphPointQueues queues;
    private final JmxService jmxService;
    private final MetricVisibility metricVisibility;

    public JvmRamBackendImpl(
            ProcessController processController,
            GraphController graphController,
            GraphPointQueues queues,
            JmxService jmxService,
            MetricVisibility metricVisibility
    ) {
        this.processController = processController;
        this.graphController = graphController;
        this.queues = queues;
        this.jmxService = jmxService;
        this.metricVisibility = metricVisibility;
    }

    @Override
    public void listenJvmProcessList(Empty request, StreamObserver<JvmProcessListResponse> responseObserver) {
        processController.addAvailableJvmProcessesListener(procInfos -> {
                    var resp = JvmProcessListResponse.newBuilder()
                            .addAllInfos(procInfos.stream().map(Converter::convert2Grpc).toList())
                            .build();
                    responseObserver.onNext(resp);
                }
        );
    }

    @Override
    public void listenGraphQueues(Empty request, StreamObserver<GraphQueues> responseObserver) {
        graphController.addRenderer(() -> {
            queues.keys()
                    .stream()
                    .collect(groupingBy(GraphKey::pid))
                    .forEach((pid, keys) -> {
                        var resp = GraphQueues.newBuilder()
                                .setPid(pid)
                                .addAllQueues(
                                        keys.stream()
                                                .map(k -> convert2Grpc(
                                                        k,
                                                        queues.getApplicationStart(),
                                                        queues.getPoints(k))
                                                )
                                                .toList()
                                ).build();
                        responseObserver.onNext(resp);
                    });
        });
    }

    @Override
    public void setFollowingPids(PidList request, StreamObserver<Empty> responseObserver) {
        fireEmptyResponse(responseObserver);

        processController.setCurrentlySelectedPids(
                request.getPidsList().stream().map(Converter::fromGrpc).toList()
        );
    }

    @Override
    public void triggerGc(Pid request, StreamObserver<Empty> responseObserver) {
        fireEmptyResponse(responseObserver);

        jmxService.gc(request.getPid());
    }

    @Override
    public void dumpHeap(File request, StreamObserver<Empty> responseObserver) {
        fireEmptyResponse(responseObserver);

        jmxService.createHeapDump(request.getFileName());
    }

    @Override
    public void getExplicitlyFollowingPids(Empty request, StreamObserver<PidList> responseObserver) {
        var pids = processController.getExplicitlyFollowingPids();
        var response = convert2Grpc(pids);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void areChildrenProcessesIncluded(Empty request, StreamObserver<ChildrenProcessIncludedResponse> responseObserver) {
        boolean areIncluded = processController.areChildrenProcessesIncluded();
        var response = ChildrenProcessIncludedResponse.newBuilder().setAreIncluded(areIncluded).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void includeChildrenProcesses(Empty request, StreamObserver<Empty> responseObserver) {
        fireEmptyResponse(responseObserver);
        processController.includeChildrenProcesses();
    }

    @Override
    public void excludeChildrenProcesses(Empty request, StreamObserver<Empty> responseObserver) {
        fireEmptyResponse(responseObserver);
        processController.excludeChildrenProcesses();
    }

    @Override
    public void getApplicableMetrics(Empty request, StreamObserver<ApplicableMetricsResponse> responseObserver) {
        var metrics = Arrays.stream(MetricType.values())
                .filter(it -> it.isApplicable(Config.os))
                .map(Converter::convert2Grpc)
                .toList();
        var response = ApplicableMetricsResponse.newBuilder().addAllTypes(metrics).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void setVisible(SetVisibleRequest request, StreamObserver<Empty> responseObserver) {
        fireEmptyResponse(responseObserver);

        var type = Converter.fromGrpc(request.getMetricType());
        if (type != null) {
            metricVisibility.setVisible(type);
        } else {
            LOG.warn("Failed to convert input type of setVisible {}", request);
        }
    }

    @Override
    public void setInvisible(SetInvisibleRequest request, StreamObserver<Empty> responseObserver) {
        fireEmptyResponse(responseObserver);

        var type = Converter.fromGrpc(request.getMetricType());
        if (type != null) {
            metricVisibility.setInvisible(type);
        } else {
            LOG.warn("Failed to convert input type of setInvisible {}", request);
        }
    }

    @Override
    public void refreshAvailableJvmProcesses(Empty request, StreamObserver<Empty> responseObserver) {
        fireEmptyResponse(responseObserver);

        processController.refreshAvailableJvmProcesses();
    }

    private static void fireEmptyResponse(StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(EMPTY);
        responseObserver.onCompleted();
    }
}
