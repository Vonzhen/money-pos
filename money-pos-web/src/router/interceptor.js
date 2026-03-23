import NProgress from "nprogress";
import 'nprogress/nprogress.css'
import {getToken, removeToken, setToken} from "@/composables/token.js";
import {useAppStore, useUserStore} from "@/store/index.js";

// 🌟 核心修复：把客显屏路由加入免检白名单！
const whitelist = ['/login', '/guest']

export default function (router) {
    router.beforeEach(async (to) => {
        if (import.meta.env.VITE_ONLY_UI) {
            setToken('only_ui')
        }
        const hasToken = getToken()
        if (hasToken && !useAppStore().menus) {
            await useAppStore().loadRoutes()
            return to.fullPath
        }
    })

    router.beforeEach(async (to, from, next) => {
        NProgress.start()
        const hasToken = getToken()

        // 🌟 新增判断逻辑：如果是客显屏，无论有没有 Token，直接放行！
        if (to.path === '/guest') {
            next()
            return
        }

        if (hasToken && to.path === '/login') {
            next({path: '/'})
        } else if (hasToken) {
            try {
                await useUserStore().loadInfo()
                next()
            } catch (e) {
                removeToken()
                next(`/login?redirect=${to.path}`)
            }
        } else if (whitelist.indexOf(to.path) !== -1) {
            next()
        } else {
            next(`/login?redirect=${to.path}`)
        }
    })

    router.afterEach(() => {
        NProgress.done()
    })

    return router
}