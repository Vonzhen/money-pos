<template>
    <div class="bg-gray-50 p-5 rounded-lg border border-gray-200 shadow-sm mt-5">
        <div class="grid grid-cols-3 gap-6 mb-4">
            <el-form-item label="上架状态" prop="status" class="mb-0">
                <el-select v-model="form.status" placeholder="请选择" class="w-full">
                    <el-option v-for="item in dict.goodsStatus" :key="item.value" :label="item.desc" :value="item.value" />
                </el-select>
            </el-form-item>
            <el-form-item label="当前库存" prop="stock" class="mb-0">
                <ComputeInput v-model="form.stock" />
            </el-form-item>
            <el-form-item label="计量单位" prop="unit" class="mb-0">
                <el-input v-model.trim="form.unit" placeholder="如: 盒" />
            </el-form-item>
        </div>
        <div class="mb-4">
            <el-form-item label="规格尺寸" prop="size" class="mb-0">
                <el-input v-model.trim="form.size" placeholder="选填" />
            </el-form-item>
        </div>
        <el-form-item label="商品描述" class="mb-0">
            <el-input v-model.trim="form.description" type="textarea" :rows="2" maxlength="250" show-word-limit />
        </el-form-item>
    </div>
</template>

<script setup>
import ComputeInput from "@/components/ComputeInput.vue";
defineProps({ form: Object, dict: Object });
</script>