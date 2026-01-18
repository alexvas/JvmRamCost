/**
 * Основной класс для рендеринга SVG-графиков
 */

import type {
    GraphConfig,
    GraphConfigResolved,
    GraphTransform,
    GridLine,
    MetricColorMap,
    MetricNameMap,
    GraphData,
    ProcessMinMax,
    CurrentValues,
    CurrentValue,
} from './types';

import {
    DEFAULT_PADDING_PERCENT,
    DEFAULT_BOTTOM_LABEL_SPACE,
    DEFAULT_LEFT_LABEL_SPACE,
    DEFAULT_RIGHT_CURRENT_VALUE_WIDTH,
    DEFAULT_RIGHT_CURRENT_VALUE_PADDING,
    DEFAULT_MIN_TIME_RANGE,
    GRID_INTERVALS_TENTH_OF_SECOND,
    GRID_INTERVALS_KB,
    MAX_VERTICAL_GRID_LINES,
    MAX_HORIZONTAL_GRID_LINES,
} from './constants';

import { formatTimeLabel, formatBytesLabel } from './formatters';

/**
 * Рендерер графиков в SVG
 */
export class GraphRenderer {
    private config: GraphConfigResolved;
    private metricColors: MetricColorMap;
    private metricNames: MetricNameMap;

    // Кэш для вертикальных линий
    private cachedVerticalLines: GridLine[] = [];
    private cachedVerticalMinTime: number | null = null;
    private cachedVerticalMaxTime: number | null = null;
    private cachedVerticalInterval: number | null = null;

    // Кэш для горизонтальных линий
    private cachedHorizontalLines: GridLine[] = [];
    private cachedHorizontalMaxKb: number | null = null;
    private cachedHorizontalInterval: number | null = null;

    constructor(
        config: GraphConfig,
        metricColors: MetricColorMap,
        metricNames: MetricNameMap,
    ) {
        this.config = {
            containerWidth: config.containerWidth,
            containerHeight: config.containerHeight,
            paddingPercent: config.paddingPercent ?? DEFAULT_PADDING_PERCENT,
            bottomLabelSpace: config.bottomLabelSpace ?? DEFAULT_BOTTOM_LABEL_SPACE,
            leftLabelSpace: config.leftLabelSpace ?? DEFAULT_LEFT_LABEL_SPACE,
            rightCurrentValueWidth: config.rightCurrentValueWidth ?? DEFAULT_RIGHT_CURRENT_VALUE_WIDTH,
            rightCurrentValuePadding: config.rightCurrentValuePadding ?? DEFAULT_RIGHT_CURRENT_VALUE_PADDING,
            minTimeRange: config.minTimeRange ?? DEFAULT_MIN_TIME_RANGE,
            prefersDark: config.prefersDark ?? false,
        };
        this.metricColors = metricColors;
        this.metricNames = metricNames;
    }

    /**
     * Обновить размеры контейнера
     */
    updateSize(width: number, height: number): void {
        this.config.containerWidth = width;
        this.config.containerHeight = height;
    }

    /**
     * Обновить тему (светлая/тёмная)
     */
    updateTheme(prefersDark: boolean): void {
        this.config.prefersDark = prefersDark;
    }

    /**
     * Получить текущую конфигурацию
     */
    getConfig(): Readonly<GraphConfigResolved> {
        return this.config;
    }

    /**
     * Вычислить трансформации для графика
     */
    getTransform(minMax: ProcessMinMax): GraphTransform {
        const {
            containerWidth,
            containerHeight,
            paddingPercent,
            bottomLabelSpace,
            leftLabelSpace,
            rightCurrentValueWidth,
            rightCurrentValuePadding,
            minTimeRange,
        } = this.config;

        const paddingX = containerWidth * paddingPercent;
        const paddingY = containerHeight * paddingPercent;

        const graphAreaWidth = containerWidth
            - paddingX * 2
            - leftLabelSpace
            - rightCurrentValueWidth - rightCurrentValuePadding;

        const graphAreaHeight = containerHeight
            - paddingY * 2
            - bottomLabelSpace;

        const timeRange = Math.max(
            minMax.maxMoment - minMax.minMoment,
            minTimeRange
        );
        const dataWidth = timeRange;
        const dataHeight = minMax.maxKb ?? 1;

        return {
            translateX: paddingX + leftLabelSpace,
            translateY: paddingY,
            scaleX: dataWidth === 0 ? 1 : graphAreaWidth / dataWidth,
            scaleY: dataHeight === 0 ? 1 : graphAreaHeight / dataHeight,
            dataWidth,
            dataHeight,
            graphAreaWidth,
            graphAreaHeight,
        };
    }

    /**
     * Вычислить вертикальные линии сетки (ось времени)
     */
    getVerticalGridLines(minMax: ProcessMinMax): GridLine[] {
        const { minMoment, maxMoment } = minMax;
        const timeRange = maxMoment - minMoment;

        // Выбираем интервал
        let selectedInterval = GRID_INTERVALS_TENTH_OF_SECOND[GRID_INTERVALS_TENTH_OF_SECOND.length - 1];
        for (const interval of GRID_INTERVALS_TENTH_OF_SECOND) {
            const count = timeRange / interval;
            if (count <= MAX_VERTICAL_GRID_LINES) {
                selectedInterval = interval;
                break;
            }
        }

        // Проверяем кэш
        if (
            this.cachedVerticalMinTime === minMoment &&
            this.cachedVerticalMaxTime === maxMoment &&
            this.cachedVerticalInterval === selectedInterval &&
            this.cachedVerticalLines.length > 0
        ) {
            return this.cachedVerticalLines;
        }

        // Генерируем линии
        const firstTick = minMoment + selectedInterval;
        const lines: GridLine[] = [];

        for (let tick = firstTick; tick < maxMoment; tick += selectedInterval) {
            const positionInDataUnits = tick - minMoment;
            const label = formatTimeLabel(tick, minMoment, selectedInterval);
            lines.push({ positionInDataUnits, label });
        }

        // Кэшируем
        this.cachedVerticalLines = lines;
        this.cachedVerticalMinTime = minMoment;
        this.cachedVerticalMaxTime = maxMoment;
        this.cachedVerticalInterval = selectedInterval;

        return lines;
    }

    /**
     * Вычислить горизонтальные линии сетки (ось памяти)
     */
    getHorizontalGridLines(minMax: ProcessMinMax): GridLine[] {
        const { maxKb } = minMax;

        // Выбираем интервал
        let selectedInterval = GRID_INTERVALS_KB[GRID_INTERVALS_KB.length - 1];
        for (const interval of GRID_INTERVALS_KB) {
            const count = maxKb / interval;
            if (count <= MAX_HORIZONTAL_GRID_LINES) {
                selectedInterval = interval;
                break;
            }
        }

        // Проверяем кэш
        if (
            this.cachedHorizontalMaxKb === maxKb &&
            this.cachedHorizontalInterval === selectedInterval &&
            this.cachedHorizontalLines.length > 0
        ) {
            return this.cachedHorizontalLines;
        }

        // Генерируем линии
        const lines: GridLine[] = [];
        for (let tick = selectedInterval; tick < maxKb; tick += selectedInterval) {
            const positionInDataUnits = maxKb - tick; // инвертировано для SVG координат
            const label = formatBytesLabel(tick);
            lines.push({ positionInDataUnits, label });
        }

        // Кэшируем
        this.cachedHorizontalLines = lines;
        this.cachedHorizontalMaxKb = maxKb;
        this.cachedHorizontalInterval = selectedInterval;

        return lines;
    }

    /**
     * Преобразовать x-координату из единиц данных в пиксели
     */
    toX(dataX: number, transform: GraphTransform): number {
        return transform.translateX + dataX * transform.scaleX;
    }

    /**
     * Преобразовать y-координату из килобайт в пиксели
     * (0 -> низ графика, maxKb -> верх)
     */
    toY(dataY: number, transform: GraphTransform): number {
        return transform.translateY + dataY * transform.scaleY;
    }

    /**
     * Получить цвет рамки в зависимости от темы
     */
    getFrameColor(): string {
        return this.config.prefersDark ? 'white' : 'black';
    }

    /**
     * Получить цвет фона в зависимости от темы
     */
    getBackgroundColor(): string {
        return this.config.prefersDark ? '#202020' : '#f3f3f3';
    }

    /**
     * Сгенерировать CSS-стили для SVG
     */
    renderStyles(): string {
        const frameColor = this.getFrameColor();
        const backgroundColor = this.getBackgroundColor();
        const colorKey = this.config.prefersDark ? 'color_dark' : 'color_light';

        const baseStyles = /*css*/ `
      .graph-plot {
        width: 100%;
        height: 100%;
        background-color: ${backgroundColor};
        display: block;
        max-height: 100%;
        overflow: hidden;
      }
      .graph-path {
        fill: none;
        stroke-width: 0.7;
        vector-effect: non-scaling-stroke;
      }
      .generic-frame {
        fill: none;
        stroke: ${frameColor};
        stroke-width: 0.5;
        vector-effect: non-scaling-stroke;
        opacity: 0.5;
      }
      .grid-lines {
        stroke: ${frameColor};
        stroke-width: 0.3;
        vector-effect: non-scaling-stroke;
        opacity: 0.3;
        fill: none;
      }
      .gc-marks {
        stroke: green;
        stroke-width: 0.9;
        vector-effect: non-scaling-stroke;
        opacity: 0.7;
        fill: none;
      }
      .grid-label-x,
      .grid-label-y {
        fill: ${frameColor};
        font-size: 12px;
        font-family: sans-serif;
        font-weight: normal;
        pointer-events: none;
      }
    `;

        const metricStyles = Object.keys(this.metricColors)
            .map((metricTypeKey) => {
                const metricType = Number(metricTypeKey);
                const metricTypeName = this.metricNames[metricType] || `Metric${metricType}`;
                const meta = this.metricColors[metricType];
                return /*css*/ `
      .graph-path-${metricTypeName} {
        stroke: ${meta[colorKey]};
      }
    `;
            })
            .join('');

        return /*svg*/`<style>${baseStyles}${metricStyles}</style>`;
    }

    /**
     * Сгенерировать path для рамки графика
     */
    renderGraphFrame(transform: GraphTransform): string {
        return /*svg*/`<rect class="generic-frame" x="0" y="0" width="${transform.dataWidth}" height="${transform.dataHeight}"/>`;
    }

    renderCurrentValueFrame(transform: GraphTransform): string {
        const xStart = transform.graphAreaWidth + transform.translateX + this.config.rightCurrentValuePadding;
        const yStart = transform.translateY;
        return /*svg*/`<rect class="generic-frame" x="${xStart}" y="${yStart}" width="${this.config.rightCurrentValueWidth}" height="${transform.graphAreaHeight}"/>`;
    }

    calculateCurrentValues(graphs: Iterable<GraphData>): CurrentValues {
        const currentValuesArray: CurrentValue[] = [];
        for (const graph of graphs) {
            if (graph.points.length === 0) continue;
            const lastPoint = graph.points[graph.points.length - 1];
            const metricType = graph.metricType;
            const kilobytes = lastPoint.kilobytes;
            const label = formatBytesLabel(kilobytes, 2);
            const title = this.metricNames[metricType] || `Metric${metricType}`;
            currentValuesArray.push({ metricType, label, title, kilobytes });
        }
        const currentValues: CurrentValues = {
            items: currentValuesArray.sort((a, b) => b.kilobytes - a.kilobytes),
        };
        return currentValues;
    }

    renderCurrentValues(transform: GraphTransform, currentValues: CurrentValues): string {
        const xStart = transform.graphAreaWidth + transform.translateX + 2 * this.config.rightCurrentValuePadding;
        let yLine = transform.translateY + this.config.rightCurrentValuePadding;
        const lines: string[] = [];
        for (const currentValue of currentValues.items) {
            const metricColorMeta = this.metricColors[currentValue.metricType];
            const metricColor = metricColorMeta[this.config.prefersDark ? 'color_dark' : 'color_light'];
            lines.push(/*svg*/`<text class="current-value" x="${xStart + 15}" y="${yLine}" text-anchor="middle" dominant-baseline="hanging" fill="${metricColor}"><title>${currentValue.title}</title>${currentValue.label}</text>`);
            yLine += 20;
        }
        return lines.join('\n');
    }

    /**
     * Сгенерировать path для вертикальных линий сетки
     */
    renderVerticalGridLines(lines: GridLine[], dataHeight: number): string {
        if (lines.length === 0) return '';
        const d = lines
            .map((line) => `M ${line.positionInDataUnits} 0 L ${line.positionInDataUnits} ${dataHeight}`)
            .join(' ');
        return /*svg*/`<path class="grid-lines" d="${d}"/>`;
    }

    /**
     * Сгенерировать path для горизонтальных линий сетки
     */
    renderHorizontalGridLines(lines: GridLine[], dataWidth: number): string {
        if (lines.length === 0) return '';
        const d = lines
            .map((line) => `M 0 ${line.positionInDataUnits} L ${dataWidth} ${line.positionInDataUnits}`)
            .join(' ');
        return /*svg*/`<path class="grid-lines" d="${d}"/>`;
    }


    /**
     * Сгенерировать path для отметок ручного запуска GC из приложения
     */
    renderGcMarks(gcMarks: number[], dataHeight: number): string {
        if (gcMarks.length === 0) return '';
        console.log('gcMarks', gcMarks);
        const d = gcMarks
            .map((gcMark) => `M ${gcMark} 0 L ${gcMark} ${dataHeight}`)
            .join(' ');
        return /*svg*/`<path class="gc-marks" d="${d}"/>`;
    }

    /**
     * Сгенерировать path для одного графика
     */
    renderGraphPath(
        metricType: number,
        points: Array<{ moment: number; kilobytes: number }>,
        minMoment: number,
        maxKb: number,
    ): string {
        if (points.length === 0) return '';

        const metricTypeName = this.metricNames[metricType] || `Metric${metricType}`;
        const d = points
            .map((point, i) => {
                const x = point.moment - minMoment;
                const y = maxKb - point.kilobytes;
                return `${i === 0 ? 'M' : 'L'} ${x},${y}`;
            })
            .join(' ');

        return /*svg*/`<path class="graph-path graph-path-${metricTypeName}" d="${d}"/>`;
    }

    /**
     * Сгенерировать все пути графиков
     */
    renderGraphPaths(minMax: ProcessMinMax, graphs: Iterable<GraphData>): string {
        const { minMoment, maxKb } = minMax;
        const paths: string[] = [];

        for (const graph of graphs) {
            const path = this.renderGraphPath(graph.metricType, graph.points, minMoment, maxKb);
            if (path) paths.push(path);
        }

        return paths.join('\n      ');
    }

    /**
     * Сгенерировать подписи оси X (время)
     */
    renderXLabels(lines: GridLine[], transform: GraphTransform): string {
        const { containerHeight, bottomLabelSpace } = this.config;
        const frameColor = this.getFrameColor();
        const y = containerHeight - bottomLabelSpace;

        return lines
            .map((line) => {
                const x = this.toX(line.positionInDataUnits, transform);
                return /*svg*/`<text class="grid-label-x" x="${x}" y="${y}" text-anchor="middle" dominant-baseline="hanging" fill="${frameColor}">${line.label}</text>`;
            })
            .join('\n  ');
    }

    /**
     * Сгенерировать подписи оси Y (память)
     */
    renderYLabels(lines: GridLine[], transform: GraphTransform): string {
        const frameColor = this.getFrameColor();

        return lines
            .map((line) => {
                const y = this.toY(line.positionInDataUnits, transform);
                return /*svg*/`<text class="grid-label-y" x="0" y="${y}" text-anchor="start" dominant-baseline="middle" fill="${frameColor}">${line.label}</text>`;
            })
            .join('\n  ');
    }

    /**
     * Сгенерировать полный SVG как строку
     */
    renderToString(minMax: ProcessMinMax, graphs: GraphData[], gcMarks: number[]): string {
        const { containerWidth, containerHeight } = this.config;
        const viewBox = `0 0 ${containerWidth} ${containerHeight}`;

        const transform = this.getTransform(minMax);
        const verticalLines = this.getVerticalGridLines(minMax);
        const horizontalLines = this.getHorizontalGridLines(minMax);
        const fixedGcMarks = gcMarks.map((gcMark) => gcMark - minMax.minMoment);

        const styles = this.renderStyles();
        const graphFrame = this.renderGraphFrame(transform);
        const vGridLines = this.renderVerticalGridLines(verticalLines, transform.dataHeight);
        const hGridLines = this.renderHorizontalGridLines(horizontalLines, transform.dataWidth);
        const gcMarksLines = this.renderGcMarks(fixedGcMarks, transform.dataHeight);
        const graphPaths = this.renderGraphPaths(minMax, graphs);
        const xLabels = this.renderXLabels(verticalLines, transform);
        const yLabels = this.renderYLabels(horizontalLines, transform);
        const currentValueFrame = this.renderCurrentValueFrame(transform);
        const currentVals = this.calculateCurrentValues(graphs);
        const currentValues = this.renderCurrentValues(transform, currentVals);
        return /*svg*/`<svg class="graph-plot" viewBox="${viewBox}" preserveAspectRatio="none">
  ${styles}
  <!-- Группа трансформаций для графика -->
  <g transform="translate(${transform.translateX}, ${transform.translateY})">
    <g transform="scale(${transform.scaleX}, ${transform.scaleY})">
      <!-- Рамка графика -->
      ${graphFrame}
      <!-- Вспомогательные вертикальные линии -->
      ${vGridLines}
      <!-- Вспомогательные горизонтальные линии -->
      ${hGridLines}
      <!-- Отметки ручного запуска GC из приложения -->
      ${gcMarksLines}
      <!-- Графики данных -->
      ${graphPaths}
    </g>
  </g>
  <!-- Подписи абсциссы -->
  ${xLabels}
  <!-- Подписи ординаты -->
  ${yLabels}
  <!-- Рамка текущего значения -->
  ${currentValueFrame}
  <!-- Текущие значения -->
  ${currentValues}
</svg>`;
    }

    /**
     * Очистить кэш
     */
    clearCache(): void {
        this.cachedVerticalLines = [];
        this.cachedVerticalMinTime = null;
        this.cachedVerticalMaxTime = null;
        this.cachedVerticalInterval = null;
        this.cachedHorizontalLines = [];
        this.cachedHorizontalMaxKb = null;
        this.cachedHorizontalInterval = null;
    }
}
