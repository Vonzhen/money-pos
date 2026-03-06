import { req } from '../index.js'

export default {
  list: (query) => req({ url: '/ums/member', method: 'GET', params: query }),
  add: (data) => req({ url: '/ums/member', method: 'POST', data }),
  edit: (data) => req({ url: '/ums/member', method: 'PUT', data }),
  del: (ids) => req({ url: '/ums/member', method: 'DELETE', data: ids }),
  recharge: (data) => req({ url: '/ums/member/recharge', method: 'post', data }),
  importMembers: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return req({
      url: '/ums/member/import',
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  downloadTemplate: () => req({
    url: '/ums/member/template',
    method: 'GET',
    responseType: 'blob'
  }),
  // 🌟 核心：POS 专属搜会员 (路径必须是 ums)
  posSearch: (keyword) => req({
    url: '/ums/member/pos-search',
    method: 'GET',
    params: { keyword }
  }),
  // 🌟 获取满减券规则下拉列表
  getCouponRules: () => req({ url: '/ums/member/coupon-rules', method: 'GET' })
}