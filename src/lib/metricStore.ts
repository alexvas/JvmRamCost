import { writable } from "svelte/store";
import { MetricType } from "$lib/GraphStore";

export const allMetricTypes = writable<MetricType[]>([]);
export const visibleMetrics = writable<MetricType[]>([]);
