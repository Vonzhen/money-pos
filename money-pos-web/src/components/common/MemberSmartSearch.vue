<template>
    <el-select
        ref="selectRef"
        v-model="selectedValue"
        filterable
        remote
        reserve-keyword
        default-first-option
        :placeholder="placeholder"
        :remote-method="querySearch"
        :loading="loading"
        class="w-full"
        :size="size"
        clearable
        @change="handleSelect"
        @clear="handleClear"
    >
        <template #prefix><el-icon class="text-gray-400 text-lg"><Search /></el-icon></template>
        <el-option
            v-for="item in options"
            :key="item.id"
            :label="`${item.name} (${item.phone})`"
            :value="item.id"
        >
            <div class="flex justify-between items-center w-full">
                <span class="font-bold text-gray-800">{{ item.name }}</span>
                <span class="text-gray-400 text-sm ml-4 font-mono">{{ item.phone }}</span>
            </div>
        </el-option>
    </el-select>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import memberApi from "@/api/ums/member.js"

const props = defineProps({
    modelValue: { type: [String, Number], default: null },
    placeholder: { type: String, default: '🔍 输入手机号 / 姓名拼音 / 扫码' },
    size: { type: String, default: 'large' }
})

const emit = defineEmits(['update:modelValue', 'select', 'clear'])

const selectRef = ref(null) // 🌟 获取下拉组件实例
const selectedValue = ref(props.modelValue)
const options = ref([])
const loading = ref(false)

watch(() => props.modelValue, (newVal) => {
    selectedValue.value = newVal
    if (!newVal) options.value = []
})

const querySearch = async (query) => {
    if (!query) {
        options.value = [];
        return;
    }
    loading.value = true
    try {
        const res = await memberApi.posSearch(query);
        options.value = res.data || []
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
    const selectedObj = options.value.find(i => i.id === val)
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

// 🌟 暴露给父组件，让父组件可以指挥它获取焦点
const focus = () => {
    selectRef.value?.focus()
}

defineExpose({ handleClear, focus })
</script>