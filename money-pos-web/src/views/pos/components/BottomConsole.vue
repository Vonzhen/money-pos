<template>
    <div class="bg-gray-900 text-gray-200 p-2 2xl:p-3 shadow-inner flex gap-2 2xl:gap-3 h-40 2xl:h-56 select-none border-t border-gray-800">

        <div class="w-[24%] bg-gray-800 rounded-lg border border-gray-700 p-2.5 2xl:p-4 flex flex-col shadow-md relative shrink-0">
            <div v-if="currentMember.id" class="w-full h-full flex flex-col justify-between text-xs 2xl:text-sm text-gray-300">
                <div class="flex justify-between items-center border-b border-gray-700 pb-1 2xl:pb-2">
                    <div class="flex items-baseline gap-2 overflow-hidden">
                        <span class="text-white font-bold text-sm 2xl:text-base truncate">{{ currentMember.name }}</span>
                        <span class="text-gray-400 text-[10px] 2xl:text-xs">{{ currentMember.phone }}</span>
                    </div>
                    <el-button size="small" type="danger" link @click="handleClearMember">退出</el-button>
                </div>

                <div class="flex justify-between items-start mt-0.5 2xl:mt-1">
                    <span class="text-gray-400 shrink-0 mr-2 mt-0.5 2xl:mt-1">会员身份:</span>
                    <div class="flex flex-wrap gap-1 justify-end">
                        <template v-if="currentMember.brandLevelDesc && Object.keys(currentMember.brandLevelDesc).length > 0">
                            <el-tag
                                v-for="(levelName, brandName) in currentMember.brandLevelDesc"
                                :key="brandName"
                                size="small"
                                type="success"
                                effect="dark"
                                class="font-bold tracking-wider border-0 shadow-[0_0_8px_rgba(16,185,129,0.3)] !text-[10px] 2xl:!text-xs"
                            >
                                {{ brandName }}: {{ levelName }}
                            </el-tag>
                        </template>
                        <span v-else class="text-gray-500 font-bold mt-1">普通客</span>
                    </div>
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

            <div v-else class="w-full h-full flex flex-col justify-center space-y-2 2xl:space-y-3 text-xs 2xl:text-sm">
                <div class="text-gray-400 mb-0.5 2xl:mb-1 border-b border-gray-700 pb-1 2xl:pb-2 flex items-center gap-2">
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

        <div class="flex-1 bg-gray-800 rounded-lg border border-gray-700 p-2.5 2xl:p-4 flex shadow-md relative">
            <div class="flex-1 flex flex-col pr-4 2xl:pr-6 border-r border-gray-700">
                <div class="text-gray-300 text-xs 2xl:text-sm font-medium space-y-1.5 2xl:space-y-2.5 mt-0.5 2xl:mt-1">
                    <div class="flex items-center gap-1.5 2xl:gap-2"><el-icon class="text-gray-400"><Tickets /></el-icon>流水号: {{ currentOrderNo }}</div>
                    <div class="flex items-center gap-1.5 2xl:gap-2"><el-icon class="text-gray-400"><Timer /></el-icon>时　间: {{ currentTime }}</div>
                </div>

                <div class="w-full mt-auto">
                    <el-autocomplete
                        :key="autocompleteKey"
                        ref="scannerInput"
                        v-model="scanKeyword"
                        :fetch-suggestions="querySearchAsync"
                        placeholder="扫码 / 空格收款 / 加减数量"
                        clearable
                        class="w-full scanner-input"
                        value-key="name"
                        :trigger-on-focus="false"
                        :hide-loading="true"
                        @select="handleSelect"
                        @keyup.enter="handleScan"
                    >
                        <template #prefix><el-icon class="text-xl 2xl:text-2xl text-blue-500"><Search /></el-icon></template>
                        <template #default="{ item }">
                            <div class="flex justify-between items-center w-full">
                                <div class="flex items-center truncate overflow-hidden pr-2">
                                    <span class="font-bold text-gray-800 truncate">{{ item.name }}</span>
                                    <span class="text-gray-400 text-xs ml-2 font-mono shrink-0">({{ item.barcode }})</span>
                                </div>
                                <div class="flex items-center gap-3 shrink-0">
                                    <span class="text-red-500 font-bold text-right">￥{{ item.salePrice?.toFixed(2) }}</span>
                                    <span :class="[
                                        'text-xs px-1.5 py-0.5 rounded font-bold border w-16 text-center',
                                        (item.stock == null || item.stock <= 0)
                                            ? 'bg-red-50 text-red-500 border-red-100'
                                            : 'bg-emerald-50 text-emerald-600 border-emerald-100'
                                    ]">
                                        存: {{ item.stock || 0 }}
                                    </span>
                                </div>
                            </div>
                        </template>
                    </el-autocomplete>
                </div>
            </div>

            <div class="min-w-[150px] 2xl:min-w-[180px] flex flex-col items-end justify-between pl-3 2xl:pl-4 pb-0.5 2xl:pb-1 shrink-0 h-full">
                <div class="text-gray-400 font-bold text-sm 2xl:text-base w-full text-right mt-0.5 2xl:mt-1">
                    共 <span class="text-white text-xl 2xl:text-2xl mx-1 font-black">{{ totalCount }}</span> 件
                </div>

                <div class="flex-1 w-full flex justify-end items-center overflow-hidden">
                    <div class="text-red-500 font-black leading-none tracking-tight whitespace-nowrap text-right"
                         :style="{ fontSize: getResponsiveFontSize(finalPayAmount) + 'px' }">
                        ￥{{ formatMoney(finalPayAmount) }}
                    </div>
                </div>

                <div class="w-full flex flex-col items-end gap-1 2xl:gap-1.5 mt-auto">
                    <div v-if="currentMember.id && participatingAmount > 0" class="text-gray-400 text-xs 2xl:text-sm font-bold w-full text-right transition-all">
                        满减总额: <span class="text-blue-400">￥{{ formatMoney(participatingAmount) }}</span>
                    </div>
                    <div class="text-gray-400 text-xs 2xl:text-sm font-bold w-full text-right">
                        已省/券扣: <span class="text-teal-400">￥{{ formatMoney(actualCouponUsed) }}</span>
                    </div>
                </div>
            </div>
        </div>

        <div class="w-[22%] shrink-0 grid grid-cols-3 grid-rows-2 gap-1.5 2xl:gap-2">
            <button class="pos-btn bg-blue-600 hover:bg-blue-500 text-white relative" @click="$emit('suspend')">
                <div v-if="suspendCount > 0" class="absolute -top-1.5 -right-1.5 2xl:-top-2 2xl:-right-2 bg-red-500 text-white text-[10px] 2xl:text-xs font-black min-w-[20px] 2xl:min-w-[24px] h-[20px] 2xl:h-[24px] flex items-center justify-center rounded-full border-2 border-gray-900 shadow-md">
                    {{ suspendCount }}
                </div>
                <el-icon class="text-xl 2xl:text-2xl mb-0.5 2xl:mb-1"><Tickets /></el-icon>
                <span class="text-sm 2xl:text-base font-bold">挂单</span>
            </button>

            <button class="pos-btn bg-purple-600 hover:bg-purple-500 text-white" @click="openMemberDialog">
                <el-icon class="text-xl 2xl:text-2xl mb-0.5 2xl:mb-1"><User /></el-icon>
                <span class="text-sm 2xl:text-base font-bold">会员</span>
            </button>

            <button class="pos-btn bg-red-600 hover:bg-red-500 text-white row-span-2 shadow-[0_0_15px_rgba(239,68,68,0.3)] border border-red-500/50" @click="$emit('open-checkout')">
                <span class="text-2xl 2xl:text-3xl font-black tracking-widest">收款</span>
                <span class="text-[10px] 2xl:text-xs font-bold opacity-90 mt-1 bg-red-800/50 px-2 py-0.5 2xl:py-1 rounded-full">[空格键]</span>
            </button>
            <button class="pos-btn bg-emerald-600 hover:bg-emerald-500 text-white" @click="$emit('open-drawer')">
                <el-icon class="text-xl 2xl:text-2xl mb-0.5 2xl:mb-1"><Unlock /></el-icon>
                <span class="text-sm 2xl:text-base font-bold">钱箱</span>
            </button>
            <button class="pos-btn bg-slate-600 hover:bg-slate-500 text-white" @click="clearAllWithFocus">
                <el-icon class="text-xl 2xl:text-2xl mb-0.5 2xl:mb-1"><Delete /></el-icon>
                <span class="text-sm 2xl:text-base font-bold">清空</span>
            </button>
        </div>

        <el-dialog v-model="memberDialogVisible" title="👤 绑定收银会员" width="550px" append-to-body destroy-on-close @opened="focusMemberSearch" @closed="handleMemberDialogClosed">
            <div class="py-4">
                <MemberSmartSearch ref="memberSearchComp" v-model="bindMemberId" class="w-full" placeholder="请使用扫码枪或直接输入手机号" @select="handleMemberBind" />
            </div>
            <template #footer>
                <div class="text-xs text-gray-400 text-left">💡 弹窗打开后已自动聚焦，可直接扫描或输入。</div>
            </template>
        </el-dialog>

    </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted } from 'vue'
import { Search, Delete, User, Unlock, Tickets, Timer, Monitor } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { req } from '@/api/index.js'
import { usePosStore } from '../hooks/usePosStore'
import MemberSmartSearch from '@/components/common/MemberSmartSearch.vue'

const props = defineProps(['lastOrder', 'currentOrderNo', 'currentTime', 'memberTypesDict', 'suspendCount'])
const emit = defineEmits(['open-checkout', 'open-drawer', 'clear-cart', 'suspend', 'quick-add'])

const {
    currentMember, totalCount, totalAmount, finalPayAmount, participatingAmount, actualCouponUsed,
    addToCart, clearAll, bindMember, clearMember
} = usePosStore()

const scanKeyword = ref('')
const scannerInput = ref(null)
const memberDialogVisible = ref(false)
const bindMemberId = ref(null)
const memberSearchComp = ref(null)
const autocompleteKey = ref(0)

const isDialogOpen = computed(() => memberDialogVisible.value);
const closeAllDialogs = () => {
    if (memberDialogVisible.value) {
        memberDialogVisible.value = false;
        return true;
    }
    return false;
}

onMounted(() => {
    nextTick(() => { setTimeout(() => { focusInput() }, 300) })
})

const openMemberDialog = () => { bindMemberId.value = null; memberDialogVisible.value = true; }
const focusMemberSearch = () => { memberSearchComp.value?.focus() }
const handleMemberDialogClosed = () => { bindMemberId.value = null; focusInput(); }

const handleMemberBind = (member) => {
    bindMember(member)
    memberDialogVisible.value = false
    ElMessage.success(`已绑定会员：${member.name}`)
    nextTick(() => focusInput())
}

const handleClearMember = () => {
    clearMember()
    ElMessage.info('已清除当前会员绑定')
    nextTick(() => focusInput())
}

const resetScanner = async () => {
    scanKeyword.value = '';
    autocompleteKey.value++;
    await nextTick();
    scannerInput.value?.focus();
}

const focusInput = () => { scannerInput.value?.focus(); }

const clearAllWithFocus = () => {
    emit('clear-cart');
}

const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00';
    const num = Number(val);
    return isNaN(num) ? '0.00' : num.toFixed(2);
}

const getResponsiveFontSize = (val) => {
    const len = formatMoney(val).length;
    const isSmallScreen = window.innerWidth < 1536;
    if (len > 8) return isSmallScreen ? 28 : 36;
    return isSmallScreen ? 30 : 38;
}

const querySearchAsync = async (queryString, cb) => {
    if (!queryString || queryString.trim() === '') { cb([]); return; }
    try {
        const res = await req({ url: '/pos/goods', method: 'GET', params: { barcode: queryString } });
        cb(res.data || []);
    } catch (e) { cb([]); }
}

const handleSelect = (item) => {
    addToCart(item);
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
            ElMessageBox.confirm(`条码 [${scanKeyword.value}] 未录入系统，是否立即极速建档？`, '未建档商品', {
                confirmButtonText: '立即建档',
                cancelButtonText: '重新扫码',
                type: 'warning'
            }).then(() => {
                emit('quick-add', scanKeyword.value);
            }).catch(() => {
                resetScanner();
            });
        }
    } catch (e) { resetScanner(); }
}

defineExpose({ focusInput, isDialogOpen, closeAllDialogs })
</script>

<style scoped>
.pos-btn { @apply rounded-xl shadow-sm transition-all duration-150 active:scale-95 flex flex-col items-center justify-center tracking-wider; }

:deep(.scanner-input .el-input__wrapper) {
    box-shadow: 0 0 0 2px #3b82f6 inset !important;
    background-color: #ffffff;
    height: 48px;
    border-radius: 8px;
    font-size: 16px;
    font-weight: 900;
}

@media (min-width: 1536px) {
    :deep(.scanner-input .el-input__wrapper) {
        height: 64px;
        font-size: 20px;
    }
}

:deep(.scanner-input .el-input__inner) { color: #1f2937; }
</style>