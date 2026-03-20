import { ref } from 'vue';
import { ElMessage } from 'element-plus';

/**
 * 通用导入导出 Hook (智能战报增强版)
 */
export function useDataImportExport({ downloadApi, importApi, onSuccess, fileName = '模板.xlsx' }) {
    const importLoading = ref(false);

    // 🌟 通用下载逻辑 (保持不变)
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

    // 🌟 核心改造：让导入逻辑学会“看战报”
    const handleImport = async (fileOptions) => {
        const file = fileOptions.file || fileOptions.raw;
        if (!file) return;

        importLoading.value = true;
        try {
            // 1. 接收后端返回的响应
            const res = await importApi(file);

            // 2. 智能解析后端战报
            // 兼容多种返回情况：直接返回字符串、返回包含 data 的对象、或者返回包含 msg 的对象
            let resultMsg = '数据导入成功！';
            if (typeof res === 'string' && res.includes('导入完成')) {
                resultMsg = res;
            } else if (res && res.data && typeof res.data === 'string') {
                resultMsg = res.data;
            } else if (res && res.msg) {
                resultMsg = res.msg;
            }

            // 3. 弹出详细提示，持续时间设为 5 秒，方便店长看清具体数字
            ElMessage({
                message: resultMsg,
                type: 'success',
                duration: 5000,
                showClose: true
            });

            if (onSuccess) onSuccess();
        } catch (error) {
            console.error(error);
            // 如果后端抛出了 BaseException，这里通常能拿到 error.message
            ElMessage.error(error.message || '导入失败，请检查文件格式或数据内容');
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