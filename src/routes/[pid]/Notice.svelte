<script lang="ts">
  import { onMount, tick } from "svelte";

  let { anchorEl }: { anchorEl: HTMLElement | null } = $props();

  let message = $state<string | null>(null);
  let runId = $state(0);
  let targetY = $state(0);

  let hideTimer: ReturnType<typeof setTimeout> | null = null;

  function updateTargetY() {
    if (!anchorEl) {
      targetY = 0;
      return;
    }
    const rect = anchorEl.getBoundingClientRect();
    // Popup останавливается сразу под заголовком.
    targetY = Math.max(0, Math.round(rect.bottom + 8));
  }

  $effect(() => {
    void anchorEl;
    updateTargetY();
  });

  onMount(() => {
    updateTargetY();
    if (typeof window === "undefined") return;
    const onResize = () => updateTargetY();
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  });

  export async function show(text: string) {
    message = text;
    runId += 1;
    await tick();
    updateTargetY();

    const runIdAtStart = runId;
    if (hideTimer) clearTimeout(hideTimer);
    hideTimer = setTimeout(() => {
      if (runId === runIdAtStart) message = null;
    }, 2000);
  }
</script>

{#if message !== null}
  {#key runId}
    <div
      class="notice-popup"
      style={`--notice-target-y: ${targetY}px;`}
      role="status"
      aria-live="polite"
    >
      {message}
    </div>
  {/key}
{/if}

<style>
  .notice-popup {
    position: fixed;
    left: 50%;
    top: 0;
    transform: translateX(-50%) translateY(calc(-100% - 12px));
    max-width: min(640px, calc(100vw - 32px));
    padding: 10px 14px;
    border-radius: 10px;
    background: rgba(30, 30, 30, 0.92);
    color: #fff;
    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.35);
    z-index: 9999;
    pointer-events: none;
    white-space: pre-wrap;
    animation: notice-popup-anim 2s ease forwards;
  }
  @keyframes notice-popup-anim {
    0% {
      transform: translateX(-50%) translateY(calc(-100% - 12px));
      opacity: 1;
    }
    25% {
      transform: translateX(-50%) translateY(var(--notice-target-y, 0px));
      opacity: 1;
    }
    75% {
      transform: translateX(-50%) translateY(var(--notice-target-y, 0px));
      opacity: 1;
    }
    100% {
      transform: translateX(-50%) translateY(var(--notice-target-y, 0px));
      opacity: 0;
    }
  }
</style>
