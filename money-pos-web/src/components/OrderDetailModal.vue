<template>
    <el-dialog v-model="visible" :title="`🧾 订单全局审计 - ${orderNo}`" width="1000px" top="5vh" destroy-on-close @closed="$emit('closed')">

        <div v-loading="detailLoading" class="min-h-[300px] p-2">
            <div v-if="!currentOrderDetail && !detailLoading" class="flex flex-col items-center justify-center text-gray-400 py-20">
                <el-icon class="text-5xl mb-2"><Document /></el-icon>
                <p>未能获取到该单据的底层快照明细</p>
            </div>

            <div v-else-if="currentOrderDetail" class="flex flex-col gap-6">
                <el-descriptions title="关联会员档案" :column="2" border>
                    <el-descriptions-item label="会员姓名" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">{{ currentOrderDetail.member?.name || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="联系电话" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">{{ currentOrderDetail.member?.phone || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="品牌多轨身份" :span="2" label-align="center" align="left" label-class-name="whitespace-nowrap w-32">
                        <div class="flex flex-wrap gap-2" v-if="currentOrderDetail.member?.brandLevels">
                            <el-tag
                                v-for="(levelCode, brand) in currentOrderDetail.member.brandLevels"
                                :key="brand"
                                size="default"
                                type="warning"
                                effect="light"
                                class="border-warning-300"
                            >
                                <span class="font-bold text-gray-700 mr-1">{{ getBrandName(brand) }}</span>
                                <span class="text-orange-600 font-bold">{{ getLevelName(levelCode) }}</span>
                            </el-tag>
                        </div>
                        <span v-else class="text-gray-400 font-bold">无关联身份</span>
                    </el-descriptions-item>
                </el-descriptions>

                <div>
                    <div class="flex justify-between items-center mb-2">
                        <div class="font-bold text-gray-800 text-base">资金瀑布流 (强一致性核算)</div>
                        <div class="flex items-center gap-4 text-sm">
                            <el-tag effect="light" :type="currentOrderDetail.status === 'PAID' ? 'success' : 'danger'" class="font-bold">
                                状态: {{ currentOrderDetail.status === 'PAID' ? '已支付' : (currentOrderDetail.status === 'RETURN' ? '已退单' : currentOrderDetail.status) }}
                            </el-tag>
                            <span class="text-gray-500">交易时间: {{ currentOrderDetail.paymentTime || currentOrderDetail.createTime || '-' }}</span>
                        </div>
                    </div>

                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="订单总额" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <MoneyDisplay :value="currentOrderDetail.totalAmount" custom-class="text-gray-500 line-through" />
                        </el-descriptions-item>
                        <el-descriptions-item label="实付总计" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <MoneyDisplay :value="currentOrderDetail.payAmount" size="lg" color="text-green-600" />
                        </el-descriptions-item>

                        <el-descriptions-item label="实收 (会员余额)" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <MoneyDisplay :value="currentOrderDetail.balanceAmount" color="text-gray-800" />
                        </el-descriptions-item>

                        <el-descriptions-item label="实收 (聚合扫码)" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <MoneyDisplay :value="currentOrderDetail.scanAmount" color="text-gray-800" />
                            <el-tag
                                v-for="(pay, index) in (currentOrderDetail.payList || []).filter(p => p.payMethodCode === 'AGGREGATE' && p.payAmount > 0)"
                                :key="index"
                                size="small"
                                type="primary"
                                effect="plain"
                                class="ml-2 font-bold"
                            >
                                {{ getPayTagName(pay.payTag) }}
                            </el-tag>
                        </el-descriptions-item>

                        <el-descriptions-item label="实收 (现金)" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <MoneyDisplay :value="currentOrderDetail.cashAmount" color="text-gray-800" />
                        </el-descriptions-item>

                        <el-descriptions-item label="整单总成本" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <MoneyDisplay :value="currentOrderDetail.costAmount" color="text-blue-600" />
                        </el-descriptions-item>

                        <el-descriptions-item label="会员券抵扣" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <span class="text-red-500 font-bold mr-1">-</span><MoneyDisplay :value="currentOrderDetail.couponAmount" color="text-red-500" />
                        </el-descriptions-item>
                        <el-descriptions-item label="满减券抵扣" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <span class="text-red-500 font-bold mr-1">-</span><MoneyDisplay :value="currentOrderDetail.useVoucherAmount" color="text-red-500" />
                        </el-descriptions-item>

                        <el-descriptions-item label="整单优惠" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <span class="text-red-500 font-bold mr-1">-</span><MoneyDisplay :value="currentOrderDetail.manualDiscountAmount" color="text-red-500" />
                        </el-descriptions-item>
                        <el-descriptions-item label="售后退款" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <MoneyDisplay :value="returnPrice" color="text-gray-500" />
                        </el-descriptions-item>
                    </el-descriptions>
                </div>

                <div>
                    <div class="font-bold text-gray-800 text-base mb-2">商品物理快照</div>
                    <el-table :data="currentOrderDetail.details || []" stripe border size="small" class="w-full font-mono">
                        <el-table-column prop="goodsName" label="商品名称" min-width="150" show-overflow-tooltip />

                        <el-table-column prop="salePrice" label="吊牌单价" width="100" align="right">
                            <template #default="{row}"><MoneyDisplay :value="row.salePrice" /></template>
                        </el-table-column>

                        <el-table-column prop="goodsPrice" label="实际单价" width="100" align="right">
                            <template #default="{row}"><MoneyDisplay :value="row.goodsPrice" color="text-green-600" /></template>
                        </el-table-column>

                        <el-table-column prop="quantity" label="购买" width="60" align="center" />
                        <el-table-column prop="returnQuantity" label="退回" width="60" align="center">
                            <template #default="{row}">
                                <span :class="row.returnQuantity > 0 ? 'text-red-500 font-bold' : 'text-gray-400'">{{ row.returnQuantity || 0 }}</span>
                            </template>
                        </el-table-column>

                        <el-table-column prop="purchasePrice" label="锁定成本" width="100" align="right">
                            <template #default="{row}"><MoneyDisplay :value="row.purchasePrice" /></template>
                        </el-table-column>
                    </el-table>
                </div>

                <div v-if="currentOrderDetail.log && currentOrderDetail.log.length > 0">
                    <div class="font-bold text-gray-800 text-base mb-2">防篡改操作审计日志</div>
                    <el-timeline class="pl-2 mt-2">
                        <el-timeline-item
                            v-for="(log, index) in currentOrderDetail.log"
                            :key="index"
                            :timestamp="log.createTime"
                            size="small"
                            type="info"
                        >
                            <span v-html="log.description || '-'" class="text-gray-600 text-sm"></span>
                        </el-timeline-item>
                    </el-timeline>
                </div>
            </div>
        </div>
    </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onBeforeMount } from 'vue'
import { Document } from '@element-plus/icons-vue'
import { req } from "@/api/index.js"
import dictApi from "@/api/system/dict.js"
import brandApi from "@/api/gms/brand.js"
import { ElMessage } from 'element-plus'

const props = defineProps(['modelValue', 'orderNo'])
const emit = defineEmits(['update:modelValue', 'closed'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const currentOrderDetail = ref(null)
const detailLoading = ref(false)

const dict = ref({})
const brandList = ref([])

const fetchBaseData = async () => {
    try {
        const dictRes = await dictApi.loadDict(["memberType", "paySubTag"])
        dict.value = dictRes || {}
    } catch (e) {
        console.error("字典加载失败", e)
    }
    try {
        const brandRes = await (brandApi.list ? brandApi.list({ size: 1000 }) : brandApi.getSelect())
        brandList.value = brandRes?.data?.records || brandRes?.data || brandRes?.records || brandRes || []
    } catch (e) {
        console.error("品牌字典加载失败", e)
    }
}

onBeforeMount(() => {
    fetchBaseData()
})

const getBrandName = (brandId) => {
    if (!brandId) return '未知'
    if (/[\u4e00-\u9fa5]/.test(brandId.toString())) return brandId;
    const match = brandList.value.find(b => b && b.id && b.id.toString() === brandId.toString())
    return match ? match.name : `ID:${brandId}`
}

const getLevelName = (levelCode) => {
    if (!levelCode) return '无等级'
    const types = dict.value.memberType
    if (!Array.isArray(types) || types.length === 0) return levelCode

    const match = types.find(m => m && (m.value === levelCode || m.dictValue === levelCode))
    return match ? (match.desc || match.dictLabel || levelCode) : levelCode
}

const getPayTagName = (tagCode) => {
    if (!tagCode) return '其他扫码'
    const tags = dict.value.paySubTag
    if (!Array.isArray(tags) || tags.length === 0) return tagCode

    const match = tags.find(t => t && (t.value === tagCode || t.dictValue === tagCode))
    return match ? (match.desc || match.dictLabel || tagCode) : tagCode
}

const returnPrice = computed(() => {
    if (!currentOrderDetail.value || !currentOrderDetail.value.details) return 0;
    return currentOrderDetail.value.details.reduce((sum, item) => sum + ((item.returnQuantity || 0) * (item.goodsPrice || 0)), 0);
})

const cashierName = computed(() => {
    const logs = currentOrderDetail.value?.log || [];
    return logs.length > 0 ? logs[logs.length - 1].createBy : 'System';
})

watch(visible, async (newVal) => {
    if (newVal && props.orderNo) {
        if (!dict.value.memberType || dict.value.memberType.length === 0) {
            await fetchBaseData()
        }

        currentOrderDetail.value = null;
        detailLoading.value = true;
        try {
            const res = await req({ url: '/oms/order/fullDetailByOrderNo', method: 'GET', params: { orderNo: props.orderNo } })
            const data = res?.data || res || {}
            currentOrderDetail.value = {
                ...data.order,
                balanceAmount: data.balanceAmount,
                scanAmount: data.scanAmount,
                cashAmount: data.cashAmount,
                details: data.orderDetail || [],
                member: data.member || {},
                log: data.orderLog || [],
                payList: data.payments || data.pays || data.payList || []
            }
        } catch (e) {
            ElMessage.error('获取底层快照失败')
        } finally {
            detailLoading.value = false
        }
    }
})
</script>