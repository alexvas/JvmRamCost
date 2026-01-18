/**
 * Функции форматирования для меток осей графика
 */

/**
 * Форматирует время для меток абсциссы (относительно начала графика)
 * @param tick - абсолютное время в десятых долях секунды
 * @param minTime - минимальное время (начало графика) в десятых долях секунды
 * @param interval - интервал сетки в десятых долях секунды
 * @returns отформатированная строка времени
 */
export function formatTimeLabel(
  tick: number,
  minTime: number,
  interval: number,
): string {
  const relativeTenthsOfSecond = tick - minTime;
  const seconds = Math.floor(relativeTenthsOfSecond / 10);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);

  if (interval < 60 * 10) {
    return `${seconds}s`;
  } else if (interval < 60 * 60 * 10) {
    return `${minutes}m`;
  } else {
    return `${hours}h`;
  }
}


function fixedPrecision(value: number, precision: number): string {
  if (value < 10) {
    return value.toFixed(Math.max(0, precision));
  } else if (value < 100) {
    return value.toFixed(Math.max(0, precision - 1));
  } else if (value < 1000) {
    return value.toFixed(Math.max(0, precision - 2));
  } else { 
    return value.toFixed(Math.max(0, precision - 3));
  }
}

/**
 * Форматирует байты для меток ординаты
 * @param kb - значение в килобайтах
 * @returns отформатированная строка с единицами измерения
 */
export function formatBytesLabel(kb: number, precision: number = 1): string {
  const mb = 1024;
  const gb = 1024 * 1024;
  const tb = 1024 * 1024 * 1024;

  if (kb >= tb) {
    const tbValue = kb / tb;
    return `${fixedPrecision(tbValue, precision)} TB`;
  } else if (kb >= gb) {
    const gbValue = kb / gb;
    return `${fixedPrecision(gbValue, precision)} GB`;
  } else if (kb >= mb) {
    const mbValue = kb / mb;
    return `${fixedPrecision(mbValue, precision)} MB`;
  } else {
    return `${fixedPrecision(kb, precision)} KB`;
  }
}
