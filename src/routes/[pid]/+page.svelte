{#if !process}
  <h2 bind:this={headerEl}>Process {pidStr} not found</h2>
{:else}
  <h2 bind:this={headerEl}>{pidStr} {process.display_name}</h2>
{/if}

<Notice bind:this={noticePopup} anchorEl={headerEl} />

{#if process}
  {#if process.parent}
    <p>Parent: {process.parent}</p>
  {/if}
  {#if process.children.length > 0}
    <ul>
      {#each process.children as childPid}
        <li><a href={`/${childPid}`}>{childPid}</a></li>
      {/each}
    </ul>
  {/if}
  {#if !process.active}
    <p>Process is not alive</p>
  {/if}
  {#if hasGraph}
    <GraphPlot
      {process}
      {notice}
      {hiddenMetrics}
      {viewportRange}
      {followDataUpdate}
      {absoluteDates}
    />
    <GraphNavigator pid={pid!} bind:viewportRange {hiddenMetrics} bind:followDataUpdate />
  {/if}
  <ControlToolbar
    {pid}
    {notice}
    bind:hiddenMetrics
    bind:followDataUpdate
    bind:absoluteDates
  />
{/if}

<script lang="ts">
  import { page } from "$app/state";
  import { getContext } from "svelte";
  import type { ProcInfo } from "$lib/ProcHandle";
  import GraphPlot from "./GraphPlot.svelte";
  import GraphNavigator from "./GraphNavigator.svelte";
  import ControlToolbar from "./ControlToolbar.svelte";
  import Notice from "./Notice.svelte";
  let pidStr = $derived(page.params.pid);
  let pid = $derived(pidStr ? Number(pidStr) : null);
  const getJvmProcesses =
    getContext<() => Map<number, ProcInfo>>("jvmProcesses")!;
  import { graphStore, type MetricType } from "$lib/GraphStore";
  let process = $derived.by(() =>
    pid ? getJvmProcesses().get(pid) : undefined,
  );
  // Процесс может быть среди отслеживаемых, но ещё не прислал данных.
  const hasGraph = $derived(
    pid ? graphStore.hasGraphDataForProcess(pid) : false,
  );
  let hiddenMetrics = $state<Set<MetricType>>(new Set());
  interface ViewportRange {
    min: number;
    max: number;
  }
  let viewportRange = $state<ViewportRange | null>(null);
  // Режим следования за обновлениями данных
  let followDataUpdate = $state(true);
  // Режим подписей по абсолютному времени (app_start + offset)
  let absoluteDates = $state(true);
  let headerEl = $state<HTMLHeadingElement | null>(null);
  type NoticeHandle = { show: (message: string) => void | Promise<void> };
  let noticePopup = $state<NoticeHandle | null>(null);

  function notice(message: string) {
    noticePopup?.show(message);
  }
</script>

<style></style>
