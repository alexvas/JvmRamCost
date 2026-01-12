<div class="column card">
  <h3 class="card-title">Processes</h3>
  <div class="process-list">
    {#each Array.from(availableJvmProcesses.entries()) as [pid, process]}
      <label class="process-item" class:selected={followingPids.includes(pid)}>
        <input
          type="checkbox"
          checked={followingPids.includes(pid)}
          onchange={(e) => {
            const pids = getFollowingPids();
            if (e.currentTarget.checked) {
              pids.push(pid);
            } else {
              const index = pids.indexOf(pid);
              if (index > -1) {
                pids.splice(index, 1);
              }
            }
          }}
          class="process-checkbox"
        />
        <span class="process-text">{pid} - {process.display_name}</span>
      </label>
    {/each}
  </div>
</div>

<script lang="ts">
  import { getContext } from "svelte";

  const getAvailableJvmProcesses = getContext<() => Map<number, ProcInfo>>(
    "availableJvmProcesses",
  )!;
  let availableJvmProcesses = $derived(getAvailableJvmProcesses());
  const getFollowingPids = getContext<() => number[]>("followingPids")!;
  let followingPids = $derived(getFollowingPids());

  import { invoke } from "@tauri-apps/api/core";
  import type { ProcInfo } from "$lib/ProcHandle";

  async function followPids(pids: number[]) {
    const request = {
      pids: pids.map((pid) => ({ pid: Number(pid) })),
    };
    invoke("set_following_pids", { request });
  }

  $effect(() => {
    followPids(followingPids);
  });
</script>

<style>
  .process-list {
    width: 100%;
    min-height: 200px;
    max-height: 400px;
    overflow-y: auto;
    border: 1px solid #d1d1d1;
    border-radius: 4px;
    background-color: #ffffff;
    padding: 4px;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .process-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    border-radius: 4px;
    cursor: pointer;
    user-select: none;
    transition: background-color 0.15s ease;
    background-color: transparent;
  }

  .process-item:hover {
    background-color: #f3f3f3;
  }

  .process-item.selected {
    background-color: #0078d4;
  }

  .process-item.selected:hover {
    background-color: #106ebe;
  }

  .process-checkbox {
    width: 18px;
    height: 18px;
    margin: 0;
    cursor: pointer;
    accent-color: #0078d4;
    flex-shrink: 0;
  }

  .process-text {
    font-size: 14px;
    line-height: 20px;
    color: #202020;
    flex: 1;
  }

  .process-item.selected .process-text {
    color: #ffffff;
  }

  @media (prefers-color-scheme: dark) {
    .process-list {
      background-color: #1e1e1e;
      border-color: #3d3d3d;
    }

    .process-item:hover {
      background-color: #3d3d3d;
    }

    .process-item.selected {
      background-color: #60cdff;
    }

    .process-item.selected:hover {
      background-color: #40b8ff;
    }

    .process-text {
      color: #ffffff;
    }

    .process-item.selected .process-text {
      color: #000000;
    }
  }
</style>
