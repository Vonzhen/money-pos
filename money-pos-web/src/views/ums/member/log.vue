<template>
    <PageWrapper>
        <MoneyRR :money-crud="moneyCrud">
            <el-input v-model="moneyCrud.query.phone" placeholder="搜索手机号" class="md:!w-48" @keyup.enter.native="moneyCrud.doQuery" />
            <el-select v-model="moneyCrud.query.type" placeholder="资金类型" clearable class="md:!w-48">
                <el-option label="🔵 会员余额 (BALANCE)" value="BALANCE" />
                <el-option label="🟢 会员储值券 (COUPON)" value="COUPON" />
                <el-option label="🟠 满减优惠券 (VOUCHER)" value="VOUCHER" />
            </el-select>
            <el-select v-model="moneyCrud.query.operateType" placeholder="业务类型" clearable class="md:!w-40">
                <el-option label="在线充值" value="RECHARGE" />
                <el-option label="消费支出" value="CONSUME" />
                <el-option label="系统导入" value="IMPORT" />
                <el-option label="充值赠送" value="GIFT" />
                <el-option label="活动发放" value="ISSUE" />
            </el-select>
        </MoneyRR>

        <MoneyCrudTable :money-crud="moneyCrud">
            <template #operateType="{scope}">
                <el-tag :type="getTagType(scope.row.operateType)" effect="plain">{{ getTypeName(scope.row.operateType) }}</el-tag>
            </template>

            <template #type="{scope}">
                <el-tag v-if="scope.row.type === 'BALANCE'" type="primary" effect="light" class="w-[110px] text-center tracking-widest font-bold">🔵 会员余额</el-tag>
                <el-tag v-else-if="scope.row.type === 'COUPON'" type="success" effect="light" class="w-[110px] text-center tracking-widest font-bold">🟢 会员券</el-tag>
                <el-tag v-else-if="scope.row.type === 'VOUCHER'" type="warning" effect="light" class="w-[110px] text-center tracking-widest font-bold">🟠 满减券</el-tag>
                <span v-else>{{ scope.row.type }}</span>
            </template>

            <template #amount="{scope}">
                <span :class="scope.row.amount > 0 ? 'text-green-600' : 'text-red-600'" class="font-black text-base tracking-wider">
                    {{ scope.row.amount > 0 ? '+' : '' }}
                    {{ scope.row.type === 'VOUCHER' ? Math.floor(scope.row.amount) + ' 张' : scope.row.amount.toFixed(2) + ' 元' }}
                </span>
            </template>

            <template #afterAmount="{scope}">
                <span class="text-gray-800 font-mono font-bold">
                    {{ scope.row.type === 'VOUCHER' ? Math.floor(scope.row.afterAmount) + ' 张' : '￥ ' + scope.row.afterAmount.toFixed(2) }}
                </span>
            </template>

            <template #orderNo="{scope}">
                <el-link v-if="scope.row.orderNo" type="primary" :underline="false" @click="showOrderDetail(scope.row.orderNo)">
                    <span class="font-mono font-bold tracking-wider text-blue-600 hover:text-blue-800 flex items-center gap-1">
                        <el-icon><Document /></el-icon>{{ scope.row.orderNo }}
                    </span>
                </el-link>
                <span v-else class="text-gray-300">-</span>
            </template>
        </MoneyCrudTable>

        <el-dialog v-model="detailVisible" :title="`📦 订单凭证溯源 - ${currentSearchOrderNo}`" width="750px" destroy-on-close>

            <div v-if="orderSummary.order" class="mb-4 bg-gray-50 p-4 rounded-lg border border-gray-200 shadow-sm">
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

            <el-table :data="orderDetailList" border stripe size="default" v-loading="detailLoading" class="w-full">
                <el-table-column type="index" label="#" width="50" align="center" />
                <el-table-column prop="goodsName" label="商品名称" min-width="160" show-overflow-tooltip>
                    <template #default="{row}"><span class="font-bold text-gray-700">{{ row.goodsName }}</span></template>
                </el-table-column>
                <el-table-column prop="salePrice" label="零售价" width="90" align="right">
                    <template #default="{row}"><span class="text-gray-400 line-through">￥{{ row.salePrice }}</span></template>
                </el-table-column>
                <el-table-column prop="goodsPrice" label="实收单价" width="100" align="right">
                    <template #default="{row}"><span class="font-bold text-red-500">￥{{ row.goodsPrice }}</span></template>
                </el-table-column>
                <el-table-column prop="quantity" label="数量" width="80" align="center">
                    <template #default="{row}">
                        <span :class="row.quantity > 0 ? 'text-green-600' : 'text-red-600'" class="font-black text-lg">
                            {{ row.quantity > 0 ? '+' : '' }}{{ row.quantity }}
                        </span>
                    </template>
                </el-table-column>
                <el-table-column label="单品小计" width="110" align="right">
                    <template #default="{row}">
                        <span class="font-bold text-gray-800">￥{{ (row.goodsPrice * row.quantity).toFixed(2) }}</span>
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
import memberLogApi from "@/api/ums/memberLog.js";
import { req } from "@/api/index.js";
import { ref } from "vue";
import { ElMessage } from 'element-plus'
import { Document } from '@element-plus/icons-vue'

const columns = [
    {prop: 'createTime', label: '发生时间', width: 170},
    {prop: 'memberName', label: '会员姓名', width: 110},
    {prop: 'memberPhone', label: '手机号', width: 130},
    {prop: 'operateType', label: '业务类型', width: 110, align: 'center'},
    {prop: 'type', label: '资金账户', width: 150, align: 'center'},
    {prop: 'amount', label: '变动明细', width: 140, align: 'right'},
    {prop: 'afterAmount', label: '变动后余额', width: 140, align: 'right'},
    {prop: 'orderNo', label: '关联单号', width: 190, align: 'center'},
    {prop: 'remark', label: '备注说明', minWidth: 150},
]

const moneyCrud = ref(new MoneyCrud({
    columns,
    crudMethod: memberLogApi,
    optShow: { add: false, edit: false, del: false }
}))

moneyCrud.value.init(moneyCrud)

const getTypeName = (type) => {
    const map = { 'RECHARGE': '充值', 'CONSUME': '消费', 'IMPORT': '系统导入', 'GIFT': '赠送', 'REFUND': '退款', 'ISSUE': '发券' }
    return map[type] || type
}
const getTagType = (type) => {
    const map = { 'RECHARGE': 'success', 'CONSUME': 'danger', 'IMPORT': 'warning', 'GIFT': 'primary', 'ISSUE': 'warning' }
    return map[type] || ''
}

// ==================== 🌟 终极溯源弹窗逻辑 ====================
const detailVisible = ref(false)
const detailLoading = ref(false)
const orderSummary = ref({})
const orderDetailList = ref([])
const currentSearchOrderNo = ref('')

const showOrderDetail = async (orderNo) => {
    currentSearchOrderNo.value = orderNo;
    detailVisible.value = true;
    detailLoading.value = true;

    // 每次打开先清空，防止数据残留
    orderSummary.value = {};
    orderDetailList.value = [];

    try {
        // 🌟 统一调用我们在 OmsOrderController 里写好的“上帝视角”接口！
        const res = await req({ url: '/oms/order/fullDetailByOrderNo', method: 'GET', params: { orderNo: orderNo } });
        const data = res.data || res || {};

        if (!data.order) {
            ElMessage.info('未找到业务单据，可能是早期的纯充值操作');
            return;
        }

        orderSummary.value = data;
        orderDetailList.value = data.orderDetail || [];
    } catch (e) {
        console.error(e)
        ElMessage.error('获取订单明细失败，请检查网络');
    } finally {
        detailLoading.value = false;
    }
}
</script>