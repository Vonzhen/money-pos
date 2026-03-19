// 在需要打印的地方，直接发送请求给后端
import { req } from '@/api/index.js'
import { ElMessage } from 'element-plus'

const triggerHardwarePrint = async (orderNo) => {
    try {
        await req({
            url: '/oms-order/hardware/print', // 刚写的后端接口
            method: 'GET',
            params: { orderNo }
        })
        ElMessage.success('🖨️ 打印指令已发送至打印机！')
    } catch(e) {
        ElMessage.error('打印机无响应，请检查 USB 连接')
    }
}