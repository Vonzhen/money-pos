import { req } from '../index.js'

export default {
  list: (query) => req({
    url: '/ums/member-log',
    method: 'GET',
    params: query,
  })
}