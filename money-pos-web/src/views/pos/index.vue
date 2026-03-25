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
            @quick-add="handleQuickAddTrigger"
        />

        <CheckoutModal v-model="dialogs.checkout" :pay-method-dict="payMethodDict" :pay-tag-dict="payTagDict" @checkout-success="handleCheckoutSuccess" @closed="keepFocus" />
        <RestockModal v-model="dialogs.restock" @closed="keepFocus" />
        <MemberBindModal v-model="dialogs.memberBind" @select="handleMemberSelect" @closed="keepFocus" />
        <RechargeModal v-model="dialogs.recharge" @closed="keepFocus" />
        <MemberAddModal v-model="dialogs.memberAdd" :memberTypesDict="memberTypesDict" @closed="keepFocus" />
        <SuspendModal v-model="dialogs.suspendList" :suspendedList="suspendedOrderList" @retrieve="retrieveOrder" @closed="keepFocus" />
        <SalesOrderModal v-model="dialogs.sales" @closed="keepFocus" />
        <ShiftModal v-model="dialogs.shift" :cashier-name="cashierName" @closed="keepFocus" />

        <QuickAddGoodsModal
            v-model="dialogs.quickAdd"
            :initBarcode="missingBarcode"
            @success="handleQuickAddSuccess"
            @closed="keepFocus"
        />
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
import QuickAddGoodsModal from './components/dialogs/QuickAddGoodsModal.vue'

const router = useRouter()
const userStore = useUserStore()
const { cartList, currentMember, totalAmount, clearAll, restoreOrder, scanAndAddToCart, addToCart } = usePosStore();

const bottomConsoleRef = ref(null)
const keepFocus = () => bottomConsoleRef.value?.focusInput()

const dialogs = reactive({
    checkout: false,
    restock: false,
    memberBind: false,
    recharge: false,
    memberAdd: false,
    suspendList: false,
    sales: false,
    shift: false,
    quickAdd: false // 🌟 控制弹窗显隐
});

const isAnyDialogOpen = computed(() => Object.values(dialogs).some(isOpen => isOpen === true));
const { notifyPaySuccess, notifyIdle, notifyMemberBind } = useDisplaySync(computed(() => dialogs.checkout));

// 🌟 记录收银台扫码未找到的条码
const missingBarcode = ref('')

// 🌟 接收 BottomConsole 发射出来的建档信号
const handleQuickAddTrigger = (barcode) => {
    missingBarcode.value = barcode;
    dialogs.quickAdd = true;
}

// 🌟 建档成功回调：立刻加入购物车！
const handleQuickAddSuccess = (newGoods) => {
    if (newGoods) {
        addToCart(newGoods);
        ElMessage.success(`[${newGoods.name}] 建档成功并已加入收银车！`);
    }
    keepFocus();
}

// 物理扫码枪直接盲扫时触发
useScanner({
    onEnter: async (buffer) => {
        if (!isAnyDialogOpen.value) {
            if (buffer && buffer.length > 0) {
                const res = await scanAndAddToCart(buffer);
                if (!res.success && res.reason === 'not_found') {
                    // 查无此物！直接走弹框流程
                    ElMessageBox.confirm(`条码 [${res.barcode}] 未录入系统，是否立即极速建档？`, '未建档商品', {
                        confirmButtonText: '立即建档',
                        cancelButtonText: '取消',
                        type: 'warning'
                    }).then(() => {
                        handleQuickAddTrigger(res.barcode);
                    }).catch(() => {
                        keepFocus();
                    });
                } else if (!res.success && res.reason === 'multiple') {
                    ElMessage.warning('匹配到多个商品，请手动到明细中搜索');
                }
                return;
            }
            if (cartList.value.length > 0) { dialogs.checkout = true; }
        }
    },
    onEscape: () => {
        if (dialogs.checkout) { dialogs.checkout = false; return; }
        if (cartList.value.length > 0) { handleClearConfirm(); }
    }
})

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

const cashierName = computed(() => {
    const info = userStore.info;
    if (info) return info.nickname || info.nickName || info.name || info.realName || info.username || info.userName || '管理员';
    try {
        const localUserStr = localStorage.getItem('user') || localStorage.getItem('userInfo');
        if (localUserStr) {
            const parsed = JSON.parse(localUserStr);
            const localInfo = parsed.info || parsed;
            const name = localInfo.nickname || localInfo.nickName || localInfo.name || localInfo.username;
            if (name) return name;
        }
    } catch (e) {}
    return '未知收银员';
});

let clockTimer = null;

onMounted(async () => {
    try {
        const dict = await dictApi.loadDict(["memberType", "pos_payment_method", "paySubTag"])
        if (dict.memberType) memberTypesDict.value = dict.memberType
        if (dict.pos_payment_method) payMethodDict.value = dict.pos_payment_method
        if (dict.paySubTag) payTagDict.value = dict.paySubTag
    } catch (e) { }

    try {
        const localSuspend = localStorage.getItem('pos_suspended_orders');
        if (localSuspend) suspendedOrderList.value = JSON.parse(localSuspend);
    } catch (e) {}

    keepFocus();
    clockTimer = setInterval(() => currentTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss'), 1000)
})

onUnmounted(() => { if (clockTimer) clearInterval(clockTimer); })

watch(suspendedOrderList, (newVal) => { localStorage.setItem('pos_suspended_orders', JSON.stringify(newVal)); }, { deep: true });

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
    }).catch(() => { keepFocus(); });
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
    else if (suspendedOrderList.value.length > 0) { dialogs.suspendList = true; }
    else { ElMessage.warning('当前没有挂单记录'); }
}

const retrieveOrder = (index) => {
    const order = suspendedOrderList.value[index];
    restoreOrder(order.cart, order.member);
    suspendedOrderList.value.splice(index, 1);
    ElMessage.success('订单已取回');
    notifyMemberBind(order.member);
}
</script>