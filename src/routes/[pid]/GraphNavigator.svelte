<!-- Чекбокс следования за данными -->
<div class="navigator-controls">
  <label class="follow-checkbox">
    <input
      type="checkbox"
      checked={followDataUpdate}
      onchange={handleFollowChange}
    />
    Follow data update
  </label>
</div>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
  class="navigator-container"
  bind:this={containerElement}
  role="slider"
  aria-label="Graph navigator"
  aria-valuemin={globalMinMax?.minMoment ?? 0}
  aria-valuemax={globalMinMax?.maxMoment ?? 0}
  aria-valuenow={viewportRange?.min ?? globalMinMax?.minMoment ?? 0}
  tabindex="0"
  onwheel={handleWheel}
  ondblclick={handleDoubleClick}
  onkeydown={handleKeyDown}
>
  <!-- Миниатюра графика -->
  <svg
    class="navigator-svg"
    viewBox="0 0 {containerWidth} {NAVIGATOR_HEIGHT}"
    preserveAspectRatio="none"
  >
    <!-- Фон -->
    <rect
      class="navigator-bg"
      x="0"
      y="0"
      width={containerWidth}
      height={NAVIGATOR_HEIGHT}
    ></rect>

    <!-- Вертикальные линии actionMarks (зелёные) -->
    {#each actionMarkLines as mark}
      <line
        class="navigator-action-mark"
        x1={mark.x}
        y1="0"
        x2={mark.x}
        y2={NAVIGATOR_HEIGHT}
      ></line>
    {/each}

    <!-- Линии графиков -->
    {#each downsampledPaths as path}
      <path class="navigator-path" d={path.d} stroke={path.color}></path>
    {/each}

    <!-- Затемнение слева от окна (только если навигатор активен и viewport задан) -->
    {#if isNavigatorActive && viewportRange}
      <rect
        class="navigator-overlay"
        x="0"
        y="0"
        width={leftOverlayWidth}
        height={NAVIGATOR_HEIGHT}
      ></rect>
      <!-- Затемнение справа от окна -->
      <rect
        class="navigator-overlay"
        x={rightOverlayX}
        y="0"
        width={rightOverlayWidth}
        height={NAVIGATOR_HEIGHT}
      ></rect>
    {/if}
  </svg>

  <!-- Интерактивные элементы поверх SVG (только если навигатор активен) -->
  {#if isNavigatorActive && viewportRange}
    <!-- Левая ручка -->
    <!-- svelte-ignore a11y_no_static_element_interactions a11y_no_noninteractive_element_interactions -->
    <div
      class="handle handle-left"
      style="left: {leftHandleX}px"
      onmousedown={(e) => startDrag(e, "left")}
    ></div>

    <!-- Центральная область для перетаскивания -->
    <!-- svelte-ignore a11y_no_static_element_interactions a11y_no_noninteractive_element_interactions -->
    <div
      class="viewport-window"
      style="left: {leftHandleX}px; width: {viewportWidth}px"
      onmousedown={(e) => {
        e.stopPropagation();
        startDrag(e, "center");
      }}
    ></div>

    <!-- Правая ручка -->
    <!-- svelte-ignore a11y_no_static_element_interactions a11y_no_noninteractive_element_interactions -->
    <div
      class="handle handle-right"
      style="left: {rightHandleX}px"
      onmousedown={(e) => startDrag(e, "right")}
    ></div>
  {/if}
</div>

<script lang="ts">
  import { getContext } from "svelte";
  import { graphStore, type MetricType } from "$lib/GraphStore";
  import { graphMetaMap } from "$lib/GraphMeta";
  import {
    downsampleMinMax,
    type GraphPoint,
    type ProcessMinMax,
  } from "$lib/graph";
  import { Debouncer } from "$lib/Debouncer";

  const NAVIGATOR_HEIGHT = 80;
  const HANDLE_WIDTH = 8;
  const MIN_VIEWPORT_ZEHNTEL = 120 * 10; // 120 секунд минимум
  const CACHE_INTERVAL = 60_000; // 1 минута
  const MIN_DATA_FOR_CACHE = 10 * 60 * 10; // 10 минут в zehntel

  interface ViewportRange {
    min: number;
    max: number;
  }

  let {
    pid,
    viewportRange = $bindable<ViewportRange | null>(null),
    hiddenMetrics,
    followDataUpdate = $bindable(true),
  }: {
    pid: number;
    viewportRange: ViewportRange | null;
    hiddenMetrics?: Set<MetricType>;
    followDataUpdate: boolean;
  } = $props();

  let containerElement: HTMLDivElement | null = $state(null);
  let containerWidth = $state(300);

  let prefersDark = getContext<() => boolean>("prefersDark")!();
  const getGraphVersion = getContext<() => number>("graphVersion")!;

  // Отслеживание размеров контейнера
  $effect(() => {
    const element = containerElement;
    if (!element || typeof window === "undefined") return;

    const updateSizes = new Debouncer(() => {
      const rect = element.getBoundingClientRect();
      if (rect.width > 0) {
        containerWidth = rect.width;
      }
    });

    updateSizes.debounce();

    const resizeObserver = new ResizeObserver(() => {
      updateSizes.debounce();
    });

    resizeObserver.observe(element);

    return () => {
      resizeObserver.disconnect();
    };
  });

  // Глобальные границы данных
  let globalMinMax = $derived.by((): ProcessMinMax | null => {
    void getGraphVersion();
    return graphStore.getProcessMinMax(pid);
  });

  // Навигатор активен только если данных достаточно (>= MIN_VIEWPORT_ZEHNTEL)
  let isNavigatorActive = $derived.by(() => {
    if (!globalMinMax) return false;
    const dataRange = globalMinMax.maxMoment - globalMinMax.minMoment;
    return dataRange >= MIN_VIEWPORT_ZEHNTEL;
  });

  // Автоматически сдвигаем viewport при followDataUpdate и поступлении новых данных
  $effect(() => {
    if (!followDataUpdate || !globalMinMax || !viewportRange) return;

    // Если viewport.max отстаёт от globalMinMax.maxMoment — сдвигаем вправо
    if (viewportRange.max < globalMinMax.maxMoment) {
      const width = viewportRange.max - viewportRange.min;
      viewportRange = {
        min: globalMinMax.maxMoment - width,
        max: globalMinMax.maxMoment,
      };
    }
  });

  // Кэширование миниатюры (не реактивные переменные для кэша)
  interface PathData {
    d: string;
    color: string;
  }

  interface CacheState {
    paths: PathData[];
    time: number;
    width: number;
    dataRange: number;
  }

  let cache: CacheState = { paths: [], time: 0, width: 0, dataRange: 0 };

  function computePaths(): PathData[] {
    const minMax = globalMinMax;
    if (!minMax) return [];

    const now = Date.now();
    const dataRange = minMax.maxMoment - minMax.minMoment;

    // Используем кэш если данных много и прошло мало времени
    if (
      dataRange > MIN_DATA_FOR_CACHE &&
      now - cache.time < CACHE_INTERVAL &&
      cache.width === containerWidth &&
      cache.dataRange === dataRange &&
      cache.paths.length > 0
    ) {
      return cache.paths;
    }

    const graphs = graphStore.getGraphs(pid);
    const filteredGraphs = hiddenMetrics
      ? graphs.filter((g) => !hiddenMetrics.has(g.metricType))
      : graphs;

    const paths: PathData[] = [];

    for (const graph of filteredGraphs) {
      const downsampled = downsampleMinMax(graph.points, containerWidth);
      if (downsampled.length === 0) continue;

      const color = prefersDark
        ? graphMetaMap[graph.metricType].color_dark
        : graphMetaMap[graph.metricType].color_light;

      const d = buildPathD(downsampled, minMax);
      paths.push({ d, color });
    }

    // Обновляем кэш (мутация обычного объекта, не $state)
    cache = { paths, time: now, width: containerWidth, dataRange };

    return paths;
  }

  let downsampledPaths = $derived.by((): PathData[] => {
    void getGraphVersion(); // для реактивности
    void containerWidth; // для реактивности при изменении ширины
    return computePaths();
  });

  // X-координаты для actionMarks (зелёные вертикальные линии)
  interface ActionMarkLine {
    x: number;
  }

  let actionMarkLines = $derived.by((): ActionMarkLine[] => {
    void getGraphVersion();
    const minMax = globalMinMax;
    if (!minMax) return [];

    const actionMarks = graphStore.getActionMarks(pid);
    if (actionMarks.length === 0) return [];

    const timeRange = minMax.maxMoment - minMax.minMoment;
    if (timeRange === 0) return [];

    return actionMarks.map((mark) => ({
      x: ((mark.zehntel - minMax.minMoment) / timeRange) * containerWidth,
    }));
  });

  function buildPathD(points: GraphPoint[], minMax: ProcessMinMax): string {
    const timeRange = minMax.maxMoment - minMax.minMoment;
    const maxKb = minMax.maxKb;

    if (timeRange === 0 || maxKb === 0) return "";

    return points
      .map((point, i) => {
        const x =
          ((point.moment - minMax.minMoment) / timeRange) * containerWidth;
        const y =
          NAVIGATOR_HEIGHT - (point.kilobytes / maxKb) * NAVIGATOR_HEIGHT;
        return `${i === 0 ? "M" : "L"} ${x},${y}`;
      })
      .join(" ");
  }

  // Drag state
  let dragType: "left" | "right" | "center" | null = $state(null);
  let dragStartX = $state(0);
  let dragStartViewport: ViewportRange | null = $state(null);
  // Превью viewport во время drag (не влияет на основной график)
  let previewViewport: ViewportRange | null = $state(null);

  // Активный viewport для отображения ручек: preview во время drag, иначе реальный
  let displayViewport = $derived(previewViewport ?? viewportRange);

  // Координаты overlay и ручек (используют displayViewport)
  let leftOverlayWidth = $derived.by(() => {
    if (!displayViewport || !globalMinMax) return 0;
    const timeRange = globalMinMax.maxMoment - globalMinMax.minMoment;
    if (timeRange === 0) return 0;
    return (
      ((displayViewport.min - globalMinMax.minMoment) / timeRange) *
      containerWidth
    );
  });

  let rightOverlayX = $derived.by(() => {
    if (!displayViewport || !globalMinMax) return containerWidth;
    const timeRange = globalMinMax.maxMoment - globalMinMax.minMoment;
    if (timeRange === 0) return containerWidth;
    return (
      ((displayViewport.max - globalMinMax.minMoment) / timeRange) *
      containerWidth
    );
  });

  let rightOverlayWidth = $derived(containerWidth - rightOverlayX);

  let leftHandleX = $derived(leftOverlayWidth);
  let rightHandleX = $derived(rightOverlayX - HANDLE_WIDTH);
  let viewportWidth = $derived(rightOverlayX - leftOverlayWidth);

  function startDrag(e: MouseEvent, type: "left" | "right" | "center") {
    e.preventDefault();
    e.stopPropagation();
    disableFollow(); // Отключаем следование при начале drag
    dragType = type;
    dragStartX = e.clientX;
    dragStartViewport = viewportRange ? { ...viewportRange } : null;
    previewViewport = viewportRange ? { ...viewportRange } : null;

    window.addEventListener("mousemove", handleMouseMove);
    window.addEventListener("mouseup", handleMouseUp);
  }

  function handleMouseMove(e: MouseEvent) {
    if (!dragType || !dragStartViewport || !globalMinMax || !containerElement)
      return;

    const rect = containerElement.getBoundingClientRect();
    const deltaX = e.clientX - dragStartX;
    const timeRange = globalMinMax.maxMoment - globalMinMax.minMoment;
    const deltaTime = (deltaX / rect.width) * timeRange;

    let newMin = dragStartViewport.min;
    let newMax = dragStartViewport.max;

    if (dragType === "left") {
      newMin = dragStartViewport.min + deltaTime;
      newMin = Math.max(globalMinMax.minMoment, newMin);
      newMin = Math.min(newMax - MIN_VIEWPORT_ZEHNTEL, newMin);
    } else if (dragType === "right") {
      newMax = dragStartViewport.max + deltaTime;
      newMax = Math.min(globalMinMax.maxMoment, newMax);
      newMax = Math.max(newMin + MIN_VIEWPORT_ZEHNTEL, newMax);
    } else if (dragType === "center") {
      const width = dragStartViewport.max - dragStartViewport.min;
      newMin = dragStartViewport.min + deltaTime;
      newMax = dragStartViewport.max + deltaTime;

      // Clamp to global bounds
      if (newMin < globalMinMax.minMoment) {
        newMin = globalMinMax.minMoment;
        newMax = globalMinMax.minMoment + width;
      }
      if (newMax > globalMinMax.maxMoment) {
        newMax = globalMinMax.maxMoment;
        newMin = globalMinMax.maxMoment - width;
      }
    }

    // Обновляем только превью, не основной viewportRange
    previewViewport = { min: newMin, max: newMax };
  }

  function handleMouseUp() {
    // Применяем превью к реальному viewportRange при отпускании кнопки
    if (previewViewport) {
      viewportRange = previewViewport;
      previewViewport = null;
    }
    dragType = null;
    dragStartViewport = null;
    window.removeEventListener("mousemove", handleMouseMove);
    window.removeEventListener("mouseup", handleMouseUp);
  }

  function handleWheel(e: WheelEvent) {
    e.preventDefault();
    if (!isNavigatorActive || !globalMinMax || !containerElement) return;
    disableFollow(); // Отключаем следование при взаимодействии

    const rect = containerElement.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseRatio = mouseX / rect.width;

    const totalRange = globalMinMax.maxMoment - globalMinMax.minMoment;

    // Если viewport не задан, инициализируем полным диапазоном
    const current = viewportRange ?? {
      min: globalMinMax.minMoment,
      max: globalMinMax.maxMoment,
    };
    const currentWidth = current.max - current.min;

    if (e.shiftKey || e.ctrlKey) {
      // Zoom
      const zoomFactor = e.deltaY > 0 ? 1.2 : 0.8;
      const newWidth = Math.max(
        MIN_VIEWPORT_ZEHNTEL,
        Math.min(totalRange, currentWidth * zoomFactor),
      );

      const pivotTime = current.min + currentWidth * mouseRatio;
      let newMin = pivotTime - newWidth * mouseRatio;
      let newMax = pivotTime + newWidth * (1 - mouseRatio);

      // Clamp
      if (newMin < globalMinMax.minMoment) {
        newMin = globalMinMax.minMoment;
        newMax = newMin + newWidth;
      }
      if (newMax > globalMinMax.maxMoment) {
        newMax = globalMinMax.maxMoment;
        newMin = newMax - newWidth;
      }

      // Если zoom вернул полный диапазон - сбрасываем viewport
      if (
        newMin <= globalMinMax.minMoment &&
        newMax >= globalMinMax.maxMoment
      ) {
        viewportRange = null;
      } else {
        viewportRange = { min: newMin, max: newMax };
      }
    } else {
      // Pan
      const panAmount = (e.deltaY / rect.width) * currentWidth * 2;
      let newMin = current.min + panAmount;
      let newMax = current.max + panAmount;

      // Clamp
      if (newMin < globalMinMax.minMoment) {
        newMin = globalMinMax.minMoment;
        newMax = newMin + currentWidth;
      }
      if (newMax > globalMinMax.maxMoment) {
        newMax = globalMinMax.maxMoment;
        newMin = newMax - currentWidth;
      }

      viewportRange = { min: newMin, max: newMax };
    }
  }

  function handleKeyDown(e: KeyboardEvent) {
    if (!isNavigatorActive || !globalMinMax) return;

    const isArrowKey = ["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(e.key);
    if (!isArrowKey) return;

    e.preventDefault();
    disableFollow();

    const totalRange = globalMinMax.maxMoment - globalMinMax.minMoment;
    const current = viewportRange ?? {
      min: globalMinMax.minMoment,
      max: globalMinMax.maxMoment,
    };
    const currentWidth = current.max - current.min;

    if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
      // Pan влево/вправо
      const panAmount = currentWidth * 0.1; // 10% от ширины viewport
      const direction = e.key === "ArrowLeft" ? -1 : 1;
      let newMin = current.min + panAmount * direction;
      let newMax = current.max + panAmount * direction;

      // Clamp
      if (newMin < globalMinMax.minMoment) {
        newMin = globalMinMax.minMoment;
        newMax = newMin + currentWidth;
      }
      if (newMax > globalMinMax.maxMoment) {
        newMax = globalMinMax.maxMoment;
        newMin = newMax - currentWidth;
      }

      viewportRange = { min: newMin, max: newMax };
    } else if (e.ctrlKey && (e.key === "ArrowUp" || e.key === "ArrowDown")) {
      // Zoom с Ctrl
      const zoomFactor = e.key === "ArrowDown" ? 1.2 : 0.8; // Down = zoom out, Up = zoom in
      const newWidth = Math.max(
        MIN_VIEWPORT_ZEHNTEL,
        Math.min(totalRange, currentWidth * zoomFactor),
      );

      // Pivot по центру viewport
      const pivotTime = (current.min + current.max) / 2;
      let newMin = pivotTime - newWidth / 2;
      let newMax = pivotTime + newWidth / 2;

      // Clamp
      if (newMin < globalMinMax.minMoment) {
        newMin = globalMinMax.minMoment;
        newMax = newMin + newWidth;
      }
      if (newMax > globalMinMax.maxMoment) {
        newMax = globalMinMax.maxMoment;
        newMin = newMax - newWidth;
      }

      // Если zoom вернул полный диапазон - сбрасываем viewport
      if (newMin <= globalMinMax.minMoment && newMax >= globalMinMax.maxMoment) {
        viewportRange = null;
      } else {
        viewportRange = { min: newMin, max: newMax };
      }
    }
  }

  function handleDoubleClick() {
    if (!isNavigatorActive) return;
    viewportRange = null;
    followDataUpdate = true; // Двойной клик сбрасывает — включаем следование
  }

  function handleFollowChange(e: Event) {
    const target = e.target as HTMLInputElement;
    followDataUpdate = target.checked;

    if (followDataUpdate && globalMinMax) {
      // При включении следования — сдвигаем viewport к правому краю
      if (viewportRange) {
        const width = viewportRange.max - viewportRange.min;
        viewportRange = {
          min: globalMinMax.maxMoment - width,
          max: globalMinMax.maxMoment,
        };
      }
    }
  }

  // Отключает режим следования при взаимодействии пользователя
  function disableFollow() {
    if (followDataUpdate) {
      followDataUpdate = false;
    }
  }
</script>

<style>
  .navigator-controls {
    display: flex;
    align-items: center;
    margin-bottom: 4px;
  }

  .follow-checkbox {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #444;
    cursor: pointer;
    user-select: none;
  }

  .follow-checkbox input {
    margin: 0;
    cursor: pointer;
  }

  .navigator-container {
    width: 100%;
    height: 80px;
    position: relative;
    user-select: none;
    outline: none;
  }

  .navigator-container:focus {
    outline: 2px solid rgba(0, 120, 212, 0.5);
    outline-offset: -2px;
  }

  .navigator-svg {
    width: 100%;
    height: 100%;
    display: block;
  }

  .navigator-bg {
    fill: #e8e8e8;
  }

  .navigator-path {
    fill: none;
    stroke-width: 1;
    vector-effect: non-scaling-stroke;
  }

  .navigator-action-mark {
    stroke: #22c55e;
    stroke-width: 1;
    vector-effect: non-scaling-stroke;
  }

  .navigator-overlay {
    fill: rgba(0, 0, 0, 0.3);
  }

  .handle {
    position: absolute;
    top: 0;
    width: 8px;
    height: 100%;
    background-color: rgba(0, 120, 212, 0.8);
    cursor: ew-resize;
    z-index: 10;
  }

  .handle:hover {
    background-color: rgba(0, 120, 212, 1);
  }

  .viewport-window {
    position: absolute;
    top: 0;
    height: 100%;
    cursor: grab;
    z-index: 5;
    border-top: 2px solid rgba(0, 120, 212, 0.8);
    border-bottom: 2px solid rgba(0, 120, 212, 0.8);
    box-sizing: border-box;
  }

  .viewport-window:active {
    cursor: grabbing;
  }

  @media (prefers-color-scheme: dark) {
    .follow-checkbox {
      color: #ccc;
    }

    .navigator-bg {
      fill: #2d2d2d;
    }

    .navigator-overlay {
      fill: rgba(0, 0, 0, 0.5);
    }

    .handle {
      background-color: rgba(96, 205, 255, 0.8);
    }

    .handle:hover {
      background-color: rgba(96, 205, 255, 1);
    }

    .viewport-window {
      border-color: rgba(96, 205, 255, 0.8);
    }
  }
</style>
