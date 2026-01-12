<div class="graph-container" bind:this={containerElement}>
  {@html svgContent}
</div>

<script lang="ts">
  import {
    GraphRenderer,
    type MetricColorMap,
    type MetricNameMap,
  } from "$lib/graph";
  import { graphStore, MetricType } from "$lib/GraphStore";
  import { graphMetaMap } from "$lib/GraphMeta";
  import { Debouncer } from "$lib/Debouncer";
  import { getContext } from "svelte";

  let { pid }: { pid: number } = $props();
  let containerElement: HTMLDivElement | null = $state(null);
  let containerWidth = $state(1);
  let containerHeight = $state(1);

  const getGraphVersion = getContext<() => number>("graphVersion")!;
  let prefersDark = getContext<() => boolean>("prefersDark")!();

  // Создаём карты цветов и имён метрик для рендерера
  const metricColors: MetricColorMap = graphMetaMap;
  const metricNames: MetricNameMap = Object.fromEntries(
    Object.keys(MetricType)
      .filter((key) => isNaN(Number(key)))
      .map((name) => [MetricType[name as keyof typeof MetricType], name]),
  );

  // Создаём рендерер
  const renderer = new GraphRenderer(
    {
      containerWidth: 1,
      containerHeight: 1,
      prefersDark,
    },
    metricColors,
    metricNames,
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

  // Реактивный рендеринг SVG
  let svgContent = $derived.by(() => {
    const graphVersion = getGraphVersion(); // для реактивности
    void graphVersion;

    // Обновляем размеры рендерера
    renderer.updateSize(containerWidth, containerHeight);

    // Получаем данные
    const processMinMax = graphStore.getProcessMinMax(pid);
    if (!processMinMax) {
      return /*svg*/ `<svg class="graph-plot"></svg>`;
    }
    const graphs = graphStore.getGraphs(pid);
    return renderer.renderToString(processMinMax, graphs);
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
</style>
