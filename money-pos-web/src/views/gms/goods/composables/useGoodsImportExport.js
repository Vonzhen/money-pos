import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import goodsApi, { importGoods } from '@/api/gms/goods.js';

export function useGoodsImportExport(onImportSuccess) {
    const importing = ref(false);

    // 模板下载原生实现
    const handleDownloadTemplate = async () => {
        try {
            const res = await goodsApi.downloadTemplate();
            const blob = new Blob([res]);
            const link = document.createElement('a');
            link.href = window.URL.createObjectURL(blob);
            link.download = '商品导入模板.xlsx';
            link.click();
        } catch (e) {
            ElMessage.error('模板下载失败');
        }
    };

    // 批量导入原生实现
    const handleImport = async (options) => {
        importing.value = true;
        try {
            await importGoods(options.file);
            ElMessage.success('导入成功');
            if (onImportSuccess) {
                onImportSuccess(); // 触发回调，如刷新表格
            }
        } catch (e) {
            ElMessage.error('导入失败，请检查文件格式或数据合法性');
        } finally {
            importing.value = false;
        }
    };

    return {
        importing,
        handleDownloadTemplate,
        handleImport
    };
}