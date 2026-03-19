<template>
    <el-dialog
        v-model="visible"
        :title="`🌟 ${memberInfo.name || '加载中...'} 的全景画像`"
        width="550px"
        align-center
        destroy-on-close
        class="rounded-2xl member-profile-dialog"
    >
        <div class="px-2 pb-2" v-loading="loading">
            <el-tabs v-model="activeTab" class="custom-tabs">
                <el-tab-pane name="profile">
                    <template #label>
                        <span class="flex items-center gap-1"><el-icon><User /></el-icon> 消费画像</span>
                    </template>

                    <div class="flex flex-col gap-5 mt-2">
                        <div class="bg-gradient-to-br from-blue-50 to-indigo-50 p-5 rounded-xl border border-blue-100 shadow-sm relative overflow-hidden">
                            <div class="absolute right-[-10px] top-[-10px] opacity-10 text-6xl"><el-icon><Trophy /></el-icon></div>
                            <div class="text-sm text-gray-500 font-bold mb-1">历史累计消费金额</div>
                            <div class="text-3xl font-black text-blue-600 mb-3">￥{{ (memberInfo.consumeAmount || 0).toFixed(2) }}</div>

                            <div class="flex flex-col gap-2 text-sm text-gray-600 border-t border-blue-100/50 pt-3">
                                <div class="flex justify-between">
                                    <span>📱 手机号码:</span> <span class="font-mono font-bold">{{ memberInfo.phone || '-' }}</span>
                                </div>
                                <div class="flex justify-between">
                                    <span>💳 会员余额:</span> <span class="font-bold text-gray-800">￥{{ (memberInfo.balance || 0).toFixed(2) }}</span>
                                </div>
                                <div class="flex justify-between">
                                    <span>🎫 会员券:</span> <span class="font-bold text-orange-500">￥{{ (memberInfo.coupon || 0).toFixed(2) }}</span>
                                </div>
                                <div class="flex justify-between">
                                    <span>🎟️ 剩余满减券:</span> <span class="font-bold text-red-500">{{ memberInfo.voucherCount || 0 }} 张</span>
                                </div>

                                <div class="flex justify-between mt-2 pt-2 border-t border-blue-100/50 items-start">
                                    <span class="text-blue-500 font-bold whitespace-nowrap mt-1">特权版图:</span>
                                    <div class="flex flex-wrap gap-1 justify-end max-w-[220px]">
                                        <template v-if="memberInfo.brandLevels && Object.keys(memberInfo.brandLevels).length > 0">
                                            <el-tag v-for="(levelCode, brandId) in memberInfo.brandLevels" :key="brandId" size="small" effect="dark" type="success" class="border-0 shadow-sm">
                                                {{ getBrandName(brandId) }}: {{ getLevelName(levelCode) }}
                                            </el-tag>
                                        </template>
                                        <span v-else class="text-gray-400 border border-gray-300 border-dashed px-2 py-0.5 rounded text-xs">仅限普通零售</span>
                                    </div>
                                </div>
                                <div class="flex justify-between mt-2 pt-2 border-t border-blue-100/50">
                                    <span class="text-blue-500">最后到店时间:</span>
                                    <span class="font-bold text-blue-600">{{ memberInfo.lastVisitTime || '暂无消费记录' }}</span>
                                </div>
                            </div>
                        </div>

                        <div>
                            <h3 class="font-bold text-gray-800 mb-3 flex items-center gap-2 text-base">
                                <el-icon class="text-orange-500"><Histogram /></el-icon> 核心购物偏好 Top 20
                            </h3>
                            <el-table :data="topList" size="small" height="250" stripe border class="w-full shadow-sm rounded-lg overflow-hidden">
                                <el-table-column type="index" label="排" width="45" align="center">
                                    <template #default="scope">
                                        <span :class="{'text-red-500 font-black': scope.$index === 0, 'text-orange-500 font-bold': scope.$index === 1, 'text-yellow-500 font-bold': scope.$index === 2, 'text-gray-400': scope.$index > 2}">
                                            {{ scope.$index + 1 }}
                                        </span>
                                    </template>
                                </el-table-column>
                                <el-table-column prop="goodsName" label="商品名称" show-overflow-tooltip />
                                <el-table-column prop="buyCount" label="累计购买(件)" width="90" align="center">
                                    <template #default="{row}"><span class="font-black text-blue-600">{{ row.buyCount }}</span></template>
                                </el-table-column>
                            </el-table>
                        </div>
                    </div>
                </el-tab-pane>

                <el-tab-pane name="logs">
                    <template #label>
                        <span class="flex items-center gap-1"><el-icon><List /></el-icon> 资产变动流水</span>
                    </template>
                    <div class="mt-2">
                        <el-table :data="logList" size="small" height="420px" border stripe v-loading="logLoading">
                            <el-table-column prop="createTime" label="变动时间" width="140" align="center" />
                            <el-table-column prop="type" label="资产" width="90" align="center">
                                <template #default="{row}">
                                    <el-tag size="small" :type="getAssetTagType(row.type)">
                                        {{ getAssetTypeName(row.type) }}
                                    </el-tag>
                                </template>
                            </el-table-column>
                            <el-table-column prop="amount" label="变动金额" width="90" align="right">
                                <template #default="{row}">
                                    <span :class="row.amount > 0 ? 'text-green-600 font-bold' : 'text-red-500 font-bold'">
                                        {{ row.amount > 0 ? '+' : '' }}{{ row.amount.toFixed(2) }}
                                    </span>
                                </template>
                            </el-table-column>
                            <el-table-column prop="orderNo" label="关联单据" min-width="120">
                                <template #default="{row}">
                                    <el-link v-if="row.orderNo" type="primary" :underline="false" @click="showOrderDetail(row.orderNo)" class="font-mono text-xs">
                                        {{ row.orderNo }}
                                    </el-link>
                                    <span v-else class="text-gray-300">-</span>
                                </template>
                            </el-table-column>
                            <el-table-column prop="remark" label="摘要说明" show-overflow-tooltip />
                        </el-table>
                    </div>
                </el-tab-pane>
            </el-tabs>
        </div>

        <RechargeOrderDetail v-model="rechargeDetailVisible" :order-no="currentOrderNo" @refresh="refreshAfterVoid" />
        <OrderDetailModal v-model="salesDetailVisible" :order-no="currentOrderNo" />
    </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Trophy, User, List, Histogram } from '@element-plus/icons-vue'
import { req } from "@/api/index.js"
import RechargeOrderDetail from "@/views/ums/member/components/RechargeOrderDetail.vue"
import OrderDetailModal from "@/components/OrderDetailModal.vue"

const props = defineProps({
    modelValue: { type: Boolean, default: false },
    memberInfo: { type: Object, default: () => ({}) },
    brandsDict: { type: Object, default: () => ({}) },
    levelsDict: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(props.modelValue)
const loading = ref(false)
const logLoading = ref(false)
const activeTab = ref('profile')
const topList = ref([])
const logList = ref([])

const rechargeDetailVisible = ref(false)
const salesDetailVisible = ref(false)
const currentOrderNo = ref('')

// 🌟 新增：资产流水名称映射器
const getAssetTypeName = (type) => {
    if (type === 'BALANCE') return '会员余额';
    if (type === 'COUPON') return '会员券';
    if (type === 'VOUCHER') return '满减券';
    return type || '未知';
}

// 🌟 新增：资产流水颜色映射器
const getAssetTagType = (type) => {
    if (type === 'BALANCE') return '';         // 默认蓝色
    if (type === 'COUPON') return 'warning';   // 橙色
    if (type === 'VOUCHER') return 'danger';   // 红色
    return 'info';                             // 灰色兜底
}

watch(() => props.modelValue, (newVal) => {
    visible.value = newVal
    if (newVal && props.memberInfo.id) {
        activeTab.value = 'profile'
        fetchTopGoods(props.memberInfo.id)
    }
})

watch(activeTab, (val) => {
    if (val === 'logs') fetchLogs()
})

watch(visible, (newVal) => {
    emit('update:modelValue', newVal)
})

const fetchTopGoods = async (memberId) => {
    loading.value = true
    try {
        const res = await req({ url: '/ums/member/top10Goods', method: 'GET', params: { memberId } })
        topList.value = res.data || res || []
    } finally {
        loading.value = false
    }
}

const fetchLogs = async () => {
    if (!props.memberInfo.id) return
    logLoading.value = true
    try {
        const res = await req({ url: '/ums/member/logs', method: 'GET', params: { memberId: props.memberInfo.id } })
        logList.value = res.data || res || []
    } finally {
        logLoading.value = false
    }
}

const showOrderDetail = (orderNo) => {
    if (!orderNo) return
    currentOrderNo.value = orderNo
    if (orderNo.startsWith('RC')) {
        rechargeDetailVisible.value = true
    } else if (orderNo.startsWith('RE')) {
        salesDetailVisible.value = true
    }
}

const refreshAfterVoid = () => { fetchLogs() }
const getBrandName = (brandId) => props.brandsDict[brandId] || '未知品牌'
const getLevelName = (levelCode) => props.levelsDict[levelCode] || levelCode
</script>

<style scoped>
.member-profile-dialog :deep(.el-dialog__body) { padding-top: 10px; }
.custom-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; }
</style>