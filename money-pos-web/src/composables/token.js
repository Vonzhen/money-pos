const tokenKey = 'accessToken'

// 🌟 核心修复：将 localStorage 改为 sessionStorage
// 做到“阅后即焚”：只要关闭软件/刷新页面，必须重新输入密码，保障 POS 资金安全！
export const setToken = (token) => {
    sessionStorage.setItem(tokenKey, token)
}

export const getToken = () => {
    return sessionStorage.getItem(tokenKey)
}

export const removeToken = () => {
    sessionStorage.removeItem(tokenKey)
}