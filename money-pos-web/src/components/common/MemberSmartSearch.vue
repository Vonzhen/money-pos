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
            :label="item.phone"
            :value="item.id"
            class="!h-auto !py-1.5 !px-3 border-b border-gray-100 last:border-0"
        >
            <div class="flex flex-col justify-center w-full">

                <div class="flex items-center justify-between w-full">
                    <div class="flex items-baseline gap-2 truncate">
                        <span class="font-bold text-gray-800 text-[15px] truncate max-w-[120px]">{{ item.name }}</span>
                        <span class="text-gray-400 text-xs font-mono">{{ item.phone }}</span>
                    </div>
                    <div class="shrink-0 ml-2 flex items-baseline gap-1">
                        <span class="text-gray-400 text-[11px]">会员券</span>
                        <span class="text-blue-500 font-bold font-mono text-sm">￥{{ formatMoney(item.coupon) }}</span>
                    </div>
                </div>

                <div class="flex flex-wrap gap-1 mt-0.5">
                    <template v-if="item.brandLevels && Object.keys(item.brandLevels).length > 0">
                        <el-tag
                            v-for="(levelCode, brandId) in item.brandLevels"
                            :key="brandId"
                            size="small"
                            type="success"
                            effect="dark"
                            class="font-bold tracking-wider border-0 shadow-[0_0_8px_rgba(16,185,129,0.2)] !text-[10px] !h-[18px] !leading-[16px] !px-1.5"
                        >
                            {{ getBrandName(brandId) }}: {{ getLevelName(levelCode) }}
                        </el-tag>
                    </template>
                    <span v-else class="text-gray-400 font-bold text-[11px] mt-0.5">普通客</span>
                </div>

            </div>
        </el-option>
    </el-select>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import memberApi from "@/api/ums/member.js"
import dictApi from "@/api/system/dict.js" // 🌟 引入字典 API
import brandApi from '@/api/gms/brand.js'  // 🌟 引入品牌 API
import { usePosStore } from "@/views/pos/hooks/usePosStore.js"

const props = defineProps({
    modelValue: { type: [String, Number], default: null },
    placeholder: { type: String, default: '🔍 输入手机号 / 姓名拼音 / 扫码' },
    size: { type: String, default: 'large' }
})

const emit = defineEmits(['update:modelValue', 'select', 'clear'])

const selectRef = ref(null)
const selectedValue = ref(props.modelValue)
const options = ref([])
const loading = ref(false)

// 从内存读取字典和写入方法
const { globalBrandsKv, globalMemberTypes, initGlobalDicts } = usePosStore();

onMounted(async () => {
    // 🌟 核心修复：智能自愈机制
    // 如果内存是空的（代表用户是直接进入后台的），则主动去后台查一次，并存入全局内存！
    if (Object.keys(globalBrandsKv.value || {}).length === 0 || (globalMemberTypes.value || []).length === 0) {
        let fetchedMemberTypes = [];
        let fetchedBrandsKv = {};

        try {
            const dict = await dictApi.loadDict(["memberType"]);
            if (dict.memberType) fetchedMemberTypes = dict.memberType;
        } catch (e) {}

        try {
            const brandRes = await (brandApi.list ? brandApi.list({ size: 1000 }) : brandApi.getSelect());
            const brandList = brandRes?.data?.records || brandRes?.data || brandRes?.records || brandRes || [];
            brandList.forEach(e => { fetchedBrandsKv[e.id || e.value] = e.name || e.label });
        } catch (e) {}

        // 反向注入内存，造福全系统！
        if(initGlobalDicts) {
            initGlobalDicts(fetchedBrandsKv, fetchedMemberTypes);
        }
    }
})

const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00';
    const num = Number(val);
    return isNaN(num) ? '0.00' : num.toFixed(2);
}

const getBrandName = (id) => {
    return globalBrandsKv.value[id] || id;
}

const getLevelName = (code) => {
    if (!code) return '';
    if (globalMemberTypes.value && globalMemberTypes.value.length > 0) {
        const match = globalMemberTypes.value.find(item => String(item.value) === String(code) || String(item.dictValue) === String(code));
        if (match) return match.desc || match.dictLabel || match.label || code;
    }
    return code;
}

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

const focus = () => {
    selectRef.value?.focus()
}

defineExpose({ handleClear, focus })
</script>