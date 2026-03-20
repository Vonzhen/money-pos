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
    // 3. 🌟 修复：直接接收 params 对象，完美透传 startTime 和 cashierName
    getShiftHandover(params) {
        return req({ url: '/finance/shift-handover', method: 'get', params: params })
    },
    // 4. 获取满减营销复盘分析
    getCampaignReview() {
        return req({ url: '/finance/campaign-review', method: 'get' })
    },
    // ==========================================
    // 5. 🌟 8.1新增：获取首页资产驾驶舱数据
    // ==========================================
    getAssetDashboard() {
        return req({ url: '/finance/dashboard/asset', method: 'get' })
    }
}