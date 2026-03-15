<template>
    <div class="flex flex-col gap-4">
        <MoneyRR :money-crud="moneyCrud">
            <MemberSmartSearch
                class="w-[350px] md:!w-[420px]"
                size="default"
                placeholder="快速定位会员 (支持名/号/手机模糊查)"
                @select="handleSelect"
                @clear="handleClear"
            />
        </MoneyRR>

        <div class="flex items-center gap-2">
            <MoneyCUD :money-crud="moneyCrud" />
            <el-button type="info" icon="Download" plain @click="handleDownloadTemplate">下载模板</el-button>
            <el-upload action="#" :http-request="handleImport" :show-file-list="false" accept=".xlsx, .xls">
                <el-button type="success" icon="Upload" plain :loading="importLoading">导入老会员</el-button>
            </el-upload>
        </div>
    </div>
</template>

<script setup>
import MoneyRR from "@/components/crud/MoneyRR.vue";
import MoneyCUD from "@/components/crud/MoneyCUD.vue";
import MemberSmartSearch from "@/components/common/MemberSmartSearch.vue";
import { useDataImportExport } from "@/hooks/useDataImportExport.js";
import memberApi from "@/api/ums/member.js";

const props = defineProps({ moneyCrud: Object });

const { importLoading, handleDownloadTemplate, handleImport } = useDataImportExport({
    downloadApi: memberApi.downloadTemplate,
    importApi: memberApi.importMembers,
    onSuccess: () => props.moneyCrud.doQuery(),
    fileName: '会员导入模板.xlsx'
});

const handleSelect = (m) => { props.moneyCrud.query.code = m.code; props.moneyCrud.doQuery(); };
const handleClear = () => { props.moneyCrud.query.code = null; props.moneyCrud.doQuery(); };
</script>