import {req} from '../index.js'

export default {
    list(params) {
        return req({ url: '/pos/couponRule', method: 'get', params })
    },
    add(data) {
        return req({ url: '/pos/couponRule', method: 'post', data })
    },
    edit(data) {
        return req({ url: '/pos/couponRule', method: 'put', data })
    },
    del(data) {
        return req({ url: '/pos/couponRule', method: 'delete', data })
    }, // 👈 就是这里！原来缺少了这个救命的英文逗号！

    // 获取会员可用满减券
    getMemberCoupons(memberId) {
        return req({ url: '/pos/couponRule/memberCoupons/' + memberId, method: 'get' })
    }
}