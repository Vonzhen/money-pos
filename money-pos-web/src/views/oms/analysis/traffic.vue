<template>
  <PageWrapper>
    <div class="mb-6 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
      <h2 class="text-xl font-bold text-gray-800 flex items-center gap-2">
        <svg-icon name="sun" class="w-6 h-6 text-orange-500" />
        时段交易客流分析 <span class="text-sm font-normal text-gray-500">(按订单笔数)</span>
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
      <el-tag type="success" effect="dark" size="large" v-if="safeHours.length > 0">
        💡 根据历史概率，以下时段比较空闲：{{ safeHours.join(', ') }}
      </el-tag>
      <el-tag type="danger" effect="dark" size="large" v-else>
        🔥 当前选中日期的全天单值较高，建议留守坐镇
      </el-tag>
    </div>

    <el-card shadow="hover" class="border-none shadow-sm rounded-lg" v-loading="loading">
      <div ref="trafficChartRef" class="w-full h-[500px]"></div>
    </el-card>

    <el-alert
      title="算法说明：系统将精准抽取【过去4周的该星期日】进行加权平均。绿色背景区域代表 [平均产出 < 50元 且 平均 < 1单] 的安全空闲窗口。📌 注意：客流代表【有效交易订单笔数】（已剔除全额退款），并不等同于自然进店人数。"
      type="info" show-icon class="mt-4" :closable="false" />
  </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import { ref, onMounted, onUnmounted, computed } from "vue";
import analysisApi from "@/api/oms/analysis.js";
import * as echarts from 'echarts';

const loading = ref(true);
const trafficChartRef = ref(null);
let chartInstance = null;

const trafficData = ref([]);
const selectedDay = ref("");

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
  const orderCounts = trafficData.value.map(item => item.avgOrderCount);
  const salesAmounts = trafficData.value.map(item => item.avgSalesAmount);

  const markAreaPieces = trafficData.value.filter(item => item.suggestion === 'OUT').map(item => {
    return [
      { xAxis: item.hour + '点' },
      { xAxis: (item.hour + 1) === 24 ? '0点' : (item.hour + 1) + '点' }
    ];
  });

  const option = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { data: ['平均单量 (笔)', '平均产出 (元)'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: hours, axisPointer: { type: 'shadow' } },
    yAxis: [
      { type: 'value', name: '平均单量', min: 0, axisLabel: { formatter: '{value} 笔' } },
      { type: 'value', name: '平均产出', min: 0, splitLine: { show: false }, axisLabel: { formatter: '￥{value}' } }
    ],
    series: [
      {
        name: '平均单量 (笔)', type: 'bar', barWidth: '40%',
        itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] },
        data: orderCounts,
        markArea: { itemStyle: { color: 'rgba(16, 185, 129, 0.15)' }, data: markAreaPieces }
      },
      {
        name: '平均产出 (元)', type: 'line', yAxisIndex: 1, smooth: true, symbolSize: 8,
        itemStyle: { color: '#f59e0b' }, lineStyle: { width: 3 }, data: salesAmounts
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