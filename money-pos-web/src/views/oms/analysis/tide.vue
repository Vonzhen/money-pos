<template>
  <PageWrapper>
    <div class="mb-6 flex justify-between items-center">
      <h2 class="text-xl font-bold text-gray-800 flex items-center gap-2">
        <svg-icon name="data-line" class="w-6 h-6 text-indigo-500" />
        营业潮汐趋势分析
      </h2>
      <div class="text-sm text-gray-500">
        * 采样参数由【全局策略中心】大脑统一控制
      </div>
    </div>

    <el-tabs v-model="activeTab" class="bg-white p-4 rounded-lg shadow-sm" @tab-change="initChart" v-loading="loading">

      <el-tab-pane name="week">
        <template #label><span class="font-bold text-lg px-4">📅 每周规律趋势分析</span></template>
        <div ref="weekChartRef" class="w-full h-[500px] mt-4"></div>
      </el-tab-pane>

      <el-tab-pane name="month">
        <template #label><span class="font-bold text-lg px-4">🗓️ 月度潮汐趋势分析</span></template>
        <div ref="monthChartRef" class="w-full h-[500px] mt-4"></div>
      </el-tab-pane>

    </el-tabs>
  </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import { ref, onMounted, nextTick } from "vue";
import analysisApi from "@/api/oms/analysis.js";
import * as echarts from 'echarts';

const activeTab = ref("week");
const loading = ref(false);
const weekChartRef = ref(null);
const monthChartRef = ref(null);
let chartInstance = null;

// MySQL DAYOFWEEK: 1=周日, 2=周一...7=周六
const weekMap = { 1: '周日', 2: '周一', 3: '周二', 4: '周三', 5: '周四', 6: '周五', 7: '周六' };

const loadWeekData = async () => {
  const res = await analysisApi.getWeeklyTraffic();
  const data = res.data || res;
  // 把周日排到最后，更符合国人习惯
  const sorted = data.sort((a, b) => (a.timeKey===1?8:a.timeKey) - (b.timeKey===1?8:b.timeKey));

  const xData = sorted.map(item => weekMap[item.timeKey]);
  const orderData = sorted.map(item => item.avgOrderCount);
  const salesData = sorted.map(item => item.avgSalesAmount);
  renderChart(weekChartRef.value, xData, orderData, salesData, '平均日均单量', '平均日均产出');
}

const loadMonthData = async () => {
  const res = await analysisApi.getMonthlyTraffic();
  const data = res.data || res;
  const xData = data.map(item => item.timeKey + '号');
  const orderData = data.map(item => item.avgOrderCount);
  const salesData = data.map(item => item.avgSalesAmount);
  renderChart(monthChartRef.value, xData, orderData, salesData, '平均单量', '平均产出');
}

const renderChart = (dom, xData, orderData, salesData, y1Name, y2Name) => {
  if (!dom) return;
  if (chartInstance) { chartInstance.dispose(); }
  chartInstance = echarts.init(dom);

  const option = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { data: [y1Name, y2Name] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: xData, axisPointer: { type: 'shadow' } },
    yAxis: [
      { type: 'value', name: y1Name, min: 0 },
      { type: 'value', name: y2Name, min: 0, splitLine: { show: false } }
    ],
    series: [
      { name: y1Name, type: 'bar', barWidth: '40%', itemStyle: { color: '#8b5cf6', borderRadius: [4, 4, 0, 0] }, data: orderData },
      { name: y2Name, type: 'line', yAxisIndex: 1, smooth: true, itemStyle: { color: '#ec4899' }, lineStyle: { width: 3 }, data: salesData }
    ]
  };
  chartInstance.setOption(option);
}

const initChart = async () => {
  loading.value = true;
  await nextTick();
  try {
    if (activeTab.value === 'week') await loadWeekData();
    if (activeTab.value === 'month') await loadMonthData();
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  initChart();
  window.addEventListener('resize', () => chartInstance?.resize());
});
</script>