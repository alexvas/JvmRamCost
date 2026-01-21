<div class="column card">
  <h3 class="card-title">Metrics</h3>
  <div class="checkbox-group">
    {#each allMetricTypes as mt}
      <label
        class="checkbox-label"
        style="background-color: {backgroundColors.get(mt)}"
      >
        <input
          type="checkbox"
          name="metric-types"
          value={mt}
          checked={visibleMetrics.includes(mt)}
          onchange={onCheckboxChange}
          class="fluent-checkbox"
        />
        <span class="checkbox-text">{MetricType[mt]}</span>
      </label>
    {/each}
  </div>
</div>

<script lang="ts">
  import { MetricType } from "$lib/GraphStore";
  import {
    setVisible,
    setInvisible,
    getApplicableMetrics,
  } from "$lib/ProtoAdapter";
  import { graphMetaMap } from "$lib/GraphMeta";
  import { getContext, onMount } from "svelte";
  import {
    allMetricTypes as allMetricTypesStore,
    visibleMetrics as visibleMetricsStore,
  } from "$lib/metricStore";

  let allMetricTypes = $state<MetricType[]>([]);
  let visibleMetrics = $state<MetricType[]>([]);

  allMetricTypesStore.subscribe((v) => (allMetricTypes = v));
  visibleMetricsStore.subscribe((v) => (visibleMetrics = v));

  onMount(() => {
    getApplicableMetrics().then((types) => {
      allMetricTypesStore.set(types);
      // Инициализируем visibleMetrics только если он пуст
      visibleMetricsStore.update((current) =>
        current.length === 0 ? types : current,
      );
    });
  });

  let oldVisibleMetrics: MetricType[] | undefined = undefined;
  $effect(() => {
    const newVal = visibleMetrics;
    if (oldVisibleMetrics !== undefined) {
      const added = (newVal?.filter(
        (m: MetricType) => !oldVisibleMetrics?.includes(m),
      ) ?? []) as MetricType[];
      const removed = (oldVisibleMetrics?.filter(
        (m: MetricType) => !newVal?.includes(m),
      ) ?? []) as MetricType[];
      added.forEach((mt: MetricType) => setVisible(mt));
      removed.forEach((mt: MetricType) => setInvisible(mt));
    }
    oldVisibleMetrics = [...newVal];
  });

  function onCheckboxChange(event: Event) {
    const target = event.target as HTMLInputElement;
    const value = parseInt(target.value) as MetricType;
    if (target.checked) {
      visibleMetricsStore.update((arr) => [...arr, value]);
    } else {
      visibleMetricsStore.update((arr) => arr.filter((m) => m !== value));
    }
  }

  let prefersDark = getContext<() => boolean>("prefersDark")!();

  const backgroundColors = new Map<MetricType, string>();
  Object.values(MetricType)
    .filter((v): v is MetricType => typeof v === "number")
    .forEach((metricType: MetricType) => {
      const meta = graphMetaMap[metricType];
      const color = prefersDark ? meta.color_dark : meta.color_light;
      backgroundColors.set(metricType, color);
    });
</script>

<style>
  .checkbox-group {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .checkbox-label {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    padding: 4px 8px;
    border-radius: 4px;
    user-select: none;
  }

  .fluent-checkbox {
    width: 18px;
    height: 18px;
    margin: 0;
    cursor: pointer;
    accent-color: #0078d4;
    flex-shrink: 0;
  }

  .checkbox-text {
    font-size: 14px;
    line-height: 20px;
    color: #202020;
  }

  @media (prefers-color-scheme: dark) {
    .checkbox-text {
      color: #ffffff;
    }
  }
</style>
