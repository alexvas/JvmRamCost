<div class="graph-container" bind:this={containerElement}>
  {@html svgContent}
</div>

<script lang="ts">
  import { GraphRenderer, type MetricMetaMap, renderGraphSvgOrEmpty, EMPTY_SVG } from "$lib/graph";
  import { graphMetaMap } from "$lib/GraphMeta";
  import { Debouncer } from "$lib/Debouncer";
  import { getContext } from "svelte";
  import type { ProcInfo } from "$lib/ProcHandle";
  import type { MetricType } from "$lib/GraphStore";

  let {
    process,
    notice,
    hiddenMetrics,
  }: {
    process: ProcInfo;
    notice: (message: string) => void;
    hiddenMetrics?: Set<MetricType>;
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

  // Реактивный рендеринг SVG
  let svgContent = $derived.by(() => {
    const graphVersion = getGraphVersion(); // для реактивности
    void graphVersion;

    // Ждём реальных размеров контейнера
    if (containerWidth <= 1 || containerHeight <= 1) {
      return EMPTY_SVG;
    }

    // Обновляем размеры рендерера и рендерим
    renderer.updateSize(containerWidth, containerHeight);
    const content = renderGraphSvgOrEmpty(pid, renderer, 'embedded', hiddenMetrics);

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
