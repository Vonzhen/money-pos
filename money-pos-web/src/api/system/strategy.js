import { req } from '../index.js'

export default {
    getStrategy() {
        return req({ url: '/sys/strategy/get', method: 'get' })
    },
    saveStrategy(data) {
        return req({ url: '/sys/strategy/save', method: 'post', data: data })
    }
}