import { req } from '@/api/index.js'

export default {
    list: (query) => req({
        url: `/oms/order`,
        method: 'GET',
        params: query
    }),
    getCount: (query) => req({
        url: `/oms/order/count`,
        method: 'GET',
        params: query
    }),
    getDetail: (id) => req({
        url: `/oms/order/detail?id=${id}`,
        method: 'GET'
    }),
    returnOrder: (data) => req({
        url: `/oms/order/returnOrder`,
        method: 'DELETE',
        data
    }),
    returnGoods: (data) => req({
        url: `/oms/order/returnGoods`,
        method: 'DELETE',
        data
    }),

    // 🌟 核心新增：按单号穿透获取完整订单与财务明细
    fullDetailByOrderNo: (orderNo) => req({
        url: `/oms/order/fullDetailByOrderNo?orderNo=${orderNo}`,
        method: 'GET'
    }),

    // 🌟 6.6 真实损益毛利审计
    getProfitAuditPage: (params) => req({
        url: '/oms/order/profit/audit',
        method: 'GET',
        params: params
    })
}