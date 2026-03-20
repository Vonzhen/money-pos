import { req } from '@/api/index.js'

export default {
    getHomeCount() {
        return req({
            url: '/home/count',
            method: 'GET'
        })
    },
    // 🌟 核心修改：支持接收 params 参数 (包含 timeRange)
    getChartsData(params) {
        return req({
            url: '/home/charts',
            method: 'GET',
            params // 将前端点选的时间范围传给后端
        })
    }
}