{#if !process}
  <h2>Process {pidStr} not found</h2>
{:else}
  <h2>{pidStr} {process.display_name}</h2>
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
    <GraphPlot {process} {comment} {notice} />
  {/if}
  <p class="notice-container">{noticeMessage}</p>
  <div class="controls-container">
    <div class="dump-controls">
      <input type="text" bind:value={comment} />
      <button onclick={dump_heap}>Dump Heap</button>
      <button onclick={dump_thread}>Dump Thread</button>
    </div>
    <button onclick={trigger_gc} class="btn-gc">Trigger GC</button>
  </div>
{/if}

<script lang="ts">
  import { page } from "$app/state";
  import { getContext } from "svelte";
  import type { ProcInfo } from "$lib/ProcHandle";
  import GraphPlot from "./GraphPlot.svelte";
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
        graphStore.addGcMark(pid);
      })
      .catch((error) => {
        console.error("trigger_gc error", error);
      });
  }
  let comment = $state("");
  let noticeMessage = $state(" ");

  function notice(message: string) {
    noticeMessage = message;
    setTimeout(() => {
      noticeMessage = " ";
    }, 5000);
  }

  function dump_heap() {
    if (!pid) return;
    dumpHeap(pid, comment).then((filename) => {
      notice(`Heap dump saved to ${filename}`);
    });
  }
  function dump_thread() {
    if (!pid) return;
    dumpThread(pid, comment).then((filename) => {
      notice(`Thread dump saved to ${filename}`);
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
