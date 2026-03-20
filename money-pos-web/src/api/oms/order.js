import { req } from '@/api/index.js'

export default {
    // 🌟 1. 列表分页 (对齐后端 /oms-order/page)
    list: (query) => req({
        url: `/oms-order/page`,
        method: 'GET',
        params: query
    }),

    // 🌟 2. 看板统计 (对齐后端 /oms-order/statistics)
    getCount: (query) => req({
        url: `/oms-order/statistics`,
        method: 'GET',
        params: query
    }),

    // 🌟 3. 获取详情 (按 ID)
    getDetail: (id) => req({
        url: `/oms-order/detail`,
        method: 'GET',
        params: { id: id }
    }),

    // 🌟 4. 双擎合一：按单号获取详情 (复用同一个强力后端接口)
    fullDetailByOrderNo: (orderNo) => req({
        url: `/oms-order/detail`,
        method: 'GET',
        params: { orderNo: orderNo }
    }),

    // 🌟 5. 整单退款 (改 DELETE 为 POST，对接 DTO 校验)
    // 注意：这里的 data 必须是 { orderNo: 'xxx', reqId: 'xxx' } 格式
    returnOrder: (data) => req({
        url: `/oms-order/return`,
        method: 'POST',
        data
    }),

    // 🌟 6. 部分退货 (改 DELETE 为 POST，对接资金逆向安全校验)
    returnGoods: (data) => req({
        url: `/oms-order/returnGoods`,
        method: 'POST',
        data
    }),

    // 🌟 7. 真实损益毛利审计
    getProfitAuditPage: (params) => req({
        url: '/oms-order/profit-audit',
        method: 'GET',
        params: params
    })
}