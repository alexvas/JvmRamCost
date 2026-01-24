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
    <GraphPlot {process} {notice} />
  {/if}
  <div class="controls-container">
    <div class="dump-controls">
      <input type="text" bind:value={comment} />
      <button onclick={dump_heap}>Dump Heap</button>
      <button onclick={dump_thread}>Dump Thread</button>
      <button onclick={saveGraphWithComment}>Save Graph</button>
    </div>
    <button onclick={trigger_gc} class="btn-gc">Trigger GC</button>
  </div>
{/if}

<script lang="ts">
  import { page } from "$app/state";
  import { getContext } from "svelte";
  import type { ProcInfo } from "$lib/ProcHandle";
  import GraphPlot from "./GraphPlot.svelte";
  import Notice from "./Notice.svelte";
  import { triggerGc, dumpHeap, dumpThread } from "$lib/ProtoAdapter";
  let pidStr = $derived(page.params.pid);
  let pid = $derived(pidStr ? Number(pidStr) : null);
  const getJvmProcesses =
    getContext<() => Map<number, ProcInfo>>("jvmProcesses")!;
  import { graphStore } from "$lib/GraphStore";
  let process = $derived.by(() =>
    pid ? getJvmProcesses().get(pid) : undefined,
  );
  // Процесс может быть среди отслеживаемых, но ещё не прислал данных.
  const hasGraph = $derived(
    pid ? graphStore.hasGraphDataForProcess(pid) : false,
  );
  function trigger_gc() {
    if (!pid) return;
    console.log("trigger_gc", pid);
    triggerGc(pid)
      .then(() => {
        graphStore.addActionMark(pid, "GC");
      })
      .catch((error) => {
        console.error("trigger_gc error", error);
      });
  }
  let comment = $state("");
  let headerEl = $state<HTMLHeadingElement | null>(null);
  type NoticeHandle = { show: (message: string) => void | Promise<void> };
  let noticePopup = $state<NoticeHandle | null>(null);

  function notice(message: string) {
    noticePopup?.show(message);
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

  import { GraphRenderer, renderGraphSvg } from "$lib/graph";
  import { graphMetaMap } from "$lib/GraphMeta";
  import { saveSvg } from "$lib/ProtoAdapter";

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

    const svg = renderGraphSvg(pid, exportRenderer, "standalone");
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
  button {
    margin-top: 10px;
  }
  .controls-container {
    /*раздвинуть на максимальную ширину*/
    width: 100%;
    display: flex;
    flex-direction: row;
    justify-content: space-between;
    margin-top: 10px;
  }
  .dump-controls {
    display: flex;
    flex-direction: column;
    align-items: start;
    gap: 10px;
    margin-top: 10px;
  }
</style>
