<template>
  <div :class="['h-[200px] border-t-4 flex p-2 gap-2 shadow-inner shrink-0 transition-colors duration-300 w-full', theme.bottomPanel, theme.bottomBorder]">

    <div :class="['w-36 rounded border p-3 flex flex-col justify-center text-sm shadow-sm shrink-0', theme.cardBg, theme.cardBorder]">
        <div :class="['mb-2 border-b pb-1 font-bold', theme.mutedText]">上一单汇总</div>
        <div :class="['flex justify-between mt-1', theme.normalText]"><span>应收:</span> <span class="font-bold">￥{{ lastOrder.total.toFixed(2) }}</span></div>
        <div :class="['flex justify-between mt-1', theme.normalText]"><span>实收:</span> <span class="font-bold text-green-500">￥{{ lastOrder.paid.toFixed(2) }}</span></div>
        <div :class="['flex justify-between mt-1', theme.normalText]"><span>找零:</span> <span class="font-bold text-red-500">￥{{ lastOrder.change.toFixed(2) }}</span></div>
    </div>

    <div :class="['flex-1 rounded border p-3 flex flex-col justify-center shadow-sm min-w-[200px]', theme.cardBg, theme.cardBorder]" @click="focusInput">
        <div :class="['flex justify-between text-sm font-bold mb-2 truncate', theme.mutedText]">
            <span class="truncate mr-2">流水号: {{ currentOrderNo }}</span>
            <span v-if="currentMember.id" class="text-blue-500 flex items-center bg-blue-50 px-2 rounded shrink-0 cursor-pointer" @click="clearMember">
                <el-icon class="mr-1"><Avatar /></el-icon> {{ currentMember.name }} ({{ memberLevelDesc }}) <el-icon class="ml-1 text-red-400"><Close /></el-icon>
            </span>
            <span v-else class="bg-gray-100 px-2 rounded shrink-0">普通散客</span>
        </div>

        <el-autocomplete
            ref="scanInputRef" v-model="scanKeyword" :fetch-suggestions="queryGoodsSearch"
            placeholder="扫码，或输入条码/首拼联想" :class="['pos-input mt-1 w-full', theme.inputStyle]"
            clearable highlight-first-item value-key="displayValue" @select="handleSelectGoods" @keyup.enter="handleScanEnter"
        >
            <template #prefix><el-icon class="text-3xl font-black ml-1"><Search /></el-icon></template>
            <template #default="{ item }">
                <div class="flex justify-between items-center w-full">
                    <span class="font-bold">{{ item.name }}</span>
                    <div class="flex gap-4"><span class="text-gray-400 text-sm">{{ item.barcode }}</span><span class="text-red-500 font-bold">￥{{ item.salePrice }}</span></div>
                </div>
            </template>
        </el-autocomplete>
    </div>

    <div :class="['w-56 rounded border p-3 flex flex-col justify-between items-end shadow-sm shrink-0 relative', theme.amountBg, theme.amountBorder]">
        <div :class="['text-sm w-full flex justify-between font-bold', theme.mutedText]">
            <span>共 <b class="text-red-500 text-xl mx-1">{{ totalCount }}</b> 件</span>
        </div>
        <div class="flex items-baseline text-red-500 mb-1 relative w-full justify-end">
            <div v-if="actualCouponUsed > 0" class="absolute -top-8 right-0 bg-teal-50 border border-teal-200 px-2 py-0.5 rounded text-[11px] font-bold text-teal-600 shadow-sm whitespace-nowrap">
                本次扣会员券: ￥{{ actualCouponUsed.toFixed(2) }}
            </div>
            <span class="text-3xl font-bold mr-1 tracking-tighter">￥</span>
            <span class="text-[55px] leading-none font-black tracking-tighter">{{ totalAmount.toFixed(2) }}</span>
        </div>
    </div>

    <div class="flex gap-2 shrink-0">
        <div class="grid grid-cols-2 grid-rows-2 gap-2 w-[200px]">
            <button :class="['pos-btn', suspendedCount > 0 ? 'bg-[#00b482]' : 'bg-[#4080ff]']" @click="$emit('suspend-retrieve')"><el-icon class="text-xl mb-1"><Calendar /></el-icon><span>{{ suspendedCount > 0 ? `取单(${suspendedCount})` : '挂单' }}</span></button>
            <button class="pos-btn bg-[#8b5cf6]" @click="$emit('bind-member')"><el-icon class="text-xl mb-1"><User /></el-icon><span>会员</span></button>
            <button class="pos-btn bg-[#14b8a6]" @click="$emit('open-drawer')"><el-icon class="text-xl mb-1"><Unlock /></el-icon><span>钱箱</span></button>
            <button class="pos-btn bg-[#6b7280]" @click="$emit('clear-cart')"><el-icon class="text-xl mb-1"><Delete /></el-icon><span>清空</span></button>
        </div>
        <button class="w-[120px] h-full bg-[#ff4d4f] text-white rounded shadow-md hover:brightness-110 active:scale-95 transition-all flex flex-col items-center justify-center border-b-4 border-red-700 shrink-0" @click="$emit('open-checkout')">
            <span class="text-3xl font-black tracking-widest">收款</span><span class="text-xs mt-2 font-bold opacity-90 bg-black/20 px-2 py-0.5 rounded">[Enter]</span>
        </button>
    </div>

  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { Search, Avatar, Close, Calendar, User, Unlock, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { usePosStore } from '../hooks/usePosStore'
import goodsApi from "@/api/gms/goods.js"

const props = defineProps(['theme', 'lastOrder', 'currentOrderNo', 'memberLevelDesc', 'suspendedCount'])
const emit = defineEmits(['open-checkout', 'suspend-retrieve', 'bind-member', 'open-drawer', 'clear-cart'])
const { currentMember, totalCount, totalAmount, actualCouponUsed, addToCart, clearMember } = usePosStore()

const scanKeyword = ref(''); const scanInputRef = ref(null)

const queryGoodsSearch = async (queryString, cb) => {
    if (!queryString) { cb([]); return }
    try { const res = await goodsApi.posSearch(queryString); cb(res.data.map(item => ({ ...item, displayValue: item.barcode }))) } catch (e) { cb([]) }
}
const handleSelectGoods = (item) => { addToCart(item); setTimeout(() => { scanKeyword.value = '' }, 50); focusInput() }
const handleScanEnter = async () => {
    const code = scanKeyword.value.trim(); if (!code) return;
    try { const res = await goodsApi.posSearch(code); if (res.data?.length > 0) addToCart(res.data[0]); else ElMessage.warning(`未找到条码 [${code}]`) }
    catch (e) { ElMessage.error('查询异常') } finally { scanKeyword.value = ''; focusInput() }
}
const focusInput = () => { nextTick(() => { scanInputRef.value?.focus() }) }
defineExpose({ focusInput })
</script>

<style scoped>
/* 🌟 修复：找回底部各种炫酷组件的样式！ */
.pos-btn { @apply flex flex-col items-center justify-center text-white rounded shadow-sm hover:brightness-110 active:scale-95 transition-all; border-bottom: 3px solid rgba(0,0,0,0.2); }
.pos-btn span { @apply text-sm font-bold tracking-widest; }
:deep(.pos-input .el-input__wrapper) { height: 65px; font-size: 26px; font-weight: 900; padding-left: 10px; transition: all 0.3s ease; }
:deep(.pos-input .el-input__inner) { letter-spacing: 2px; }
</style>