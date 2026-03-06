import { req } from '../index.js'

export default {
    list: (query) => req({
        url: '/gms/stockLog',
        method: 'GET',
        params: query
    })
}