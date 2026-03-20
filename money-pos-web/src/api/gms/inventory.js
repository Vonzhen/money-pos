import { req } from '@/api/index.js'

export default {
    // 采购入库
    createInbound: (data) => req({ url: '/gms/inventory/inbound', method: 'POST', data }),

    // 库存盘点
    createCheck: (data) => req({ url: '/gms/inventory/check', method: 'POST', data }),

    // 报损出库
    createOutbound: (data) => req({ url: '/gms/inventory/outbound', method: 'POST', data })
}