import req from '../index'

// 商品查询
export function listGoods(barcode) {
    return req({ url: '/pos/goods', method: 'GET', params: { barcode } })
}

// 会员查询
export function listMember(member) {
    return req({ url: '/pos/members', method: 'GET', params: { member } })
}

// 🌟 新增：后端镜像试算 (不落库)
export function trialCalculate(data) {
    return req({ url: '/pos/trial', method: 'POST', data })
}

// 正式结算
export function settleAccounts(data) {
    return req({ url: '/pos/settleAccounts', method: 'POST', data })
}