<template>
    <el-autocomplete
        :key="autocompleteKey"
        ref="scannerInput"
        v-model="internalKeyword"
        :fetch-suggestions="querySearchAsync"
        :placeholder="placeholder"
        clearable
        :class="['w-full', mode === 'pos' ? 'pos-theme' : '']"
        value-key="name"
        :trigger-on-focus="false"
        :hide-loading="true"
        @select="handleSelect"
        @keyup.enter="handleScan"
        @clear="handleClear"
    >
        <template #prefix>
            <el-icon :class="mode === 'pos' ? 'text-2xl text-blue-500' : 'text-gray-400'">
                <Search />
            </el-icon>
        </template>
        <template #default="{ item }">
            <div class="flex justify-between items-center w-full">
                <div>
                    <span class="font-bold text-gray-800">{{ item.name }}</span>
                    <span class="text-gray-400 text-xs ml-2">({{ item.barcode }})</span>
                </div>
                <span class="text-red-500 font-bold ml-4">￥{{ item.salePrice }}</span>
            </div>
        </template>
    </el-autocomplete>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { req } from '@/api/index.js'
import { ElMessage } from 'element-plus'

const props = defineProps({
    modelValue: { type: String, default: '' },
    placeholder: { type: String, default: '搜索商品名称 / 条码 / 拼音' },
    mode: { type: String, default: 'report' } // 'pos' (收银模式) 或 'report' (报表模式)
})

const emit = defineEmits(['update:modelValue', 'select', 'search', 'clear'])

const internalKeyword = ref(props.modelValue)
const scannerInput = ref(null)
const autocompleteKey = ref(0)

// 双向绑定同步
watch(() => props.modelValue, (val) => { internalKeyword.value = val })
watch(internalKeyword, (val) => { emit('update:modelValue', val) })

// 🌟 核武器重置机制：直接销毁重建输入框
const resetScanner = async () => {
    internalKeyword.value = ''
    autocompleteKey.value++
    await nextTick()
    scannerInput.value?.focus()
}

const focus = () => scannerInput.value?.focus()

// 底层异步查库
const querySearchAsync = async (queryString, cb) => {
    if (!queryString || queryString.trim() === '') {
        cb([])
        return
    }
    try {
        const res = await req({ url: '/pos/goods', method: 'GET', params: { barcode: queryString } })
        cb(res.data || [])
    } catch (e) {
        cb([])
    }
}

// 鼠标或键盘上下键选中下拉列表项
const handleSelect = (item) => {
    emit('select', item)
    if (props.mode === 'pos') {
        resetScanner() // 收银台选完立刻清空
    } else {
        internalKeyword.value = item.barcode // 报表里选完，将精确条码填入框内
        emit('search', internalKeyword.value) // 触发报表查数据
    }
}

// 扫码枪或回车键触发
const handleScan = async () => {
    if (!internalKeyword.value) return;

    if (props.mode === 'pos') {
        // POS模式：严格校验，直接加购
        try {
            const res = await req({ url: '/pos/goods', method: 'GET', params: { barcode: internalKeyword.value } })
            const items = res.data || []

            if (items.length === 1) {
                handleSelect(items[0])
            } else if (items.length > 1) {
                ElMessage.warning('匹配到多个商品，请在列表中手动选择')
                scannerInput.value?.focus()
            } else {
                ElMessage.error('未找到该商品条码')
                resetScanner()
            }
        } catch (e) {
            resetScanner()
        }
    } else {
        // 报表模式：直接把输入的关键字传给父组件查报表
        emit('search', internalKeyword.value)
    }
}

const handleClear = () => {
    emit('clear')
    emit('search', '')
}

defineExpose({ focus, resetScanner })
</script>

<style scoped>
/* 仅在 POS 模式下生效的巨无霸样式 */
.pos-theme:deep(.el-input__wrapper) {
    box-shadow: 0 0 0 2px #3b82f6 inset !important;
    background-color: #ffffff;
    height: 64px;
    border-radius: 8px;
    font-size: 20px;
    font-weight: 900;
}
.pos-theme:deep(.el-input__inner) {
    color: #1f2937;
}
</style>