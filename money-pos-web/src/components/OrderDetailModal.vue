<template>
    <el-dialog v-model="visible" title="单据审计溯源中心" width="900px" top="5vh" destroy-on-close @closed="$emit('closed')">
        <div v-loading="loading" class="min-h-[400px] flex flex-col bg-white">
            <div v-if="!currentOrderDetail" class="h-full flex flex-col items-center justify-center text-gray-400 py-10 bg-gray-50 rounded">
                <el-icon class="text-5xl mb-2"><Document /></el-icon>
                <p>未能获取到该单据明细，可能是历史遗留数据或非交易单据</p>
            </div>

            <div v-else class="h-full flex flex-col">
                <div class="bg-gray-50 p-3 border border-gray-200 rounded-t-lg flex justify-between items-center shrink-0">
                    <div>
                        <div class="font-bold text-gray-800 text-lg">单号: <span class="font-mono">{{ currentOrderDetail.orderNo }}</span></div>
                        <div class="text-xs text-gray-500 mt-1">创建时间: {{ currentOrderDetail.createTime }} | 收银员: {{ cashierName }}</div>
                    </div>
                    <el-tag effect="dark" :type="currentOrderDetail.status === 'PAID' ? 'success' : (currentOrderDetail.status === 'RETURN' || currentOrderDetail.status === 'REFUNDED' ? 'info' : 'warning')" class="tracking-widest font-bold">
                        {{ currentOrderDetail.status === 'PAID' ? '已支付' : '已退单' }}
                    </el-tag>
                </div>

                <div class="flex-1 overflow-y-auto p-4 bg-white border-x border-b border-gray-200 rounded-b-lg space-y-4">
                    <div class="border border-blue-100 rounded-lg overflow-hidden text-sm shadow-sm">
                        <div class="bg-blue-50 px-3 py-1.5 font-bold text-blue-700 border-b border-blue-100 flex items-center gap-2">
                            <el-icon><User /></el-icon> 会员身份
                        </div>
                        <div class="p-3 grid grid-cols-2 gap-y-2">
                            <div><span class="text-gray-500 mr-2">姓名:</span><span class="font-bold text-gray-800">{{ currentOrderDetail.member?.name || '散客' }}</span></div>
                            <div><span class="text-gray-500 mr-2">电话:</span><span class="font-mono text-gray-800">{{ currentOrderDetail.member?.phone || '-' }}</span></div>
                            <div class="col-span-2 flex items-start gap-2 mt-1">
                                <span class="text-gray-500 shrink-0">矩阵:</span>
                                <div class="flex flex-wrap gap-1.5">
                                    <span v-if="!currentOrderDetail.member?.brandLevels || Object.keys(currentOrderDetail.member.brandLevels).length === 0" class="font-bold text-gray-500">
                                        普通顾客
                                    </span>
                                    <template v-else>
                                        <el-tag v-for="(levelCode, brand) in currentOrderDetail.member.brandLevels" :key="brand" effect="dark" type="warning" size="small" class="font-bold tracking-widest">
                                            {{ dict?.memberTypeKv?.[levelCode] || levelCode }}
                                        </el-tag>
                                    </template>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="border border-gray-200 rounded-lg p-2.5 bg-gray-50 flex items-center justify-between overflow-x-auto shadow-inner">
                        <div class="text-center min-w-[50px]">
                            <div class="text-[11px] text-gray-500 font-bold mb-0.5">应收</div>
                            <div class="text-base font-black text-gray-800">￥{{ currentOrderDetail.totalAmount || 0 }}</div>
                        </div>
                        <div class="text-gray-300 font-black text-lg">-</div>

                        <div class="text-center min-w-[50px]">
                            <div class="text-[11px] text-orange-500 font-bold mb-0.5">会员券</div>
                            <div class="text-base font-black text-orange-500">￥{{ currentOrderDetail.couponAmount || 0 }}</div>
                        </div>
                        <div class="text-gray-300 font-black text-lg">-</div>

                        <div class="text-center min-w-[50px]">
                            <div class="text-[11px] text-red-400 font-bold mb-0.5">满减券</div>
                            <div class="text-base font-black text-red-500">￥{{ currentOrderDetail.useVoucherAmount || 0 }}</div>
                        </div>
                        <div class="text-gray-300 font-black text-lg">-</div>

                        <div class="text-center min-w-[50px]">
                            <div class="text-[11px] text-red-400 font-bold mb-0.5">整单优惠</div>
                            <div class="text-base font-black text-red-500">￥{{ currentOrderDetail.manualDiscountAmount || 0 }}</div>
                        </div>
                        <div class="text-gray-300 font-black text-lg">=</div>

                        <div class="text-center min-w-[50px]">
                            <div class="text-[11px] text-blue-600 font-bold mb-0.5">实付</div>
                            <div class="text-base font-black text-blue-600">￥{{ currentOrderDetail.payAmount || 0 }}</div>
                        </div>
                        <div class="text-gray-300 font-black text-lg">-</div>

                        <div class="text-center min-w-[50px]">
                            <div class="text-[11px] text-gray-500 font-bold mb-0.5">退款</div>
                            <div class="text-base font-black text-gray-600">￥{{ returnPrice }}</div>
                        </div>
                        <div class="text-gray-300 font-black text-lg">=</div>

                        <div class="text-center bg-white border border-green-200 p-1.5 rounded min-w-[65px] shadow-sm">
                            <div class="text-[11px] text-green-600 font-black mb-0.5">净收</div>
                            <div class="text-lg font-black text-green-500">￥{{ netIncome }}</div>
                        </div>
                    </div>

                    <div class="font-bold text-gray-700 border-l-4 border-green-500 pl-2">商品明细审计</div>
                    <el-table :data="currentOrderDetail.details" border size="small" class="w-full">
                        <el-table-column prop="goodsName" label="商品名称" show-overflow-tooltip min-width="120" />
                        <el-table-column label="系统价 -> 成交价" align="center" width="130">
                            <template #default="{row}">
                                <div class="flex items-center justify-center gap-1">
                                    <span class="text-gray-400 line-through text-[10px]">￥{{ row.salePrice }}</span>
                                    <el-icon class="text-gray-300"><Right /></el-icon>
                                    <span class="font-bold text-red-500">￥{{ row.goodsPrice }}</span>
                                </div>
                            </template>
                        </el-table-column>
                        <el-table-column label="数量状态" width="80" align="center">
                            <template #default="{row}">
                                <div class="font-bold">{{ row.quantity }}</div>
                                <div v-if="row.returnQuantity > 0" class="text-[10px] text-red-500 font-bold">-已退{{ row.returnQuantity }}</div>
                            </template>
                        </el-table-column>
                        <el-table-column label="小计(实付)" width="90" align="right">
                            <template #default="{row}">
                                <span class="font-bold">￥{{ NP.times(row.quantity, row.goodsPrice) }}</span>
                            </template>
                        </el-table-column>
                    </el-table>
                </div>
            </div>
        </div>
    </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onBeforeMount } from 'vue'
import { Document, User, Right } from '@element-plus/icons-vue'
import { req } from "@/api/index.js"
import dictApi from "@/api/system/dict.js"
import { ElMessage } from 'element-plus'
import NP from "number-precision"

const props = defineProps(['modelValue', 'orderNo'])
const emit = defineEmits(['update:modelValue', 'closed'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const loading = ref(false)
const currentOrderDetail = ref(null)

const dict = ref({})
onBeforeMount(async () => {
    dict.value = await dictApi.loadDict(["memberType"])
})

const returnPrice = computed(() => {
    if (!currentOrderDetail.value || !currentOrderDetail.value.details) return 0;
    return currentOrderDetail.value.details.reduce((sum, item) => NP.plus(sum, NP.times(item.returnQuantity || 0, item.goodsPrice || 0)), 0);
})
const netIncome = computed(() => NP.minus(currentOrderDetail.value?.payAmount || 0, returnPrice.value))
const cashierName = computed(() => {
    const logs = currentOrderDetail.value?.log || [];
    return logs.length > 0 ? logs[logs.length - 1].createBy : 'System';
})

watch(() => props.modelValue, (newVal) => {
    if (newVal && props.orderNo) {
        fetchOrderDetail(props.orderNo)
    } else {
        currentOrderDetail.value = null
    }
})

const fetchOrderDetail = async (orderNo) => {
    loading.value = true
    try {
        const res = await req({ url: '/oms/order/fullDetailByOrderNo', method: 'GET', params: { orderNo } })
        const data = res.data || res || {}
        if (data && data.order) {
            currentOrderDetail.value = {
                ...data.order,
                details: data.orderDetail || [],
                member: data.member || {},
                log: data.orderLog || []
            }
        } else {
            currentOrderDetail.value = null
            ElMessage.warning('未找到相关的业务销售单据')
        }
    } catch (e) {
        currentOrderDetail.value = null
    } finally {
        loading.value = false
    }
}
</script>