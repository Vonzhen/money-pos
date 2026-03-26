<template>
    <div class="flex flex-col gap-4">
        <MoneyRR :money-crud="moneyCrud">
            <SmartGoodsSelector
                class="w-full md:!w-[350px]" size="default" placeholder="智能搜商品(支持名称/条码/拼音)"
                @select="handleGoodsSelect" @clear="handleGoodsClear"
            />
            <el-select v-model="moneyCrud.query.brandId" clearable class="w-full md:!w-48" placeholder="品牌" @change="moneyCrud.doQuery">
                <el-option v-for="item in brands" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="moneyCrud.query.categoryId" clearable class="w-full md:!w-48 md:!hidden" placeholder="分类" @change="moneyCrud.doQuery">
                <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="moneyCrud.query.status" clearable placeholder="状态" class="md:!w-48" @change="moneyCrud.doQuery">
                <el-option v-for="item in dict.goodsStatus" :key="item.value" :label="item.desc" :value="item.value" />
            </el-select>
        </MoneyRR>

        <div class="flex items-center gap-2">
            <MoneyCUD :money-crud="moneyCrud" />

            <el-dropdown @command="handleBatchDiscount" class="mr-2">
                <el-button type="primary" plain>
                    <el-icon class="mr-1"><Edit /></el-icon> 批量设置
                    <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                    <el-dropdown-menu>
                        <el-dropdown-item :command="1">
                            <span class="text-orange-500 font-bold">● 批量允许满减</span>
                        </el-dropdown-item>
                        <el-dropdown-item :command="0" divided>
                            <span class="text-gray-500 font-bold">● 批量禁止满减</span>
                        </el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>

            <el-button type="warning" icon="Download" plain v-if="moneyCrud.optShow.export" @click="handleDownloadTemplate">模板下载</el-button>

            <el-upload
                action="" :http-request="handleImport" :show-file-list="false" accept=".xls,.xlsx"
                class="inline-block" v-if="moneyCrud.optShow.import"
            >
                <el-button type="success" icon="Upload" plain :loading="importing">批量导入</el-button>
            </el-upload>

            <el-button type="primary" icon="Download" plain @click="handleExportGoods">一键导出</el-button>
        </div>
    </div>
</template>

<script setup>
import MoneyRR from "@/components/crud/MoneyRR.vue";
import MoneyCUD from "@/components/crud/MoneyCUD.vue";
import SmartGoodsSelector from "@/components/common/SmartGoodsSelector.vue";
import { useGoodsImportExport } from '../composables/useGoodsImportExport.js';
import { ElMessage } from "element-plus";
import { req } from '@/api/index.js';
import { Edit, ArrowDown } from '@element-plus/icons-vue';
import goodsApi from "@/api/gms/goods.js";

const props = defineProps({
    moneyCrud: Object, brands: Array, categories: Array, dict: Object
});

const { importing, handleDownloadTemplate, handleImport } = useGoodsImportExport(() => props.moneyCrud.doQuery());

const handleGoodsSelect = (goods) => {
    props.moneyCrud.query.barcode = goods.barcode;
    props.moneyCrud.query.name = null;
    props.moneyCrud.doQuery();
};

const handleGoodsClear = () => {
    props.moneyCrud.query.barcode = null;
    props.moneyCrud.query.name = null;
    props.moneyCrud.doQuery();
};

// 🌟 核心修复 2：增强版并发批量修改逻辑
const handleBatchDiscount = async (status) => {
    // 兼容所有可能的选中数据变量名
    const selected = props.moneyCrud.selections || props.moneyCrud.selection || props.moneyCrud.selectedRows || [];

    // 如果一条都没勾，抛出精准提示！
    if (!selected || selected.length === 0) {
        ElMessage.warning('⚠️ 请先在左侧列表中打勾勾选商品，再进行批量设置！');
        console.warn('当前底层 CRUD 对象调试信息:', props.moneyCrud);
        return;
    }

    const actionText = status === 1 ? '允许' : '禁止';
    const loading = ElMessage.loading({ message: `正在为您批量[${actionText}]满减中，请勿关闭页面...`, duration: 0 });

    try {
        await Promise.all(selected.map(row =>
            goodsApi.edit({ id: row.id, isDiscountParticipable: status })
        ));
        loading.close();
        ElMessage.success(`✅ 搞定！已成功为 ${selected.length} 个商品批量[${actionText}]满减`);

        // 延迟 500ms 刷新表格，防止后端事务没提交完
        setTimeout(() => {
            props.moneyCrud.doQuery();
        }, 500);
    } catch (e) {
        loading.close();
        console.error("批量设置异常:", e);
        ElMessage.error(`批量修改出现异常，请刷新后重试`);
    }
};

const handleExportGoods = async () => {
    ElMessage.success("正在生成 Excel 数据，请稍候...");
    try {
        const res = await req({
            url: '/gms/goods/export',
            method: 'GET',
            responseType: 'blob'
        });

        const blob = new Blob([res.data || res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
        const downloadUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.style.display = 'none';
        link.href = downloadUrl;
        link.download = `门店商品全量档案_${new Date().getTime()}.xlsx`;
        document.body.appendChild(link);
        link.click();
        window.URL.revokeObjectURL(downloadUrl);
        document.body.removeChild(link);

        ElMessage.success("✅ 导出成功！");
    } catch (e) {
        console.error("导出异常:", e);
        ElMessage.error("导出失败，请检查网络！");
    }
};
</script>