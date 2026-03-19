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

    // 批量导入原生实现 (已打通后端真实战报展示)
    const handleImport = async (options) => {
        importing.value = true;
        try {
            // 接收后端的完整响应
            const res = await importGoods(options.file);

            // 🌟 核心兼容解析：提取后端传回的那个漂亮的 String
            // 如果拦截器剥离了外壳，拿到的直接就是 string；否则去 data 里找
            let resultMsg = '导入成功';
            if (typeof res === 'string' && res.includes('导入完成')) {
                resultMsg = res;
            } else if (res && res.data && typeof res.data === 'string') {
                resultMsg = res.data;
            } else if (res && res.msg) {
                resultMsg = res.msg; // 兼容部分标准结构体
            }

            // 弹出包含详细战报的消息框 (设置显示时间稍长一点，方便店长看清)
            ElMessage({
                message: resultMsg,
                type: 'success',
                duration: 5000
            });

            if (onImportSuccess) {
                onImportSuccess(); // 触发回调，刷新表格
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