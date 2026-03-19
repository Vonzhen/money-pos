import { req } from '../index.js'

export default {
    // 1. 办事罗盘数据 (注意这里结尾的逗号，极大概率是刚才漏了它)
    getTrafficAnalysis(dayOfWeek) {
        return req({
            url: '/oms/analysis/traffic',
            method: 'get',
            params: { dayOfWeek: dayOfWeek }
        })
    },

    // 2. 潮汐趋势分析 - 按周宏观大盘
    getWeeklyTraffic() {
        return req({ url: '/oms/analysis/weekly-traffic', method: 'get' })
    },

    // 3. 潮汐趋势分析 - 按月潮汐预判
    getMonthlyTraffic() {
        return req({ url: '/oms/analysis/monthly-traffic', method: 'get' })
    }
}