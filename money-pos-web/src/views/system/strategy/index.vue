<template>
  <PageWrapper>
    <div class="mb-6">
      <h2 class="text-xl font-bold text-gray-800 flex items-center gap-2">
        <svg-icon name="setting" class="w-6 h-6 text-blue-500" />
        全局策略中心 (大脑控制台)
      </h2>
      <p class="text-gray-500 mt-2">在这里调整的所有参数，将立刻影响全系统的雷达算法与预警看板。</p>
    </div>

    <el-form :model="formData" label-width="200px" v-loading="loading">

      <el-card shadow="hover" class="mb-4 border-none shadow-sm bg-orange-50">
        <template #header>
          <span class="font-bold text-orange-600">🧭 办事罗盘 (客流价值) 判定参数</span>
        </template>
        <el-form-item label="极低客流 单量阈值 (笔)">
          <el-input-number v-model="formData.trafficOrderThreshold" :min="0" :step="0.5" />
          <span class="ml-4 text-gray-500 text-sm">低于此单量的时段，将被判定为闲时（出厂默认：1.0 笔）</span>
        </el-form-item>
        <el-form-item label="极低客流 产出阈值 (元)">
          <el-input-number v-model="formData.trafficValueThreshold" :min="0" :step="10" />
          <span class="ml-4 text-gray-500 text-sm">低于此营业额的时段，将被判定为闲时（出厂默认：50.0 元）</span>
        </el-form-item>
      </el-card>

      <el-card shadow="hover" class="mb-4 border-none shadow-sm bg-red-50">
        <template #header>
          <span class="font-bold text-red-600">🔔 周转预警 (库存智能) 判定参数</span>
        </template>
        <el-form-item label="进货提前期 Lead Time (天)">
          <el-input-number v-model="formData.turnoverLeadTime" :min="1" :max="30" />
          <span class="ml-4 text-gray-500 text-sm">从下单到货品送达门店所需的时间，决定断货预警红线（出厂默认：3 天）</span>
        </el-form-item>
        <el-form-item label="期望备货天数 (天)">
          <el-input-number v-model="formData.turnoverTargetDays" :min="1" :max="90" />
          <span class="ml-4 text-gray-500 text-sm">系统建议补货时，希望补足支撑多少天的库存（出厂默认：14 天）</span>
        </el-form-item>
        <el-form-item label="死库存判定阈值 (天)">
          <el-input-number v-model="formData.deadStockDays" :min="7" :max="365" />
          <span class="ml-4 text-gray-500 text-sm">超过多少天未售出的商品，将被打上【僵尸货】标签（出厂默认：60 天）</span>
        </el-form-item>
      </el-card>

      <el-card shadow="hover" class="mb-4 border-none shadow-sm bg-blue-50">
              <template #header>
                <span class="font-bold text-blue-600">📅 潮汐趋势分析 (休假罗盘) 采样参数</span>
              </template>
              <el-form-item label="按周分析 取样天数 (天)">
                <el-input-number v-model="formData.weeklyAnalysisDays" :min="28" :max="365" :step="7" />
                <span class="ml-4 text-gray-500 text-sm">建议 90 天。天数越长，排雷效应越明显，规律越平滑真实。</span>
              </el-form-item>
              <el-form-item label="按月分析 取样天数 (天)">
                <el-input-number v-model="formData.monthlyAnalysisDays" :min="60" :max="730" :step="30" />
                <span class="ml-4 text-gray-500 text-sm">建议 180 天。用半年的数据，精准算出每个月的哪几天生意最淡。</span>
              </el-form-item>
            </el-card>

      <div class="mt-6 flex justify-center">
        <el-button type="primary" size="large" @click="saveConfig" class="w-48 font-bold" icon="Check">保存并刷新全局策略</el-button>
      </div>
    </el-form>
  </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import { ref, onMounted } from "vue";
import { ElMessage } from 'element-plus';
import strategyApi from "@/api/system/strategy.js";

const loading = ref(true);
const formData = ref({
  trafficOrderThreshold: 1.0,
  trafficValueThreshold: 50.0,
  turnoverLeadTime: 3,
  turnoverTargetDays: 14,
  deadStockDays: 60
});

const loadData = async () => {
  loading.value = true;
  try {
    const res = await strategyApi.getStrategy();
    if (res.data || res) {
        formData.value = res.data || res;
    }
  } catch (e) {
    console.error("策略数据加载失败", e);
  } finally {
    loading.value = false;
  }
}

const saveConfig = async () => {
  loading.value = true;
  try {
    await strategyApi.saveStrategy(formData.value);
    ElMessage.success("全局经营策略已更新生效！");
    loadData();
  } catch (e) {
    ElMessage.error("保存失败，请检查网络！");
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadData();
});
</script>