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
                <div class="flex items-center truncate overflow-hidden pr-2">
                    <span class="font-bold text-gray-800 truncate">{{ item.name }}</span>
                    <span class="text-gray-400 text-xs ml-2 font-mono shrink-0">({{ item.barcode }})</span>
                </div>
                <div class="flex items-center gap-3 shrink-0">
                    <span class="text-red-500 font-bold text-right">￥{{ item.salePrice?.toFixed(2) }}</span>

                    <span :class="[
                        'text-xs px-1.5 py-0.5 rounded font-bold border w-16 text-center',
                        (item.stock == null || item.stock <= 0)
                            ? 'bg-red-50 text-red-500 border-red-100'
                            : 'bg-emerald-50 text-emerald-600 border-emerald-100'
                    ]">
                        存: {{ item.stock || 0 }}
                    </span>
                </div>
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
    mode: { type: String, default: 'report' }
})

const emit = defineEmits(['update:modelValue', 'select', 'search', 'clear'])

const internalKeyword = ref(props.modelValue)
const scannerInput = ref(null)
const autocompleteKey = ref(0)

watch(() => props.modelValue, (val) => { internalKeyword.value = val })
watch(internalKeyword, (val) => { emit('update:modelValue', val) })

const resetScanner = async () => {
    internalKeyword.value = ''
    autocompleteKey.value++
    await nextTick()
    scannerInput.value?.focus()
}

const focus = () => scannerInput.value?.focus()

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

const handleSelect = (item) => {
    emit('select', item)
    if (props.mode === 'pos') {
        resetScanner()
    } else {
        internalKeyword.value = item.barcode
        emit('search', internalKeyword.value)
    }
}

const handleScan = async () => {
    if (!internalKeyword.value) return;

    if (props.mode === 'pos') {
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