<template>
    <PageWrapper>
        <div class="p-6 bg-gray-50 min-h-full">
            <div class="flex items-center justify-between mb-6">
                <div>
                    <h2 class="text-2xl font-bold text-red-600 flex items-center">
                        <el-icon class="mr-2"><WarnTriangleFilled /></el-icon> 门店收银防损风控雷达
                    </h2>
                    <p class="text-sm text-gray-500 mt-1">自动追踪跑冒滴漏、私自改价放水、违规退单套现等高危操作</p>
                </div>
                <div class="flex gap-4">
                    <el-date-picker
                        v-model="dateRange"
                        type="daterange"
                        range-separator="至"
                        start-placeholder="开始日期"
                        end-placeholder="结束日期"
                        value-format="YYYY-MM-DD"
                        :shortcuts="shortcuts"
                        @change="fetchData"
                        class="!w-[300px]"
                    />
                    <el-button type="danger" @click="fetchData" :loading="loading">
                        <el-icon class="mr-1"><Aim /></el-icon> 执行雷达扫描
                    </el-button>
                </div>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-6">
                <el-card shadow="hover" class="border-l-4 border-l-red-500 bg-red-50/50">
                    <div class="text-sm text-red-800 font-bold mb-1">拦截高危异常单数</div>
                    <div class="text-3xl font-mono font-black text-red-600">{{ data.abnormalOrderCount }} <span class="text-sm">笔</span></div>
                </el-card>

                <el-card shadow="hover" class="border-l-4 border-l-orange-500 bg-orange-50/50">
                    <div class="text-sm text-orange-800 font-bold mb-1">倒挂亏本直接损失</div>
                    <div class="text-3xl font-mono font-black text-orange-600">¥ {{ formatMoney(data.totalLossAmount) }}</div>
                </el-card>

                <el-card shadow="hover" class="border-l-4 border-l-purple-500 bg-purple-50/50">
                    <div class="text-sm text-purple-800 font-bold mb-1">收银手工改价让利总额</div>
                    <div class="text-3xl font-mono font-black text-purple-600">¥ {{ formatMoney(data.totalManualDiscount) }}</div>
                </el-card>

                <el-card shadow="hover" class="border-l-4 border-l-gray-500 bg-gray-100">
                    <div class="text-sm text-gray-800 font-bold mb-1">发生售后退单总频次</div>
                    <div class="text-3xl font-mono font-black text-gray-700">{{ data.totalRefundCount }} <span class="text-sm">次</span></div>
                </el-card>
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <el-card shadow="hover" class="rounded-lg border border-red-100" header="🚨 重点盯防：收银操作黑榜">
                    <el-table :data="data.cashierRiskList" height="500" stripe style="width: 100%" v-loading="loading" size="small">
                        <el-table-column prop="cashierName" label="收银工号" min-width="100">
                            <template #default="{row}"><span class="font-bold">{{ row.cashierName }}</span></template>
                        </el-table-column>
                        <el-table-column prop="orderCount" label="经手单数" width="80" align="center" />
                        <el-table-column prop="refundCount" label="退单数" width="70" align="center">
                            <template #default="{row}">
                                <span :class="row.refundCount > 5 ? 'text-red-500 font-bold' : ''">{{ row.refundCount }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="manualDiscountAmount" label="私改让利" width="90" align="right">
                            <template #default="{row}">
                                <span class="font-bold text-purple-600">¥{{ formatMoney(row.manualDiscountAmount) }}</span>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-card>

                <el-card shadow="hover" class="lg:col-span-2 rounded-lg border border-orange-100" header="⚠️ 高危亏单/异常单据抓拍表">
                    <el-table :data="data.recentAbnormalOrders" height="500" stripe style="width: 100%" v-loading="loading">
                        <el-table-column prop="createTime" label="发生时间" width="120" />

                        <el-table-column prop="cashier" label="操作员" min-width="90" show-overflow-tooltip />

                        <el-table-column prop="riskType" label="风控标签" width="130" align="center">
                            <template #default="{row}">
                                <el-tag :type="row.riskType.includes('亏损') ? 'danger' : 'warning'" effect="dark" size="small" class="font-bold">
                                    {{ row.riskType }}
                                </el-tag>
                            </template>
                        </el-table-column>

                        <el-table-column prop="payAmount" label="实收金额" width="120" align="right">
                            <template #default="{row}"><span class="font-bold text-gray-800">¥{{ formatMoney(row.payAmount) }}</span></template>
                        </el-table-column>

                        <el-table-column prop="profit" label="产生毛利" width="120" align="right">
                            <template #default="{row}">
                                <span :class="row.profit < 0 ? 'text-red-600 font-black' : 'text-green-600 font-bold'">
                                    ¥{{ formatMoney(row.profit) }}
                                </span>
                            </template>
                        </el-table-column>

                        <el-table-column label="底稿穿透" width="100" align="center" fixed="right">
                            <template #default="{row}">
                                <el-button type="primary" link class="font-bold tracking-widest" @click="openAudit(row.orderNo)">
                                    审查 <el-icon class="ml-1"><Search /></el-icon>
                                </el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-card>
            </div>
        </div>

        <OrderDetailModal v-model="auditVisible" :order-no="currentAuditOrderNo" />
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import { req } from "@/api/index.js"
import { WarnTriangleFilled, Aim, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'

// 导入统一审计底稿组件
import OrderDetailModal from "@/components/OrderDetailModal.vue"

const dateRange = ref([
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD')
])
const loading = ref(false)

const shortcuts = [
    { text: '最近7天', value: () => [dayjs().subtract(6, 'day').toDate(), dayjs().toDate()] },
    { text: '最近30天', value: () => [dayjs().subtract(29, 'day').toDate(), dayjs().toDate()] },
    { text: '本月', value: () => [dayjs().startOf('month').toDate(), dayjs().endOf('month').toDate()] }
]

const data = ref({
    abnormalOrderCount: 0, totalLossAmount: 0, totalManualDiscount: 0, totalRefundCount: 0,
    cashierRiskList: [], recentAbnormalOrders: []
})

// 审计穿透状态
const auditVisible = ref(false)
const currentAuditOrderNo = ref('')

const fetchData = async () => {
    loading.value = true
    try {
        const [startDate, endDate] = dateRange.value || ['', '']
        const res = await req({ url: '/finance/risk-control', method: 'GET', params: { startDate, endDate } })
        data.value = res?.data || res || {}
    } catch (error) {
        ElMessage.error("雷达扫描失败，请检查网络")
    } finally {
        loading.value = false
    }
}

const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00'
    return Number(val).toFixed(2)
}

const openAudit = (orderNo) => {
    currentAuditOrderNo.value = orderNo
    auditVisible.value = true
}

onMounted(() => {
    fetchData()
})
</script>