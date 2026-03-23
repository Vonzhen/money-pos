import NProgress from "nprogress";
import 'nprogress/nprogress.css'
import {getToken, removeToken, setToken} from "@/composables/token.js";
import {useAppStore, useUserStore} from "@/store/index.js";

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

        if (to.path === '/guest') {
            next()
            return
        }

        if (hasToken && to.path === '/login') {
            // 🌟 核心修改：如果已登录，打开软件直接送到收银台，而不是后台
            next({path: '/pos'})
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