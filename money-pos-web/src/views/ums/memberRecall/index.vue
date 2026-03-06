<template>
    <div class="p-4 bg-gray-50 min-h-screen">
        <div class="mb-6 bg-white p-5 rounded-xl shadow-sm border border-gray-100">
            <h2 class="text-xl font-black text-gray-800 mb-4 flex items-center gap-2">
                <el-icon class="text-blue-600 text-2xl"><Aim /></el-icon> 沉睡会员精准雷达
            </h2>
            <div class="flex gap-4">
                <div @click="scanDormant(30)" :class="['cursor-pointer flex-1 p-4 rounded-lg border-2 transition-all', currentDays === 30 ? 'border-orange-500 bg-orange-50' : 'border-gray-100 hover:border-orange-300']">
                    <div class="text-2xl mb-1">⚠️</div><div class="font-bold text-gray-700">30天未到店</div><div class="text-xs text-gray-400">流失风险极高，建议立即触达</div>
                </div>
                <div @click="scanDormant(90)" :class="['cursor-pointer flex-1 p-4 rounded-lg border-2 transition-all', currentDays === 90 ? 'border-blue-500 bg-blue-50' : 'border-gray-100 hover:border-blue-300']">
                    <div class="text-2xl mb-1">💤</div><div class="font-bold text-gray-700">3个月未到店</div><div class="text-xs text-gray-400">已形成睡眠，需要大额券刺激</div>
                </div>
                <div @click="scanDormant(180)" :class="['cursor-pointer flex-1 p-4 rounded-lg border-2 transition-all', currentDays === 180 ? 'border-indigo-500 bg-indigo-50' : 'border-gray-100 hover:border-indigo-300']">
                    <div class="text-2xl mb-1">🛌</div><div class="font-bold text-gray-700">半年未到店</div><div class="text-xs text-gray-400">深度睡眠，大概率已被竞品抢走</div>
                </div>
                <div @click="scanDormant(365)" :class="['cursor-pointer flex-1 p-4 rounded-lg border-2 transition-all', currentDays === 365 ? 'border-gray-500 bg-gray-100' : 'border-gray-100 hover:border-gray-300']">
                    <div class="text-2xl mb-1">⚰️</div><div class="font-bold text-gray-700">1年以上未到店</div><div class="text-xs text-gray-400">基本流失，死马当活马医</div>
                </div>
            </div>
        </div>

        <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden relative">
            <el-table :data="tableData" v-loading="loading" @selection-change="handleSelectionChange" stripe size="large" class="w-full">
                <el-table-column type="selection" width="55" align="center" />
                <el-table-column prop="name" label="客户姓名" width="120">
                    <template #default="{row}"><span class="font-bold text-gray-800">{{ row.name }}</span></template>
                </el-table-column>
                <el-table-column prop="phone" label="联系电话" width="140">
                    <template #default="{row}"><span class="font-mono">{{ row.phone }}</span></template>
                </el-table-column>
                <el-table-column prop="lastVisitTime" label="最后到店时间" width="180">
                    <template #default="{row}"><span class="text-red-500 font-bold">{{ row.lastVisitTime }}</span></template>
                </el-table-column>
                <el-table-column prop="consumeAmount" label="历史贡献总额(LTV)" width="180" align="right">
                    <template #default="{row}"><span class="text-blue-600 font-black text-lg">￥{{ row.consumeAmount.toFixed(2) }}</span></template>
                </el-table-column>
                <el-table-column prop="balance" label="账户剩余本金" align="right">
                    <template #default="{row}"><span class="text-gray-500">￥{{ row.balance.toFixed(2) }}</span></template>
                </el-table-column>
                <template #empty>
                    <el-empty :description="currentDays ? `恭喜！没有 ${currentDays} 天未到店的流失客户` : '请点击上方雷达开始扫描流失客户'" />
                </template>
            </el-table>
        </div>

        <transition name="el-zoom-in-bottom">
            <div v-if="selectedMembers.length > 0" class="fixed bottom-10 left-1/2 transform -translate-x-1/2 bg-gray-900 text-white px-6 py-4 rounded-full shadow-2xl flex items-center gap-6 z-50 border-4 border-gray-700">
                <div class="flex items-center gap-2">
                    <span class="text-gray-300">已锁定目标:</span>
                    <span class="text-3xl font-black text-orange-400">{{ selectedMembers.length }}</span>
                    <span class="text-gray-300">人</span>
                </div>
                <div class="h-8 w-px bg-gray-600"></div>
                <div class="flex items-center gap-3">
                    <el-select v-model="batchForm.ruleId" placeholder="选择挽回满减券" class="w-48">
                        <el-option v-for="item in ruleList" :key="item.id" :label="`满${item.thresholdAmount}减${item.discountAmount}`" :value="item.id" />
                    </el-select>
                    <el-input-number v-model="batchForm.quantity" :min="1" :max="10" :step="1" :controls="false" placeholder="张数" class="w-20" />
                    <el-button type="danger" size="large" class="font-bold tracking-widest px-8" round @click="executeBatchIssue" :loading="submitLoading">
                        <el-icon class="mr-1"><Position /></el-icon> 锁定并群发
                    </el-button>
                </div>
            </div>
        </transition>
    </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { req } from '@/api/index.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Aim, Position } from '@element-plus/icons-vue'

const currentDays = ref(null)
const loading = ref(false)
const tableData = ref([])
const selectedMembers = ref([])

const ruleList = ref([])
const batchForm = ref({ ruleId: null, quantity: 1 })
const submitLoading = ref(false)

onMounted(async () => {
    // 获取后台生效中的满减券规则
    try {
        const res = await req({ url: '/ums/member/coupon-rules', method: 'GET' })
        ruleList.value = res.data || []
    } catch (e) {}
})

const scanDormant = async (days) => {
    currentDays.value = days
    loading.value = true
    try {
        const res = await req({ url: '/ums/member/dormant', method: 'GET', params: { days } })
        tableData.value = res.data || []
    } catch (e) {
        ElMessage.error('雷达扫描失败')
    } finally {
        loading.value = false
    }
}

const handleSelectionChange = (val) => {
    selectedMembers.value = val
}

const executeBatchIssue = async () => {
    if (!batchForm.value.ruleId) return ElMessage.warning('请先选择要发射的满减券类型！')

    await ElMessageBox.confirm(
        `确定要向这 ${selectedMembers.value.length} 名高价值流失客户派发专属优惠券吗？动作无法撤回！`,
        '发射确认',
        { confirmButtonText: '直接发射', cancelButtonText: '再想想', type: 'warning' }
    )

    submitLoading.value = true
    try {
        const payload = {
            memberIds: selectedMembers.value.map(m => m.id),
            ruleId: batchForm.value.ruleId,
            quantity: batchForm.value.quantity
        }
        await req({ url: '/ums/member/batch-issue-voucher', method: 'POST', data: payload })
        ElMessage.success('🎉 挽回神券已全部空投至客户账户！')

        // 发送完毕后，清空选中并刷新当前雷达
        selectedMembers.value = []
        scanDormant(currentDays.value)
    } catch (e) {
        ElMessage.error('空投失败，请检查网络')
    } finally {
        submitLoading.value = false
    }
}
</script>

<style scoped>
/* 隐藏原生输入框箭头，让悬浮台更清爽 */
:deep(.el-input-number .el-input__inner) { text-align: center; }
</style>