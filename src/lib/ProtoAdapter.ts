import {
    MetricType as ProtoMetricType,
    SetVisibleRequest,
    SetInvisibleRequest,
    ApplicableMetricsResponse,
    type GraphQueues,
    type JvmProcessListResponse,
    Pid
} from "$lib/generated/proto/protocol";
import type { Timestamp } from "$lib/generated/google/protobuf/timestamp";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import type { ProcInfo } from "./ProcHandle";
import { MetricType } from "./GraphStore";
import { Temporal } from "@js-temporal/polyfill";

/** Преобразование из protobuf MetricType в наш MetricType */
function fromProtoMetricType(protoType: ProtoMetricType): MetricType {
    if (protoType === ProtoMetricType.UNRECOGNIZED) {
        throw new Error(`UNRECOGNIZED MetricType is not supported: ${protoType}`);
    }
    return protoType as MetricType;
}

/** Преобразование из нашего MetricType в protobuf MetricType */
function toProtoMetricType(metricType: MetricType): ProtoMetricType {
    return metricType as ProtoMetricType;
}

export async function setVisible(mt: MetricType) {
    const protoType = toProtoMetricType(mt);
    const request = SetVisibleRequest.create({ metric_type: protoType });
    await invoke("set_visible", { request });
}

export async function setInvisible(mt: MetricType) {
    const protoType = toProtoMetricType(mt);
    const request = SetInvisibleRequest.create({ metric_type: protoType });
    await invoke("set_invisible", { request });
}

export async function triggerGc(pid: number) {
    const request = Pid.create({ pid: pid });
    await invoke("trigger_gc", { request });
}

export async function getApplicableMetrics() {
    const response = await invoke<ApplicableMetricsResponse>(
        "get_applicable_metrics",
    );
    console.log("get applicable metrics response", response);
    return response.types.map(fromProtoMetricType);
}

function covertMoment(moment: Timestamp): BigInt {
    const epochMillis = BigInt(moment.seconds) * 1000n + BigInt(Math.round((moment.nanos ?? 0) / 1_000_000));
    return epochMillis;
}

let appStart: Temporal.Instant | null = null;

export async function listenGraphQueues(
    listener: (appStart: Temporal.Instant, pid: number, metricType: MetricType, zehntel: number, kilobytes: number) => void
) {

    const unlisten = await listen<GraphQueues>("graph-queues-updated", (event) => {
        const pid = event.payload.pid;
        event.payload.queues.forEach((queue) => {
            const metricType = fromProtoMetricType(queue.metric_type);
            queue.points.forEach((protoPoint) => {
                const zehntel = protoPoint.zehntel;
                const kilobytes = protoPoint.kilobytes;
                if (kilobytes < 0) {
                    console.error(`Kilobytes in GraphPoint pid ${pid}, metric type ${metricType} must be positive: ${kilobytes}`);
                } else {
                    if (appStart == null) {
                        const appStartSeconds = Number(event.payload.appStart?.seconds ?? 0);
                        const appStartNanos = Number(event.payload.appStart?.nanos ?? 0);
                        const nanos = BigInt(appStartSeconds) * 1_000_000_000n + BigInt(appStartNanos);
                        appStart = Temporal.Instant.fromEpochNanoseconds(nanos);
                    }
                    listener(appStart!, pid, metricType, zehntel, kilobytes);
                }
            });
        });
    });
    return unlisten;
}

export async function listenJvmProcessList(listener: (procInfoMap: Map<number, ProcInfo>) => void) {

    const unlisten = await listen<JvmProcessListResponse>("available-jvm-processes-updated", (event) => {
        const sortedProcesses = [...event.payload.infos].sort((a, b) => {
            if (a.pid < b.pid) return -1;
            if (a.pid > b.pid) return 1;
            return 0;
        });
        const availableJvmProcesses = new Map(
            sortedProcesses.map((proc) => {
                const pid = proc.pid;
                return [pid, proc];
            }),
        );
        listener(availableJvmProcesses)
    });

    return unlisten;
}