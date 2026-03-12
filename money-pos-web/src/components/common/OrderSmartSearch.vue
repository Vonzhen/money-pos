<template>
    <el-select
        ref="selectRef"
        v-model="selectedValue"
        filterable
        remote
        reserve-keyword
        default-first-option
        :placeholder="placeholderText"
        :remote-method="querySearch"
        :loading="loading"
        class="w-full order-smart-search"
        :size="size"
        clearable
        @change="handleSelect"
        @clear="handleClear"
    >
        <template #prefix>
            <el-dropdown @command="handleModeChange" trigger="click" @click.stop>
                <span class="text-blue-600 font-bold flex items-center bg-blue-50 hover:bg-blue-100 px-2 py-1 rounded cursor-pointer mr-1 mt-[1px] transition-colors">
                    {{ modeMap[searchMode] }}
                    <el-icon class="ml-1 text-xs"><ArrowDown /></el-icon>
                </span>
                <template #dropdown>
                    <el-dropdown-menu>
                        <el-dropdown-item command="auto">🧠 智能 (自动猜)</el-dropdown-item>
                        <el-dropdown-item command="orderNo">🧾 仅搜订单号</el-dropdown-item>
                        <el-dropdown-item command="member">👤 仅搜会员</el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>
        </template>

        <el-option
            v-for="item in options"
            :key="item.id || item.orderNo"
            :label="item.orderNo"
            :value="item.orderNo"
            class="!h-auto py-2 border-b border-gray-50 last:border-0"
        >
            <div class="flex flex-col gap-1 w-full leading-tight">
                <div class="flex justify-between items-center w-full">
                    <span class="font-mono font-bold text-gray-800 text-sm">{{ item.orderNo }}</span>
                    <span class="text-blue-600 font-bold text-sm">￥{{ formatMoney(item.payAmount) }}</span>
                </div>
                <div class="flex justify-between items-center w-full text-xs text-gray-500">
                    <span class="flex items-center gap-1">
                        <el-icon><User /></el-icon>
                        <span class="truncate max-w-[120px]">{{ item.member || '散客 / 未知' }}</span>
                    </span>
                    <span class="font-mono">{{ formatTime(item.createTime) }}</span>
                </div>
            </div>
        </el-option>
    </el-select>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { Search, User, ArrowDown } from '@element-plus/icons-vue'
import { req } from "@/api/index.js"

const props = defineProps({
    modelValue: { type: String, default: '' },
    size: { type: String, default: 'large' }
})

const emit = defineEmits(['update:modelValue', 'select', 'clear'])

const selectRef = ref(null)
const selectedValue = ref(props.modelValue)
const options = ref([])
const loading = ref(false)
const lastQuery = ref('')

// 🌟 模式控制逻辑
const searchMode = ref('auto')
const modeMap = {
    'auto': '🧠 智能',
    'orderNo': '🧾 单号',
    'member': '👤 会员'
}

const placeholderText = computed(() => {
    if (searchMode.value === 'orderNo') return '输入订单号(支持部分模糊)...';
    if (searchMode.value === 'member') return '输入会员手机或姓名(支持模糊)...';
    return '输入单号/手机/姓名，自动联想';
})

// 切换模式后，如果框里有字，自动重新查一遍
const handleModeChange = (cmd) => {
    searchMode.value = cmd;
    if (lastQuery.value) {
        querySearch(lastQuery.value);
    }
}

watch(() => props.modelValue, (newVal) => {
    selectedValue.value = newVal
    if (!newVal) options.value = []
})

// 🌟 全新放宽的模糊搜索引擎
const querySearch = async (query) => {
    const val = query ? query.trim() : '';
    lastQuery.value = val;

    if (!val) {
        options.value = [];
        return;
    }

    const params = { current: 1, size: 20 };

    if (searchMode.value === 'auto') {
        if (/^[a-zA-Z]/.test(val)) {
            // 包含字母 (如 PO, SO)，必定是单号
            params.orderNo = val;
        } else if (/^1[3-9]\d{1,9}$/.test(val)) {
            // 1开头且形似手机号的前缀，猜它是搜会员
            params.member = val;
        } else if (/^20\d{2}/.test(val)) {
            // 2023, 2024 开头，通常是系统生成的日期级订单号
            params.orderNo = val;
        } else if (/^[\u4e00-\u9fa5]+/.test(val)) {
            // 包含汉字，绝对是搜会员姓名
            params.member = val;
        } else {
            // 如果只输入了 "8888" 这种模糊短数字
            // 默认优先去匹配订单尾号
            params.orderNo = val;
        }
    } else {
        // 如果用户手动指定了模式，绝对服从用户！
        params[searchMode.value] = val;
    }

    loading.value = true
    try {
        const res = await req({
            url: '/oms/order',
            method: 'GET',
            params: params
        })
        options.value = res.data?.records || res.records || res || [];
    } catch (e) {
        options.value = []
    } finally {
        loading.value = false
    }
}

const handleSelect = (val) => {
    emit('update:modelValue', val)
    if (!val) {
        handleClear()
        return
    }
    const selectedObj = options.value.find(i => i.orderNo === val || i.id === val)
    if (selectedObj) {
        emit('select', selectedObj)
    }
}

const handleClear = () => {
    options.value = []
    selectedValue.value = null
    emit('update:modelValue', null)
    emit('clear')
}

const focus = () => {
    selectRef.value?.focus()
}

const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00';
    return Number(val).toFixed(2);
}
const formatTime = (timeStr) => {
    if (!timeStr) return '';
    return timeStr.substring(5, 16);
}

defineExpose({ handleClear, focus })
</script>

<style scoped>
.order-smart-search :deep(.el-select-dropdown__item) {
    padding-top: 8px;
    padding-bottom: 8px;
}
/* 隐藏 el-select 原本自带的 prefix 图标占位，因为我们放了更大的 dropdown */
.order-smart-search :deep(.el-input__prefix-inner > :first-child:not(.el-dropdown)) {
    display: none;
}
</style>