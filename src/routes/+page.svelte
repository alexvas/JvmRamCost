<h2>Setup</h2>
<div class="two-column-layout">
  <ProcessList />
  <MetricList />
</div>

<script lang="ts">
  import { MetricType } from "$lib/GraphStore";
  import MetricList from "$lib/MetricList.svelte";
  import ProcessList from "./ProcessList.svelte";
</script>
