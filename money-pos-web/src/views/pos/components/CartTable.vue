<template>
    <el-table
        :data="cartList"
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
                <span v-if="getMemberPrice(row) !== null" class="text-blue-600 font-bold">￥{{ getMemberPrice(row)?.toFixed(2) }}</span>
                <span v-else class="text-gray-400">-</span>
            </template>
        </el-table-column>

        <el-table-column label="会员券" width="100" align="right">
            <template #default="{ row }">
                <span v-if="getMemberCoupon(row) > 0" class="text-green-600">￥{{ getMemberCoupon(row)?.toFixed(2) }}</span>
                <span v-else class="text-gray-400">-</span>
            </template>
        </el-table-column>

        <el-table-column label="数量" width="160" align="center">
            <template #default="{ row }">
                <el-input-number v-model="row.qty" :min="1" :max="9999" size="small" class="!w-28" />
            </template>
        </el-table-column>

        <el-table-column label="小计" width="160" align="right">
            <template #default="{ row }">
                <span class="text-red-600 font-bold text-lg">￥{{ getSubtotal(row)?.toFixed(2) }}</span>
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

const { cartList, currentMember, removeItem } = usePosStore()

// 🌟 纯渲染逻辑：根据会员绑定的品牌等级，从商品矩阵中提取对应价格
const getLevelCode = (brandId) => {
    if (!currentMember.value?.id || !brandId) return null;
    return currentMember.value.brandLevels?.[String(brandId)] || null;
}

const getMemberPrice = (row) => {
    const code = getLevelCode(row.brandId);
    if (code && row.levelPrices && row.levelPrices[code] != null) return row.levelPrices[code];
    return null; // 没命中特权
}

const getMemberCoupon = (row) => {
    const code = getLevelCode(row.brandId);
    if (code && row.levelCoupons && row.levelCoupons[code] != null) return row.levelCoupons[code];
    return 0;
}

const getSubtotal = (row) => {
    const activePrice = getMemberPrice(row) !== null ? getMemberPrice(row) : (row.salePrice || 0);
    return activePrice * (row.qty || 1);
}
</script>