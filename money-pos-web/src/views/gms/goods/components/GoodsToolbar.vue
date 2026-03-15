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
            <el-button type="warning" icon="Download" plain v-if="moneyCrud.optShow.export" @click="handleDownloadTemplate">模板下载</el-button>
            <el-upload
                action="" :http-request="handleImport" :show-file-list="false" accept=".xls,.xlsx"
                class="inline-block" v-if="moneyCrud.optShow.import"
            >
                <el-button type="success" icon="Upload" plain :loading="importing">批量导入</el-button>
            </el-upload>
        </div>
    </div>
</template>

<script setup>
import MoneyRR from "@/components/crud/MoneyRR.vue";
import MoneyCUD from "@/components/crud/MoneyCUD.vue";
import SmartGoodsSelector from "@/components/common/SmartGoodsSelector.vue";
import { useGoodsImportExport } from '../composables/useGoodsImportExport.js';

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
</script>