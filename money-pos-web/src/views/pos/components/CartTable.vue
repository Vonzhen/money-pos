<template>
  <div class="flex-1 overflow-hidden flex flex-col p-2">
    <el-table
      :data="cartList" border stripe height="100%" size="large"
      :class="['w-full text-lg font-bold shadow-sm rounded overflow-hidden', theme.tableText]"
      :header-cell-style="tableHeaderStyle"
    >
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="barcode" label="条码" width="180" />
      <el-table-column prop="name" label="商品名称" min-width="200" show-overflow-tooltip />

      <el-table-column label="单价" width="140" align="right">
        <template #default="{row}">
          <div v-if="currentMember.id && SettleEngine.getRealPrice(row, currentMember) < row.salePrice">
            <div class="text-gray-400 line-through text-sm">￥{{ row.salePrice.toFixed(2) }}</div>
            <div class="text-orange-500 font-bold">￥{{ SettleEngine.getRealPrice(row, currentMember).toFixed(2) }}</div>
          </div>
          <div v-else>￥{{ row.salePrice.toFixed(2) }}</div>
        </template>
      </el-table-column>

      <el-table-column prop="quantity" label="数量" width="160" align="center">
        <template #default="{row}">
          <el-input-number v-model="row.quantity" :min="1" size="large" class="!w-full cart-number" />
        </template>
      </el-table-column>

      <el-table-column label="小计" width="140" align="right">
        <template #default="{row}">
          <span class="text-red-600 text-xl">￥{{ (SettleEngine.getRealPrice(row, currentMember) * row.quantity).toFixed(2) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="90" align="center">
        <template #default="{$index}">
          <el-button type="danger" link @click="cartList.splice($index, 1)">
            <el-icon size="20"><Delete /></el-icon>
          </el-button>
        </template>
      </el-table-column>

      <template #empty>
        <div class="flex flex-col items-center justify-center h-full text-gray-400 py-10 opacity-60">
          <el-icon :size="80"><FullScreen /></el-icon>
          <p class="mt-4 text-2xl tracking-widest font-black">请扫描商品条码或输入首拼</p>
        </div>
      </template>
    </el-table>
  </div>
</template>

<script setup>
import { watch } from 'vue'
import { Delete, FullScreen } from '@element-plus/icons-vue'
import { usePosStore } from '../hooks/usePosStore'
import { SettleEngine } from '../engine/settleEngine'

defineProps(['theme', 'tableHeaderStyle'])
const { cartList, currentMember } = usePosStore()

// 🌟 强行逼迫表格重绘：一旦发现切换了会员，就打乱重组购物车数组，让 el-table 必须刷新价格！
watch(() => currentMember.value, () => {
    if (cartList.value.length > 0) {
        cartList.value = [...cartList.value];
    }
}, { deep: true })

</script>

<style scoped>
:deep(.cart-number .el-input-number__increase),
:deep(.cart-number .el-input-number__decrease) { width: 40px; font-size: 18px; }
:deep(.cart-number .el-input__inner) { font-size: 18px; font-weight: bold; }
:deep(.el-table__empty-block) { border-bottom: none; }
:deep(.el-table) { transition: background-color 0.3s; }
</style>