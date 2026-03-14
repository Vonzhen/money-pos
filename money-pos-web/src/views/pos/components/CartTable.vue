<template>
    <el-table
        :data="enrichedCartList"
        height="100%"
        stripe
        border
        :header-cell-style="{ background: '#e0e7ff', color: '#3730a3', fontSize: '15px', fontWeight: 'bold' }"
        :row-style="{ fontSize: '16px', color: '#1f2937' }"
    >
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="barcode" label="条码" width="160" />

        <el-table-column label="商品名称" min-width="220">
            <template #default="{ row }">
                <span class="font-bold mr-2">{{ row.name }}</span>
                <el-tag v-if="row.isCombo === 1" type="warning" size="small" effect="dark" class="mr-1">套餐</el-tag>
                <el-tag v-if="row.isDiscountParticipable === 1" type="danger" size="small" effect="plain">满减</el-tag>
            </template>
        </el-table-column>

        <el-table-column label="单价" width="120" align="right">
            <template #default="{ row }">￥{{ row.salePrice?.toFixed(2) }}</template>
        </el-table-column>

        <el-table-column label="会员价" width="120" align="right">
            <template #default="{ row }">
                <span v-if="getLevelCode(row.brandId)" class="text-blue-600 font-bold">
                    ￥{{ row.displayPrice?.toFixed(2) }}
                </span>
                <span v-else class="text-gray-400">-</span>
            </template>
        </el-table-column>

        <el-table-column label="会员券" width="120" align="right">
            <template #default="{ row }">
                <span v-if="getLevelCode(row.brandId) && row.displayCouponDeduct > 0" class="text-green-600 font-bold">
                    ￥{{ (row.displayCouponDeduct / row.qty).toFixed(2) }}
                </span>
                <span v-else class="text-gray-400">-</span>
            </template>
        </el-table-column>

        <el-table-column label="数量" width="160" align="center">
            <template #default="{ row, $index }">
                <el-input-number
                    :model-value="row.qty"
                    :min="1" :max="9999" size="small" class="!w-28"
                    @change="(val) => handleQtyChange($index, val)"
                />
            </template>
        </el-table-column>

        <el-table-column label="小计" width="160" align="right">
            <template #default="{ row }">
                <span
                    class="text-red-600 font-bold text-lg transition-opacity duration-300"
                    :class="{ 'opacity-40': row.isPending }"
                >
                    ￥{{ row.displaySubtotal?.toFixed(2) }}
                </span>
            </template>
        </el-table-column>

        <el-table-column label="库存" width="100" align="center">
            <template #default="{ row }">
                <span :class="{'text-red-600 font-bold': row.qty > (row.stock || 0), 'text-gray-600': row.qty <= (row.stock || 0)}">
                    {{ row.stock !== undefined ? row.stock : '-' }}
                </span>
            </template>
        </el-table-column>

        <el-table-column label="操作" width="100" align="center" fixed="right">
            <template #default="{ $index }">
                <el-button type="danger" icon="Delete" circle plain @click="removeItem($index)" />
            </template>
        </el-table-column>
    </el-table>
</template>

<script setup>
import { usePosStore } from '../hooks/usePosStore'

const { cartList, enrichedCartList, currentMember, removeItem, runTrial } = usePosStore()

// 🌟 原汁原味恢复：保留判断是否有会员特权的逻辑
const getLevelCode = (brandId) => {
    if (!currentMember.value?.id || !brandId) return null;
    return currentMember.value.brandLevels?.[String(brandId)] || null;
}

// 🌟 极速响应枢纽：控制数据流向，立即触发前端计算并防抖请求后端
const handleQtyChange = (index, newVal) => {
    cartList.value[index].qty = newVal;
    runTrial();
}
</script>