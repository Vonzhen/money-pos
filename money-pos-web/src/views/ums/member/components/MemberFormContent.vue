<template>
    <div v-if="form" class="w-full">
        <div class="bg-gray-50 p-4 rounded-lg border border-gray-100 mb-4">
            <h4 class="font-bold text-gray-700 mb-3 flex items-center gap-2"><el-icon><User /></el-icon> 基础档案</h4>
            <div class="flex gap-4">
                <el-form-item label="会员名称" prop="name" class="!w-1/2">
                    <el-input v-model.trim="form.name" placeholder="请输入姓名" />
                </el-form-item>
                <el-form-item label="手机号码" prop="phone" class="!w-1/2">
                    <el-input v-model="form.phone" placeholder="结账识别号" />
                </el-form-item>
            </div>
            <el-form-item label="备注说明" prop="remark" class="mb-0">
                <el-input v-model="form.remark" placeholder="非必填" />
            </el-form-item>
        </div>

        <div class="bg-blue-50/50 p-4 rounded-lg border border-blue-100 relative">
            <h4 class="font-bold text-blue-800 mb-4 flex items-center gap-2"><el-icon><Trophy /></el-icon> 品牌专属身份 (特权矩阵)</h4>
            <div class="w-full text-xs text-blue-600/60 mb-3 tracking-tighter">不选择等级的品牌，结账时该客户无法享受对应的会员折扣。</div>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-2">
                <el-form-item v-for="brand in brands" :key="brand.value" :label="brand.label" class="!mb-2 font-bold">
                    <el-select v-model="uiBrandLevels[brand.value]" placeholder="普通零售客" clearable class="w-full">
                        <el-option v-for="item in memberTypes" :key="item.value" :label="item.desc" :value="item.value" />
                    </el-select>
                </el-form-item>
            </div>
        </div>
    </div>
</template>

<script setup>
import { reactive, watch, onMounted } from 'vue';
import { User, Trophy } from "@element-plus/icons-vue";

const props = defineProps({
    form: Object,
    brands: Array,
    memberTypes: Array
});

// 🌟 物理隔离：等级映射表
const uiBrandLevels = reactive({});

// 🌟 同步机制：将本地状态推送到主表单载荷
const syncToMainForm = () => {
    if (!props.form) return;
    const cleaned = {};
    Object.keys(uiBrandLevels).forEach(k => {
        if (uiBrandLevels[k]) cleaned[k] = uiBrandLevels[k];
    });
    props.form._brandLevels = cleaned;
};

// 监听本地选择，实时同步
watch(uiBrandLevels, syncToMainForm, { deep: true });

onMounted(() => {
    // 1. 先清空，防止内存残留
    Object.keys(uiBrandLevels).forEach(key => delete uiBrandLevels[key]);

    // 2. 如果是编辑模式，灌入初始数据
    if (props.form && props.form.brandLevels) {
        Object.assign(uiBrandLevels, props.form.brandLevels);
    }

    // 3. 立即触发一次同步，防止用户未操作直接保存导致数据丢失
    syncToMainForm();
});
</script>