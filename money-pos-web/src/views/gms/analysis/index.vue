<template>
    <PageWrapper>
        <div class="p-6 bg-gray-50 min-h-full flex flex-col min-w-[800px]">
            <div class="flex items-center justify-between mb-6 shrink-0">
                <div>
                    <h2 class="text-2xl font-bold text-gray-800 flex items-center">
                        <el-icon class="mr-2 text-indigo-600"><DataBoard /></el-icon> 进销存财报与盈亏审计
                    </h2>
                    <p class="text-sm text-gray-500 mt-1">全局追踪商品流转轨迹，自动核算因报损、盘亏导致的资产流失金额</p>
                </div>
            </div>

            <div class="bg-white p-4 rounded-lg shadow-sm border border-gray-100 mb-4 flex justify-between items-center shrink-0">
                <div class="flex items-center gap-4">
                    <SmartGoodsSelector
                        v-model="keyword"
                        mode="report"
                        placeholder="检索商品名称 / 条码 / 拼音"
                        class="!w-64"
                        @search="fetchData"
                    />

                    <el-date-picker
                        v-model="dateRange"
                        type="daterange"
                        range-separator="至"
                        start-placeholder="回溯开始日期"
                        end-placeholder="回溯结束日期"
                        value-format="YYYY-MM-DD"
                        :shortcuts="shortcuts"
                        @change="fetchData"
                        class="!w-[300px]"
                    />

                    <el-button type="primary" @click="fetchData" :loading="loading" class="font-bold tracking-widest px-6">
                        <el-icon class="mr-1"><Aim /></el-icon> 执行深度审计
                    </el-button>
                </div>

                <el-button type="success" @click="handleExport" :loading="exportLoading" class="font-bold">
                    <el-icon class="mr-1"><Download /></el-icon> 导出 Excel
                </el-button>
            </div>

            <div class="bg-white rounded-lg shadow-sm border border-gray-100 p-2 flex-1 flex flex-col min-h-[500px] min-w-0 overflow-hidden">
                <el-table
                    :data="tableData"
                    v-loading="loading"
                    stripe
                    border
                    height="100%"
                    class="w-full text-sm font-mono custom-header-table"
                    show-summary
                    :summary-method="getSummaries"
                >
                    <el-table-column label="商品基础档案" align="center" fixed="left">
                        <el-table-column prop="goodsName" label="审计标的物 (商品)" width="180" show-overflow-tooltip>
                            <template #default="{row}"><span class="font-bold text-gray-800">{{ row.goodsName }}</span></template>
                        </el-table-column>
                        <el-table-column prop="goodsBarcode" label="国际条码" width="130" align="center" />
                    </el-table-column>

                    <el-table-column label="📥 资产入库阵营" align="center">
                        <el-table-column prop="inboundQty" label="采购入库" width="110" align="center" sortable>
                            <template #default="{row}">
                                <span v-if="row.inboundQty > 0" class="text-blue-600 font-bold">+{{ row.inboundQty }}</span>
                                <span v-else class="text-gray-300">-</span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="returnQty" label="售后退回" width="110" align="center" sortable>
                            <template #default="{row}">
                                <span v-if="row.returnQty > 0" class="text-orange-500 font-bold">+{{ row.returnQty }}</span>
                                <span v-else class="text-gray-300">-</span>
                            </template>
                        </el-table-column>
                    </el-table-column>

                    <el-table-column label="🛒 业务出库" align="center">
                        <el-table-column prop="saleQty" label="销售卖出" width="110" align="center" sortable>
                            <template #default="{row}">
                                <span v-if="row.saleQty > 0" class="text-green-600 font-black">-{{ row.saleQty }}</span>
                                <span v-else class="text-gray-300">-</span>
                            </template>
                        </el-table-column>
                    </el-table-column>

                    <el-table-column label="🚨 风控与非正常损耗" align="center">
                        <el-table-column prop="scrapQty" label="报损销毁" width="110" align="center" sortable>
                            <template #default="{row}">
                                <span v-if="row.scrapQty > 0" class="text-red-500 font-bold bg-red-50 px-2 py-1 rounded">-{{ row.scrapQty }}</span>
                                <span v-else class="text-gray-300">-</span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="checkQty" label="盘点盈亏" width="110" align="center" sortable>
                            <template #default="{row}">
                                <span v-if="row.checkQty > 0" class="text-blue-500 font-bold">+{{ row.checkQty }} (盈)</span>
                                <span v-else-if="row.checkQty < 0" class="text-red-600 font-black">{{ row.checkQty }} (亏)</span>
                                <span v-else class="text-gray-300">-</span>
                            </template>
                        </el-table-column>
                    </el-table-column>

                    <el-table-column label="💰 财务核算指标" align="center">
                        <el-table-column prop="netChangeQty" label="期间净变动" width="120" align="center" sortable>
                            <template #default="{row}">
                                <span class="font-bold text-gray-700 text-base" :class="row.netChangeQty < 0 ? 'text-red-500' : ''">
                                    {{ row.netChangeQty > 0 ? '+' : '' }}{{ row.netChangeQty }} 件
                                </span>
                            </template>
                        </el-table-column>

                        <el-table-column prop="purchasePrice" label="进货成本价" width="120" align="right">
                            <template #default="{row}"><span class="text-gray-500">¥ {{ formatMoney(row.purchasePrice) }}</span></template>
                        </el-table-column>

                        <el-table-column prop="lossAmount" label="资产流失总额" min-width="140" align="right" sortable>
                            <template #default="{row}">
                                <span v-if="row.lossAmount > 0" class="font-black text-red-600 text-lg tracking-wider">
                                    - ¥ {{ formatMoney(row.lossAmount) }}
                                </span>
                                <span v-else class="text-gray-300">安全</span>
                            </template>
                        </el-table-column>
                    </el-table-column>
                </el-table>
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import { req } from "@/api/index.js"
import { DataBoard, Aim, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'

// 🌟 引入刚修复好的智能搜索组件
import SmartGoodsSelector from '@/components/SmartGoodsSelector.vue'

const keyword = ref('')
const dateRange = ref([
    dayjs().startOf('month').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD')
])
const loading = ref(false)
const exportLoading = ref(false)
const tableData = ref([])

const shortcuts = [
    { text: '最近7天', value: () => [dayjs().subtract(6, 'day').toDate(), dayjs().toDate()] },
    { text: '本月至今', value: () => [dayjs().startOf('month').toDate(), dayjs().toDate()] },
    { text: '上个月', value: () => [dayjs().subtract(1, 'month').startOf('month').toDate(), dayjs().subtract(1, 'month').endOf('month').toDate()] }
]

const fetchData = async () => {
    loading.value = true
    try {
        const [startDate, endDate] = dateRange.value || ['', '']
        const res = await req({
            url: '/gms/analysis/report',
            method: 'GET',
            params: { startDate, endDate, keyword: keyword.value }
        })
        tableData.value = res?.data || res || []
    } catch (error) {
        ElMessage.error("获取进销存报表失败")
    } finally {
        loading.value = false
    }
}

// 🌟 Excel 导出逻辑
const handleExport = async () => {
    try {
        exportLoading.value = true
        const [startDate, endDate] = dateRange.value || ['', '']

        const res = await req({
            url: '/gms/analysis/export',
            method: 'GET',
            params: { startDate, endDate, keyword: keyword.value },
            responseType: 'blob'
        })

        const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const link = document.createElement('a')
        link.href = window.URL.createObjectURL(blob)
        link.download = `进销存盈亏财报_${dayjs().format('YYYYMMDD')}.xlsx`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(link.href)

        ElMessage.success('Excel 报表生成成功')
    } catch (error) {
        ElMessage.error("导出失败，请检查网络或后端配置")
    } finally {
        exportLoading.value = false
    }
}

const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00'
    return Number(val).toFixed(2)
}

const getSummaries = (param) => {
    const { columns, data } = param;
    const sums = [];
    columns.forEach((column, index) => {
        if (index === 0) { sums[index] = '全盘总计'; return; }
        if (column.property === 'goodsBarcode' || column.property === 'purchasePrice') { sums[index] = '-'; return; }

        const values = data.map(item => Number(item[column.property]));
        if (!values.every(value => isNaN(value))) {
            const total = values.reduce((prev, curr) => {
                const value = Number(curr);
                return !isNaN(value) ? prev + curr : prev;
            }, 0);

            if (column.property === 'lossAmount') {
                sums[index] = total > 0 ? '- ¥ ' + total.toFixed(2) : '¥ 0.00';
            } else {
                sums[index] = total;
            }
        } else {
            sums[index] = '';
        }
    });
    return sums;
}

onMounted(() => {
    fetchData()
})
</script>

<style scoped>
:deep(.custom-header-table th.el-table__cell > .cell) {
    white-space: nowrap !important;
    word-break: keep-all !important;
    padding-left: 8px;
    padding-right: 8px;
}

:deep(.el-table__footer-wrapper tbody td.el-table__cell) {
    background-color: #fff5f5;
    color: #c53030;
    font-weight: 900;
    font-size: 1.1em;
}
</style>