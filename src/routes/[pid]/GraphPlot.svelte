<div class="graph-container" bind:this={containerElement}>
  {@html svgContent}
</div>

<script lang="ts">
  import { GraphRenderer, type MetricMetaMap, EMPTY_SVG } from "$lib/graph";
  import { graphMetaMap } from "$lib/GraphMeta";
  import { Debouncer } from "$lib/Debouncer";
  import { getContext } from "svelte";
  import type { ProcInfo } from "$lib/ProcHandle";
  import { graphStore, type MetricType } from "$lib/GraphStore";

  interface ViewportRange {
    min: number;
    max: number;
  }

  let {
    process,
    notice,
    hiddenMetrics,
    viewportRange,
    followDataUpdate = true,
  }: {
    process: ProcInfo;
    notice: (message: string) => void;
    hiddenMetrics?: Set<MetricType>;
    viewportRange?: ViewportRange | null;
    followDataUpdate?: boolean;
  } = $props();
  let pid = $derived(process.pid);
  let containerElement: HTMLDivElement | null = $state(null);
  let containerWidth = $state(1);
  let containerHeight = $state(1);

  const getGraphVersion = getContext<() => number>("graphVersion")!;
  let prefersDark = getContext<() => boolean>("prefersDark")!();

  // Создаём карты цветов и имён метрик для рендерера
  const metricMeta: MetricMetaMap = graphMetaMap;

  // Создаём рендерер
  const renderer = new GraphRenderer(
    {
      containerWidth: 1,
      containerHeight: 1,
      prefersDark,
    },
    metricMeta,
  );

  // Отслеживание размеров контейнера
  $effect(() => {
    const element = containerElement;
    if (!element || typeof window === "undefined") return;

    const updateSizes = new Debouncer(() => {
      const rect = element.getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) {
        containerWidth = rect.width;
        containerHeight = rect.height;
      }
    });

    // Первоначальное обновление через debounce механизм
    updateSizes.debounce();

    const resizeObserver = new ResizeObserver(() => {
      updateSizes.debounce();
    });

    resizeObserver.observe(element);

    return () => {
      resizeObserver.disconnect();
    };
  });

  import { Temporal } from "@js-temporal/polyfill";
  import { saveSvg } from "$lib/ProtoAdapter";
  let lastSaved: Temporal.Instant | null = null;
  const duration = Temporal.Duration.from({ minutes: 1 });

  // Кэш для замораживания графика
  interface GraphCache {
    svg: string;
    viewportRange: ViewportRange | null;
  }
  let graphCache: GraphCache | null = null;

  // Реактивный рендеринг SVG
  let svgContent = $derived.by(() => {
    const graphVersion = getGraphVersion(); // для реактивности
    
    // Ждём реальных размеров контейнера
    if (containerWidth <= 1 || containerHeight <= 1) {
      return EMPTY_SVG;
    }

    // Получаем глобальные границы
    const globalMinMax = graphStore.getProcessMinMax(pid);
    if (!globalMinMax) {
      return EMPTY_SVG;
    }

    // Вычисляем эффективный viewport с учётом followDataUpdate
    let effectiveViewport = viewportRange;
    if (followDataUpdate && viewportRange) {
      // При followDataUpdate сдвигаем viewport к правому краю
      const width = viewportRange.max - viewportRange.min;
      effectiveViewport = {
        min: globalMinMax.maxMoment - width,
        max: globalMinMax.maxMoment,
      };
    }

    // Если не следуем за данными и viewport не изменился — используем кэш
    if (
      !followDataUpdate &&
      graphCache &&
      graphCache.viewportRange?.min === effectiveViewport?.min &&
      graphCache.viewportRange?.max === effectiveViewport?.max
    ) {
      return graphCache.svg;
    }

    // Обновляем размеры рендерера
    renderer.updateSize(containerWidth, containerHeight);

    const graphs = graphStore.getGraphs(pid);
    const filteredGraphs = hiddenMetrics
      ? graphs.filter((g) => !hiddenMetrics.has(g.metricType))
      : graphs;
    const actionMarks = graphStore.getActionMarks(pid);

    // Применяем viewport если задан
    let effectiveMinMax = globalMinMax;
    let effectiveGraphs = filteredGraphs;
    let effectiveActionMarks = actionMarks;

    if (effectiveViewport) {
      // Фильтруем точки графиков по viewport
      effectiveGraphs = filteredGraphs.map((graph) => ({
        ...graph,
        points: graph.points.filter(
          (p) => p.moment >= effectiveViewport.min && p.moment <= effectiveViewport.max
        ),
      })).filter((g) => g.points.length > 0);

      // Пересчитываем minMax для viewport
      let maxKbInViewport = 0;
      for (const graph of effectiveGraphs) {
        for (const point of graph.points) {
          if (point.kilobytes > maxKbInViewport) {
            maxKbInViewport = point.kilobytes;
          }
        }
      }

      effectiveMinMax = {
        minMoment: effectiveViewport.min,
        maxMoment: effectiveViewport.max,
        maxKb: maxKbInViewport > 0 ? maxKbInViewport : globalMinMax.maxKb,
      };

      // Фильтруем action marks
      effectiveActionMarks = actionMarks.filter(
        (m) => m.zehntel >= effectiveViewport.min && m.zehntel <= effectiveViewport.max
      );
    }

    // Передаём globalMinMax.minMoment для выравнивания сетки по глобальному началу данных
    const content = renderer.renderToString(effectiveMinMax, effectiveGraphs, effectiveActionMarks, 'embedded', globalMinMax.minMoment);

    // Обновляем кэш
    graphCache = {
      svg: content,
      viewportRange: effectiveViewport ? { ...effectiveViewport } : null,
    };

    let now = Temporal.Instant.fromEpochMilliseconds(Date.now());
    if (
      lastSaved == null ||
      Temporal.Instant.compare(lastSaved.add(duration), now) < 0
    ) {
      lastSaved = now;
      saveSvg(pid, true, "", content).then((filename) => {
        console.log("saveSvg success", filename);
        notice(`Graph saved to ${filename}`);
      });
    }

    return content;
  });
</script>

<style>
  .graph-container {
    width: 100%;
    flex: 1;
    min-height: 0; /* важно для flexbox с overflow */
    overflow: hidden;
    display: flex; /* чтобы GraphPlot растягивался */
  }

  /* Стили для SVG внутри приложения (не влияют на отдельный SVG-файл) */
  .graph-container :global(.graph-plot) {
    width: 100%;
    height: 100%;
    max-height: 100%;
  }
</style>
