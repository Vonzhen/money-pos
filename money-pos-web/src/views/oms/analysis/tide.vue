<template>
  <PageWrapper>
    <div class="mb-5 flex flex-col md:flex-row justify-between items-start md:items-center gap-4 border-b pb-4">
      <h2 class="text-xl font-bold text-gray-800 flex items-center gap-2">
        <svg-icon name="data-line" class="w-6 h-6 text-indigo-500" />
        营业潮汐趋势分析 <span class="text-sm font-normal text-gray-500">(长效周期规律)</span>
      </h2>
    </div>

    <el-tabs v-model="activeTab" class="bg-white p-4 rounded-lg shadow-sm border-none" @tab-change="initChart" v-loading="loading">

      <el-tab-pane name="week">
        <template #label><span class="font-bold text-lg px-4">📅 每周规律 (1-7)</span></template>
        <div ref="weekChartRef" class="w-full h-[500px] mt-2"></div>
      </el-tab-pane>

      <el-tab-pane name="month">
        <template #label><span class="font-bold text-lg px-4">🗓️ 月内分布 (1-31)</span></template>
        <div ref="monthChartRef" class="w-full h-[500px] mt-2"></div>
      </el-tab-pane>

    </el-tabs>

    <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
      <div class="bg-indigo-50 border border-indigo-100 p-4 rounded-lg shadow-sm">
        <div class="text-indigo-700 font-bold mb-1.5 flex items-center gap-1"><el-icon><InfoFilled /></el-icon> 采样算法</div>
        <p class="text-xs text-indigo-600 leading-relaxed">系统基于后端 <b>动态智能推算</b> 的长效历史周期（约 {{ (currentSampleDays || 0).toFixed(1) }} 个循环单位），进行宏观聚合剖析。</p>
      </div>

      <div class="bg-orange-50 border border-orange-100 p-4 rounded-lg shadow-sm">
        <div class="text-orange-700 font-bold mb-1.5 flex items-center gap-1"><el-icon><Operation /></el-icon> 计算逻辑</div>
        <p class="text-xs text-orange-600 leading-relaxed">
          <b>平均值:</b> 剔除全额退款后，分配到单日的均摊人气与客单。<br>
          <b>总量值:</b> 该星期/日期在整个历史采样周期内的真实累加压舱石总额。
        </p>
      </div>

      <div class="bg-gray-50 border border-gray-200 p-4 rounded-lg shadow-sm">
        <div class="text-gray-700 font-bold mb-1.5 flex items-center gap-1"><el-icon><Warning /></el-icon> 阅图指南</div>
        <p class="text-xs text-gray-600 leading-relaxed">
          图表默认隐藏【总量】线，如需透视长期贡献度，可点击下方图例唤出。<br>
          <span class="text-orange-600 font-bold">月度日历效应：</span>可精准识别“发工资日”或“大促”的拉动力，但易受双休偏移扰动。
        </p>
      </div>
    </div>
  </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import { ref, onMounted, nextTick } from "vue";
import { InfoFilled, Operation, Warning } from '@element-plus/icons-vue';
import analysisApi from "@/api/oms/analysis.js";
import * as echarts from 'echarts';

const activeTab = ref("week");
const loading = ref(false);
const weekChartRef = ref(null);
const monthChartRef = ref(null);
let chartInstance = null;
const currentSampleDays = ref(0); // 🌟 用于保存后端下发的采样系数

const weekMap = { 1: '周日', 2: '周一', 3: '周二', 4: '周三', 5: '周四', 6: '周五', 7: '周六' };

const loadWeekData = async () => {
  const res = await analysisApi.getWeeklyTraffic();
  const data = res.data || res;
  const sorted = data.sort((a, b) => (a.timeKey===1?8:a.timeKey) - (b.timeKey===1?8:b.timeKey));

  if(sorted.length > 0) currentSampleDays.value = sorted[0].sampleDays || 0;

  const xData = sorted.map(item => weekMap[item.timeKey]);
  const avgOrders = sorted.map(item => item.avgOrderCount);
  const avgSales = sorted.map(item => item.avgSalesAmount);

  const totalOrders = sorted.map(item => item.totalOrderCount || 0);
  const totalSales = sorted.map(item => item.totalSalesAmount || 0);

  renderChart(weekChartRef.value, xData, avgOrders, avgSales, totalOrders, totalSales, '日均单量', '日均产出');
}

const loadMonthData = async () => {
  const res = await analysisApi.getMonthlyTraffic();
  const data = res.data || res;

  if(data.length > 0) currentSampleDays.value = data[0].sampleDays || 0;

  const xData = data.map(item => item.timeKey + '号');
  const avgOrders = data.map(item => item.avgOrderCount);
  const avgSales = data.map(item => item.avgSalesAmount);

  const totalOrders = data.map(item => item.totalOrderCount || 0);
  const totalSales = data.map(item => item.totalSalesAmount || 0);

  renderChart(monthChartRef.value, xData, avgOrders, avgSales, totalOrders, totalSales, '平均单量', '平均产出');
}

const renderChart = (dom, xData, orderData, salesData, tOrderData, tSalesData, y1Name, y2Name) => {
  if (!dom) return;
  if (chartInstance) { chartInstance.dispose(); }
  chartInstance = echarts.init(dom);

  const series = [
    { name: y1Name, type: 'bar', barWidth: '35%', itemStyle: { color: '#8b5cf6', borderRadius: [4, 4, 0, 0] }, data: orderData },
    { name: y2Name, type: 'line', yAxisIndex: 1, smooth: true, itemStyle: { color: '#ec4899' }, lineStyle: { width: 3 }, data: salesData }
  ];

  if (tOrderData) {
    series.push({ name: '周期累计总单量', type: 'line', smooth: true, lineStyle: { type: 'dashed', width: 1 }, itemStyle: { color: '#c4b5fd' }, data: tOrderData });
    series.push({ name: '周期累计总产出', type: 'line', yAxisIndex: 1, smooth: true, lineStyle: { type: 'dotted', width: 2 }, itemStyle: { color: '#f9a8d4' }, data: tSalesData });
  }

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params) => {
        let html = `<div class="font-bold border-b border-gray-200 pb-1 mb-1">${params[0].axisValue} 潮汐截面</div>`;
        params.forEach(p => {
          html += `<div class="flex justify-between gap-4 text-xs py-0.5">
            <span>${p.marker} ${p.seriesName}</span>
            <span class="font-mono font-bold">${p.value.toLocaleString()}</span>
          </div>`;
        });
        return html;
      }
    },
    // 🌟 核心改进：默认折叠总量线，点击唤出
    legend: {
      data: [y1Name, y2Name, '周期累计总单量', '周期累计总产出'],
      bottom: 0,
      selected: {
        '周期累计总单量': false,
        '周期累计总产出': false
      }
    },
    grid: { left: '3%', right: '4%', top: '10%', bottom: '10%', containLabel: true },
    xAxis: { type: 'category', data: xData, axisPointer: { type: 'shadow' } },
    yAxis: [
      { type: 'value', name: y1Name, min: 0, axisLabel: { formatter: '{value}' } },
      { type: 'value', name: y2Name, min: 0, splitLine: { show: false }, axisLabel: { formatter: '￥{value}' } }
    ],
    series: series
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