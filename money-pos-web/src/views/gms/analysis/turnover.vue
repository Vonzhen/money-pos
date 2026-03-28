<template>
  <PageWrapper>
    <div class="mb-4 flex justify-between items-center">
      <h2 class="text-xl font-bold text-gray-800 flex items-center gap-2">
        <svg-icon name="bell" class="w-6 h-6 text-red-500" />
        周转预警中心 (Lead Time)
      </h2>
    </div>

    <el-tabs v-model="activeTab" class="demo-tabs bg-white p-3 rounded-lg shadow-sm" v-loading="loading">

      <el-tab-pane name="replenish">
        <template #label>
          <span class="font-bold flex items-center gap-1 text-orange-600">
            🔥 紧急补货 <el-badge :value="replenishList.length" class="ml-1" type="danger" />
          </span>
        </template>

        <div class="flex justify-between items-center mb-3">
          <el-alert title="触发条件：当前库存不足以支撑 3 天的预估销量。" type="warning" show-icon :closable="false" class="flex-1 mr-4 py-1" />
          <el-button type="primary" class="font-bold tracking-widest shadow-md" @click="exportExcel('replenish')" :loading="exportingReplenish">
            <el-icon class="mr-1 text-lg"><Download /></el-icon> 导出采购单
          </el-button>
        </div>

        <el-table :data="replenishList" border stripe size="small" class="w-full">
          <el-table-column type="index" label="排" width="45" align="center" />
          <el-table-column prop="goodsName" label="商品名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="sales30Days" label="近30天销量" width="90" align="center">
            <template #default="scope">
              <span class="text-orange-500 font-bold">{{ scope.row.sales30Days }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="currentStock" label="当前库存" width="90" align="center">
            <template #default="scope">
              <el-tag type="danger" effect="dark" size="small" v-if="scope.row.currentStock <= 0">售罄</el-tag>
              <span class="text-red-600 font-bold" v-else>{{ scope.row.currentStock }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="suggestedQty" label="💡 建议补货" width="100" align="center">
            <template #default="scope">
              <span class="text-green-600 font-black text-base">+{{ scope.row.suggestedQty }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane name="dead">
        <template #label>
          <span class="font-bold flex items-center gap-1 text-gray-500">
            🧊 积压库存 <el-badge :value="deadStockList.length" class="ml-1" type="info" />
          </span>
        </template>

        <div class="flex justify-between items-center mb-3">
          <el-alert title="触发条件：库存 > 0，但超过 60 天未产生任何销售记录。" type="info" show-icon :closable="false" class="flex-1 mr-4 py-1" />
          <el-button type="danger" class="font-bold tracking-widest shadow-md" plain @click="exportExcel('dead')" :loading="exportingDead">
            <el-icon class="mr-1 text-lg"><Download /></el-icon> 导出清仓单
          </el-button>
        </div>

        <el-table :data="deadStockList" border stripe size="small" class="w-full">
          <el-table-column type="index" label="排" width="45" align="center" />
          <el-table-column prop="goodsName" label="商品名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="currentStock" label="积压数量" width="80" align="center">
            <template #default="scope">
              <span class="text-gray-600 font-bold">{{ scope.row.currentStock }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastSaleTime" label="最后售出时间" width="150" align="center" />
          <el-table-column prop="deadDays" label="🧊 沉睡天数" width="100" align="center">
            <template #default="scope">
              <span class="text-gray-500 font-black">{{ scope.row.deadDays }} 天</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

    </el-tabs>
  </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import { ref, onMounted } from "vue";
import { Download } from '@element-plus/icons-vue';
import { req } from "@/api/index.js";
import { ElMessage } from 'element-plus';
import gmsApi from "@/api/gms/analysis.js";

const activeTab = ref("replenish");
const loading = ref(true);
const replenishList = ref([]);
const deadStockList = ref([]);

const exportingReplenish = ref(false);
const exportingDead = ref(false);

const loadData = async () => {
  loading.value = true;
  try {
    const res = await gmsApi.getTurnoverWarnings();
    const data = res.data || res;
    replenishList.value = data.replenishList || [];
    deadStockList.value = data.deadStockList || [];
  } catch (e) {
    console.error("加载周转预警失败", e);
  } finally {
    loading.value = false;
  }
}

const exportExcel = async (type) => {
  const url = type === 'replenish' ? '/gms/analysis/export-replenish' : '/gms/analysis/export-deadstock';
  const fileName = type === 'replenish' ? '智能采购建议单.xlsx' : '积压库存清仓单.xlsx';

  if (type === 'replenish') exportingReplenish.value = true;
  else exportingDead.value = true;

  try {
    const res = await req({ url, method: 'GET', responseType: 'blob' });
    const blobData = res.data ? res.data : res;
    const blob = new Blob([blobData], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const downloadUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.setAttribute('download', fileName);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(downloadUrl);
    ElMessage.success(`🎉 ${fileName} 导出成功！`);
  } catch (e) {
    console.error("导出异常", e);
    ElMessage.error("导出失败，请检查网络或后端日志");
  } finally {
    if (type === 'replenish') exportingReplenish.value = false;
    else exportingDead.value = false;
  }
}

onMounted(() => loadData());
</script>

<style scoped>
:deep(.el-alert) { align-items: center; }
/* 优化小尺寸表格表头高度 */
:deep(.el-table--small .el-table__header th) { padding: 4px 0; }
</style>