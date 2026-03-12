<template>
    <el-dialog v-model="visible" title="销售单查询与审计中心" width="1200px" top="3vh" destroy-on-close @closed="$emit('closed')">

        <div class="flex justify-between items-center mb-4 bg-gray-50 p-2 rounded border">
            <el-radio-group v-model="timeRange" size="large" @change="fetchOrders">
                <el-radio-button value="today">今天</el-radio-button>
                <el-radio-button value="3days">近 3 天</el-radio-button>
                <el-radio-button value="7days">近 7 天</el-radio-button>
                <el-radio-button value="1month">近 1 个月</el-radio-button>
            </el-radio-group>
            <div class="flex gap-2 w-[300px]">
                <el-input v-model="searchKeyword" placeholder="输入订单号搜索" clearable @keyup.enter="fetchOrders" @clear="fetchOrders">
                    <template #append><el-button @click="fetchOrders"><el-icon><Search /></el-icon></el-button></template>
                </el-input>
            </div>
        </div>

        <div class="flex gap-4 h-[600px]">
            <div class="w-[32%] border rounded flex flex-col overflow-hidden shadow-sm">
                <el-table :data="orderList" v-loading="loading" stripe highlight-current-row height="100%" class="w-full text-sm" @current-change="handleSelectOrder">
                    <el-table-column label="时间" width="80">
                        <template #default="{row}">
                            <div class="font-bold">{{ formatTime(row.createTime) }}</div>
                            <div class="text-[10px] text-gray-400">{{ formatDate(row.createTime) }}</div>
                        </template>
                    </el-table-column>
                    <el-table-column label="订单号" min-width="100" show-overflow-tooltip>
                        <template #default="{row}">
                            <span :class="['font-mono', row.status === 'REFUNDED' || row.status === 'RETURN' ? 'line-through text-gray-400' : '']">{{ row.orderNo }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column label="实收" width="125" align="right">
                        <template #default="{row}">
                            <div class="whitespace-nowrap">
                                <span v-if="row.status === 'REFUNDED' || row.status === 'RETURN'" class="text-gray-400 font-bold">已退单</span>
                                <span v-else class="font-bold text-red-500 tracking-tight">￥{{ Number(row.payAmount || 0).toFixed(2) }}</span>
                            </div>
                        </template>
                    </el-table-column>
                </el-table>
                <div class="p-2 border-t bg-gray-50 flex justify-between items-center shrink-0">
                    <span class="text-xs text-gray-500">共 {{ totalRecords }} 单</span>
                    <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="totalRecords" layout="prev, pager, next" size="small" @current-change="fetchOrders" />
                </div>
            </div>

            <div class="w-[68%] border rounded bg-white flex flex-col overflow-hidden relative shadow-sm">
                <div v-if="detailLoading" class="absolute inset-0 z-10 bg-white/70 flex items-center justify-center text-blue-500"><el-icon class="is-loading text-4xl"><Loading /></el-icon></div>
                <div v-if="!currentOrderDetail" class="h-full flex flex-col items-center justify-center text-gray-400 bg-gray-50">
                    <el-icon class="text-5xl mb-2"><Document /></el-icon><p>请在左侧选择一笔订单查看详情</p>
                </div>

                <div v-else class="h-full flex flex-col">
                    <div class="bg-gray-50 p-3 border-b flex justify-between items-center shrink-0">
                        <div>
                            <div class="font-bold text-gray-800 text-lg">单号: <span class="font-mono">{{ currentOrderDetail.orderNo }}</span></div>
                            <div class="text-xs text-gray-500 mt-1">创建时间: {{ currentOrderDetail.createTime }} | 收银员: {{ cashierName }}</div>
                        </div>
                        <el-tag effect="dark" :type="currentOrderDetail.status === 'PAID' ? 'success' : 'info'" class="tracking-widest font-bold">
                            {{ currentOrderDetail.status === 'PAID' ? '已支付' : '已退单' }}
                        </el-tag>
                    </div>

                    <div class="flex-1 overflow-y-auto p-4 bg-white space-y-4">
                        <div class="border border-blue-100 rounded-lg overflow-hidden text-sm shadow-sm">
                            <div class="bg-blue-50 px-3 py-1.5 font-bold text-blue-700 border-b border-blue-100 flex items-center gap-2"><el-icon><User /></el-icon> 会员身份</div>
                            <div class="p-3 grid grid-cols-2 gap-y-2">
                                <div><span class="text-gray-500 mr-2">会员姓名:</span><span class="font-bold text-gray-800">{{ currentOrderDetail.member?.name || currentOrderDetail.member || '散客' }}</span></div>
                                <div><span class="text-gray-500 mr-2">联系电话:</span><span class="font-mono text-gray-800">{{ currentOrderDetail.member?.phone || '-' }}</span></div>
                            </div>
                        </div>

                        <div>
                            <div class="border border-gray-200 rounded-lg p-2.5 bg-gray-50 flex items-center justify-between shadow-inner overflow-x-auto">
                                <div class="flex flex-col items-center flex-1 px-0.5"><div class="text-[10px] text-gray-500 font-bold mb-0.5 whitespace-nowrap">应收</div><div class="text-sm font-black text-gray-800 whitespace-nowrap tracking-tight">￥{{ Number(currentOrderDetail.totalAmount || 0).toFixed(2) }}</div></div>
                                <div class="text-gray-300 font-black shrink-0 mx-1">-</div>
                                <div class="flex flex-col items-center flex-1 px-0.5"><div class="text-[10px] text-orange-500 font-bold mb-0.5 whitespace-nowrap">会员券</div><div class="text-sm font-black text-orange-500 whitespace-nowrap tracking-tight">￥{{ Number(currentOrderDetail.couponAmount || 0).toFixed(2) }}</div></div>
                                <div class="text-gray-300 font-black shrink-0 mx-1">-</div>
                                <div class="flex flex-col items-center flex-1 px-0.5"><div class="text-[10px] text-red-400 font-bold mb-0.5 whitespace-nowrap">满减券</div><div class="text-sm font-black text-red-500 whitespace-nowrap tracking-tight">￥{{ Number(currentOrderDetail.useVoucherAmount || 0).toFixed(2) }}</div></div>
                                <div class="text-gray-300 font-black shrink-0 mx-1">-</div>
                                <div class="flex flex-col items-center flex-1 px-0.5"><div class="text-[10px] text-red-400 font-bold mb-0.5 whitespace-nowrap">整单优惠</div><div class="text-sm font-black text-red-500 whitespace-nowrap tracking-tight">￥{{ Number(currentOrderDetail.manualDiscountAmount || 0).toFixed(2) }}</div></div>
                                <div class="text-gray-300 font-black shrink-0 mx-1">=</div>
                                <div class="flex flex-col items-center flex-1 px-0.5"><div class="text-[10px] text-blue-600 font-bold mb-0.5 whitespace-nowrap">实付</div><div class="text-sm font-black text-blue-600 whitespace-nowrap tracking-tight">￥{{ Number(currentOrderDetail.payAmount || 0).toFixed(2) }}</div></div>
                                <div class="text-gray-300 font-black shrink-0 mx-1">-</div>
                                <div class="flex flex-col items-center flex-1 px-0.5"><div class="text-[10px] text-gray-500 font-bold mb-0.5 whitespace-nowrap">退款</div><div class="text-sm font-black text-gray-600 whitespace-nowrap tracking-tight">￥{{ Number(returnPrice || 0).toFixed(2) }}</div></div>
                                <div class="text-gray-300 font-black shrink-0 mx-1">=</div>
                                <div class="bg-white border border-green-200 p-1.5 px-2 rounded shadow-sm text-center ml-1 shrink-0"><div class="text-[10px] text-green-600 font-black mb-0.5 whitespace-nowrap">净收</div><div class="text-base font-black text-green-500 whitespace-nowrap tracking-tighter">￥{{ Number(netIncome || 0).toFixed(2) }}</div></div>
                            </div>

                            <div class="mt-2 bg-blue-50/50 border border-blue-100 rounded-lg py-1.5 px-3 flex items-center justify-between text-[11px] shadow-sm">
                                <span class="text-blue-700 font-bold shrink-0">实付资金组成：</span>
                                <div class="flex gap-4 items-center flex-1 ml-2">
                                    <span>会员余额: <span class="font-bold text-gray-800">￥{{ Number(currentOrderDetail.balanceAmount || 0).toFixed(2) }}</span></span>

                                    <span class="flex items-center">
                                        聚合扫码: <span class="font-bold text-gray-800 ml-1">￥{{ Number(currentOrderDetail.scanAmount || 0).toFixed(2) }}</span>
                                        <el-tag
                                            v-for="(pay, index) in (currentOrderDetail.payList || []).filter(p => p.payMethodCode === 'AGGREGATE' && p.payAmount > 0)"
                                            :key="index"
                                            size="small"
                                            type="primary"
                                            effect="plain"
                                            class="ml-1 font-bold !h-[18px] !leading-[16px] !px-1.5 !text-[10px]"
                                        >
                                            {{ getPayTagName(pay.payTag) }}
                                        </el-tag>
                                    </span>

                                    <span>现金收银: <span class="font-bold text-gray-800">￥{{ Number(currentOrderDetail.cashAmount || 0).toFixed(2) }}</span></span>
                                </div>
                            </div>
                        </div>

                        <div class="font-bold text-gray-700 border-l-4 border-green-500 pl-2 mt-4">商品明细</div>
                        <el-table :data="currentOrderDetail.details" border size="small" class="w-full">
                            <el-table-column prop="goodsName" label="商品名称" show-overflow-tooltip min-width="120" />
                            <el-table-column label="成交价" width="100" align="right">
                                <template #default="{row}"><span class="font-bold text-red-500">￥{{ Number(row.goodsPrice || 0).toFixed(2) }}</span></template>
                            </el-table-column>
                            <el-table-column label="数量状态" width="80" align="center">
                                <template #default="{row}">
                                    <div class="font-bold">{{ row.quantity }}</div>
                                    <div v-if="row.returnQuantity > 0" class="text-[10px] text-red-500 font-bold">- 已退 {{ row.returnQuantity }}</div>
                                </template>
                            </el-table-column>
                            <el-table-column label="小计" width="110" align="right">
                                <template #default="{row}"><span class="font-bold">￥{{ Number(NP.times(row.quantity, row.goodsPrice)).toFixed(2) }}</span></template>
                            </el-table-column>
                            <el-table-column label="售后" width="70" align="center" fixed="right" v-if="currentOrderDetail.status !== 'RETURN' && currentOrderDetail.status !== 'REFUNDED'">
                                <template #default="{row}">
                                    <el-button type="danger" link size="small" class="font-bold" @click="handlePartialReturn(row)" :disabled="(row.returnQuantity || 0) >= row.quantity">退货</el-button>
                                </template>
                            </el-table-column>
                        </el-table>
                    </div>

                    <div class="p-3 border-t bg-gray-50 flex justify-between items-center shrink-0">
                        <div class="flex gap-3">
                            <el-button type="primary" plain @click="reprintOrder"><el-icon class="mr-1"><Printer /></el-icon> 补打小票</el-button>
                            <el-button v-if="currentOrderDetail.status !== 'RETURN' && currentOrderDetail.status !== 'REFUNDED'" type="danger" plain @click="handleFullReturn">
                                <el-icon class="mr-1"><RefreshLeft /></el-icon> 整单退款
                            </el-button>
                        </div>
                        <div class="text-right flex items-center gap-3">
                            <span class="text-sm font-bold text-gray-500 whitespace-nowrap">当前订单净收:</span>
                            <span class="text-3xl font-black text-green-600 tracking-tighter whitespace-nowrap">￥{{ Number(netIncome || 0).toFixed(2) }}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue' // 🌟 引入 onMounted
import { Search, Document, Loading, Printer, RefreshLeft, User } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { req } from "@/api/index.js"
import { ElMessage, ElMessageBox } from 'element-plus'
import dictApi from "@/api/system/dict.js" // 🌟 引入字典 API
import NP from "number-precision"

const props = defineProps(['modelValue'])
const emit = defineEmits(['update:modelValue', 'closed'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const timeRange = ref('today')
const searchKeyword = ref('')
const orderList = ref([]); const loading = ref(false); const currentPage = ref(1); const pageSize = ref(15); const totalRecords = ref(0)
const currentOrderDetail = ref(null); const detailLoading = ref(false)

const payTagDict = ref([]) // 🌟 存储字典

// 🌟 组件挂载时拉取字典
onMounted(async () => {
    try {
        const dictRes = await dictApi.loadDict(["paySubTag"])
        if (dictRes && dictRes.paySubTag) {
            payTagDict.value = dictRes.paySubTag
        }
    } catch (e) {
        console.error("字典加载失败", e)
    }
})

// 🌟 翻译标签方法
const getPayTagName = (tagCode) => {
    if (!tagCode) return '其他扫码'
    const tags = payTagDict.value
    if (!Array.isArray(tags) || tags.length === 0) return tagCode
    const match = tags.find(t => t && (t.value === tagCode || t.dictValue === tagCode))
    return match ? (match.desc || match.dictLabel || tagCode) : tagCode
}

const formatTime = (timeStr) => timeStr ? dayjs(timeStr).format('HH:mm') : ''
const formatDate = (timeStr) => timeStr ? dayjs(timeStr).format('MM-DD') : ''

const returnPrice = computed(() => {
    if (!currentOrderDetail.value || !currentOrderDetail.value.details) return 0;
    return currentOrderDetail.value.details.reduce((sum, item) => NP.plus(sum, NP.times(item.returnQuantity || 0, item.goodsPrice || 0)), 0);
})
const netIncome = computed(() => NP.minus(currentOrderDetail.value?.payAmount || 0, returnPrice.value))
const cashierName = computed(() => {
    const logs = currentOrderDetail.value?.log || [];
    return logs.length > 0 ? logs[logs.length - 1].createBy : 'System';
})

watch(visible, (newVal) => {
    if (newVal) { timeRange.value = 'today'; searchKeyword.value = ''; currentPage.value = 1; currentOrderDetail.value = null; fetchOrders() }
})

const getTimeRangeParams = () => {
    const end = dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss')
    let start = ''
    if (timeRange.value === 'today') start = dayjs().startOf('day').format('YYYY-MM-DD HH:mm:ss')
    else if (timeRange.value === '3days') start = dayjs().subtract(2, 'day').startOf('day').format('YYYY-MM-DD HH:mm:ss')
    else if (timeRange.value === '7days') start = dayjs().subtract(6, 'day').startOf('day').format('YYYY-MM-DD HH:mm:ss')
    else if (timeRange.value === '1month') start = dayjs().subtract(1, 'month').startOf('day').format('YYYY-MM-DD HH:mm:ss')
    return { startTime: start, endTime: end }
}

const fetchOrders = async () => {
    loading.value = true
    try {
        const { startTime, endTime } = getTimeRangeParams()
        const params = { current: currentPage.value, size: pageSize.value, startTime, endTime, orderNo: searchKeyword.value || undefined }
        const res = await req({ url: '/oms/order', method: 'GET', params })
        if (res.data && res.data.records) { orderList.value = res.data.records; totalRecords.value = res.data.total }
        else if (res.records) { orderList.value = res.records; totalRecords.value = res.total }
        else { orderList.value = res || []; totalRecords.value = (res || []).length }
    } catch (e) { ElMessage.error('获取订单列表失败') } finally { loading.value = false }
}

const handleSelectOrder = async (row) => {
    if (!row || !row.id) return
    detailLoading.value = true
    currentOrderDetail.value = { ...row, details: [], log: [] }

    try {
        const res = await req({ url: '/oms/order/detail', method: 'GET', params: { id: row.id } })
        const data = res.data || res || {}
        const orderInfo = data.order || {};
        currentOrderDetail.value = {
            ...row,
            ...orderInfo,
            balanceAmount: data.balanceAmount,
            scanAmount: data.scanAmount,
            cashAmount: data.cashAmount,
            details: data.orderDetail || [],
            member: data.member || {},
            log: data.orderLog || [],
            // 🌟 接收后端的支付流水数据，用于渲染标签
            payList: data.payments || []
        }
    } catch (e) { ElMessage.error('获取明细失败') } finally { detailLoading.value = false }
}

const reprintOrder = () => ElMessage.success(`指令已发送！正在补打单号：${currentOrderDetail.value.orderNo}`)

const handleFullReturn = async () => {
    try {
        await ElMessageBox.confirm('确定要将此订单【全额退款】吗？', '整单退款确认', { confirmButtonText: '确定退款', cancelButtonText: '取消', type: 'warning' })
        detailLoading.value = true
        await req({ url: '/oms/order/returnOrder', method: 'DELETE', data: [currentOrderDetail.value.id] })
        ElMessage.success('退单成功！')
        fetchOrders()
        currentOrderDetail.value.status = 'RETURN'
    } catch (e) {} finally { detailLoading.value = false }
}

const handlePartialReturn = async (row) => {
    try {
        const maxQty = row.quantity - (row.returnQuantity || 0)
        if (maxQty <= 0) return ElMessage.warning('该商品已全部退货')

        const { value } = await ElMessageBox.prompt(`请输入【${row.goodsName}】的退货数量 (最多可退 ${maxQty} 个)`, '商品退货', {
            confirmButtonText: '确定退货', cancelButtonText: '取消',
            inputPattern: /^[1-9]\d*$/, inputErrorMessage: '请输入正整数', inputValue: 1
        })
        const returnQty = parseInt(value)
        if (returnQty > maxQty) return ElMessage.error('不能超过可退数量！')

        detailLoading.value = true
        await req({ url: '/oms/order/returnGoods', method: 'DELETE', data: { id: row.id, quantity: returnQty } })
        ElMessage.success('退货成功！')
        handleSelectOrder(currentOrderDetail.value)
    } catch (e) {} finally { detailLoading.value = false }
}
</script>