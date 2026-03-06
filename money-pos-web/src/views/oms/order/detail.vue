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
                <el-descriptions-item label="交易时间" label-class-name="bg-gray-50 w-28 font-bold text-gray-600">
                    <span class="font-mono">{{ order.createTime }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="收银人员" label-class-name="bg-gray-50 w-28 font-bold text-gray-600">
                    <span class="font-bold text-gray-800">{{ log.length > 0 ? log[0].createBy : 'System' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="订单备注" :span="2" label-class-name="bg-gray-50 w-28 font-bold text-gray-600">
                    {{ order.remark || '无' }}
                </el-descriptions-item>
            </el-descriptions>

            <el-descriptions class="flex-1 shadow-sm rounded-lg overflow-hidden border border-blue-100" title="👤 会员身份信息" :column="2" border>
                <el-descriptions-item label="会员姓名" label-class-name="bg-blue-50 w-28 font-bold text-blue-700">
                    <span class="font-bold text-blue-600">{{ member?.name || order.member || '散客' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="联系电话" label-class-name="bg-blue-50 w-28 font-bold text-blue-700">
                    <span class="font-mono">{{ member?.phone || '-' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="会员类型" label-class-name="bg-blue-50 w-28 font-bold text-blue-700">
                    {{ member?.type ? dict?.memberTypeKv?.[member.type] : '普通顾客' }}
                </el-descriptions-item>
                <el-descriptions-item label="联系地址" label-class-name="bg-blue-50 w-28 font-bold text-blue-700">
                    {{ (order.province || '') + (order.city || '') + (order.district || '') + (order.address || '-') }}
                </el-descriptions-item>
            </el-descriptions>
        </div>

        <h4 class="mb-3 font-black text-gray-800 flex items-center gap-2 text-lg">
            <el-icon class="text-orange-500"><Money /></el-icon>财务对账单 (The Ledger)
        </h4>
        <el-descriptions class="mb-6 shadow-md rounded-lg overflow-hidden border-2 border-orange-100" :column="5" border direction="vertical">
            <el-descriptions-item label="应收总价" label-class-name="bg-gray-100 text-center font-bold text-gray-600 text-base" class-name="text-center font-bold text-gray-800 text-lg">
                ￥{{ order.totalAmount || 0 }}
            </el-descriptions-item>

            <el-descriptions-item label="会员券 (单品直减)" label-class-name="bg-orange-50 text-center font-bold text-orange-600 text-base" class-name="text-center font-bold text-orange-600 text-lg">
                - ￥{{ order.couponAmount || 0 }}
                <div v-if="returnCoupon > 0" class="text-xs text-gray-400 mt-1 font-normal">(已退回 ￥{{ returnCoupon }})</div>
            </el-descriptions-item>

            <el-descriptions-item label="满减券 (达标满减)" label-class-name="bg-orange-50 text-center font-bold text-orange-600 text-base" class-name="text-center font-bold text-orange-600 text-lg">
                - ￥{{ order.useVoucherAmount || 0 }}
            </el-descriptions-item>

            <el-descriptions-item label="整单优惠 (手工调整)" label-class-name="bg-red-50 text-center font-bold text-red-500 text-base" class-name="text-center font-bold text-red-500 text-lg">
                - ￥{{ order.manualDiscountAmount || 0 }}
            </el-descriptions-item>

            <el-descriptions-item label="最终实付" label-class-name="bg-red-100 text-center font-black text-red-700 text-base" class-name="text-center font-black text-red-600 text-2xl">
                ￥{{ order.payAmount || 0 }}
                <div v-if="returnPrice > 0" class="text-xs text-gray-500 mt-1 font-bold tracking-widest">(已退款 ￥{{ returnPrice }})</div>
            </el-descriptions-item>
        </el-descriptions>

        <h4 class="mb-3 font-black text-gray-800 flex items-center gap-2 text-lg">
            <el-icon class="text-green-600"><Goods /></el-icon>商品明细审计
        </h4>
        <el-table class="mb-6 border rounded-lg" :data="detail" border :summary-method="getSummaries" show-summary stripe>
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="goodsBarcode" label="商品条码" align="center" width="160" />
            <el-table-column prop="goodsName" label="商品名称" min-width="150" show-overflow-tooltip>
                <template #default="{row}"><span class="font-bold">{{ row.goodsName }}</span></template>
            </el-table-column>

            <el-table-column prop="salePrice" label="系统零售价" align="right" width="110">
                <template #default="{row}"><span class="text-gray-400 line-through">￥{{ row.salePrice }}</span></template>
            </el-table-column>
            <el-table-column prop="goodsPrice" label="实际成交单价" align="right" width="120">
                <template #default="{row}"><span class="font-bold text-red-500">￥{{ row.goodsPrice }}</span></template>
            </el-table-column>

            <el-table-column prop="quantity" label="购买数量" align="center" width="100">
                <template #default="{row}"><span class="font-black text-lg">{{ row.quantity }}</span></template>
            </el-table-column>

            <el-table-column label="单品小计(实付)" align="right" width="130">
                <template #default="{ row }">
                    <span class="font-bold">￥{{ NP.times(row.quantity, row.goodsPrice) }}</span>
                </template>
            </el-table-column>

            <el-table-column label="售后操作" align="center" width="160" fixed="right">
                <template #default="{ row }">
                    <el-button type="danger" plain size="small" @click="handleReturnGoods(row)" :disabled="row.returnQuantity >= row.quantity || order.status === 'RETURN'">退货</el-button>
                    <div v-if="row.returnQuantity > 0" class="text-xs font-bold text-red-500 mt-1">已退 x{{ row.returnQuantity }}</div>
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
import { Document, Printer, Money, Goods, Clock } from '@element-plus/icons-vue'
import { onBeforeMount, ref } from 'vue'
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

// 独立的状态变量，在拿到数据时一次性计算好，坚决不在渲染阶段做加减法！
const returnPrice = ref(0)
const returnCoupon = ref(0)

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

    // 🌟 在这里把“已退金额”算好，保持渲染层纯粹
    returnPrice.value = detail.value.reduce((sum, item) => NP.plus(sum, NP.times(item.returnQuantity || 0, item.goodsPrice || 0)), 0)
    returnCoupon.value = detail.value.reduce((sum, item) => NP.plus(sum, NP.times(item.returnQuantity || 0, item.coupon || 0)), 0)
}

function print() {
    printOrder.value.print({
        info: order.value,
        detail: detail.value,
        member: member.value
    })
}

function handleReturnGoods(row) {
    ElMessageBox.prompt(`请输入【${row.goodsName}】的退货数量`, '单品售后退货', {
        confirmButtonText: '确定退货',
        cancelButtonText: '取消',
        inputPattern: /^[1-9]\d*$/,
        inputErrorMessage: '请输入大于 0 的正整数',
    }).then(({ value }) => {
        const qty = parseInt(value)
        if (qty > (row.quantity - (row.returnQuantity || 0))) {
            ElMessage.warning('退货数量不能超过当前可退数量！')
            return
        }
        const params = { id: row.id, quantity: qty }
        orderApi.returnGoods(params).then(() => {
            ElMessage.success('退货成功，财务和库存已同步更新！')
            loadDetail() // 重新拉取最新的绝对真实数据
        })
    }).catch(() => {})
}

// 纯粹的表格列合计，没有任何业务副作用！
function getSummaries(param) {
    const { columns, data } = param
    const sums = []
    columns.forEach((column, index) => {
        if (index === 0) { sums[index] = '总计'; return; }
        if (index === 1 || index === 2 || index === 3 || index === 7) { sums[index] = ''; return; } // 不参与合计的列

        // 计算数量、原单价合计、实付单价合计、小计合计
        if (column.property === 'quantity' || column.label === '单品小计(实付)') {
            const values = data.map(item => {
                if (column.property === 'quantity') return Number(item.quantity)
                return Number(NP.times(item.quantity || 0, item.goodsPrice || 0)) // 小计
            })
            sums[index] = values.reduce((prev, curr) => !isNaN(curr) ? NP.plus(prev, curr) : prev, 0)
            if (column.label === '单品小计(实付)') sums[index] = '￥' + sums[index]
        } else {
            sums[index] = ''
        }
    })
    return sums
}
</script>

<style scoped>
:deep(.el-descriptions__label) {
    vertical-align: middle !important;
}
</style>