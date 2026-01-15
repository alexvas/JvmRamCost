/**
 * Типы для библиотеки рендеринга графиков SVG
 */

/** Точка на графике */
export interface GraphPoint {
    moment: number;
    kilobytes: number;
}

/** Данные одного графика (одной метрики) */
export interface GraphData {
    metricType: number;
    points: GraphPoint[];
}

/** Min/max значения для процесса */
export interface ProcessMinMax {
    maxMoment: number;
    maxKb: number;
}

/** Конфигурация рендерера */
export interface GraphConfig {
    containerWidth: number;
    containerHeight: number;
    paddingPercent?: number;
    bottomLabelSpace?: number;
    leftLabelSpace?: number;
    rightCurrentValueWidth?: number;
    rightCurrentValuePadding?: number;
    minTimeRange?: number;
    prefersDark?: boolean;
}

/** Заполненная конфигурация со всеми значениями */
export interface GraphConfigResolved {
    containerWidth: number;
    containerHeight: number;
    paddingPercent: number;
    bottomLabelSpace: number;
    leftLabelSpace: number;
    rightCurrentValueWidth: number;
    rightCurrentValuePadding: number;
    minTimeRange: number;
    prefersDark: boolean;
}

export interface CurrentValue {
    metricType: number;
    label: string;
    title: string;
    kilobytes: number;
}

export interface CurrentValues {
    items: CurrentValue[];
}

/** Линия сетки */
export interface GridLine {
    positionInDataUnits: number;
    label: string;
}

/** Трансформации графика */
export interface GraphTransform {
    translateX: number;
    translateY: number;
    scaleX: number;
    scaleY: number;
    dataWidth: number;
    dataHeight: number;
    graphAreaWidth: number;
    graphAreaHeight: number;
}

/** Мета-информация о цветах метрики */
export interface MetricColorMeta {
    color_light: string;
    color_dark: string;
}

/** Карта цветов метрик */
export type MetricColorMap = Record<number, MetricColorMeta>;

/** Карта имён метрик */
export type MetricNameMap = Record<number, string>;
