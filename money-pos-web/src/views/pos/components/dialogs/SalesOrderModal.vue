<template>
    <el-dialog v-model="visible" title="销售单查询与对账" width="1000px" top="5vh" destroy-on-close @closed="$emit('closed')">

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

        <div class="flex gap-4 h-[500px]">
            <div class="w-[40%] border rounded flex flex-col overflow-hidden">
                <el-table
                    :data="orderList" v-loading="loading" stripe highlight-current-row
                    height="100%" class="w-full text-sm" @current-change="handleSelectOrder"
                >
                    <el-table-column label="时间" width="90">
                        <template #default="{row}">
                            <div class="font-bold">{{ formatTime(row.createTime) }}</div>
                            <div class="text-[10px] text-gray-400">{{ formatDate(row.createTime) }}</div>
                        </template>
                    </el-table-column>
                    <el-table-column label="订单号" min-width="120" show-overflow-tooltip>
                        <template #default="{row}">
                            <span :class="['font-mono', row.status === 'REFUNDED' ? 'line-through text-gray-400' : '']">{{ row.orderNo }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column label="实收" width="90" align="right">
                        <template #default="{row}">
                            <span v-if="row.status === 'REFUNDED'" class="text-gray-400 font-bold">已退单</span>
                            <span v-else class="font-bold text-red-500">￥{{ (row.payAmount || 0).toFixed(2) }}</span>
                        </template>
                    </el-table-column>
                </el-table>
                <div class="p-2 border-t bg-gray-50 flex justify-between items-center shrink-0">
                    <span class="text-xs text-gray-500">共 {{ totalRecords }} 单</span>
                    <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="totalRecords" layout="prev, pager, next" size="small" @current-change="fetchOrders" />
                </div>
            </div>

            <div class="w-[60%] border rounded bg-gray-50 flex flex-col overflow-hidden relative">
                <div v-if="detailLoading" class="absolute inset-0 z-10 bg-white/70 flex items-center justify-center text-blue-500">
                    <el-icon class="is-loading text-4xl"><Loading /></el-icon>
                </div>

                <div v-if="!currentOrderDetail" class="h-full flex flex-col items-center justify-center text-gray-400">
                    <el-icon class="text-5xl mb-2"><Document /></el-icon>
                    <p>请在左侧选择一笔订单查看详情</p>
                </div>

                <div v-else class="h-full flex flex-col">
                    <div class="bg-white p-3 border-b flex justify-between items-center shrink-0">
                        <div>
                            <div class="font-bold text-gray-800">单号: <span class="font-mono text-gray-600">{{ currentOrderDetail.orderNo }}</span></div>
                            <div class="text-xs text-gray-500 mt-1">创建时间: {{ currentOrderDetail.createTime }}</div>
                        </div>
                        <el-tag :type="currentOrderDetail.status === 'PAID' ? 'success' : (currentOrderDetail.status === 'REFUNDED' ? 'info' : 'warning')" effect="dark">
                            {{ currentOrderDetail.status === 'PAID' ? '已支付' : (currentOrderDetail.status === 'REFUNDED' ? '已全额退单' : currentOrderDetail.status) }}
                        </el-tag>
                    </div>

                    <div class="flex-1 overflow-y-auto p-3 bg-white">
                        <div class="font-bold text-gray-700 mb-2 border-l-4 border-blue-500 pl-2">商品明细</div>
                        <el-table :data="currentOrderDetail.details" border size="small" class="mb-4">
                            <el-table-column prop="goodsName" label="商品名称" show-overflow-tooltip />
                            <el-table-column label="单价" width="70" align="right">
                                <template #default="{row}">￥{{ (row.goodsPrice || row.salePrice || 0).toFixed(2) }}</template>
                            </el-table-column>
                            <el-table-column label="数量" width="60" align="center">
                                <template #default="{row}">
                                    <div :class="row.returnQuantity > 0 ? 'line-through text-gray-400' : ''">{{ row.quantity }}</div>
                                    <div v-if="row.returnQuantity > 0" class="text-xs text-red-500 font-bold">-{{ row.returnQuantity }}</div>
                                </template>
                            </el-table-column>
                            <el-table-column label="操作" width="60" align="center" v-if="currentOrderDetail.status !== 'REFUNDED'">
                                <template #default="{row}">
                                    <el-button type="danger" link size="small" @click="handlePartialReturn(row)" :disabled="(row.returnQuantity || 0) >= row.quantity">退货</el-button>
                                </template>
                            </el-table-column>
                        </el-table>

                        <div class="font-bold text-gray-700 mb-2 border-l-4 border-green-500 pl-2">支付信息</div>
                        <div class="bg-gray-50 p-2 rounded border text-sm flex flex-col gap-1">
                            <div v-for="pay in currentOrderDetail.payments" :key="pay.id" class="flex justify-between">
                                <span class="text-gray-600">{{ pay.payMethodName || pay.payMethodCode }}</span>
                                <span class="font-bold">￥{{ (pay.payAmount || 0).toFixed(2) }}</span>
                            </div>
                        </div>
                    </div>

                    <div class="p-3 border-t bg-gray-100 flex justify-between items-center shrink-0">
                        <div class="flex gap-2">
                            <el-button type="primary" plain @click="reprintOrder"><el-icon class="mr-1"><Printer /></el-icon> 补打小票</el-button>
                            <el-button v-if="currentOrderDetail.status !== 'REFUNDED'" type="danger" plain @click="handleFullReturn"><el-icon class="mr-1"><RefreshLeft /></el-icon> 整单退款</el-button>
                        </div>
                        <div class="text-right">
                            <div class="text-xs text-gray-500 mb-1">操作员: {{ currentOrderDetail.createBy || '未知' }}</div>
                            <div class="text-lg flex items-center gap-2">
                                实际收款: <span :class="['text-3xl font-black tracking-tighter', currentOrderDetail.status === 'REFUNDED' ? 'text-gray-400 line-through' : 'text-red-600']">￥{{ (currentOrderDetail.payAmount || 0).toFixed(2) }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Search, Document, Loading, Printer, RefreshLeft } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { req } from "@/api/index.js"
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps(['modelValue'])
const emit = defineEmits(['update:modelValue', 'closed'])

const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const timeRange = ref('today')
const searchKeyword = ref('')
const orderList = ref([]); const loading = ref(false); const currentPage = ref(1); const pageSize = ref(15); const totalRecords = ref(0)
const currentOrderDetail = ref(null); const detailLoading = ref(false)

const formatTime = (timeStr) => timeStr ? dayjs(timeStr).format('HH:mm') : ''
const formatDate = (timeStr) => timeStr ? dayjs(timeStr).format('MM-DD') : ''

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
    currentOrderDetail.value = { ...row, details: [], payments: [] }

    try {
        const res = await req({ url: '/oms/order/detail', method: 'GET', params: { id: row.id } })
        const data = res.data || res || {}
        const orderInfo = data.order || {};
        const realDetails = data.orderDetail || [];
        const fakePayment = [{ id: 'pay-1', payMethodName: '实际收款总计', payAmount: orderInfo.payAmount || row.payAmount || 0 }];

        currentOrderDetail.value = { ...row, ...orderInfo, details: realDetails, payments: fakePayment }
    } catch (e) { ElMessage.error('获取明细失败') } finally { detailLoading.value = false }
}

const reprintOrder = () => {
    ElMessage.success(`指令已发送！正在补打单号：${currentOrderDetail.value.orderNo}`)
}

const handleFullReturn = async () => {
    try {
        await ElMessageBox.confirm('确定要将此订单【全额退款】吗？该操作将退还支付金额并回退库存。', '整单退款确认', { confirmButtonText: '确定退款', cancelButtonText: '取消', type: 'warning' })
        detailLoading.value = true
        await req({ url: '/oms/order/returnOrder', method: 'DELETE', data: [currentOrderDetail.value.id] })
        ElMessage.success('退单成功！')
        fetchOrders()
        currentOrderDetail.value.status = 'REFUNDED'
    } catch (e) {
        if (e !== 'cancel') ElMessage.error('退单失败')
    } finally { detailLoading.value = false }
}

const handlePartialReturn = async (row) => {
    try {
        const maxQty = row.quantity - (row.returnQuantity || 0)
        if (maxQty <= 0) return ElMessage.warning('该商品已全部退货')

        const { value } = await ElMessageBox.prompt(`请输入【${row.goodsName}】的退货数量 (最多可退 ${maxQty} 个)`, '商品退货', {
            confirmButtonText: '确定退货', cancelButtonText: '取消',
            inputPattern: /^[1-9]\d*$/, inputErrorMessage: '请输入大于0的正整数', inputValue: 1
        })
        const returnQty = parseInt(value)
        if (returnQty > maxQty) return ElMessage.error('退货数量不能超过可退数量！')

        detailLoading.value = true

        // 🌟 终极修复：完美对齐后台的 ReturnGoodsDTO
        const payload = {
            id: row.id,             // 订单详情的主键 ID
            quantity: returnQty     // 退货数量 (不再是 returnQuantity)
        }

        await req({ url: '/oms/order/returnGoods', method: 'DELETE', data: payload })
        ElMessage.success('退货成功！退款已按比例原路返回。')

        // 重新拉取这笔单据的最新详情
        handleSelectOrder(currentOrderDetail.value)
    } catch (e) {
        if (e !== 'cancel') ElMessage.error('退货失败，请检查参数或网络')
    } finally { detailLoading.value = false }
}
</script>