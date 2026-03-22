// const tokenKey = 'accessToken'
// 弃用不兼容 file:// 协议的 cookie 方案
// import { useCookies } from '@vueuse/integrations/useCookies'

const tokenKey = 'accessToken'

// 🌟 核心修复：改用 HTML5 标准的 localStorage，完美兼容 Electron 的本地加载模式
export const setToken = (token) => {
    localStorage.setItem(tokenKey, token)
}

export const getToken = () => {
    return localStorage.getItem(tokenKey)
}

export const removeToken = () => {
    localStorage.removeItem(tokenKey)
}