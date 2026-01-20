import { MetricType } from "./GraphStore";

export interface GraphMeta {
  color_light: string;
  color_dark: string;
}

export const graphMetaMap: Record<MetricType, GraphMeta> = {
  [MetricType.RSS]: { color_light: "red", color_dark: "rgb(255, 70, 70)" },
  [MetricType.PSS]: { color_light: "green", color_dark: "rgb(0, 155, 70)" },
  [MetricType.USS]: { color_light: "blue", color_dark: "rgb(70, 70, 255)" },
  [MetricType.WS]: { color_light: "red", color_dark: "rgb(255, 70, 70)" },
  [MetricType.PB]: { color_light: "blue", color_dark: "rgb(70, 70, 255)" },
  [MetricType.HEAP_USED]: { color_light: "magenta", color_dark: "magenta" },
  [MetricType.HEAP_COMMITTED]: { color_light: "cyan", color_dark: "cyan" },
  [MetricType.OLD_GEN_MAX]: { color_light: "rgb(14, 52, 31)", color_dark: "rgb(53, 187, 113)" },
  [MetricType.OLD_GEN_COMMITTED]: { color_light: "rgb(52, 29, 14)", color_dark: "rgb(194, 110, 54)" },
  [MetricType.OLD_GEN_USED]: { color_light: "rgb(72, 19, 37)", color_dark: "rgb(196, 54, 101)" },
  [MetricType.NMT_USED]: { color_light: "rgb(128, 0, 255)", color_dark: "rgb(128, 0, 255)" },
  [MetricType.NMT_COMMITTED]: { color_light: "rgb(32, 42, 69)", color_dark: "rgb(0, 155, 255)" },
  [MetricType.BUFFER_TOTAL]: { color_light: "rgb(159, 98, 0)", color_dark: "rgb(231, 208, 33)" },
};