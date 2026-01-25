<div class="controls-toolbar">
  <div class="save-group">
    <input
      type="text"
      class="comment-input"
      bind:value={comment}
      placeholder="Comment..."
      title="Комментарий добавляется в имя файла"
    />
    <button class="btn" onclick={dump_heap}>Dump Heap</button>
    <button class="btn" onclick={dump_thread}>Dump Thread</button>
    <button
      class="btn btn-icon"
      onclick={saveGraphWithComment}
      title="Save Graph"
    >
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
        <path
          d="M5.5 3L4.85 4.29A1 1 0 0 1 3.94 5H2a1 1 0 0 0-1 1v7a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1h-1.94a1 1 0 0 1-.91-.71L10.5 3h-5zM8 12a3 3 0 1 1 0-6 3 3 0 0 1 0 6zm0-1a2 2 0 1 0 0-4 2 2 0 0 0 0 4z"
        ></path>
      </svg>
    </button>
  </div>

  <button class="btn" onclick={trigger_gc}>Trigger GC</button>

  {#if availableMetrics.length > 0}
    <details class="metrics-filter">
      <summary>Metrics</summary>
      <div class="metrics-list">
        {#each availableMetrics as metricType}
          <label
            class="metric-item"
            style="color: {prefersDark
              ? graphMetaMap[metricType].color_dark
              : graphMetaMap[metricType].color_light}"
          >
            <input
              type="checkbox"
              checked={!hiddenMetrics.has(metricType)}
              onchange={() => toggleMetric(metricType)}
            />
            {graphMetaMap[metricType].title}
          </label>
        {/each}
      </div>
    </details>

    <label
      class="follow-checkbox"
      title="Абсолютные метки времени по стартовому моменту"
    >
      <input
        type="checkbox"
        checked={absoluteDates}
        onchange={(e) => {
          const target = e.target as HTMLInputElement;
          absoluteDates = target.checked;
        }}
      />
      Absolute Dates
    </label>

    <label class="follow-checkbox" title="Следовать за обновлением данных">
      <input
        type="checkbox"
        checked={followDataUpdate}
        onchange={(e) => {
          const target = e.target as HTMLInputElement;
          followDataUpdate = target.checked;
        }}
      />
      Follow data update
    </label>
  {/if}
</div>

<script lang="ts">
  import { getContext } from "svelte";
  import { graphStore, type MetricType } from "$lib/GraphStore";
  import { graphMetaMap } from "$lib/GraphMeta";
  import { triggerGc, dumpHeap, dumpThread, saveSvg } from "$lib/ProtoAdapter";
  import { GraphRenderer, renderGraphSvg } from "$lib/graph";

  type Props = {
    pid: number | null;
    notice: (message: string) => void;
    hiddenMetrics: Set<MetricType>;
    followDataUpdate: boolean;
    absoluteDates: boolean;
  };

  // Внешнее состояние: чтобы родитель мог передать фильтр в GraphPlot/экспорт
  let {
    pid,
    notice,
    hiddenMetrics = $bindable(),
    followDataUpdate = $bindable(),
    absoluteDates = $bindable(true),
  }: Props = $props();

  // Comment входит в название создаваемого файла
  let comment = $state("");

  let prefersDark = getContext<() => boolean>("prefersDark")!();

  // availableMetrics: только те, для которых есть данные у процесса
  const getGraphVersion = getContext<() => number>("graphVersion")!;
  let availableMetrics = $derived.by(() => {
    void getGraphVersion(); // для реактивности
    if (!pid) return [];
    const mts = graphStore.getGraphs(pid).map((g) => g.metricType);
    return mts.sort((a, b) => a - b);
  });

  function toggleMetric(metricType: MetricType) {
    const next = new Set(hiddenMetrics);
    if (next.has(metricType)) {
      next.delete(metricType);
    } else {
      next.add(metricType);
    }
    hiddenMetrics = next;
  }

  function trigger_gc() {
    if (!pid) return;
    triggerGc(pid)
      .then(() => {
        graphStore.addActionMark(pid, "GC");
      })
      .catch((error) => {
        console.error("trigger_gc error", error);
      });
  }

  function dump_heap() {
    if (!pid) return;
    dumpHeap(pid, comment).then((filename) => {
      graphStore.addActionMark(pid, filename);
      notice(`Heap dump saved to ${filename}`);
    });
  }

  function dump_thread() {
    if (!pid) return;
    dumpThread(pid, comment).then((filename) => {
      graphStore.addActionMark(pid, filename);
      notice(`Thread dump saved to ${filename}`);
    });
  }

  // Фиксированные размеры для экспорта
  const EXPORT_WIDTH = 1920;
  const EXPORT_HEIGHT = 1080;

  function saveGraphWithComment() {
    if (!pid) return;

    const exportRenderer = new GraphRenderer(
      {
        containerWidth: EXPORT_WIDTH,
        containerHeight: EXPORT_HEIGHT,
        prefersDark: false,
      },
      graphMetaMap,
    );

    const appStartInstant = graphStore.getAppStartInstant();
    const svg = renderGraphSvg(
      pid,
      exportRenderer,
      "standalone",
      {
        absoluteDates,
        appStartInstant,
      },
      hiddenMetrics,
    );
    if (!svg) {
      notice("Нет данных для экспорта");
      return;
    }

    saveSvg(pid, false, comment, svg).then((filename) => {
      notice(`Graph saved to ${filename}`);
    });
  }
</script>

<style>
  .controls-toolbar {
    width: 100%;
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 12px;
    margin-top: 10px;
  }

  .save-group {
    display: flex;
    gap: 0;
  }

  .save-group .comment-input {
    width: 180px;
    padding: 6px 10px;
    border: 1px solid #d1d1d1;
    border-radius: 0;
    border-top-left-radius: 4px;
    border-bottom-left-radius: 4px;
    background-color: #ffffff;
    color: #202020;
    font-size: 14px;
    outline: none;
    transition:
      border-color 0.15s ease,
      box-shadow 0.15s ease;
  }

  .save-group .comment-input:focus {
    border-color: #0078d4;
    box-shadow: 0 0 0 1px #0078d4;
    z-index: 1;
  }

  .btn {
    padding: 6px 12px;
    border: 1px solid #d1d1d1;
    border-radius: 4px;
    background-color: #ffffff;
    color: #202020;
    font-size: 14px;
    cursor: pointer;
    transition:
      background-color 0.15s ease,
      border-color 0.15s ease;
  }

  .btn:hover {
    background-color: #f5f5f5;
  }

  .btn:active {
    background-color: #e0e0e0;
  }

  .save-group .btn {
    border-radius: 0;
    border-left: none;
  }

  .save-group .btn:last-child {
    border-top-right-radius: 4px;
    border-bottom-right-radius: 4px;
  }

  .btn-icon {
    padding: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
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

  @media (prefers-color-scheme: dark) {
    .follow-checkbox {
      color: #ccc;
    }
  }

  .metrics-filter {
    position: relative;
  }

  .metrics-filter summary {
    padding: 6px 12px;
    border: 1px solid #d1d1d1;
    border-radius: 4px;
    background-color: #ffffff;
    color: #202020;
    font-size: 14px;
    cursor: pointer;
    list-style: none;
  }

  .metrics-filter summary::-webkit-details-marker {
    display: none;
  }

  .metrics-filter summary::before {
    content: "▸ ";
  }

  .metrics-filter[open] summary::before {
    content: "▾ ";
  }

  .metrics-list {
    position: absolute;
    bottom: 100%;
    left: 0;
    margin-bottom: 4px;
    padding: 8px;
    background-color: #ffffff;
    border: 1px solid #d1d1d1;
    border-radius: 4px;
    box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.15);
    z-index: 100;
    min-width: 200px;
    max-height: 60vh;
    overflow-y: auto;
  }

  .metric-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 0;
    cursor: pointer;
    font-size: 14px;
    white-space: nowrap;
  }

  .metric-item input[type="checkbox"] {
    margin: 0;
    cursor: pointer;
  }

  @media (prefers-color-scheme: dark) {
    .save-group .comment-input {
      background-color: #3d3d3d;
      border-color: #5a5a5a;
      color: #ffffff;
    }

    .save-group .comment-input:focus {
      border-color: #60cdff;
      box-shadow: 0 0 0 1px #60cdff;
    }

    .btn {
      background-color: #3d3d3d;
      border-color: #5a5a5a;
      color: #ffffff;
    }

    .btn:hover {
      background-color: #4a4a4a;
    }

    .btn:active {
      background-color: #555555;
    }

    .metrics-filter summary {
      background-color: #3d3d3d;
      border-color: #5a5a5a;
      color: #ffffff;
    }

    .metrics-list {
      background-color: #2d2d2d;
      border-color: #5a5a5a;
      box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.4);
    }
  }
</style>
