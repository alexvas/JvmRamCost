{#if !process}
  <h2>Process {pidStr} not found</h2>
{:else}
  <h2>{pidStr} {process.display_name}</h2>
  {#if hasGraph}
    <GraphPlot pid={pid!} />
  {/if}
  <button onclick={trigger_gc}>Trigger GC</button>
{/if}

<script lang="ts">
  import { page } from "$app/state";
  import { getContext } from "svelte";
  import type { ProcInfo } from "$lib/ProcHandle";
  import GraphPlot from "./GraphPlot.svelte";
  import { triggerGc } from "$lib/ProtoAdapter";
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
</script>

<style>
  button {
    margin-top: 10px;
  }
</style>
