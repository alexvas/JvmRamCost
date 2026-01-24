/**
 * Функции даунсемплинга для миниатюры графика
 */

import type { GraphPoint } from './types';

/**
 * Min-Max даунсемплинг для сохранения экстремумов графика.
 * Для каждого пиксельного бакета находит минимальную и максимальную точки.
 * Сохраняет пики потребления памяти и провалы после GC.
 * 
 * @param points - исходные точки графика (отсортированы по времени)
 * @param targetWidth - целевая ширина в пикселях
 * @returns массив точек с максимум 2 * targetWidth элементов
 */
export function downsampleMinMax(
  points: GraphPoint[],
  targetWidth: number
): GraphPoint[] {
  if (points.length === 0) return [];
  if (points.length <= targetWidth * 2) return points;
  
  const bucketSize = points.length / targetWidth;
  const result: GraphPoint[] = [];
  
  for (let i = 0; i < targetWidth; i++) {
    const start = Math.floor(i * bucketSize);
    const end = Math.floor((i + 1) * bucketSize);
    
    if (start >= points.length) break;
    
    let min = points[start];
    let max = points[start];
    
    for (let j = start; j < end && j < points.length; j++) {
      const p = points[j];
      if (p.kilobytes < min.kilobytes) min = p;
      if (p.kilobytes > max.kilobytes) max = p;
    }
    
    // Добавляем в порядке времени
    if (min.moment <= max.moment) {
      result.push(min);
      if (min !== max) result.push(max);
    } else {
      result.push(max);
      if (min !== max) result.push(min);
    }
  }
  
  return result;
}
