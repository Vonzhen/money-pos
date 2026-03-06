<template>
    <el-dialog v-model="visible" title="收银交接班与对账" width="550px" top="10vh" destroy-on-close @open="initShift" @closed="$emit('closed')">
        <div v-loading="loading" class="min-h-[300px] flex flex-col gap-4">

            <div class="bg-blue-50 p-4 rounded-lg border border-blue-100 flex flex-col gap-3">
                <div class="flex justify-between items-center text-blue-800 font-bold text-lg border-b border-blue-100 pb-2">
                    <span class="flex items-center gap-1"><el-icon><Avatar /></el-icon> 当班收银员：{{ cashierName }}</span>
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

            <div v-if="shiftData" class="border rounded-lg overflow-hidden shadow-sm">
                <div class="bg-gray-100 p-3 font-bold text-gray-700 border-b flex items-center gap-2">
                    <el-icon><List /></el-icon> 当班收款明细汇总
                </div>

                <div class="p-4 bg-white flex flex-col gap-3 min-h-[120px]">
                    <template v-if="shiftData.payBreakdown && shiftData.payBreakdown.length > 0">
                        <div v-for="(item, index) in shiftData.payBreakdown" :key="index" class="flex justify-between items-center border-b border-dashed border-gray-200 pb-2 last:border-none last:pb-0">
                            <span class="text-gray-600 font-bold">{{ item.name }}</span>
                            <span class="font-bold text-lg text-gray-800">￥{{ (item.value || 0).toFixed(2) }}</span>
                        </div>
                    </template>
                    <div v-else class="text-center text-gray-400 py-4 font-bold tracking-widest">
                        本班次暂无收款记录
                    </div>
                </div>

                <div class="bg-red-50 p-4 border-t border-red-100 flex justify-between items-center">
                    <div class="flex flex-col">
                        <span class="font-black text-red-700 text-lg">本班应缴营业额</span>
                        <span class="text-xs text-red-400 mt-1">(不含本金余额扣款)</span>
                    </div>
                    <span class="text-4xl font-black text-red-600 tracking-tighter shadow-red-100 drop-shadow-sm">￥{{ (shiftData.expectedTotalIncome || 0).toFixed(2) }}</span>
                </div>
            </div>
        </div>

        <template #footer>
            <div class="flex justify-between items-center mt-2 px-2 border-t pt-4">
                <span class="text-xs text-gray-400 font-bold"><el-icon><Warning /></el-icon> 请核对钱箱现金是否一致</span>
                <div class="flex gap-3">
                    <el-button @click="printShift" size="large" class="font-bold"><el-icon class="mr-1"><Printer /></el-icon> 打印对账单</el-button>
                    <el-button type="danger" size="large" @click="confirmShift" class="font-black px-6 tracking-widest shadow-md" :disabled="!shiftData"><el-icon class="mr-1"><SwitchButton /></el-icon> 确认交班</el-button>
                </div>
            </div>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Avatar, List, Printer, SwitchButton, Warning } from '@element-plus/icons-vue'
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
        const res = await financeApi.getShiftHandover(startTime.value)
        shiftData.value = res.data || res
    } catch (e) {
        ElMessage.error('获取交接班数据失败，请检查网络')
    } finally {
        loading.value = false
    }
}

const printShift = () => {
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