import { ref } from 'vue';
import { ElMessage } from 'element-plus';

/**
 * 通用导入导出 Hook
 * @param {Function} downloadApi 下载模板的 API 函数
 * @param {Function} importApi 导入数据的 API 函数
 * @param {Function} onSuccess 成功后的回调（通常是刷新列表）
 * @param {String} fileName 下载的文件名
 */
export function useDataImportExport({ downloadApi, importApi, onSuccess, fileName = '模板.xlsx' }) {
    const importLoading = ref(false);

    // 🌟 通用下载逻辑
    const handleDownloadTemplate = async () => {
        try {
            const res = await downloadApi();
            const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', fileName);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);
        } catch (e) {
            console.error(e);
            ElMessage.error('模板下载失败，请检查网络或权限');
        }
    };

    // 🌟 通用导入逻辑
    const handleImport = async (fileOptions) => {
        // 支持 Element Plus upload 的 http-request 或 on-change 模式
        const file = fileOptions.file || fileOptions.raw;
        if (!file) return;

        importLoading.value = true;
        try {
            await importApi(file);
            ElMessage.success('数据导入成功！');
            if (onSuccess) onSuccess();
        } catch (error) {
            console.error(error);
            ElMessage.error('导入失败，请检查文件格式或数据内容');
        } finally {
            importLoading.value = false;
        }
    };

    return {
        importLoading,
        handleDownloadTemplate,
        handleImport
    };
}