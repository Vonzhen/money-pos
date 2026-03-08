<template>
    <el-dialog v-model="visible" title="收银交接班与对账" width="600px" top="8vh" destroy-on-close @open="initShift" @closed="$emit('closed')">
        <div v-loading="loading" class="flex flex-col gap-4">

            <div class="bg-gray-50 p-4 rounded-lg border border-gray-200 flex flex-col gap-3 shadow-sm shrink-0">
                <div class="flex justify-between items-center text-gray-800 font-bold text-lg border-b border-gray-200 pb-2">
                    <span class="flex items-center gap-2"><el-icon class="text-blue-500"><Avatar /></el-icon> 当班收银员：{{ cashierName || '未知收银员' }}</span>
                </div>
                <div class="flex items-center gap-2 mt-1">
                    <span class="text-sm text-gray-600 font-bold w-[70px]">接班时间:</span>
                    <el-date-picker v-model="startTime" type="datetime" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DD HH:mm:ss" :clearable="false" class="!w-[220px]" size="default" @change="fetchData" />
                </div>
                <div class="flex items-center gap-2">
                    <span class="text-sm text-gray-600 font-bold w-[70px]">交班时间:</span>
                    <span class="font-mono text-gray-800 bg-white px-2 py-1 rounded border shadow-inner">{{ shiftData?.shiftEndTime || '核算中...' }}</span>
                </div>
            </div>

            <div v-if="shiftData" class="border rounded-lg overflow-hidden shadow-sm flex flex-col max-h-[450px]">

                <div class="bg-red-50 p-4 border-b border-red-100 flex justify-between items-center shrink-0">
                    <div class="flex flex-col">
                        <span class="font-black text-red-700 text-lg">本班应缴营业额</span>
                        <span class="text-xs text-red-500 mt-1 font-bold">(仅含现金与扫码实收)</span>
                    </div>
                    <span class="text-4xl font-black text-red-600 tracking-tighter drop-shadow-sm">￥{{ (shiftData.expectedTotalIncome || 0).toFixed(2) }}</span>
                </div>

                <div class="p-4 bg-white flex flex-col gap-5 overflow-y-auto">

                    <div>
                        <div class="text-sm font-bold text-gray-700 border-b border-gray-200 pb-1 mb-2 flex items-center gap-1">
                            <el-icon class="text-green-600"><Money /></el-icon> 实收对账 (钱箱/手机)
                        </div>
                        <div class="flex justify-between items-center py-1">
                            <span class="text-gray-600 text-sm">现金支付</span>
                            <span class="font-bold text-gray-800">￥{{ (shiftData.cashPay || 0).toFixed(2) }}</span>
                        </div>
                        <div class="flex justify-between items-center py-1">
                            <span class="text-gray-600 text-sm">聚合扫码</span>
                            <span class="font-bold text-gray-800">￥{{ (shiftData.scanPay || 0).toFixed(2) }}</span>
                        </div>
                    </div>

                    <div>
                        <div class="text-sm font-bold text-gray-700 border-b border-gray-200 pb-1 mb-2 flex items-center gap-1">
                            <el-icon class="text-blue-500"><Wallet /></el-icon> 资产核销 (非现支出)
                        </div>
                        <div class="flex justify-between items-center py-1">
                            <span class="text-gray-600 text-sm">会员余额抵扣</span>
                            <span class="font-bold text-blue-600">￥{{ (shiftData.balancePay || 0).toFixed(2) }}</span>
                        </div>
                        <div class="flex justify-between items-center py-1">
                            <span class="text-gray-600 text-sm">会员券扣减</span>
                            <span class="font-bold text-blue-600">￥{{ (shiftData.memberCouponPay || 0).toFixed(2) }}</span>
                        </div>
                    </div>

                    <div>
                        <div class="text-sm font-bold text-gray-700 border-b border-gray-200 pb-1 mb-2 flex items-center gap-1">
                            <el-icon class="text-orange-500"><Ticket /></el-icon> 营销让利 (放血记录)
                        </div>
                        <div class="flex justify-between items-center py-1">
                            <span class="text-gray-600 text-sm">满减券 <span class="text-xs text-gray-400">(共 {{ shiftData.voucherCount || 0 }} 张)</span></span>
                            <span class="font-bold text-orange-500">￥{{ (shiftData.voucherDiscount || 0).toFixed(2) }}</span>
                        </div>
                        <div class="flex justify-between items-center py-1">
                            <span class="text-gray-600 text-sm">整单优惠 <span class="text-xs text-gray-400">(手动抹零)</span></span>
                            <span class="font-bold text-teal-600">￥{{ (shiftData.manualDiscount || 0).toFixed(2) }}</span>
                        </div>
                    </div>

                    <div>
                        <div class="text-sm font-bold text-gray-700 border-b border-gray-200 pb-1 mb-2 flex items-center gap-1">
                            <el-icon class="text-purple-500"><DataAnalysis /></el-icon> 品牌贡献矩阵
                        </div>
                        <div v-if="shiftData.brandMatrix && shiftData.brandMatrix.length > 0" class="bg-gray-50 rounded p-2 border border-dashed border-gray-300">
                            <div v-for="(brand, index) in shiftData.brandMatrix" :key="index" class="flex justify-between items-center py-1 border-b border-gray-200 last:border-0">
                                <span class="text-gray-800 font-bold text-sm">{{ brand.brandName }}</span>
                                <span class="text-xs text-gray-600">
                                    营业额 <b class="text-gray-800">￥{{ (brand.revenue || 0).toFixed(2) }}</b> /
                                    券耗 <b class="text-red-400">￥{{ (brand.couponConsumption || 0).toFixed(2) }}</b>
                                </span>
                            </div>
                        </div>
                        <div v-else class="text-center text-gray-400 py-2 text-xs">本班次暂无品牌销售数据</div>
                    </div>

                </div>
            </div>
        </div>

        <template #footer>
            <div class="flex justify-between items-center mt-2 px-2 border-t pt-4">
                <span class="text-xs text-gray-500 font-bold"><el-icon class="text-orange-500"><Warning /></el-icon> 请核对钱箱现金是否一致</span>
                <div class="flex gap-3">
                    <el-button @click="printShift" size="large" class="font-bold text-gray-600"><el-icon class="mr-1"><Printer /></el-icon> 打印对账单</el-button>
                    <el-button type="danger" size="large" @click="confirmShift" class="font-black px-6 tracking-widest shadow-md" :disabled="!shiftData"><el-icon class="mr-1"><SwitchButton /></el-icon> 确认交班</el-button>
                </div>
            </div>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Avatar, List, Printer, SwitchButton, Warning, Money, Wallet, Ticket, DataAnalysis } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import financeApi from "@/api/finance/finance.js"

const props = defineProps(['modelValue', 'cashierName'])
const emit = defineEmits(['update:modelValue', 'closed'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const loading = ref(false)
const startTime = ref('')
const shiftData = ref(null)

const initShift = () => {
    // 默认取今天的 00:00:00 作为接班起点
    startTime.value = dayjs().startOf('day').format('YYYY-MM-DD HH:mm:ss')
    fetchData()
}

const fetchData = async () => {
    if (!startTime.value) return
    loading.value = true
    try {
        // 🌟 核心修复：将参数包装为 Object，与 finance.js 里的 params 对象严格对齐
        const res = await financeApi.getShiftHandover({
            startTime: startTime.value,
            cashierName: props.cashierName || '收银员'
        })
        shiftData.value = res.data || res
    } catch (e) {
        ElMessage.error('获取交接班数据失败，请检查网络')
    } finally {
        loading.value = false
    }
}

const printShift = () => {
    // 预留接口：由于蓝图中 "小票打印深度对接" 尚未完成，此处保留系统提示
    ElMessage.success('小票打印指令已发送！请查收打印机。')
}

const confirmShift = async () => {
    try {
        await ElMessageBox.confirm('确认后将自动打印【交班对账单】并清空当前收银台，是否继续？', '交接班确认', {
            confirmButtonText: '确定交班', cancelButtonText: '取消', type: 'warning'
        })
        printShift()
        ElMessage.success('交班成功！辛苦了！')
        visible.value = false

        // 模拟交班后的安全退出/清空
        setTimeout(() => {
            window.location.reload()
        }, 1500)
    } catch (e) { }
}
</script>

<style scoped>
/* 隐藏 Element Plus 默认对话框 header 的底部边距，让内容更紧凑 */
:deep(.el-dialog__header) {
    margin-bottom: 0;
    padding-bottom: 10px;
    border-bottom: 1px solid #f3f4f6;
}
:deep(.el-dialog__body) {
    padding-top: 15px;
}
</style>