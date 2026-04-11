<template>
  <PageWrapper>
    <div class="mb-5 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
      <h2 class="text-xl font-bold text-gray-800 flex items-center gap-2">
        <svg-icon name="sun" class="w-6 h-6 text-orange-500" />
        时段交易客流分析 <span class="text-sm font-normal text-gray-500">(决策罗盘)</span>
      </h2>

      <el-radio-group v-model="selectedDay" @change="loadData" size="large">
        <el-radio-button label="">28天全盘平均</el-radio-button>
        <el-radio-button :label="1">周一</el-radio-button>
        <el-radio-button :label="2">周二</el-radio-button>
        <el-radio-button :label="3">周三</el-radio-button>
        <el-radio-button :label="4">周四</el-radio-button>
        <el-radio-button :label="5">周五</el-radio-button>
        <el-radio-button :label="6">周六</el-radio-button>
        <el-radio-button :label="7">周日</el-radio-button>
      </el-radio-group>
    </div>

    <div class="mb-4">
      <el-tag type="success" effect="dark" size="large" v-if="safeHours.length > 0" class="w-full justify-center text-sm py-4 shadow-sm">
        <span class="flex items-center gap-2"><el-icon class="text-lg"><MagicStick /></el-icon> 建议空闲窗口：{{ safeHours.join(', ') }}</span>
      </el-tag>
      <el-tag type="danger" effect="dark" size="large" v-else class="w-full justify-center text-sm py-4 shadow-sm">
        <span class="flex items-center gap-2"><el-icon class="text-lg"><WarningFilled /></el-icon> 建议全天留守：此日无显著空闲时段</span>
      </el-tag>
    </div>

    <el-card shadow="hover" class="border-none shadow-sm rounded-lg" v-loading="loading">
      <div ref="trafficChartRef" class="w-full h-[500px]"></div>
    </el-card>

    <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
      <div class="bg-blue-50 border border-blue-100 p-4 rounded-lg shadow-sm">
        <div class="text-blue-700 font-bold mb-1.5 flex items-center gap-1"><el-icon><InfoFilled /></el-icon> 采样算法</div>
        <p class="text-xs text-blue-600 leading-relaxed">系统基于过去 <b>{{ currentSampleDays }}</b> 个真实采样日的历史流水，进行小时级对齐剖析。</p>
      </div>

      <div class="bg-orange-50 border border-orange-100 p-4 rounded-lg shadow-sm">
        <div class="text-orange-700 font-bold mb-1.5 flex items-center gap-1"><el-icon><Operation /></el-icon> 计算逻辑</div>
        <p class="text-xs text-orange-600 leading-relaxed">
          <b>平均值:</b> 总量 ÷ 采样天数。代表该时段的日常人气。<br>
          <b>总量值:</b> 采样期内真实累计。代表该时段长期的吸金厚度。
        </p>
      </div>

      <div class="bg-gray-50 border border-gray-200 p-4 rounded-lg shadow-sm">
        <div class="text-gray-700 font-bold mb-1.5 flex items-center gap-1"><el-icon><Warning /></el-icon> 阅图指南</div>
        <p class="text-xs text-gray-600 leading-relaxed">
          图表默认隐藏【总计】线，如需观察长效贡献度，可点击下方图例唤出。<br>
          <span class="bg-green-100 text-green-700 px-1 rounded">绿色背景区域</span> 代表 [平均产出 &lt; 50元 且 平均单量 &lt; 1单] 的绝对安全空闲窗。
        </p>
      </div>
    </div>
  </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import { ref, onMounted, onUnmounted, computed } from "vue";
import { InfoFilled, Operation, Warning, MagicStick, WarningFilled } from '@element-plus/icons-vue';
import analysisApi from "@/api/oms/analysis.js";
import * as echarts from 'echarts';

const loading = ref(true);
const trafficChartRef = ref(null);
let chartInstance = null;

const trafficData = ref([]);
const selectedDay = ref("");

const currentSampleDays = computed(() => {
  return trafficData.value.length > 0 ? (trafficData.value[0].sampleDays || 28) : 28;
});

const safeHours = computed(() => {
  return trafficData.value.filter(item => item.suggestion === 'OUT').map(item => item.hour + "点");
});

const loadData = async () => {
  loading.value = true;
  try {
    const res = await analysisApi.getTrafficAnalysis(selectedDay.value || null);
    trafficData.value = res.data || res || [];
    drawChart();
  } catch (e) {
    console.error("罗盘数据加载失败", e);
  } finally {
    loading.value = false;
  }
}

const drawChart = () => {
  if (!trafficChartRef.value) return;
  if (!chartInstance) {
    chartInstance = echarts.init(trafficChartRef.value);
  }

  const hours = trafficData.value.map(item => item.hour + '点');
  const avgOrderCounts = trafficData.value.map(item => item.avgOrderCount);
  const avgSalesAmounts = trafficData.value.map(item => item.avgSalesAmount);
  const totalOrderCounts = trafficData.value.map(item => item.totalOrderCount || 0);
  const totalSalesAmounts = trafficData.value.map(item => item.totalSalesAmount || 0);

  const markAreaPieces = trafficData.value.filter(item => item.suggestion === 'OUT').map(item => {
    return [
      { xAxis: item.hour + '点' },
      { xAxis: (item.hour + 1) === 24 ? '0点' : (item.hour + 1) + '点' }
    ];
  });

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params) => {
        let html = `<div class="font-bold border-b border-gray-200 pb-1 mb-1">${params[0].axisValue} 深度画像</div>`;
        params.forEach(p => {
          html += `<div class="flex justify-between gap-4 text-xs py-0.5">
            <span>${p.marker} ${p.seriesName}</span>
            <span class="font-mono font-bold">${p.value.toLocaleString()}</span>
          </div>`;
        });
        return html;
      }
    },
    // 🌟 核心改进：配置 selected 属性，默认关闭“周期累计总单量”和“周期累计总产出”
    legend: {
      data: ['平均单量', '平均产出', '周期累计总单量', '周期累计总产出'],
      bottom: 0,
      selected: {
        '周期累计总单量': false,
        '周期累计总产出': false
      }
    },
    grid: { left: '3%', right: '4%', top: '10%', bottom: '10%', containLabel: true },
    xAxis: { type: 'category', data: hours, axisPointer: { type: 'shadow' } },
    yAxis: [
      { type: 'value', name: '单量 (笔)', min: 0, axisLabel: { formatter: '{value}' } },
      { type: 'value', name: '金额 (元)', min: 0, splitLine: { show: false }, axisLabel: { formatter: '￥{value}' } }
    ],
    series: [
      {
        name: '平均单量', type: 'bar', barWidth: '30%',
        itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] },
        data: avgOrderCounts,
        markArea: { itemStyle: { color: 'rgba(16, 185, 129, 0.12)' }, data: markAreaPieces }
      },
      {
        name: '周期累计总单量', type: 'line', smooth: true,
        lineStyle: { type: 'dashed', width: 1 }, itemStyle: { color: '#93c5fd' },
        data: totalOrderCounts
      },
      {
        name: '平均产出', type: 'line', yAxisIndex: 1, smooth: true, symbolSize: 8,
        itemStyle: { color: '#f59e0b' }, lineStyle: { width: 3 }, data: avgSalesAmounts
      },
      {
        name: '周期累计总产出', type: 'line', yAxisIndex: 1, smooth: true,
        lineStyle: { type: 'dotted', width: 2 }, itemStyle: { color: '#fbbf24' },
        data: totalSalesAmounts
      }
    ]
  };

  chartInstance.setOption(option, true);
}

onMounted(() => {
  loadData();
  window.addEventListener('resize', () => chartInstance?.resize());
});

onUnmounted(() => {
  window.removeEventListener('resize', () => chartInstance?.resize());
  chartInstance?.dispose();
});
</script>