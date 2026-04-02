import { req } from '../index.js'

export default {
    getDashboardData() {
        return req({ url: '/finance/dashboard', method: 'get' })
    },
    getProfitRanking() {
        return req({ url: '/finance/profit-ranking', method: 'get' })
    },
    getShiftHandover(params) {
        return req({ url: '/finance/shift-handover', method: 'get', params: params })
    },
    getCampaignReview() {
        return req({ url: '/finance/campaign-review', method: 'get' })
    },
    getAssetDashboard() {
        return req({ url: '/finance/dashboard/asset', method: 'get' })
    },
    // ==========================================
    // 🌟 核心新增：桌面端调用，底层硬件级静默交接班打印
    // ==========================================
    printShiftHandover(params) {
        return req({ url: '/finance/shift-handover/print', method: 'get', params: params })
    }
}