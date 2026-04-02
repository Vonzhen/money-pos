import { req } from '../index.js'

export default {
    // 1. 获取今日实时周转预警数据
    getTurnoverWarnings() {
        return req({ url: '/gms/analysis/turnover-warnings', method: 'get' })
    },
    // 2. 🌟 新增：获取近 30 天预警压力趋势与顽疾商品黑榜
    getTurnoverWarningTrend() {
        return req({ url: '/gms/analysis/turnover-warning-trend', method: 'get' })
    }
}