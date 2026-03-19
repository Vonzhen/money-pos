import { req } from '@/api/index.js'

export default {
    // 获取当前打印配置
    getConfig() {
        return req({
            url: '/system/config/print',
            method: 'GET'
        })
    },
    // 更新打印配置
    updateConfig(data) {
        return req({
            url: '/system/config/print/update',
            method: 'POST',
            data
        })
    },
    // 测试硬件打印 (调用 OmsOrderController 里的接口)
    testHardwarePrint(orderNo) {
        return req({
            url: '/oms-order/hardware/print',
            method: 'GET',
            params: { orderNo }
        })
    }
}