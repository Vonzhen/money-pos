<template>
    <el-dialog
        v-model="visible"
        :title="`🌟 ${memberInfo.name || '加载中...'} 的消费画像`"
        width="450px"
        align-center
        destroy-on-close
        class="rounded-2xl"
    >
        <div class="flex flex-col gap-5 px-2 py-1" v-loading="loading">
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
                        <span>🎫 剩余满减券:</span> <span class="font-bold text-orange-500">{{ memberInfo.voucherCount || 0 }} 张</span>
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
                    <el-icon class="text-orange-500"><Trophy /></el-icon> 最爱买的商品 Top 20
                </h3>
                <el-table :data="top10List" size="small" stripe border class="w-full shadow-sm rounded-lg overflow-hidden">
                    <el-table-column type="index" label="排" width="45" align="center">
                        <template #default="scope">
                            <span :class="{'text-red-500 font-black text-lg': scope.$index === 0, 'text-orange-500 font-bold text-base': scope.$index === 1, 'text-yellow-500 font-bold': scope.$index === 2, 'text-gray-400': scope.$index > 2}">
                                {{ scope.$index + 1 }}
                            </span>
                        </template>
                    </el-table-column>
                    <el-table-column prop="goodsName" label="商品名称" show-overflow-tooltip>
                        <template #default="{row}">
                            <span class="font-bold text-gray-700">{{ row.goodsName }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column prop="buyCount" label="购买件数" width="80" align="center">
                        <template #default="{row}"><span class="font-black text-blue-600">{{ row.buyCount }}</span></template>
                    </el-table-column>
                </el-table>
            </div>
        </div>
    </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Trophy } from '@element-plus/icons-vue'
import { req } from "@/api/index.js"

const props = defineProps({
    modelValue: { type: Boolean, default: false },
    memberInfo: { type: Object, default: () => ({}) }, // 接收外部传入的会员基础信息
    brandsDict: { type: Object, default: () => ({}) }, // 品牌字典 {id: name}
    levelsDict: { type: Object, default: () => ({}) }  // 等级字典 {code: name}
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(props.modelValue)
const loading = ref(false)
const top10List = ref([])

// 监听弹窗显示状态
watch(() => props.modelValue, (newVal) => {
    visible.value = newVal
    if (newVal && props.memberInfo.id) {
        fetchTopGoods(props.memberInfo.id)
    } else {
        top10List.value = []
    }
})

// 同步关闭状态到父组件
watch(visible, (newVal) => {
    emit('update:modelValue', newVal)
})

// 拉取 Top 购买记录
const fetchTopGoods = async (memberId) => {
    loading.value = true
    try {
        const res = await req({ url: '/ums/member/top10Goods', method: 'GET', params: { memberId } })
        top10List.value = res.data || res || []
    } catch (e) {
        console.warn("拉取会员画像商品偏好失败", e)
        top10List.value = []
    } finally {
        loading.value = false
    }
}

// 字典解析助手
const getBrandName = (brandId) => props.brandsDict[brandId] || '未知品牌'
const getLevelName = (levelCode) => props.levelsDict[levelCode] || levelCode

</script>