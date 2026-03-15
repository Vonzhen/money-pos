<template>
  <el-dialog v-model="visible" title="充值单据审计详情" width="500px" append-to-body destroy-on-close class="rounded-xl">
    <div v-loading="loading" class="p-2">
      <template v-if="order.orderNo">
        <div class="flex justify-between items-center mb-6">
          <div class="text-gray-400 text-xs">单据编号: <span class="font-mono text-gray-700">{{ order.orderNo }}</span></div>
          <el-tag :type="order.status === 'PAID' ? 'success' : 'info'" effect="dark" class="font-bold">
            {{ order.status === 'PAID' ? '交易成功' : '已红冲/撤销' }}
          </el-tag>
        </div>

        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="业务类型">
            <b class="text-blue-600">{{ typeMap[order.type] }}</b>
          </el-descriptions-item>
          <el-descriptions-item label="充值/发券额">
            <span class="text-lg font-black">￥{{ order.amount?.toFixed(2) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="附赠金额" v-if="order.giftCoupon > 0">
            <span class="text-orange-500 font-bold">￥{{ order.giftCoupon?.toFixed(2) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="实收现金">
            <span class="text-green-600 font-bold">￥{{ order.realAmount?.toFixed(2) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="办理时间">{{ order.createTime }}</el-descriptions-item>
          <el-descriptions-item label="备注说明">{{ order.remark || '无' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="order.status === 'PAID'" class="mt-8 border-t border-red-100 pt-4 text-center">
          <div class="text-xs text-red-400 mb-3">若此笔充值录入错误，请点击下方按钮执行红冲撤销</div>
          <el-button type="danger" plain icon="RefreshLeft" @click="handleVoid">撤销此单 (红冲)</el-button>
        </div>
      </template>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import memberApi from '@/api/ums/member.js'

const props = defineProps({ modelValue: Boolean, orderNo: String })
const emit = defineEmits(['update:modelValue', 'refresh'])

const visible = ref(false)
const loading = ref(false)
const order = ref({})
const typeMap = { 'BALANCE': '账户余额充值', 'COUPON': '会员特权券充值', 'VOUCHER': '满减优惠券发放' }

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.orderNo) fetchDetail()
})
watch(visible, (val) => emit('update:modelValue', val))

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await memberApi.getRechargeOrderDetail(props.orderNo)
    order.value = res.data || res
  } finally { loading.value = false }
}

const handleVoid = () => {
  ElMessageBox.prompt('请输入撤销原因 (必填)', '红冲确认', {
    confirmButtonText: '确定撤销',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '原因不能为空',
    type: 'warning'
  }).then(async ({ value }) => {
    await memberApi.voidRecharge({ orderNo: props.orderNo, reason: value })
    ElMessage.success('单据已撤销，资金已原路退回！')
    visible.value = false
    emit('refresh')
  })
}
</script>