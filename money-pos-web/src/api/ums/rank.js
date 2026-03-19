import { req } from '../index.js'

export default {
    // 1. 获取土豪榜 (累计消费 Top 50)
    getTopConsume() {
        return req({ url: '/ums/member/rank/consume', method: 'get' })
    },
    // 2. 获取储值榜 (当前余额 Top 50)
    getTopBalance() {
        return req({ url: '/ums/member/rank/balance', method: 'get' })
    },
    // 3. 获取铁粉榜 (到店频次 Top 50)
    getTopFrequency() {
        return req({ url: '/ums/member/rank/frequency', method: 'get' })
    }
}