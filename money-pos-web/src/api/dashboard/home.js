import { req } from '@/api/index.js'

export default {
    getHomeCount() {
        return req({
            url: '/home/count',
            method: 'GET'
        })
    },
    // 🌟 核心新增：拉取大屏图表数据的接口
    getChartsData() {
        return req({
            url: '/home/charts',
            method: 'GET'
        })
    }
}