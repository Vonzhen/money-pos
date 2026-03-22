<template>
    <div class="pos-container absolute inset-0 z-40 flex flex-col overflow-hidden font-sans select-none bg-[#f3f4f6]">
        <HeaderBar :cashierName="cashierName" :currentTime="currentTime" @action="handleNavAction" />

        <div class="flex-1 overflow-hidden p-2">
            <CartTable class="h-full rounded-md shadow-sm overflow-hidden border border-gray-200" />
        </div>

        <BottomConsole
            ref="bottomConsoleRef"
            :lastOrder="lastOrder"
            :currentOrderNo="currentOrderNo"
            :currentTime="currentTime"
            :memberTypesDict="memberTypesDict"
            :suspendCount="suspendedOrderList.length"
            @open-checkout="dialogs.checkout = true"
            @open-drawer="openDrawer"
            @clear-cart="handleClearConfirm"
            @suspend="handleSuspendRetrieve"
        />

        <CheckoutModal v-model="dialogs.checkout" :pay-method-dict="payMethodDict" :pay-tag-dict="payTagDict" @checkout-success="handleCheckoutSuccess" @closed="keepFocus" />
        <RestockModal v-model="dialogs.restock" @closed="keepFocus" />
        <MemberBindModal v-model="dialogs.memberBind" @select="handleMemberSelect" @closed="keepFocus" />
        <RechargeModal v-model="dialogs.recharge" @closed="keepFocus" />
        <MemberAddModal v-model="dialogs.memberAdd" :memberTypesDict="memberTypesDict" @closed="keepFocus" />
        <SuspendModal v-model="dialogs.suspendList" :suspendedList="suspendedOrderList" @retrieve="retrieveOrder" @closed="keepFocus" />
        <SalesOrderModal v-model="dialogs.sales" @closed="keepFocus" />
        <ShiftModal v-model="dialogs.shift" :cashier-name="cashierName" @closed="keepFocus" />
    </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, toRaw } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { useUserStore } from "@/store/index.js"
import dictApi from "@/api/system/dict.js"

import { usePosStore } from './hooks/usePosStore'
import { useScanner } from './hooks/useScanner'
import { useDisplaySync } from './hooks/useDisplaySync'

import HeaderBar from './components/HeaderBar.vue'
import CartTable from './components/CartTable.vue'
import BottomConsole from './components/BottomConsole.vue'
import CheckoutModal from './components/CheckoutModal.vue'

import RestockModal from './components/dialogs/RestockModal.vue'
import MemberBindModal from './components/dialogs/MemberBindModal.vue'
import RechargeModal from './components/dialogs/RechargeModal.vue'
import MemberAddModal from './components/dialogs/MemberAddModal.vue'
import SuspendModal from './components/dialogs/SuspendModal.vue'
import SalesOrderModal from './components/dialogs/SalesOrderModal.vue'
import ShiftModal from './components/dialogs/ShiftModal.vue'

const router = useRouter()
const userStore = useUserStore()
const { cartList, currentMember, totalAmount, clearAll, restoreOrder } = usePosStore();

const bottomConsoleRef = ref(null)
const keepFocus = () => bottomConsoleRef.value?.focusInput()

// 🌟 架构升级：DialogManager 集中式状态管理，拒绝变量爆炸
const dialogs = reactive({
    checkout: false,
    restock: false,
    memberBind: false,
    recharge: false,
    memberAdd: false,
    suspendList: false,
    sales: false,
    shift: false
});

// 检查是否任何弹窗开启
const isAnyDialogOpen = computed(() => Object.values(dialogs).some(isOpen => isOpen === true));

const { notifyPaySuccess, notifyIdle, notifyMemberBind } = useDisplaySync(computed(() => dialogs.checkout));

useScanner({
    onEnter: () => {
        // 如果没有弹窗干扰，且购物车有货，触发结账
        if (!isAnyDialogOpen.value && cartList.value.length > 0) {
            dialogs.checkout = true;
        }
    },
    onEscape: () => {
        // 1. 如果在结账界面，ESC 仅关闭结账界面
        if (dialogs.checkout) {
            dialogs.checkout = false;
            return;
        }
        // 2. 🌟 安全红线：高危清空操作拦截
        if (cartList.value.length > 0) {
            handleClearConfirm();
        }
    }
})

// 🌟 安全红线：订单号并发碰撞防御机制
const generateOrderNo = () => {
    const timePart = dayjs().format('YYYYMMDDHHmmss');
    const randomPart = Math.floor(Math.random() * 10000).toString().padStart(4, '0');
    return `POS${timePart}${randomPart}`;
}

const currentOrderNo = ref(generateOrderNo())
const currentTime = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'))
const lastOrder = ref({ total: 0, paid: 0, couponUsed: 0 })
const suspendedOrderList = ref([])

const payMethodDict = ref([])
const payTagDict = ref([])
const memberTypesDict = ref([])

// ==========================================
// 🌟 核心修复：精准狙击收银员真名提取机制
// ==========================================
const cashierName = computed(() => {
    // 1. 真相大白：用户信息藏在 userStore.info 里面！
    const info = userStore.info;
    if (info) {
        // 兼容后端常见的各种中英文命名字段（昵称优先，真名其次，账号名兜底）
        return info.nickname || info.nickName || info.name || info.realName || info.username || info.userName || '管理员';
    }

    // 2. 缓存兜底：防止页面刚刷新，Store 还没来得及加载的瞬间黑屏
    try {
        // 您的系统登录后可能会存进本地缓存
        const localUserStr = localStorage.getItem('user') || localStorage.getItem('userInfo');
        if (localUserStr) {
            const parsed = JSON.parse(localUserStr);
            const localInfo = parsed.info || parsed;
            const name = localInfo.nickname || localInfo.nickName || localInfo.name || localInfo.username;
            if (name) return name;
        }
    } catch (e) {
        // 忽略解析错误
    }

    return '未知收银员';
});
// ==========================================

let clockTimer = null; // 🌟 声明时钟句柄，防止内存泄露

onMounted(async () => {
    try {
        const dict = await dictApi.loadDict(["memberType", "pos_payment_method", "paySubTag"])
        if (dict.memberType) memberTypesDict.value = dict.memberType
        if (dict.pos_payment_method) payMethodDict.value = dict.pos_payment_method
        if (dict.paySubTag) payTagDict.value = dict.paySubTag
    } catch (e) { }

    // 🌟 安全红线：挂单防丢恢复机制 (从磁盘读取)
    try {
        const localSuspend = localStorage.getItem('pos_suspended_orders');
        if (localSuspend) suspendedOrderList.value = JSON.parse(localSuspend);
    } catch (e) {}

    keepFocus();
    clockTimer = setInterval(() => currentTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss'), 1000)
})

onUnmounted(() => {
    // 🌟 核心清理：组件销毁时杀掉幽灵定时器
    if (clockTimer) clearInterval(clockTimer);
})

// 🌟 安全红线：挂单持久化监听器 (有变动立刻落盘)
watch(suspendedOrderList, (newVal) => {
    localStorage.setItem('pos_suspended_orders', JSON.stringify(newVal));
}, { deep: true });

const handleNavAction = (action) => {
    if (action === 'shift') dialogs.shift = true
    else if (action === 'sales') dialogs.sales = true
    else if (action === 'admin') window.open(router.resolve({ path: '/' }).href, '_blank')
    else if (action === 'restock') dialogs.restock = true
    else if (action === 'recharge') dialogs.recharge = true
    else if (action === 'addMember') dialogs.memberAdd = true
}

const handleClearConfirm = () => {
    if(cartList.value.length === 0) return;
    ElMessageBox.confirm('⚠️ 购物车非空，确定要彻底清空当前所有商品吗？', '高危操作警告', {
        confirmButtonText: '确定清空',
        cancelButtonText: '点错了',
        type: 'error',
    }).then(() => {
        handleClear();
        ElMessage.success('已清空订单');
    }).catch(() => {
        keepFocus();
    });
}

const handleClear = () => {
    clearAll();
    currentOrderNo.value = generateOrderNo();
    keepFocus();
    notifyIdle();
}

const openDrawer = () => ElMessage.success('指令：弹开钱箱')

const handleCheckoutSuccess = (orderSummary) => {
    lastOrder.value = orderSummary;
    openDrawer();
    handleClear();
    notifyPaySuccess(orderSummary.total);
}

const handleMemberSelect = (member) => {
    currentMember.value = member;
    notifyMemberBind(member);
}

const handleSuspendRetrieve = () => {
    if (cartList.value.length > 0) {
        // 🌟 架构升级：废除 JSON 脆弱拷贝，使用防弹级 structuredClone + toRaw 解析 Vue 代理
        const snapshotCart = cartList.value.map(item => structuredClone(toRaw(item)));
        const snapshotMember = structuredClone(toRaw(currentMember.value));

        suspendedOrderList.value.push({
            id: Date.now(),
            time: dayjs().format('HH:mm:ss'),
            cart: snapshotCart,
            member: snapshotMember,
            total: totalAmount.value
        });

        handleClear();
        ElMessage.success('🛡️ 订单已挂起并安全落盘！');
    }
    else if (suspendedOrderList.value.length > 0) {
        dialogs.suspendList = true;
    }
    else {
        ElMessage.warning('当前没有挂单记录');
    }
}

const retrieveOrder = (index) => {
    const order = suspendedOrderList.value[index];
    restoreOrder(order.cart, order.member);
    suspendedOrderList.value.splice(index, 1);
    ElMessage.success('订单已取回');
    notifyMemberBind(order.member);
}
</script>