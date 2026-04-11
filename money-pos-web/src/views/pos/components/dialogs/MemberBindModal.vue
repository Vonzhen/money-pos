<template>
    <el-dialog v-model="visible" title="识别会员" width="450px" @opened="focusInput" @closed="$emit('closed')">
        <el-select ref="memberInputRef" v-model="memberKeyword" filterable remote reserve-keyword placeholder="请输入手机号 / 姓名模糊搜索" :remote-method="querySearch" :loading="loading" size="large" class="w-full" @change="handleSelect" value-key="id">
            <template #prefix><el-icon><Avatar /></el-icon></template>

            <el-option v-for="item in options" :key="item.id" :label="`${item.name} (${item.phone})`" :value="item" class="!h-auto py-2">
                <div class="flex flex-col w-full gap-1.5 leading-tight">
                    <div class="flex justify-between items-center w-full">
                        <span class="font-bold text-gray-800 text-base">{{ item.name }}</span>
                        <span class="text-gray-500 font-mono text-sm">{{ item.phone }}</span>
                    </div>
                    <div class="flex justify-between items-center w-full text-xs">

                        <div class="flex flex-wrap gap-1">
                            <template v-if="item.brandLevelDesc && Object.keys(item.brandLevelDesc).length > 0">
                                <el-tag
                                    v-for="(levelName, brandName) in item.brandLevelDesc"
                                    :key="brandName"
                                    size="small"
                                    type="success"
                                    effect="dark"
                                    class="!text-[10px] tracking-wider border-0 shadow-sm mr-1"
                                >
                                    {{ brandName }}: {{ levelName }}
                                </el-tag>
                            </template>
                            <el-tag v-else size="small" type="info" effect="plain" class="!text-[10px] tracking-wider border-0 shadow-sm">普通客</el-tag>
                        </div>

                        <div class="flex gap-3 text-gray-500">
                            <span>余额: <b class="text-gray-700 font-mono">￥{{ (item.balance || 0).toFixed(2) }}</b></span>
                            <span>券: <b class="text-blue-500 font-mono">￥{{ (item.coupon || 0).toFixed(2) }}</b></span>
                        </div>
                    </div>
                </div>
            </el-option>

        </el-select>
    </el-dialog>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { Avatar } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import memberApi from "@/api/ums/member.js"

const props = defineProps(['modelValue'])
const emit = defineEmits(['update:modelValue', 'select', 'closed'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const memberInputRef = ref(null)
const memberKeyword = ref('')
const options = ref([])
const loading = ref(false)

const focusInput = () => { nextTick(() => memberInputRef.value?.focus()) }

const querySearch = async (query) => {
    if (!query) { options.value = []; return }
    loading.value = true
    try {
        // 这里调用的也是后端的 posSearch 专线，既然我们后端已经接好了双擎翻译，这里就可以直接享受红利了！
        const res = await memberApi.posSearch(query);
        options.value = res.data || []
    } catch (e) {
        options.value = []
    } finally {
        loading.value = false
    }
}

const handleSelect = (item) => {
    if (!item) return
    ElMessage.success(`绑定会员：${item.name}`)
    emit('select', item)
    visible.value = false
    memberKeyword.value = ''; options.value = []
}
</script>