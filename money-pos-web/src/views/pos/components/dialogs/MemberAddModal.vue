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

            <el-form-item label="生效品牌" required>
                <el-select v-model="form.brandId" placeholder="请选择要激活特权的品牌" class="w-full" filterable>
                    <el-option v-for="brand in brandList" :key="String(brand.id)" :label="brand.name" :value="String(brand.id)" />
                </el-select>
                <div class="text-xs text-gray-400 mt-1">关联全新多品牌架构，赋予此品牌专属特权</div>
            </el-form-item>

            <el-button type="primary" class="w-full mt-4" size="large" @click="submit" :loading="loading">立即开通此会员</el-button>
        </el-form>
    </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import memberApi from "@/api/ums/member.js"
import { req } from '@/api/index.js' // 引入底层请求工具

const props = defineProps(['modelValue', 'memberTypesDict'])
const emit = defineEmits(['update:modelValue', 'closed'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const form = ref({ phone: '', name: '', type: '', brandId: '' })
const loading = ref(false)
const brandList = ref([]) // 品牌列表池

// 🌟 核心升级：精准对接后端 GmsBrandController 的 /select 接口
const fetchBrands = async () => {
    try {
        const res = await req({ url: '/gms/brand/select', method: 'GET' });

        const rawList = res.data || res.rows || res || [];

        if (!Array.isArray(rawList) || rawList.length === 0) {
            throw new Error("接口通了，但未返回有效的品牌数组");
        }

        // 🌟 智能映射：后端 SelectVO 返回的是 label 和 value
        brandList.value = rawList.map(item => ({
            id: String(item.value || item.id), // 提取真实 ID
            name: item.label || item.name      // 提取中文名称
        }));

    } catch (e) {
        console.warn("获取品牌列表异常，启用本地兜底数据供测试...", e);
        brandList.value = [
            { id: '1', name: '绿叶' },
            { id: '2', name: '宛伊' },
            { id: '3', name: '小慈' }
        ];
    }
}

// 监听弹窗打开事件
watch(visible, (newVal) => {
    if (newVal) {
        // 设置默认会员等级
        if (props.memberTypesDict && props.memberTypesDict.length > 0 && !form.value.type) {
            form.value.type = props.memberTypesDict[0].value
        }
        // 弹窗打开时，拉取品牌下拉列表
        if (brandList.value.length === 0) {
            fetchBrands();
        }
    }
})

const submit = async () => {
    if (!form.value.phone) return ElMessage.error('手机号必填');
    if (!form.value.brandId) return ElMessage.error('请选择生效品牌');

    loading.value = true;
    try {
        // 🌟 完美组装发给后端的全新多品牌架构数据 UmsMemberDTO
        const payload = {
            phone: form.value.phone,
            name: form.value.name,
            type: form.value.type,
            brandLevels: {
                [form.value.brandId]: form.value.type
            }
        };

        await memberApi.add(payload);
        ElMessage.success(`开卡成功！专属品牌特权已激活！`);

        // 重置表单
        form.value = { phone: '', name: '', type: props.memberTypesDict[0]?.value, brandId: '' };
        visible.value = false;
    }
    catch(e) {
        console.error('开卡异常:', e);
    } finally {
        loading.value = false;
    }
}
</script>