import {createRouter, createWebHashHistory} from 'vue-router'
import intercept from "./interceptor.js"

import Layout from "@/layouts/DashboardLayout.vue"
import NotFound from "@/views/error/NotFound.vue";
import CustomerDisplay from "@/views/system/customerDisplay/index.vue";

const defaultRouterList = [
    {
        path: '/',
        // 🌟 恢复原状：根目录的指路牌依然指向后台，这样您点击"进入后台"才不会跳错
        redirect: () => "/dashboard"
    },
    {
        path: '/guest',
        name: 'GuestDisplay',
        component: CustomerDisplay
    },
    {
        path: '/dashboard',
        component: Layout,
        children: [
            {
                path: '',
                name: 'Dashboard',
                component: () => import('@/views/dashboard/index.vue'),
            }
        ]
    },
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/Login.vue'),
    },
    {
        path: '/personal',
        component: Layout,
        children: [
            {
                path: '',
                name: 'Personal',
                component: () => import('@/views/personal/index.vue'),
            }
        ]
    },
    {path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFound}
]

export const routes = [...defaultRouterList]

const router = createRouter({
    history: createWebHashHistory(import.meta.env.BASE_URL),
    routes,
})

export default intercept(router)