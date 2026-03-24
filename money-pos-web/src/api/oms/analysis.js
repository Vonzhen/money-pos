import { req } from '../index.js'

export default {
    getTrafficAnalysis(dayOfWeek) {
        return req({
            url: '/oms/analysis/traffic',
            method: 'get',
            params: { dayOfWeek: dayOfWeek }
        })
    },

    getWeeklyTraffic() {
        return req({ url: '/oms/analysis/weekly-traffic', method: 'get' })
    },

    getMonthlyTraffic() {
        return req({ url: '/oms/analysis/monthly-traffic', method: 'get' })
    },

    // 🌟 新增：获取分类销售占比数据
    getCategorySales(startDate, endDate) {
        return req({
            url: '/oms/analysis/category-sales',
            method: 'get',
            params: { startDate, endDate }
        })
    }
}