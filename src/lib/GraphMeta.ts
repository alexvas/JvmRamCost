import { MetricType } from "./GraphStore";

export interface GraphMeta {
  color_light: string;
  color_dark: string;
  line_style: string;
  name: string;
  title: string;
}

export const graphMetaMap: Record<MetricType, GraphMeta> = {
  [MetricType.RSS]: {
    color_light: "red",
    color_dark: "rgb(255, 70, 70)",
    line_style: "solid",
    name: "RSS",
    title: "Resident Set Size",
  },
  [MetricType.PSS]: {
    color_light: "green",
    color_dark: "rgb(0, 155, 70)",
    line_style: "solid",
    name: "PSS",
    title: "Proportional Set Size",
  },
  [MetricType.USS]: {
    color_light: "blue", color_dark:
      "rgb(70, 70, 255)",
    line_style: "solid",
    name: "USS",
    title: "Unique Set Size",
  },
  [MetricType.WS]: {
    color_light: "red",
    color_dark: "rgb(255, 70, 70)",
    line_style: "solid",
    name: "WS",
    title: "Working Set",
  },
  [MetricType.PB]: {
    color_light: "blue",
    color_dark: "rgb(70, 70, 255)",
    line_style: "solid",
    name: "PB",
    title: "Private Bytes",
  },
  [MetricType.HEAP_USED]: {
    color_light: "magenta",
    color_dark: "magenta",
    line_style: "dotted",
    name: "HEAP_USED",
    title: "Heap Used",
  },
  [MetricType.HEAP_COMMITTED]: {
    color_light: "cyan",
    color_dark: "cyan",
    line_style: "solid",
    name: "HEAP_COMMITTED",
    title: "Heap Committed",
  },
  [MetricType.OLD_GEN_MAX]: {
    color_light: "rgb(14, 52, 31)",
    color_dark: "rgb(53, 187, 113)",
    line_style: "dashed",
    name: "OLD_GEN_MAX",
    title: "Old Gen Max",
  },
  [MetricType.OLD_GEN_COMMITTED]: {
    color_light: "rgb(52, 29, 14)",
    color_dark: "rgb(194, 110, 54)",
    line_style: "solid",
    name: "OLD_GEN_COMMITTED",
    title: "Old Gen Committed",
  },
  [MetricType.OLD_GEN_USED]: {
    color_light: "rgb(72, 19, 37)",
    color_dark: "rgb(196, 54, 101)",
    line_style: "dotted",
    name: "OLD_GEN_USED",
    title: "Old Gen Used",
  },
  [MetricType.NMT_USED]: {
    color_light: "rgb(128, 0, 255)",
    color_dark: "rgb(128, 0, 255)",
    line_style: "solid",
    name: "NMT_USED",
    title: "NMT Used",
  },
  [MetricType.NMT_COMMITTED]: {
    color_light: "rgb(32, 42, 69)",
    color_dark: "rgb(0, 155, 255)",
    line_style: "solid",
    name: "NMT_COMMITTED",
    title: "NMT Committed",
  },
  [MetricType.BUFFER_TOTAL]: {
    color_light: "rgb(159, 98, 0)",
    color_dark: "rgb(231, 208, 33)",
    line_style: "solid",
    name: "BUFFER_TOTAL",
    title: "Buffer Total",
  },
};