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
            @open-checkout="openCheckout"
            @bind-member="memberBindVisible = true"
            @open-drawer="openDrawer"
            @clear-cart="handleClear"
            @suspend="handleSuspendRetrieve"
        />

        <CheckoutModal v-model="checkoutVisible" :pay-method-dict="payMethodDict" @checkout-success="handleCheckoutSuccess" @closed="keepFocus" />
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
const { cartList, currentMember, totalAmount, clearAll, restoreOrder } = usePosStore();

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
        if (!checkoutVisible.value && !memberBindVisible.value && !restockVisible.value && !memberAddVisible.value && !rechargeVisible.value && !salesVisible.value && !shiftVisible.value && !suspendListVisible.value && cartList.value.length > 0) {
            openCheckout()
        }
    },
    onEscape: () => { checkoutVisible.value ? checkoutVisible.value = false : handleClear() }
})

const currentOrderNo = ref('POS' + dayjs().format('YYYYMMDDHHmmss'))
const currentTime = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'))
const lastOrder = ref({ total: 0, paid: 0, couponUsed: 0 })
const suspendedOrderList = ref([])
const payMethodDict = ref([]); const memberTypesDict = ref([])

const cashierName = computed(() => userStore.name || '未知收银员')

onMounted(async () => {
    try {
        const dict = await dictApi.loadDict(["memberType", "pos_payment_method"])
        if (dict.memberType) memberTypesDict.value = dict.memberType
        if (dict.pos_payment_method) payMethodDict.value = dict.pos_payment_method
    } catch (e) { }
    keepFocus(); setInterval(() => currentTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss'), 1000)
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
        const snapshotCart = JSON.parse(JSON.stringify(cartList.value));
        const snapshotMember = JSON.parse(JSON.stringify(currentMember.value));
        const snapshotTotal = totalAmount.value;

        suspendedOrderList.value.push({
            id: Date.now(),
            time: dayjs().format('HH:mm:ss'),
            cart: snapshotCart,
            member: snapshotMember,
            total: snapshotTotal
        });

        handleClear();
        ElMessage.success('订单已挂起！');
    }
    else if (suspendedOrderList.value.length > 0) {
        suspendListVisible.value = true;
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
}
</script>