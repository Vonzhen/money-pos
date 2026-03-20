import { req } from '../index.js'

export default {
    // 获取周转预警数据
    getTurnoverWarnings() {
        return req({ url: '/gms/analysis/turnover-warnings', method: 'get' })
    }
}