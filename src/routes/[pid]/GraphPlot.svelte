<div class="graph-container" bind:this={containerElement}>
  {@html svgContent}
</div>

<script lang="ts">
  import { GraphRenderer, type MetricMetaMap } from "$lib/graph";
  import { graphStore } from "$lib/GraphStore";
  import { graphMetaMap } from "$lib/GraphMeta";
  import { Debouncer } from "$lib/Debouncer";
  import { getContext } from "svelte";
  import type { ProcInfo } from "$lib/ProcHandle";

  let { process }: { process: ProcInfo } = $props();
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
    const gcMarks = graphStore.getGcMarks(pid);
    return renderer.renderToString(processMinMax, graphs, gcMarks);
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
