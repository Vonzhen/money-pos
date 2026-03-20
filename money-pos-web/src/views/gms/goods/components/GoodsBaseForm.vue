<template>
    <div class="bg-gray-50 p-5 rounded-lg border border-gray-200 mb-5 shadow-sm">
        <h4 class="font-bold text-gray-700 mb-4 flex items-center gap-2"><el-icon><Goods /></el-icon> 基础档案</h4>
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <el-form-item label="品牌归属" prop="brandId" class="mb-0 col-span-2 lg:col-span-1">
                <el-select v-model="form.brandId" class="w-full" placeholder="【必选】拉取策略">
                    <el-option v-for="item in brands" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
            </el-form-item>
            <el-form-item label="所属分类" prop="categoryId" class="mb-0 col-span-2 lg:col-span-1">
                <el-select v-model="form.categoryId" class="w-full" placeholder="请选择" clearable>
                    <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
            </el-form-item>
            <el-form-item label="商品条码" prop="barcode" class="mb-0 col-span-2 lg:col-span-1">
                <el-input v-model.trim="form.barcode" placeholder="扫码枪录入或手输" />
            </el-form-item>
            <el-form-item label="商品名称" prop="name" class="mb-0 col-span-2 lg:col-span-1">
                <el-input v-model.trim="form.name" placeholder="请输入商品完整名称" />
            </el-form-item>
        </div>

        <div class="grid grid-cols-3 gap-6 mt-4 border-t border-gray-200 pt-4 items-center">
            <el-form-item label="进货成本" prop="purchasePrice" class="mb-0">
                <el-input v-model="form.purchasePrice" placeholder="￥ 0.00" />
            </el-form-item>
            <el-form-item label="系统零售价" prop="salePrice" class="mb-0">
                <el-input v-model="form.salePrice" class="font-black text-red-600" placeholder="￥ 0.00" />
            </el-form-item>
            <el-form-item label="参与满减" prop="isDiscountParticipable" class="mb-0">
                <el-switch v-model="form.isDiscountParticipable" :active-value="1" :inactive-value="0" active-text="允许" inactive-text="禁止" inline-prompt style="--el-switch-on-color: #f97316;" />
            </el-form-item>
        </div>
    </div>
</template>

<script setup>
import { Goods } from '@element-plus/icons-vue';
defineProps({ form: Object, brands: Array, categories: Array });
</script>