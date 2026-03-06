<template>
    <el-dialog v-model="visible" title="极速开卡" width="450px" @closed="$emit('closed')">
        <el-form :model="form" label-width="90px">
            <el-form-item label="手机号" required>
                <el-input v-model="form.phone" placeholder="作为唯一标识" />
            </el-form-item>
            <el-form-item label="姓名">
                <el-input v-model="form.name" placeholder="选填" />
            </el-form-item>
            <el-form-item label="会员等级">
                <el-select v-model="form.type" class="w-full">
                    <el-option v-for="item in memberTypesDict" :key="item.value" :label="item.desc" :value="item.value" />
                </el-select>
            </el-form-item>
            <el-button type="primary" class="w-full mt-4" size="large" @click="submit" :loading="loading">立即开通此会员</el-button>
        </el-form>
    </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import memberApi from "@/api/ums/member.js"

const props = defineProps(['modelValue', 'memberTypesDict'])
const emit = defineEmits(['update:modelValue', 'closed'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

// 🌟 核心修复：将 level 改为 type，完美契合后台要求
const form = ref({ phone: '', name: '', type: '' })
const loading = ref(false)

// 打开弹窗时，默认选第一个会员等级
watch(visible, (newVal) => {
    if (newVal && props.memberTypesDict && props.memberTypesDict.length > 0 && !form.value.type) {
        form.value.type = props.memberTypesDict[0].value
    }
})

const submit = async () => {
    if (!form.value.phone) return ElMessage.error('手机号必填');
    loading.value = true;
    try {
        await memberApi.add(form.value);
        ElMessage.success(`开卡成功！`);
        // 🌟 重置表单时也用 type
        form.value = { phone: '', name: '', type: props.memberTypesDict[0]?.value };
        visible.value = false;
    }
    catch(e) {
        console.error('开卡异常:', e);
    } finally {
        loading.value = false;
    }
}
</script>