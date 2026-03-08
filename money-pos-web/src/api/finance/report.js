import { req } from '@/api/index.js'

export default {
    /**
     * 获取财务瀑布流全口径日结表
     * @param {Object} params - 包含 startTime 和 endTime
     */
    getDailyWaterfall(params) {
        return req({
            url: '/finance/report/waterfall/daily',
            method: 'GET',
            params: params
        })
    }
}