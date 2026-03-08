<template>
    <PageWrapper>
        <MoneyRR :money-crud="moneyCrud">
            <el-input v-model="moneyCrud.query.phone" placeholder="搜索手机号" class="md:!w-48" @keyup.enter.native="moneyCrud.doQuery" clearable />
            <el-select v-model="moneyCrud.query.type" placeholder="资金类型" clearable class="md:!w-48">
                <el-option label="🔵 会员余额 (BALANCE)" value="BALANCE" />
                <el-option label="🟢 会员券 (COUPON)" value="COUPON" />
                <el-option label="🟠 满减券 (VOUCHER)" value="VOUCHER" />
            </el-select>
            <el-select v-model="moneyCrud.query.operateType" placeholder="业务类型" clearable class="md:!w-40">
                <el-option label="在线充值" value="RECHARGE" />
                <el-option label="消费支出" value="CONSUME" />
                <el-option label="售后退回" value="REFUND" />
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

            <template #realAmount="{scope}">
                <span v-if="scope.row.realAmount != null && scope.row.realAmount > 0" class="text-orange-600 font-bold bg-orange-50 px-2 py-0.5 rounded border border-orange-100">
                    ￥{{ scope.row.realAmount.toFixed(2) }}
                </span>
                <span v-else-if="scope.row.operateType === 'RECHARGE' && scope.row.realAmount === 0" class="text-gray-400 font-bold">
                    ￥0.00
                </span>
                <span v-else class="text-gray-300">-</span>
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

        <OrderDetailModal v-model="detailVisible" :order-no="currentSearchOrderNo" />
    </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import MoneyCrud from '@/components/crud/MoneyCrud.js'
import MoneyCrudTable from "@/components/crud/MoneyCrudTable.vue";
import MoneyRR from "@/components/crud/MoneyRR.vue";
import memberLogApi from "@/api/ums/memberLog.js";
import { ref } from "vue";
import { Document } from '@element-plus/icons-vue'

// 🌟 核心引入
import OrderDetailModal from "@/components/OrderDetailModal.vue";

// 🌟 核心修复：拓宽变动明细、实收现金、变动后余额的列宽
const columns = [
    {prop: 'createTime', label: '发生时间', width: 165},
    {prop: 'memberName', label: '会员姓名', width: 100},
    {prop: 'memberPhone', label: '手机号', width: 120},
    {prop: 'operateType', label: '业务类型', width: 100, align: 'center'},
    {prop: 'type', label: '资金账户', width: 140, align: 'center'},
    {prop: 'amount', label: '变动明细', width: 160, align: 'right'}, // 拓宽至 160
    {prop: 'realAmount', label: '实收现金', width: 120, align: 'right'}, // 拓宽至 120
    {prop: 'afterAmount', label: '变动后余额', width: 160, align: 'right'}, // 拓宽至 160
    {prop: 'orderNo', label: '关联单号', width: 180, align: 'center'},
    {prop: 'remark', label: '备注说明', minWidth: 150},
]

const moneyCrud = ref(new MoneyCrud({
    columns,
    crudMethod: memberLogApi,
    optShow: { add: false, edit: false, del: false }
}))

moneyCrud.value.init(moneyCrud)

const getTypeName = (type) => {
    const map = { 'RECHARGE': '充值', 'CONSUME': '消费', 'IMPORT': '系统导入', 'GIFT': '赠送', 'REFUND': '退货退款', 'ISSUE': '发券' }
    return map[type] || type
}
const getTagType = (type) => {
    const map = { 'RECHARGE': 'success', 'CONSUME': 'danger', 'IMPORT': 'warning', 'GIFT': 'primary', 'ISSUE': 'warning', 'REFUND': 'info' }
    return map[type] || ''
}

// 极其清爽的弹窗召唤逻辑
const detailVisible = ref(false)
const currentSearchOrderNo = ref('')

const showOrderDetail = (orderNo) => {
    currentSearchOrderNo.value = orderNo;
    detailVisible.value = true;
}
</script>