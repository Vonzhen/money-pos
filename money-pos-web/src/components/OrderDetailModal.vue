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
                        <el-descriptions-item label="订单总额" label-align="center" align="center" label-class-name="whitespace-nowrap w-32"><span class="font-mono text-gray-500 line-through">￥{{ Number(currentOrderDetail.totalAmount || 0).toFixed(2) }}</span></el-descriptions-item>
                        <el-descriptions-item label="实付总计" label-align="center" align="center" label-class-name="whitespace-nowrap w-32"><span class="font-mono font-bold text-green-600 text-lg">￥{{ Number(currentOrderDetail.payAmount || 0).toFixed(2) }}</span></el-descriptions-item>

                        <el-descriptions-item label="实收 (会员余额)" label-align="center" align="center" label-class-name="whitespace-nowrap w-32"><span class="font-mono text-gray-800 font-bold">￥{{ Number(currentOrderDetail.balanceAmount || 0).toFixed(2) }}</span></el-descriptions-item>
                        <el-descriptions-item label="实收 (聚合扫码)" label-align="center" align="center" label-class-name="whitespace-nowrap w-32"><span class="font-mono text-gray-800 font-bold">￥{{ Number(currentOrderDetail.scanAmount || 0).toFixed(2) }}</span></el-descriptions-item>
                        <el-descriptions-item label="实收 (现金)" label-align="center" align="center" label-class-name="whitespace-nowrap w-32"><span class="font-mono text-gray-800 font-bold">￥{{ Number(currentOrderDetail.cashAmount || 0).toFixed(2) }}</span></el-descriptions-item>

                        <el-descriptions-item label="整单总成本" label-align="center" align="center" label-class-name="whitespace-nowrap w-32"><span class="font-mono text-blue-600">￥{{ Number(currentOrderDetail.costAmount || 0).toFixed(2) }}</span></el-descriptions-item>

                        <el-descriptions-item label="会员券抵扣" label-align="center" align="center" label-class-name="whitespace-nowrap w-32"><span class="font-mono text-red-500">- ￥{{ Number(currentOrderDetail.couponAmount || 0).toFixed(2) }}</span></el-descriptions-item>
                        <el-descriptions-item label="满减券抵扣" label-align="center" align="center" label-class-name="whitespace-nowrap w-32"><span class="font-mono text-red-500">- ￥{{ Number(currentOrderDetail.useVoucherAmount || 0).toFixed(2) }}</span></el-descriptions-item>

                        <el-descriptions-item label="整单优惠" label-align="center" align="center" label-class-name="whitespace-nowrap w-32"><span class="font-mono text-red-500">- ￥{{ Number(currentOrderDetail.manualDiscountAmount || 0).toFixed(2) }}</span></el-descriptions-item>
                        <el-descriptions-item label="售后退款" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <span class="font-mono text-gray-500">￥{{ Number(returnPrice || 0).toFixed(2) }}</span>
                        </el-descriptions-item>
                    </el-descriptions>
                </div>

                <div>
                    <div class="font-bold text-gray-800 text-base mb-2">商品物理快照</div>
                    <el-table :data="currentOrderDetail.details || []" stripe border size="small" class="w-full font-mono">
                        <el-table-column prop="goodsName" label="商品名称" min-width="150" show-overflow-tooltip />
                        <el-table-column prop="salePrice" label="吊牌单价" width="80" align="right" />
                        <el-table-column prop="goodsPrice" label="实际单价" width="80" align="right">
                            <template #default="{row}"><span class="text-green-600 font-bold">{{ Number(row.goodsPrice || 0).toFixed(2) }}</span></template>
                        </el-table-column>
                        <el-table-column prop="quantity" label="购买" width="60" align="center" />
                        <el-table-column prop="returnQuantity" label="退回" width="60" align="center">
                            <template #default="{row}">
                                <span :class="row.returnQuantity > 0 ? 'text-red-500 font-bold' : 'text-gray-400'">{{ row.returnQuantity || 0 }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="purchasePrice" label="锁定成本" width="80" align="right" />
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

// 🌟 核心修复：抽离出独立的字典请求函数
const fetchBaseData = async () => {
    try {
        const dictRes = await dictApi.loadDict(["memberType"])
        dict.value = dictRes || {}
    } catch (e) {
        console.error("会员字典加载失败", e)
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

    // 🌟 核心修复：兼容标准的 value/desc 和 若依的 dictValue/dictLabel，双重保险
    const match = types.find(m => m && (m.value === levelCode || m.dictValue === levelCode))
    return match ? (match.desc || match.dictLabel || levelCode) : levelCode
}

const returnPrice = computed(() => {
    if (!currentOrderDetail.value || !currentOrderDetail.value.details) return 0;
    return currentOrderDetail.value.details.reduce((sum, item) => sum + ((item.returnQuantity || 0) * (item.goodsPrice || 0)), 0);
})

const cashierName = computed(() => {
    const logs = currentOrderDetail.value?.log || [];
    return logs.length > 0 ? logs[logs.length - 1].createBy : 'System';
})

// 🌟 核心修复：监听单号，如果弹窗打开时发现字典丢了，强行原地拉取一次！
watch(visible, async (newVal) => {
    if (newVal && props.orderNo) {
        // 强制防呆重试机制
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
                log: data.orderLog || []
            }
        } catch (e) {
            ElMessage.error('获取底层快照失败')
        } finally {
            detailLoading.value = false
        }
    }
})
</script>