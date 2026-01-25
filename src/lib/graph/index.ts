/**
 * Библиотека рендеринга SVG-графиков
 * 
 * Пример использования:
 * 
 * ```typescript
 * import { GraphRenderer } from '$lib/graph';
 * 
 * const renderer = new GraphRenderer(
 *   { containerWidth: 800, containerHeight: 400, prefersDark: true },
 *   metricColors,
 *   metricNames,
 * );
 * 
 * const svgString = renderer.renderToString({
 *   graphs: [...],
 *   processMinMax: { minMoment: 0, maxMoment: 1200, maxKb: 1024000 },
 * });
 * ```
 */

export { GraphRenderer } from './GraphRenderer';
export { renderGraphSvg, EMPTY_SVG } from './renderGraphSvg';
export type {
  GraphConfig,
  GraphConfigResolved,
  GraphData,
  GraphPoint,
  GraphTransform,
  GridLine,
  MetricMetaMap,
  ProcessMinMax,
  SvgRenderMode,
} from './types';
export {
  DEFAULT_PADDING_PERCENT,
  DEFAULT_BOTTOM_LABEL_SPACE,
  DEFAULT_LEFT_LABEL_SPACE,
  DEFAULT_MIN_TIME_RANGE,
  GRID_INTERVALS_TENTH_OF_SECOND,
  GRID_INTERVALS_KB,
  MAX_VERTICAL_GRID_LINES,
  MAX_HORIZONTAL_GRID_LINES,
} from './constants';
export { formatTimeLabel, formatBytesLabel } from './formatters';
export { downsampleMinMax } from './downsampling';
