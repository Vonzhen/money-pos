<template>
    <div class="bg-gray-900 text-gray-200 p-3 shadow-inner flex gap-3 h-56 select-none border-t border-gray-800">

        <div class="w-[24%] bg-gray-800 rounded-lg border border-gray-700 p-4 flex flex-col shadow-md relative shrink-0">
            <div v-if="currentMember.id" class="w-full h-full flex flex-col justify-between text-sm text-gray-300">
                <div class="flex justify-between items-center border-b border-gray-700 pb-2">
                    <div class="flex items-baseline gap-2 overflow-hidden">
                        <span class="text-white font-bold text-base truncate">{{ currentMember.name }}</span>
                        <span class="text-gray-400 text-xs">{{ currentMember.phone }}</span>
                    </div>
                    <el-button size="small" type="danger" link @click="currentMember = {}">退出</el-button>
                </div>

                <div class="flex justify-between items-start mt-1">
                    <span class="text-gray-400 shrink-0 mr-2 mt-0.5">会员等级:</span>
                    <span class="text-orange-400 text-right leading-snug">
                        <span v-if="!currentMember.brandLevels || Object.keys(currentMember.brandLevels).length === 0" class="text-gray-500">无</span>
                        <span v-else>{{ Object.values(currentMember.brandLevels).map(code => getLevelName(code)).join(', ') }}</span>
                    </span>
                </div>

                <div class="flex justify-between items-center">
                    <span class="text-gray-400">会员余额:</span>
                    <span class="text-white">￥ {{ formatMoney(currentMember.balance) }}</span>
                </div>
                <div class="flex justify-between items-center">
                    <span class="text-gray-400">会员券:</span>
                    <span class="text-white">￥ {{ formatMoney(currentMember.coupon) }}</span>
                </div>
                <div class="flex justify-between items-center">
                    <span class="text-gray-400">满减券:</span>
                    <span class="text-white">{{ currentMember.voucherCount || 0 }} 张</span>
                </div>
            </div>

            <div v-else class="w-full h-full flex flex-col justify-center space-y-3 text-sm">
                <div class="text-gray-400 mb-1 border-b border-gray-700 pb-2 flex items-center gap-2">
                    <el-icon><Monitor /></el-icon>上一单结算快照
                </div>
                <div class="flex justify-between items-center">
                    <span class="text-gray-400">应收金额:</span>
                    <span class="text-white">￥ {{ formatMoney(lastOrder.total) }}</span>
                </div>
                <div class="flex justify-between items-center">
                    <span class="text-gray-400">实收金额:</span>
                    <span class="text-white font-bold">￥ {{ formatMoney(lastOrder.paid) }}</span>
                </div>
                <div class="flex justify-between items-center">
                    <span class="text-gray-400">已扣会员券:</span>
                    <span class="text-white font-bold">￥ {{ formatMoney(lastOrder.couponUsed) }}</span>
                </div>
            </div>
        </div>

        <div class="flex-1 bg-gray-800 rounded-lg border border-gray-700 p-4 flex shadow-md relative">

            <div class="flex-1 flex flex-col pr-6 border-r border-gray-700">
                <div class="text-gray-300 text-sm font-medium space-y-2.5 mt-1">
                    <div class="flex items-center gap-2"><el-icon class="text-gray-400"><Tickets /></el-icon>流水号: {{ currentOrderNo }}</div>
                    <div class="flex items-center gap-2"><el-icon class="text-gray-400"><Timer /></el-icon>时　间: {{ currentTime }}</div>
                </div>

                <div class="w-full mt-auto">
                    <el-autocomplete
                        :key="autocompleteKey"
                        ref="scannerInput"
                        v-model="scanKeyword"
                        :fetch-suggestions="querySearchAsync"
                        placeholder="请将条码对准窗口 / 手输拼音联想"
                        clearable
                        class="w-full scanner-input"
                        value-key="name"
                        :trigger-on-focus="false"
                        :hide-loading="true"
                        @select="handleSelect"
                        @keyup.enter="handleScan"
                    >
                        <template #prefix><el-icon class="text-2xl text-blue-500"><Search /></el-icon></template>
                        <template #default="{ item }">
                            <div class="flex justify-between items-center w-full">
                                <div>
                                    <span class="font-bold text-gray-800">{{ item.name }}</span>
                                    <span class="text-gray-400 text-xs ml-2">({{ item.barcode }})</span>
                                </div>
                                <span class="text-red-500 font-bold ml-4">￥{{ item.salePrice }}</span>
                            </div>
                        </template>
                    </el-autocomplete>
                </div>
            </div>

            <div class="min-w-[180px] flex flex-col items-end justify-between pl-4 pb-1 shrink-0">
                <div class="text-gray-400 font-bold text-base w-full text-right mt-1">
                    共 <span class="text-white text-2xl mx-1 font-black">{{ computedTotalCount }}</span> 件
                </div>
                <div class="flex w-full justify-end items-end overflow-hidden">
                    <div class="text-red-500 font-black leading-none tracking-tight whitespace-nowrap text-right"
                         :style="{ fontSize: getResponsiveFontSize(computedTotalAmount) + 'px' }">
                        ￥{{ formatMoney(computedTotalAmount) }}
                    </div>
                </div>
                <div class="text-gray-400 text-sm font-bold w-full text-right">
                    已省/券扣: ￥{{ formatMoney(computedCouponUsed) }}
                </div>
            </div>
        </div>

        <div class="w-[22%] shrink-0 grid grid-cols-3 grid-rows-2 gap-2">
            <button class="pos-btn bg-blue-600 hover:bg-blue-500 text-white relative" @click="$emit('suspend')">
                <div v-if="suspendCount > 0" class="absolute -top-2 -right-2 bg-red-500 text-white text-xs font-black min-w-[24px] h-[24px] flex items-center justify-center rounded-full border-2 border-gray-900 shadow-md">
                    {{ suspendCount }}
                </div>
                <el-icon class="text-2xl mb-1"><Tickets /></el-icon><span class="text-base font-bold">挂单</span>
            </button>
            <button class="pos-btn bg-purple-600 hover:bg-purple-500 text-white" @click="$emit('bind-member')">
                <el-icon class="text-2xl mb-1"><User /></el-icon><span class="text-base font-bold">会员</span>
            </button>
            <button class="pos-btn bg-red-600 hover:bg-red-500 text-white row-span-2 shadow-[0_0_15px_rgba(239,68,68,0.3)] border border-red-500/50" @click="$emit('open-checkout')">
                <span class="text-3xl font-black tracking-widest">收款</span>
                <span class="text-xs font-bold opacity-90 mt-1 bg-red-800/50 px-2 py-1 rounded-full">[Enter]</span>
            </button>
            <button class="pos-btn bg-emerald-600 hover:bg-emerald-500 text-white" @click="$emit('open-drawer')">
                <el-icon class="text-2xl mb-1"><Unlock /></el-icon><span class="text-base font-bold">钱箱</span>
            </button>
            <button class="pos-btn bg-slate-600 hover:bg-slate-500 text-white" @click="$emit('clear-cart')">
                <el-icon class="text-2xl mb-1"><Delete /></el-icon><span class="text-base font-bold">清空</span>
            </button>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { Search, Delete, User, Unlock, Tickets, Timer, Monitor } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { req } from '@/api/index.js'
import { usePosStore } from '../hooks/usePosStore'

const props = defineProps(['lastOrder', 'currentOrderNo', 'currentTime', 'memberTypesDict', 'suspendCount'])
const emit = defineEmits(['open-checkout', 'bind-member', 'open-drawer', 'clear-cart', 'suspend'])

const { cartList, currentMember } = usePosStore()
const scanKeyword = ref('')
const scannerInput = ref(null)

// 🌟 新增：核武器开关，用于强制销毁并重建 el-autocomplete 组件
const autocompleteKey = ref(0)

// 🌟 物理重置法：只要调用这个方法，旧的搜索框直接灰飞烟灭，换个全新的上来
const resetScanner = async () => {
    scanKeyword.value = '';
    autocompleteKey.value++; // Vue 检测到 Key 变化，立即执行组件销毁与重建
    await nextTick();        // 等待新的组件渲染完毕
    scannerInput.value?.focus(); // 给全新的组件赋予焦点
}

const focusInput = () => {
    scannerInput.value?.focus();
}

const getLevelName = (code) => {
    if (!props.memberTypesDict) return code;
    const match = props.memberTypesDict.find(item => String(item.value) === String(code));
    return match ? match.desc : code;
}

const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00';
    const num = Number(val);
    return isNaN(num) ? '0.00' : num.toFixed(2);
}

const getResponsiveFontSize = (val) => {
    const len = formatMoney(val).length;
    if (len > 8) return 36;
    return 38;
}

const getLevelCode = (brandId) => {
    if (!currentMember.value?.id || !brandId) return null;
    return currentMember.value.brandLevels?.[String(brandId)] || null;
}

const computedTotalCount = computed(() => {
    return cartList.value.reduce((sum, item) => sum + (Number(item.qty) || 1), 0);
});

const computedTotalAmount = computed(() => {
    return cartList.value.reduce((sum, item) => {
        let price = Number(item.salePrice) || 0;
        const code = getLevelCode(item.brandId);
        if (code && item.levelPrices && item.levelPrices[code] != null) price = Number(item.levelPrices[code]);
        return sum + (price * (Number(item.qty) || 1));
    }, 0);
});

const computedCouponUsed = computed(() => {
    return cartList.value.reduce((sum, item) => {
        let coupon = 0;
        const code = getLevelCode(item.brandId);
        if (code && item.levelCoupons && item.levelCoupons[code] != null) coupon = Number(item.levelCoupons[code]);
        return sum + (coupon * (Number(item.qty) || 1));
    }, 0);
});

const querySearchAsync = async (queryString, cb) => {
    if (!queryString || queryString.trim() === '') {
        cb([]);
        return;
    }
    try {
        const res = await req({ url: '/pos/goods', method: 'GET', params: { barcode: queryString } });
        cb(res.data || []);
    } catch (e) {
        cb([]);
    }
}

const handleSelect = (item) => {
    const existing = cartList.value.find(i => i.id === item.id);
    if (existing) {
        existing.qty = (existing.qty || 1) + 1;
    } else {
        cartList.value.push({ ...item, qty: 1 });
    }
    // 选中商品后，直接用核武器重建输入框
    resetScanner();
}

const handleScan = async () => {
    if (!scanKeyword.value) return;

    try {
        const res = await req({ url: '/pos/goods', method: 'GET', params: { barcode: scanKeyword.value } });
        const items = res.data || [];

        if (items.length === 1) {
            handleSelect(items[0]);
        } else if (items.length > 1) {
            ElMessage.warning('匹配到多个商品，请在列表中手动选择');
            scannerInput.value?.focus();
        } else {
            ElMessage.error('未找到该商品条码');
            // 没找到商品，也要重建输入框
            resetScanner();
        }
    } catch (e) {
        resetScanner();
    }
}

defineExpose({ focusInput })
</script>

<style scoped>
.pos-btn {
    @apply rounded-xl shadow-sm transition-all duration-150 active:scale-95 flex flex-col items-center justify-center tracking-wider;
}
:deep(.scanner-input .el-input__wrapper) {
    box-shadow: 0 0 0 2px #3b82f6 inset !important;
    background-color: #ffffff;
    height: 64px;
    border-radius: 8px;
    font-size: 20px;
    font-weight: 900;
}
:deep(.scanner-input .el-input__inner) {
    color: #1f2937;
}
</style>