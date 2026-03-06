import { req } from '../index.js'

export default {
    // 提交采购入库单
    createInbound: (data) => req({
        url: '/gms/inventory/inbound',
        method: 'POST',
        data: data
    }),

    // 提交库存盘点单 (覆盖真实库存)
    createCheck: (data) => req({
        url: '/gms/inventory/check',
        method: 'POST',
        data: data
    }),

    // 提交报损出库单 (扣减破损库存)
    createOutbound: (data) => req({
        url: '/gms/inventory/outbound',
        method: 'POST',
        data: data
    })
}