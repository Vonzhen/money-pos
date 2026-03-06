<template>
    <div :class="['pos-container absolute inset-0 z-40 flex flex-col overflow-hidden font-sans select-none transition-colors duration-300', theme.bg]">
        <HeaderBar :theme="theme" :cashierName="cashierName" :currentTime="currentTime" @action="handleNavAction" @change-theme="currentThemeKey = $event" />
        <CartTable :theme="theme" :tableHeaderStyle="tableHeaderStyle" />

        <BottomConsole
            ref="bottomConsoleRef" :theme="theme" :lastOrder="lastOrder" :currentOrderNo="currentOrderNo"
            :memberLevelDesc="memberLevelDesc" :suspendedCount="suspendedOrderList.length"
            @open-checkout="openCheckout" @suspend-retrieve="handleSuspendRetrieve"
            @bind-member="memberBindVisible = true" @open-drawer="openDrawer" @clear-cart="handleClear"
        />

        <CheckoutModal v-model="checkoutVisible" :pay-method-dict="payMethodDict" :member-level-desc="memberLevelDesc" @checkout-success="handleCheckoutSuccess" @closed="keepFocus" />
        <RestockModal v-model="restockVisible" @closed="keepFocus" />
        <MemberBindModal v-model="memberBindVisible" @select="handleMemberSelect" @closed="keepFocus" />
        <RechargeModal v-model="rechargeVisible" @closed="keepFocus" />
        <MemberAddModal v-model="memberAddVisible" :memberTypesDict="memberTypesDict" @closed="keepFocus" />
        <SuspendModal v-model="suspendListVisible" :suspendedList="suspendedOrderList" @retrieve="retrieveOrder" @closed="keepFocus" />
        <SalesOrderModal v-model="salesVisible" @closed="keepFocus" />

        <ShiftModal v-model="shiftVisible" :cashier-name="cashierName" @closed="keepFocus" />
    </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { useUserStore } from "@/store/index.js"
import dictApi from "@/api/system/dict.js"

import { usePosStore } from './hooks/usePosStore'
import { useScanner } from './hooks/useScanner'

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

const { cartList, currentMember, totalCount, totalAmount, actualCouponUsed, clearAll, restoreOrder } = usePosStore();

const bottomConsoleRef = ref(null)
const keepFocus = () => bottomConsoleRef.value?.focusInput()

const checkoutVisible = ref(false);
const restockVisible = ref(false);
const memberBindVisible = ref(false);
const rechargeVisible = ref(false);
const memberAddVisible = ref(false);
const suspendListVisible = ref(false);
const salesVisible = ref(false);
const shiftVisible = ref(false);

useScanner({
    onEnter: () => {
        if (!checkoutVisible.value && !memberBindVisible.value && !restockVisible.value && !memberAddVisible.value && !suspendListVisible.value && !rechargeVisible.value && !salesVisible.value && !shiftVisible.value && cartList.value.length > 0) {
            openCheckout()
        }
    },
    onEscape: () => { checkoutVisible.value ? checkoutVisible.value = false : handleClear() }
})

const currentOrderNo = ref('POS' + dayjs().format('YYYYMMDDHHmmss'))
const currentTime = ref(dayjs().format('HH:mm:ss'))
const lastOrder = ref({ total: 0, paid: 0, change: 0 })
const suspendedOrderList = ref([])
const payMethodDict = ref([]); const memberTypesDict = ref([])

const cashierName = computed(() => userStore.name || '未知收银员')
const memberLevelDesc = computed(() => {
    const val = currentMember.value.levelId || currentMember.value.type;
    return memberTypesDict.value.find(i => String(i.value) === String(val))?.desc || '普通会员';
})

const currentThemeKey = ref('orange')
const themes = {
    orange: { bg: 'bg-[#fffaf0]', header: 'bg-gradient-to-r from-orange-500 to-red-600', bottomPanel: 'bg-[#fff7ed]', bottomBorder: 'border-orange-500', cardBg: 'bg-white', cardBorder: 'border-orange-200', amountBg: 'bg-[#fff1f2]', amountBorder: 'border-red-200', inputStyle: 'input-orange' },
    blue: { bg: 'bg-[#f0f9ff]', header: 'bg-gradient-to-r from-blue-600 to-cyan-600', bottomPanel: 'bg-[#eff6ff]', bottomBorder: 'border-blue-500', cardBg: 'bg-white', cardBorder: 'border-blue-200', amountBg: 'bg-white', amountBorder: 'border-blue-300', inputStyle: 'input-blue' },
    dark: { bg: 'bg-[#111827]', header: 'bg-gradient-to-r from-gray-800 to-gray-900', bottomPanel: 'bg-[#1f2937]', bottomBorder: 'border-gray-600', cardBg: 'bg-[#374151]', cardBorder: 'border-gray-600', amountBg: 'bg-[#111827]', amountBorder: 'border-gray-700', inputStyle: 'input-dark' }
}
const theme = computed(() => themes[currentThemeKey.value])
const tableHeaderStyle = computed(() => {
    if (currentThemeKey.value === 'blue') return { background: '#e0f2fe', color: '#0369a1', fontSize: '16px', borderColor: '#bae6fd' };
    if (currentThemeKey.value === 'dark') return { background: '#374151', color: '#d1d5db', fontSize: '16px', borderColor: '#4b5563' };
    return { background: '#ffedd5', color: '#9a3412', fontSize: '16px', borderColor: '#fed7aa' };
})

onMounted(async () => {
    try {
        const dict = await dictApi.loadDict(["memberType", "pos_payment_method"])
        // 🌟 核心修复：去掉了 .filter(i => i.value !== 'MEMBER')，原样保留所有会员类型
        if (dict.memberType) memberTypesDict.value = dict.memberType
        if (dict.pos_payment_method) payMethodDict.value = dict.pos_payment_method
    } catch (e) { }
    keepFocus(); setInterval(() => currentTime.value = dayjs().format('HH:mm:ss'), 1000)
})

const handleNavAction = (action) => {
    if (action === 'shift') shiftVisible.value = true
    else if (action === 'sales') salesVisible.value = true
    else if (action === 'admin') window.open(router.resolve({ path: '/' }).href, '_blank')
    else if (action === 'restock') restockVisible.value = true
    else if (action === 'recharge') rechargeVisible.value = true
    else if (action === 'addMember') memberAddVisible.value = true
}

const handleClear = () => { clearAll(); currentOrderNo.value = 'POS' + dayjs().format('YYYYMMDDHHmmss'); keepFocus(); }
const openDrawer = () => ElMessage.success('指令：弹开钱箱')
const openCheckout = () => { cartList.value.length ? checkoutVisible.value = true : ElMessage.warning('空单'); }
const handleCheckoutSuccess = (orderSummary) => { lastOrder.value = orderSummary; openDrawer(); handleClear(); }
const handleMemberSelect = (member) => { currentMember.value = member; }

const handleSuspendRetrieve = () => {
    if (cartList.value.length > 0) {
        suspendedOrderList.value.push({ id: Date.now(), time: dayjs().format('HH:mm:ss'), cart: [...cartList.value], member: {...currentMember.value}, total: totalAmount.value })
        handleClear(); ElMessage.success('订单挂起！')
    } else if (suspendedOrderList.value.length > 0) {
        suspendListVisible.value = true
    } else { ElMessage.warning('没有挂单记录') }
}

const retrieveOrder = (index) => {
    const order = suspendedOrderList.value[index]
    restoreOrder(order.cart, order.member);
    suspendedOrderList.value.splice(index, 1);
}
</script>

<style scoped>
:deep(.input-orange .el-input__wrapper) { box-shadow: 0 0 0 3px #f97316 inset !important; background-color: #fff; }
:deep(.input-orange .el-icon) { color: #f97316; }
:deep(.input-blue .el-input__wrapper) { box-shadow: 0 0 0 3px #3b82f6 inset !important; background-color: #fff; }
:deep(.input-blue .el-icon) { color: #3b82f6; }
:deep(.input-dark .el-input__wrapper) { box-shadow: 0 0 0 3px #4b5563 inset !important; background-color: #1f2937; color: #f3f4f6; }
:deep(.input-dark .el-input__inner) { color: #f3f4f6; }
:deep(.input-dark .el-icon) { color: #9ca3af; }
</style>