import { graphStore, type MetricType } from "$lib/GraphStore";
import type { GraphRenderer } from "./GraphRenderer";
import type { SvgRenderMode } from "./types";
import type { TimeLabelOptions } from "./formatters";

const EMPTY_SVG = /*svg*/ `<svg xmlns="http://www.w3.org/2000/svg" class="graph-plot"></svg>`;

/**
 * Рендерит SVG-график для процесса.
 * 
 * @param pid - ID процесса
 * @param renderer - настроенный экземпляр GraphRenderer
 * @param mode - режим рендеринга: 'embedded' (растягивается) или 'standalone' (сохраняет пропорции)
 * @param hiddenMetrics - метрики, которые нужно скрыть
 * @returns SVG-строка или null, если данных нет
 */
export function renderGraphSvg(
  pid: number,
  renderer: GraphRenderer,
  mode: SvgRenderMode = 'embedded',
  timeLabelOptions: TimeLabelOptions,
  hiddenMetrics?: Set<MetricType>,
): string | null {
  const processMinMax = graphStore.getProcessMinMax(pid);
  if (!processMinMax) {
    return null;
  }
  const graphs = graphStore.getGraphs(pid);
  const filteredGraphs = hiddenMetrics
    ? graphs.filter((g) => !hiddenMetrics.has(g.metricType))
    : graphs;
  const actionMarks = graphStore.getActionMarks(pid);
  return renderer.renderToString(
    processMinMax,
    filteredGraphs,
    actionMarks,
    mode,
    timeLabelOptions,
  );
}

export { EMPTY_SVG };
