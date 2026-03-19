<template>
  <PageWrapper>
    <div class="mb-6">
      <h2 class="text-2xl font-black text-gray-800 flex items-center gap-2">
        <svg-icon name="trophy" class="w-8 h-8 text-yellow-500" />
        核心大客户金榜 (Top 50 雷达)
      </h2>
      <p class="text-gray-500 mt-2 tracking-wide">
        盯紧这里的名字！他们是门店业绩的护城河，逢年过节的重点关怀对象。
      </p>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-3 gap-6" v-loading="loading">

      <el-card shadow="hover" class="border-none shadow-md rounded-xl overflow-hidden">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="font-black text-lg text-red-600 flex items-center gap-2">
              <el-icon class="text-2xl"><Money /></el-icon> 💰 累计消费榜
            </span>
            <el-tag type="danger" effect="dark" round>Top 50</el-tag>
          </div>
        </template>
        <el-table :data="consumeList" size="small" stripe height="600" class="w-full">
          <el-table-column type="index" label="排" width="45" align="center">
            <template #default="scope">
               <span :class="getRankClass(scope.$index)">{{ scope.$index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="客户姓名" show-overflow-tooltip>
            <template #default="{row}"><span class="font-bold text-gray-800">{{ row.name }}</span></template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" width="100">
            <template #default="{row}"><span class="font-mono text-xs text-gray-500">{{ maskPhone(row.phone) }}</span></template>
          </el-table-column>
          <el-table-column prop="amount" label="总消费" align="right">
            <template #default="{row}"><span class="font-black text-red-500">￥{{ row.amount?.toFixed(2) }}</span></template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="hover" class="border-none shadow-md rounded-xl overflow-hidden">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="font-black text-lg text-orange-500 flex items-center gap-2">
              <el-icon class="text-2xl"><Wallet /></el-icon> 💳 当前余额榜
            </span>
            <el-tag type="warning" effect="dark" round>Top 50</el-tag>
          </div>
        </template>
        <el-table :data="balanceList" size="small" stripe height="600" class="w-full">
          <el-table-column type="index" label="排" width="45" align="center">
            <template #default="scope">
               <span :class="getRankClass(scope.$index)">{{ scope.$index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="客户姓名" show-overflow-tooltip>
            <template #default="{row}"><span class="font-bold text-gray-800">{{ row.name }}</span></template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" width="100">
            <template #default="{row}"><span class="font-mono text-xs text-gray-500">{{ maskPhone(row.phone) }}</span></template>
          </el-table-column>
          <el-table-column prop="amount" label="账户余额" align="right">
            <template #default="{row}"><span class="font-black text-orange-500">￥{{ row.amount?.toFixed(2) }}</span></template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="hover" class="border-none shadow-md rounded-xl overflow-hidden">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="font-black text-lg text-blue-600 flex items-center gap-2">
              <el-icon class="text-2xl"><Coordinate /></el-icon> 🏃‍♂️ 消费频次榜
            </span>
            <el-tag type="primary" effect="dark" round>Top 50</el-tag>
          </div>
        </template>
        <el-table :data="frequencyList" size="small" stripe height="600" class="w-full">
          <el-table-column type="index" label="排" width="45" align="center">
            <template #default="scope">
               <span :class="getRankClass(scope.$index)">{{ scope.$index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="客户姓名" show-overflow-tooltip>
            <template #default="{row}"><span class="font-bold text-gray-800">{{ row.name }}</span></template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" width="100">
            <template #default="{row}"><span class="font-mono text-xs text-gray-500">{{ maskPhone(row.phone) }}</span></template>
          </el-table-column>
          <el-table-column prop="times" label="到店次数" align="center">
            <template #default="{row}"><span class="font-black text-blue-600">{{ row.times }} 次</span></template>
          </el-table-column>
        </el-table>
      </el-card>

    </div>
  </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import { ref, onMounted } from "vue";
import { Money, Wallet, Coordinate } from "@element-plus/icons-vue";
import rankApi from "@/api/ums/rank.js";
import { ElMessage } from 'element-plus';

const loading = ref(true);
const consumeList = ref([]);
const balanceList = ref([]);
const frequencyList = ref([]);

// 手机号脱敏打码 (保护客户隐私)
const maskPhone = (phone) => {
    if (!phone || phone.length !== 11) return phone || '-';
    return phone.substring(0, 3) + '****' + phone.substring(7);
};

// 前三名专属高亮特效
const getRankClass = (index) => {
    if (index === 0) return 'text-red-500 font-black text-lg'; // 🥇 冠军
    if (index === 1) return 'text-orange-500 font-bold text-base'; // 🥈 亚军
    if (index === 2) return 'text-yellow-500 font-bold text-base'; // 🥉 季军
    return 'text-gray-400 font-bold'; // 其他
};

const loadAllRanks = async () => {
    loading.value = true;
    try {
        const [consumeRes, balanceRes, freqRes] = await Promise.all([
            rankApi.getTopConsume(),
            rankApi.getTopBalance(),
            rankApi.getTopFrequency()
        ]);
        consumeList.value = consumeRes.data || consumeRes || [];
        balanceList.value = balanceRes.data || balanceRes || [];
        frequencyList.value = freqRes.data || freqRes || [];
    } catch (e) {
        ElMessage.error("榜单数据加载失败，请检查网络");
    } finally {
        loading.value = false;
    }
}

onMounted(() => {
    loadAllRanks();
});
</script>

<style scoped>
/* 隐藏表格滚动条，让大屏更清爽 */
:deep(.el-table__body-wrapper::-webkit-scrollbar) {
    width: 6px;
}
:deep(.el-table__body-wrapper::-webkit-scrollbar-thumb) {
    background-color: #e5e7eb;
    border-radius: 4px;
}
</style>