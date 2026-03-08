<template>
    <el-card v-if="order.id" class="flex-1 rounded-md bg-base-100 sm:m-2 my-2 shadow-sm border-0">
        <template #header>
            <div class="flex justify-between items-center">
                <div class="flex items-center gap-3">
                    <span class="font-bold text-lg flex items-center gap-2">
                        <el-icon class="text-blue-500"><Document /></el-icon>
                        单据详情 - {{ order.orderNo }}
                    </span>
                    <el-tag effect="dark" :type="statusColor[order.status] || 'primary'" class="tracking-widest font-bold">
                        {{ dict?.orderStatusKv?.[order.status] || order.status }}
                    </el-tag>
                </div>
                <el-button type="primary" plain @click="print" class="font-bold tracking-widest">
                    <el-icon class="mr-1"><Printer /></el-icon>打印单据
                </el-button>
            </div>
        </template>

        <div class="flex flex-col md:flex-row gap-4 mb-6">
            <el-descriptions class="flex-1 shadow-sm rounded-lg overflow-hidden border border-gray-100" title="📋 业务基础信息" :column="2" border>
                <el-descriptions-item label="交易时间" label-class-name="bg-gray-50 w-24 font-bold text-gray-600">
                    <span class="font-mono text-sm">{{ order.createTime }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="收银人员" label-class-name="bg-gray-50 w-24 font-bold text-gray-600">
                    <span class="font-bold text-gray-800">{{ log.length > 0 ? log[log.length-1].createBy : 'System' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="订单备注" :span="2" label-class-name="bg-gray-50 w-24 font-bold text-gray-600">
                    {{ order.remark || '无' }}
                </el-descriptions-item>
            </el-descriptions>

            <el-descriptions class="flex-1 shadow-sm rounded-lg overflow-hidden border border-blue-100" title="👤 会员身份信息" :column="2" border>
                <el-descriptions-item label="会员姓名" label-class-name="bg-blue-50 w-24 font-bold text-blue-700">
                    <span class="font-bold text-blue-600">{{ member?.name || order.member || '散客' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="联系电话" label-class-name="bg-blue-50 w-24 font-bold text-blue-700">
                    <span class="font-mono">{{ member?.phone || '-' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="多品牌矩阵" :span="2" label-class-name="bg-blue-50 w-24 font-bold text-blue-700">
                    <div class="flex flex-wrap items-center gap-2">
                        <span v-if="!member?.brandLevels || Object.keys(member.brandLevels).length === 0" class="font-bold text-gray-500">
                            普通顾客
                        </span>
                        <template v-else>
                            <el-tag v-for="(levelCode, brand) in member.brandLevels" :key="brand" effect="dark" type="warning" size="small" class="tracking-widest font-bold">
                                {{ dict?.memberTypeKv?.[levelCode] || levelCode }}
                            </el-tag>
                        </template>
                    </div>
                </el-descriptions-item>
            </el-descriptions>
        </div>

        <h4 class="mb-3 font-black text-gray-800 flex items-center gap-2 text-lg mt-6">
            <el-icon class="text-orange-500"><Money /></el-icon>财务对账单 (Waterfall Ledger)
        </h4>

        <div class="flex items-center justify-between bg-white border border-gray-200 rounded-lg p-4 shadow-sm mb-6 overflow-x-auto">
            <div class="text-center min-w-[70px]">
                <div class="text-xs text-gray-500 font-bold mb-1">应收总价</div>
                <div class="text-lg font-black text-gray-800">￥{{ order.totalAmount || 0 }}</div>
            </div>
            <div class="text-gray-300 font-black text-xl">-</div>

            <div class="text-center min-w-[70px]">
                <div class="text-xs text-orange-500 font-bold mb-1">会员券抵扣</div>
                <div class="text-lg font-black text-orange-500">￥{{ order.couponAmount || 0 }}</div>
                <div class="text-[10px] text-gray-400 font-bold tracking-widest mt-0.5" v-if="returnCoupon > 0">(退回 ￥{{ returnCoupon }})</div>
            </div>
            <div class="text-gray-300 font-black text-xl">-</div>

            <div class="text-center min-w-[70px]">
                <div class="text-xs text-red-400 font-bold mb-1">满减券抵扣</div>
                <div class="text-lg font-black text-red-500">￥{{ order.useVoucherAmount || 0 }}</div>
            </div>
            <div class="text-gray-300 font-black text-xl">-</div>

            <div class="text-center min-w-[70px]">
                <div class="text-xs text-red-400 font-bold mb-1">整单抹零</div>
                <div class="text-lg font-black text-red-500">￥{{ order.manualDiscountAmount || 0 }}</div>
            </div>
            <div class="text-gray-300 font-black text-xl">=</div>

            <div class="text-center min-w-[70px]">
                <div class="text-xs text-blue-600 font-bold mb-1">最终实付</div>
                <div class="text-lg font-black text-blue-600">￥{{ order.payAmount || 0 }}</div>
            </div>
            <div class="text-gray-300 font-black text-xl">-</div>

            <div class="text-center min-w-[70px]">
                <div class="text-xs text-gray-500 font-bold mb-1">现金退款</div>
                <div class="text-lg font-black text-gray-600">￥{{ returnPrice }}</div>
            </div>
            <div class="text-gray-300 font-black text-xl">=</div>

            <div class="text-center bg-green-50 p-2 rounded-xl border-2 border-green-200 min-w-[90px] shadow-inner">
                <div class="text-xs text-green-700 font-black mb-0.5">当前净收</div>
                <div class="text-2xl font-black text-green-600">￥{{ netIncome }}</div>
            </div>
        </div>

        <h4 class="mb-3 font-black text-gray-800 flex items-center gap-2 text-lg mt-6">
            <el-icon class="text-green-600"><Goods /></el-icon>商品明细审计
        </h4>
        <el-table class="mb-6 border rounded-lg" :data="detail" border :summary-method="getSummaries" show-summary stripe>
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="goodsBarcode" label="商品条码" align="center" width="160" />
            <el-table-column prop="goodsName" label="商品名称" min-width="150" show-overflow-tooltip>
                <template #default="{row}"><span class="font-bold">{{ row.goodsName }}</span></template>
            </el-table-column>

            <el-table-column label="系统价 -> 成交价" align="center" width="160">
                <template #default="{row}">
                    <div class="flex items-center justify-center gap-2">
                        <span class="text-gray-400 line-through text-xs">￥{{ row.salePrice }}</span>
                        <el-icon class="text-gray-300"><Right /></el-icon>
                        <span class="font-bold text-red-500">￥{{ row.goodsPrice }}</span>
                    </div>
                </template>
            </el-table-column>

            <el-table-column label="数量状态" align="center" width="120">
                <template #default="{row}">
                    <div class="font-black text-lg">{{ row.quantity }}</div>
                    <div v-if="row.returnQuantity > 0" class="text-xs text-red-500 font-bold">- 已退 {{ row.returnQuantity }}</div>
                </template>
            </el-table-column>

            <el-table-column label="单品小计(成交价)" align="right" width="140">
                <template #default="{ row }">
                    <span class="font-bold">￥{{ NP.times(row.quantity, row.goodsPrice) }}</span>
                </template>
            </el-table-column>

            <el-table-column label="售后操作" align="center" width="100" fixed="right">
                <template #default="{ row }">
                    <el-button type="danger" plain size="small" @click="handleReturnGoods(row)" :disabled="row.returnQuantity >= row.quantity || order.status === 'RETURN'">退货</el-button>
                </template>
            </el-table-column>
        </el-table>

        <h4 class="mb-3 font-black text-gray-800 flex items-center gap-2 text-lg">
            <el-icon class="text-gray-600"><Clock /></el-icon>系统操作记录
        </h4>
        <el-table :data="log" border stripe class="border rounded-lg" size="small">
            <el-table-column prop="createTime" label="操作时间" align="center" width="180" />
            <el-table-column prop="createBy" label="操作人" align="center" width="120">
                <template #default="{row}"><el-tag size="small" type="info">{{ row.createBy }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="description" label="操作详情">
                <template #default="{ row }"><span v-html="row.description" class="font-mono text-gray-700" /></template>
            </el-table-column>
        </el-table>

        <print-order ref="printOrder" />
    </el-card>
</template>

<script setup>
import PrintOrder from "@/views/pos/printOrder.vue";
import { Document, Printer, Money, Goods, Clock, Right } from '@element-plus/icons-vue'
import { onBeforeMount, ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'

import orderApi from '@/api/oms/order.js'
import dictApi from "@/api/system/dict.js";
import NP from "number-precision";

const printOrder = ref()
const id = useRoute().params.id
const statusColor = { 'RETURN': 'info', 'PAID': 'success', 'DONE': 'success' }

const dict = ref({})
const order = ref({})
const detail = ref([])
const member = ref({})
const log = ref([])

const returnPrice = ref(0)
const returnCoupon = ref(0)
const netIncome = computed(() => NP.minus(order.value.payAmount || 0, returnPrice.value))

onBeforeMount(async () => {
    dict.value = await dictApi.loadDict(["orderStatus", "memberType"])
    await loadDetail()
})

async function loadDetail() {
    const res = await orderApi.getDetail(id).then(res => res.data || res)
    order.value = res.order || {}
    detail.value = res.orderDetail || []
    member.value = res.member || {}
    log.value = res.orderLog || []

    returnPrice.value = detail.value.reduce((sum, item) => NP.plus(sum, NP.times(item.returnQuantity || 0, item.goodsPrice || 0)), 0)
    returnCoupon.value = detail.value.reduce((sum, item) => NP.plus(sum, NP.times(item.returnQuantity || 0, item.coupon || 0)), 0)
}

function print() {
    printOrder.value.print({ info: order.value, detail: detail.value, member: member.value })
}

function handleReturnGoods(row) {
    ElMessageBox.prompt(`请输入【${row.goodsName}】的退货数量`, '单品售后退货', {
        confirmButtonText: '确定退货',
        cancelButtonText: '取消',
        inputPattern: /^[1-9]\d*$/,
        inputErrorMessage: '请输入大于 0 的正整数',
    }).then(({ value }) => {
        const qty = parseInt(value)
        if (qty > (row.quantity - (row.returnQuantity || 0))) return ElMessage.warning('退货数量不能超过当前可退数量！')
        orderApi.returnGoods({ id: row.id, quantity: qty }).then(() => {
            ElMessage.success('退货成功，财务和库存已同步更新！')
            loadDetail()
        })
    }).catch(() => {})
}

function getSummaries(param) {
    const { columns, data } = param
    const sums = []
    columns.forEach((column, index) => {
        if (index === 0) { sums[index] = '总计'; return; }
        if (index === 1 || index === 2 || index === 3 || index === 6) { sums[index] = ''; return; }

        if (column.property === 'quantity' || column.label === '单品小计(成交价)') {
            const values = data.map(item => {
                if (column.property === 'quantity') return Number(item.quantity)
                return Number(NP.times(item.quantity || 0, item.goodsPrice || 0))
            })
            sums[index] = values.reduce((prev, curr) => !isNaN(curr) ? NP.plus(prev, curr) : prev, 0)
            if (column.label === '单品小计(成交价)') sums[index] = '￥' + sums[index]
        } else {
            sums[index] = ''
        }
    })
    return sums
}
</script>

<style scoped>
:deep(.el-descriptions__label) { vertical-align: middle !important; }
</style>