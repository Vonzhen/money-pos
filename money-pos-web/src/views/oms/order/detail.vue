<template>
    <el-card v-if="order.orderNo" class="flex-1 rounded-md bg-base-100 sm:m-2 my-2 shadow-sm border-0">
        <template #header>
            <div class="flex justify-between items-center">
                <div class="flex items-center gap-3">
                    <span class="font-bold text-lg flex items-center gap-2">
                        <el-icon class="text-blue-500"><Document /></el-icon>
                        单据详情 - {{ order.orderNo }}
                    </span>
                    <el-tag effect="dark" :type="getOrderStatusType(order.status)" class="tracking-widest font-bold">
                        {{ getOrderStatusName(order.status) }}
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
                        <span v-if="!member?.brandLevels || Object.keys(member.brandLevels).length === 0" class="font-bold text-gray-500">普通顾客</span>
                        <template v-else>
                            <el-tag v-for="(levelCode, brand) in member.brandLevels" :key="brand" effect="dark" type="warning" size="small" class="tracking-widest font-bold">
                                {{ dict?.memberTypeKv?.[levelCode] || levelCode }}
                            </el-tag>
                        </template>
                    </div>
                </el-descriptions-item>
            </el-descriptions>
        </div>

        <h4 class="mb-3 font-black text-gray-800 flex items-center gap-2 text-lg mt-6"><el-icon class="text-orange-500"><Money /></el-icon>财务对账单 (Waterfall Ledger)</h4>
        <div class="flex items-center justify-between bg-white border border-gray-200 rounded-lg p-4 shadow-sm mb-6 overflow-x-auto">
            <div class="text-center min-w-[70px]"><div class="text-xs text-gray-500 font-bold mb-1">应收总价</div><div class="text-lg font-black text-gray-800">￥{{ Number(order.totalAmount || 0).toFixed(2) }}</div></div>
            <div class="text-gray-300 font-black text-xl">-</div>
            <div class="text-center min-w-[70px]"><div class="text-xs text-orange-500 font-bold mb-1">会员券</div><div class="text-lg font-black text-orange-500">￥{{ Number(order.couponAmount || order.memberCouponDeduct || 0).toFixed(2) }}</div></div>
            <div class="text-gray-300 font-black text-xl">-</div>
            <div class="text-center min-w-[70px]"><div class="text-xs text-red-400 font-bold mb-1">满减券</div><div class="text-lg font-black text-red-500">￥{{ Number(order.useVoucherAmount || order.voucherDeduct || 0).toFixed(2) }}</div></div>
            <div class="text-gray-300 font-black text-xl">-</div>
            <div class="text-center min-w-[70px]"><div class="text-xs text-red-400 font-bold mb-1">整单优惠</div><div class="text-lg font-black text-red-500">￥{{ Number(order.manualDiscountAmount || order.manualDeduct || 0).toFixed(2) }}</div></div>
            <div class="text-gray-300 font-black text-xl">=</div>
            <div class="text-center min-w-[70px]"><div class="text-xs text-blue-600 font-bold mb-1">最终实付</div><div class="text-lg font-black text-blue-600">￥{{ Number(order.payAmount || 0).toFixed(2) }}</div></div>
            <div class="text-gray-300 font-black text-xl">-</div>
            <div class="text-center min-w-[70px]"><div class="text-xs text-gray-500 font-bold mb-1">现金退款</div><div class="text-lg font-black text-gray-600">￥{{ Number(returnPrice || 0).toFixed(2) }}</div></div>
            <div class="text-gray-300 font-black text-xl">=</div>
            <div class="text-center bg-green-50 p-2 rounded-xl border-2 border-green-200 min-w-[90px] shadow-inner"><div class="text-xs text-green-700 font-black mb-0.5">当前净收</div><div class="text-2xl font-black text-green-600">￥{{ Number(netIncome || 0).toFixed(2) }}</div></div>
        </div>

        <div class="mb-6 bg-blue-50/50 border border-blue-100 rounded-lg py-2 px-4 flex items-center justify-between text-xs shadow-sm">
            <span class="text-blue-700 font-bold shrink-0">实付资金组成：</span>
            <div class="flex gap-6 items-center flex-1 ml-4">
                <span>会员余额: <span class="font-bold text-gray-800 text-sm">￥{{ Number(order.balanceAmount || 0).toFixed(2) }}</span></span>
                <span class="flex items-center">
                    聚合扫码: <span class="font-bold text-gray-800 ml-1 text-sm">￥{{ Number(order.scanAmount || 0).toFixed(2) }}</span>
                    <el-tag v-for="(pay, index) in (payList || []).filter(p => p.payMethodCode === 'AGGREGATE' && p.payAmount > 0)" :key="index" size="small" type="primary" effect="plain" class="ml-2 font-bold !h-[20px] !leading-[18px] !px-2 !text-[11px]">{{ getPayTagName(pay.payTag) }}</el-tag>
                </span>
                <span>现金收银: <span class="font-bold text-gray-800 text-sm">￥{{ Number(order.cashAmount || 0).toFixed(2) }}</span></span>
            </div>
        </div>

        <h4 class="mb-3 font-black text-gray-800 flex items-center gap-2 text-lg mt-6"><el-icon class="text-green-600"><Goods /></el-icon>商品明细审计</h4>
        <el-table class="mb-6 border rounded-lg" :data="detail" border :summary-method="getSummaries" show-summary stripe>
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="goodsBarcode" label="商品条码" align="center" width="160" />
            <el-table-column prop="goodsName" label="商品名称" min-width="150" show-overflow-tooltip><template #default="{row}"><span class="font-bold">{{ row.goodsName }}</span></template></el-table-column>
            <el-table-column label="单价" align="center" width="160"><template #default="{row}"><div class="flex items-center justify-center gap-2"><span class="text-gray-400 line-through text-xs">￥{{ row.salePrice }}</span><el-icon class="text-gray-300"><Right /></el-icon><span class="font-bold text-red-500">￥{{ row.goodsPrice }}</span></div></template></el-table-column>
            <el-table-column label="数量状态" align="center" width="120"><template #default="{row}"><div class="font-black text-lg">{{ row.quantity }}</div><div v-if="row.returnQuantity > 0" class="text-xs text-red-500 font-bold">- 已退 {{ row.returnQuantity }}</div></template></el-table-column>
            <el-table-column label="单品小计" align="right" width="140"><template #default="{ row }"><span class="font-bold">￥{{ NP.times(row.quantity, row.goodsPrice) }}</span></template></el-table-column>
            <el-table-column label="售后操作" align="center" width="100" fixed="right">
                <template #default="{ row }">
                    <el-button type="danger" plain size="small" @click="handleReturnGoods(row)" :disabled="row.returnQuantity >= row.quantity || order.status === 'RETURN' || order.status === 'REFUNDED'">退货</el-button>
                </template>
            </el-table-column>
        </el-table>

        <h4 class="mb-3 font-black text-gray-800 flex items-center gap-2 text-lg"><el-icon class="text-gray-600"><Clock /></el-icon>系统审计日志</h4>
        <el-table :data="log" border stripe class="border rounded-lg" size="small">
            <el-table-column prop="createTime" label="操作时间" align="center" width="180" />
            <el-table-column prop="createBy" label="操作人" align="center" width="120"><template #default="{row}"><el-tag size="small" type="info">{{ row.createBy }}</el-tag></template></el-table-column>
            <el-table-column prop="description" label="操作详情">
                <template #default="{ row }"><span class="font-mono text-gray-600 whitespace-pre-wrap leading-relaxed">{{ formatLogMessage(row.description) }}</span></template>
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
import dictApi from "@/api/system/dict.js"
import NP from "number-precision"

const printOrder = ref()
const routeParam = useRoute().params.id

const dict = ref({}); const order = ref({}); const detail = ref([]); const member = ref({}); const log = ref([]); const payList = ref([]); const payTagDict = ref([])
const returnPrice = ref(0); const returnCoupon = ref(0)

const netIncome = computed(() => {
    if (order.value.status === 'REFUNDED' || order.value.status === 'RETURN') return 0;
    return NP.minus(order.value.payAmount || 0, returnPrice.value)
})

onBeforeMount(async () => {
    const dictRes = await dictApi.loadDict(["orderStatus", "memberType", "paySubTag"])
    dict.value = dictRes || {}
    if (dictRes && dictRes.paySubTag) payTagDict.value = dictRes.paySubTag
    await loadDetail()
})

// 🌟 统一标准的字典翻译函数（字典优先，兼容老数据兜底）
const getOrderStatusName = (status) => {
    if (!status) return '-';
    // 1. 优先从后端字典的 Key-Value Map 中取
    if (dict.value?.orderStatusKv?.[status]) {
        return dict.value.orderStatusKv[status];
    }
    // 2. 尝试从后端字典的 Array 列表中取
    const statuses = dict.value.orderStatus;
    if (Array.isArray(statuses)) {
        const match = statuses.find(s => s && (s.value === status || s.dictValue === status));
        if (match) return match.desc || match.dictLabel || status;
    }
    // 3. 终极兜底（新老标准合并，防报错）
    const fallbackMap = {
        'UNPAID': '待支付', 'PENDING': '待支付',
        'PAID': '已支付', 'DONE': '已完成',
        'PARTIAL': '部分退货', 'PARTIAL_REFUNDED': '部分退货',
        'RETURN': '已退款', 'REFUNDED': '已退款',
        'CLOSED': '已关闭', 'CANCELLED': '已取消'
    };
    return fallbackMap[status] || status;
}

const getOrderStatusType = (status) => {
    if (status === 'PAID' || status === 'DONE') return 'success';
    if (status === 'PARTIAL' || status === 'PARTIAL_REFUNDED') return 'warning';
    if (status === 'RETURN' || status === 'REFUNDED' || status === 'CLOSED' || status === 'CANCELLED') return 'info';
    return 'danger';
}

const getPayTagName = (tagCode) => {
    if (!tagCode) return '其他扫码'
    const match = payTagDict.value.find(t => t && (t.value === tagCode || t.dictValue === tagCode))
    return match ? (match.desc || match.dictLabel || tagCode) : tagCode
}

const translatePayMethods = (methodsStr) => {
    if (!methodsStr) return '';
    return methodsStr.split(',').map(method => {
        if (method === 'CASH') return '现金';
        if (method === 'BALANCE') return '会员余额';
        if (method.startsWith('AGGREGATE:')) {
            const tag = method.split(':')[1];
            return `扫码(${getPayTagName(tag)})`;
        }
        return method === 'AGGREGATE' ? '聚合扫码' : method;
    }).join(' + ');
}

const formatLogMessage = (desc) => {
    if (!desc) return '-';
    if (desc.startsWith('{') && desc.endsWith('}')) {
        try {
            const obj = JSON.parse(desc);
            let text = '';
            if (obj.action === 'SETTLE_SUCCESS') text += '✅ 收银结算成功\n';
            if (obj.payMethods) text += `[支付组合]: ${translatePayMethods(obj.payMethods)}\n`;
            if (obj.totalPaid !== undefined) text += `[应付金额]: ￥${obj.totalPaid}\n`;
            if (obj.finalPay !== undefined) text += `[实付金额]: ￥${obj.finalPay}\n`;
            if (obj.change !== undefined) text += `[找零金额]: ￥${obj.change}\n`;
            return text || desc;
        } catch (e) { return desc; }
    }
    return desc;
}

async function loadDetail() {
    const res = await orderApi.fullDetailByOrderNo(routeParam).then(res => res.data || res)
    order.value = res.order || res || {}
    detail.value = res.orderDetails || res.orderDetail || []
    member.value = res.memberInfo || res.member || {}
    log.value = res.orderLog || []
    payList.value = res.payments || []
    returnPrice.value = detail.value.reduce((sum, item) => NP.plus(sum, NP.times(item.returnQuantity || 0, item.goodsPrice || 0)), 0)
}

function print() { printOrder.value.print({ info: order.value, detail: detail.value, member: member.value }) }

function handleReturnGoods(row) {
    ElMessageBox.prompt(`请输入【${row.goodsName}】的退货数量`, '单品售后退货', {
        confirmButtonText: '确定退货', cancelButtonText: '取消', inputPattern: /^[1-9]\d*$/, inputErrorMessage: '请输入正整数',
    }).then(({ value }) => {
        const qty = parseInt(value)
        if (qty > (row.quantity - (row.returnQuantity || 0))) return ElMessage.warning('退货数量不能超过当前可退数量！')

        orderApi.returnGoods({
            orderNo: order.value.orderNo,
            detailId: row.id,
            returnQty: qty,
            reqId: 'B_PRET' + Date.now()
        }).then(() => {
            ElMessage.success('退货成功！')
            loadDetail()
        })
    }).catch(() => {})
}

function getSummaries(param) {
    const { columns, data } = param; const sums = []
    columns.forEach((column, index) => {
        if (index === 0) { sums[index] = '总计'; return; }
        if (column.property === 'quantity' || column.label === '单品小计') {
            const values = data.map(item => column.property === 'quantity' ? Number(item.quantity) : Number(NP.times(item.quantity || 0, item.goodsPrice || 0)))
            sums[index] = values.reduce((prev, curr) => !isNaN(curr) ? NP.plus(prev, curr) : prev, 0)
            if (column.label === '单品小计') sums[index] = '￥' + sums[index].toFixed(2)
        } else sums[index] = ''
    })
    return sums
}
</script>
<style scoped>:deep(.el-descriptions__label) { vertical-align: middle !important; }</style>