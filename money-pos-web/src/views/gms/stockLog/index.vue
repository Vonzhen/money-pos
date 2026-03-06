<template>
    <PageWrapper>
        <MoneyRR :money-crud="moneyCrud">
            <el-input v-model="moneyCrud.query.goodsName" placeholder="搜商品名称" class="md:!w-48" @keyup.enter.native="moneyCrud.doQuery" />
            <el-input v-model="moneyCrud.query.orderNo" placeholder="搜关联单号" class="md:!w-48" @keyup.enter.native="moneyCrud.doQuery" />
            <el-select v-model="moneyCrud.query.type" placeholder="过滤变动类型" clearable class="md:!w-48">
                <el-option label="🛒 销售出库 (SALE)" value="SALE" />
                <el-option label="🔄 售后退回 (RETURN)" value="RETURN" />
                <el-option label="📦 采购入库 (INBOUND)" value="INBOUND" />
                <el-option label="⚖️ 盘点校准 (CHECK)" value="CHECK" />
                <el-option label="🗑️ 报损出库 (SCRAP)" value="SCRAP" />
            </el-select>
        </MoneyRR>

        <MoneyCrudTable :money-crud="moneyCrud">
            <template #type="{scope}">
                <el-tag v-if="scope.row.type === 'SALE'" type="danger" effect="light" class="w-[100px] text-center font-bold">🛒 销售</el-tag>
                <el-tag v-else-if="scope.row.type === 'RETURN'" type="warning" effect="light" class="w-[100px] text-center font-bold">🔄 退货</el-tag>
                <el-tag v-else-if="scope.row.type === 'INBOUND'" type="success" effect="light" class="w-[100px] text-center font-bold">📦 入库</el-tag>
                <el-tag v-else-if="scope.row.type === 'CHECK'" type="primary" effect="light" class="w-[100px] text-center font-bold">⚖️ 盘点</el-tag>
                <el-tag v-else-if="scope.row.type === 'SCRAP'" type="info" effect="dark" class="w-[100px] text-center font-bold">🗑️ 报损</el-tag>
                <span v-else>{{ scope.row.type }}</span>
            </template>

            <template #quantity="{scope}">
                <span :class="scope.row.quantity > 0 ? 'text-green-600' : 'text-red-600'" class="font-black text-lg tracking-wider">
                    {{ scope.row.quantity > 0 ? '+' : '' }}{{ scope.row.quantity }}
                </span>
            </template>

            <template #afterQuantity="{scope}">
                <span class="text-gray-800 font-mono font-bold">{{ scope.row.afterQuantity }}</span>
            </template>

            <template #orderNo="{scope}">
                <el-link v-if="scope.row.orderNo" type="primary" :underline="false" @click="showDetail(scope.row)">
                    <span class="font-mono font-bold tracking-wider flex items-center gap-1 hover:text-blue-800">
                        <el-icon><Document /></el-icon>{{ scope.row.orderNo }}
                    </span>
                </el-link>
                <span v-else class="text-gray-400">-</span>
            </template>
        </MoneyCrudTable>

        <el-dialog v-model="detailVisible" :title="detailTitle" width="750px" destroy-on-close>

            <div v-if="(detailType === 'SALE' || detailType === 'RETURN') && orderSummary.order" class="mb-4 bg-gray-50 p-4 rounded-lg border border-gray-200 shadow-sm">
                <el-descriptions :column="2" size="default" border>
                    <el-descriptions-item label="👤 会员名字" label-class-name="whitespace-nowrap bg-blue-50 w-28 text-center" class-name="font-bold text-blue-600">
                        {{ orderSummary.member?.name || '散客' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="📱 会员电话" label-class-name="whitespace-nowrap bg-blue-50 w-28 text-center" class-name="font-mono">
                        {{ orderSummary.member?.phone || '-' }}
                    </el-descriptions-item>

                    <el-descriptions-item label="💰 应收总价" label-class-name="whitespace-nowrap bg-gray-100 w-28 text-center" class-name="font-bold text-gray-700 text-base">
                        ￥{{ orderSummary.order?.totalAmount || 0 }}
                    </el-descriptions-item>
                    <el-descriptions-item label="💰 最终实付" label-class-name="whitespace-nowrap bg-red-50 w-28 text-center" class-name="font-black text-red-500 text-lg">
                        ￥{{ orderSummary.order?.payAmount || 0 }}
                    </el-descriptions-item>

                    <el-descriptions-item label="🎫 各项优惠" :span="2" label-class-name="whitespace-nowrap bg-orange-50 w-28 text-center" class-name="text-orange-600 font-bold leading-relaxed">
                                            <div class="flex gap-6">
                                                <span>会员券: ￥{{ orderSummary.order?.couponAmount || 0 }}</span>
                                                <span>满减券: ￥{{ orderSummary.order?.useVoucherAmount || 0 }}</span>
                                                <span class="text-red-500">整单优惠: ￥{{ orderSummary.order?.manualDiscountAmount || 0 }}</span>
                                            </div>
                                        </el-descriptions-item>
                </el-descriptions>
            </div>

            <el-table :data="detailList" v-loading="detailLoading" stripe border size="default" class="w-full">
                <el-table-column type="index" label="#" width="50" align="center" />
                <el-table-column prop="goodsName" label="商品名称" show-overflow-tooltip>
                    <template #default="{row}"><span class="font-bold text-gray-700">{{ row.goodsName }}</span></template>
                </el-table-column>

                <el-table-column v-if="detailType === 'SALE' || detailType === 'RETURN'" prop="goodsPrice" label="交易单价" width="100" align="right">
                    <template #default="{row}">￥{{ row.goodsPrice }}</template>
                </el-table-column>

                <el-table-column prop="quantity" label="数量变动" width="120" align="center">
                    <template #default="{row}">
                        <span class="font-black text-lg" :class="row.quantity > 0 ? 'text-green-600' : 'text-red-600'">
                            {{ row.quantity > 0 ? '+' : '' }}{{ row.quantity }}
                        </span>
                    </template>
                </el-table-column>
            </el-table>
        </el-dialog>
    </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import MoneyCrud from '@/components/crud/MoneyCrud.js'
import MoneyCrudTable from "@/components/crud/MoneyCrudTable.vue";
import MoneyRR from "@/components/crud/MoneyRR.vue";
import stockLogApi from "@/api/gms/stockLog.js";
import { req } from "@/api/index.js";
import { ref } from "vue";
import { Document } from "@element-plus/icons-vue";

const columns = [
    {prop: 'createTime', label: '发生时间', width: 170},
    {prop: 'goodsName', label: '商品名称', minWidth: 160, showOverflowTooltip: true},
    {prop: 'goodsBarcode', label: '商品条码', width: 140},
    {prop: 'type', label: '变动类型', width: 130, align: 'center'},
    {prop: 'quantity', label: '变动数量', width: 110, align: 'center'},
    {prop: 'afterQuantity', label: '结余库存', width: 110, align: 'center'},
    {prop: 'orderNo', label: '关联单号', width: 190, align: 'center'},
    {prop: 'remark', label: '备注说明', minWidth: 150},
]

const moneyCrud = ref(new MoneyCrud({
    columns,
    crudMethod: stockLogApi,
    optShow: { add: false, edit: false, del: false }
}))

moneyCrud.value.init(moneyCrud)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailList = ref([])
const detailTitle = ref('')
const detailType = ref('')
const orderSummary = ref({})

const showDetail = async (row) => {
    detailVisible.value = true
    detailLoading.value = true
    detailList.value = []
    orderSummary.value = {}
    detailType.value = row.type

    const typeName = { 'SALE': '销售单', 'RETURN': '退货单', 'INBOUND': '采购入库单', 'CHECK': '盘点单', 'SCRAP': '报损单' }[row.type] || '单据'
    detailTitle.value = `📦 ${typeName}明细 - ${row.orderNo}`

    try {
        if (row.type === 'SALE' || row.type === 'RETURN') {
            const res = await req({ url: '/oms/order/fullDetailByOrderNo', method: 'GET', params: { orderNo: row.orderNo } })
            const data = res.data || res || {}

            orderSummary.value = data

            detailList.value = (data.orderDetail || []).map(item => ({
                goodsName: item.goodsName,
                goodsPrice: item.goodsPrice,
                quantity: row.type === 'SALE' ? -item.quantity : item.quantity
            }))
        } else {
            const res = await req({ url: '/gms/stockLog', method: 'GET', params: { orderNo: row.orderNo, size: 500 } })
            detailList.value = res.data?.records || []
        }
    } catch (e) {
        console.error("单据穿透失败", e)
    } finally {
        detailLoading.value = false
    }
}
</script>