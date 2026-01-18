<div>
  <nav class="tabs-nav">
    <ul class="tabs-list">
      <li>
        <a href="/" class="tab-link" class:active={isActive("/")}>Settings</a>
      </li>
      {#each followingPids as pid}
        <li>
          <a
            href={`/${pid}`}
            class="tab-link"
            class:active={isActive(`/${pid}`)}>{pid}</a
          >
        </li>
      {/each}
    </ul>
  </nav>
  <main class="container">
    {@render children()}
  </main>
</div>

<script lang="ts">
  import { setContext } from "svelte";
  import { page } from "$app/state";
  import { listenGraphQueues, listenJvmProcessList } from "$lib/ProtoAdapter";
  import type { ProcInfo } from "$lib/ProcHandle";
  import { graphStore } from "$lib/GraphStore";

  let { children } = $props();
  let followingPids = $state<number[]>([]);
  let jvmProcesses = $state<Map<number, ProcInfo>>(new Map());

  setContext("followingPids", () => followingPids);
  setContext("jvmProcesses", () => jvmProcesses);

  let parents = new Map<number, number>();

  listenJvmProcessList((procInfoMap) => {
    let nextJvmProcesses = new Map<number, ProcInfo>();
    for (const [pid, procInfo] of jvmProcesses) {
      nextJvmProcesses.set(pid, {
        ...procInfo,
        active: procInfoMap.has(pid),
      });
    }
    for (const [pid, procInfo] of procInfoMap) {
      for (const childPid of procInfo.children) {
        parents.set(childPid, pid);
      }
      nextJvmProcesses.set(pid, {
        ...procInfo,
        active: true,
      });
    }
    for (const [pid, procInfo] of nextJvmProcesses) {
      procInfo.parent = parents.get(pid);
    }
    jvmProcesses = nextJvmProcesses;
  });

  let graphVersion = $state(0);
  setContext("graphVersion", () => graphVersion);

  listenGraphQueues((appStart, pid, metricType, zehntel, bytes) => {
    graphStore.setAppStart(appStart);
    graphStore.put(pid, metricType, zehntel, bytes);
    graphVersion++;
  });

  let prefersDark = $state(
    typeof window !== "undefined" &&
      window.matchMedia("(prefers-color-scheme: dark)").matches,
  );
  $effect(() => {
    if (typeof window === "undefined") return;
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    const updateTheme = (e: MediaQueryListEvent | MediaQueryList) => {
      prefersDark = e.matches;
    };
    updateTheme(mediaQuery);
    mediaQuery.addEventListener("change", updateTheme);
    return () => mediaQuery.removeEventListener("change", updateTheme);
  });

  setContext("prefersDark", () => prefersDark);

  function isActive(href: string): boolean {
    const currentPath = page.url.pathname;
    if (href === "/") {
      return currentPath === "/";
    }
    return currentPath === href;
  }
</script>

<style>
  div {
    height: 100vh;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    box-sizing: border-box;
  }

  .tabs-nav {
    background-color: #ffffff;
    border-bottom: 1px solid #d1d1d1;
    padding: 0;
    margin: 0;
    height: 44px;
    box-sizing: border-box;
    flex-shrink: 0;
  }

  .tabs-list {
    display: flex;
    flex-direction: row;
    list-style: none;
    margin: 0;
    padding: 0;
    gap: 0;
  }

  .tabs-list li {
    margin: 0;
    padding: 0;
  }

  .tab-link {
    display: block;
    padding: 12px 20px;
    text-decoration: none;
    color: #202020;
    border-bottom: 2px solid transparent;
    border-top-left-radius: 8px;
    border-top-right-radius: 8px;
    transition:
      background-color 0.15s ease,
      border-color 0.15s ease;
    font-size: 14px;
    line-height: 20px;
    cursor: pointer;
  }

  .tab-link:hover {
    background-color: #f3f3f3;
  }

  .tab-link:global(.active) {
    border-bottom-color: #0078d4;
    color: #0078d4;
    font-weight: 500;
  }

  @media (prefers-color-scheme: dark) {
    .tabs-nav {
      background-color: #2d2d2d;
      border-bottom-color: #3d3d3d;
    }

    .tab-link {
      color: #ffffff;
    }

    .tab-link:hover {
      background-color: #3d3d3d;
    }

    .tab-link:global(.active) {
      border-bottom-color: #60cdff;
      color: #60cdff;
    }
  }
</style>
