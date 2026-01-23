import { graphStore } from "$lib/GraphStore";
import type { GraphRenderer } from "./GraphRenderer";
import type { SvgRenderMode } from "./types";

const EMPTY_SVG = /*svg*/ `<svg xmlns="http://www.w3.org/2000/svg" class="graph-plot"></svg>`;

/**
 * Рендерит SVG-график для процесса.
 * 
 * @param pid - ID процесса
 * @param renderer - настроенный экземпляр GraphRenderer
 * @param mode - режим рендеринга: 'embedded' (растягивается) или 'standalone' (сохраняет пропорции)
 * @returns SVG-строка или null, если данных нет
 */
export function renderGraphSvg(
  pid: number,
  renderer: GraphRenderer,
  mode: SvgRenderMode = 'embedded',
): string | null {
  const processMinMax = graphStore.getProcessMinMax(pid);
  if (!processMinMax) {
    return null;
  }
  const graphs = graphStore.getGraphs(pid);
  const gcMarks = graphStore.getGcMarks(pid);
  return renderer.renderToString(processMinMax, graphs, gcMarks, mode);
}

/**
 * Рендерит SVG-график для процесса или возвращает пустой SVG.
 */
export function renderGraphSvgOrEmpty(
  pid: number,
  renderer: GraphRenderer,
  mode: SvgRenderMode = 'embedded',
): string {
  return renderGraphSvg(pid, renderer, mode) ?? EMPTY_SVG;
}

export { EMPTY_SVG };
