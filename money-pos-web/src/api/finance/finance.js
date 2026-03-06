import { req } from '../index.js'

export default {
    // 1. 获取今日大屏统计数据
    getDashboardData() {
        return req({ url: '/finance/dashboard', method: 'get' })
    },
    // 2. 获取商品利润排行榜
    getProfitRanking() {
        return req({ url: '/finance/profit-ranking', method: 'get' })
    },
    // 3. 获取交接班对账单
    getShiftHandover(startTime) {
        return req({ url: '/finance/shift-handover', method: 'get', params: { startTime } })
    },
    // 4. 获取满减营销复盘分析 (新增)
    getCampaignReview() {
        return req({ url: '/finance/campaign-review', method: 'get' })
    }
}