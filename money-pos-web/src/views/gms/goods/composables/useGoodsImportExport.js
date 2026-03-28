import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import goodsApi, { importGoods } from '@/api/gms/goods.js';
import { req } from '@/api/index.js'; // 🌟 引入万能的 req

export function useGoodsImportExport(onImportSuccess) {
    const importing = ref(false);

    // 🌟 核心升级：标准的 Blob 二进制流下载，杜绝文件损坏！
    const handleDownloadTemplate = async () => {
        try {
            ElMessage.info("正在生成最新版导入模板...");

            // 假设 goodsApi.downloadTemplate 底层是纯净的路径调用，若报错，可直接替换为 req 调用
            const res = await goodsApi.downloadTemplate();

            // 强制转换为 Excel 专属的 Blob 类型
            const blob = new Blob([res.data || res], {
                type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
            });

            const link = document.createElement('a');
            const downloadUrl = window.URL.createObjectURL(blob);
            link.href = downloadUrl;
            link.download = `商品极速导入模板_${new Date().getTime()}.xlsx`; // 加上时间戳防浏览器缓存
            document.body.appendChild(link);
            link.click();

            // 释放内存
            window.URL.revokeObjectURL(downloadUrl);
            document.body.removeChild(link);

            ElMessage.success("✅ 模板下载成功！");
        } catch (e) {
            console.error("模板下载异常:", e);
            ElMessage.error('模板下载失败，请检查网络或后端服务');
        }
    };

    const handleImport = async (options) => {
        importing.value = true;
        try {
            const res = await importGoods(options.file);

            let resultMsg = '导入成功';
            if (typeof res === 'string' && res.includes('导入完成')) {
                resultMsg = res;
            } else if (res && res.data && typeof res.data === 'string') {
                resultMsg = res.data;
            } else if (res && res.msg) {
                resultMsg = res.msg;
            }

            ElMessage({
                message: resultMsg,
                type: 'success',
                duration: 5000
            });

            if (onImportSuccess) {
                onImportSuccess();
            }
        } catch (e) {
            ElMessage.error(e.message || '导入失败，请检查文件格式或数据合法性');
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