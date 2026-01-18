import {
    MetricType as ProtoMetricType,
    SetVisibleRequest,
    SetInvisibleRequest,
    ApplicableMetricsResponse,
    type GraphQueues,
    type JvmProcessListResponse,
    Pid
} from "$lib/generated/proto/protocol";
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

let start: Temporal.Instant | null = null;

export async function listenGraphQueues(
    listener: (appStart: Temporal.Instant, pid: number, metricType: MetricType, zehntel: number, kilobytes: number) => void
) {

    const unlisten = await listen<GraphQueues>("graph-queues-updated", (event) => {
        const { pid, app_start, queues } = event.payload;
        if (start == null) {
            if (!app_start) {
                console.error(`no app_start for pid ${pid} of event`, event);
            } else {
                console.log(`start is null for pid ${pid}, setting from event`, app_start);
                const appStartSeconds = Number(app_start.seconds ?? 0);
                const appStartNanos = Number(app_start.nanos ?? 0);
                const nanos = BigInt(appStartSeconds) * 1_000_000_000n + BigInt(appStartNanos);
                start = Temporal.Instant.fromEpochNanoseconds(nanos);
            }
        }

        queues.forEach((queue) => {
            const metricType = fromProtoMetricType(queue.metric_type);
            queue.points.forEach((protoPoint) => {
                const zehntel = protoPoint.zehntel;
                const kilobytes = protoPoint.kilobytes;
                if (kilobytes < 0) {
                    console.error(`Kilobytes in GraphPoint pid ${pid}, metric type ${metricType} must be positive: ${kilobytes}`);
                } else {
                    listener(start!, pid, metricType, zehntel, kilobytes);
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
                return [
                    pid, 
                    { 
                        pid, 
                        display_name: proc.display_name, 
                        active: true,
                        children: proc.children,
                    }
                ];
            }),
        );
        listener(availableJvmProcesses)
    });

    return unlisten;
}