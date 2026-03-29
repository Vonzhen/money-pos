<template>
    <el-card shadow="never" class="border-none shadow-sm rounded-xl h-full bg-gray-50/50">
        <template #header>
            <div class="flex items-center justify-between">
                <span class="font-bold text-gray-800 text-lg flex items-center gap-2">
                    <el-icon class="text-orange-500"><VideoCamera /></el-icon> 终端实时映射 (状态机)
                </span>
                <div class="flex items-center gap-2">
                    <span class="flex h-2 w-2 relative">
                        <span v-if="posState !== 'OFFLINE'" class="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                        <span class="relative inline-flex rounded-full h-2 w-2" :class="posState !== 'OFFLINE' ? 'bg-green-500' : 'bg-red-500'"></span>
                    </span>
                    <span class="text-xs font-mono" :class="posState !== 'OFFLINE' ? 'text-green-600' : 'text-red-500'">
                        {{ posState !== 'OFFLINE' ? '通讯直连中' : '设备离线' }}
                    </span>
                </div>
            </div>
        </template>

        <div class="mt-4 relative pointer-events-none">
            <div class="relative bg-gray-900 rounded-xl p-3 shadow-2xl border-b-8 border-gray-800 aspect-[4/3] flex flex-col">
                <div class="w-full h-full bg-black rounded overflow-hidden relative border border-gray-950">

                    <div v-if="!settings.enabled" class="absolute inset-0 bg-black flex flex-col items-center justify-center text-gray-800 z-50">
                        <el-icon class="text-5xl mb-2 opacity-30"><Monitor /></el-icon><span class="font-mono text-xs tracking-widest opacity-30">DISPLAY SLEEPING</span>
                    </div>

                    <div v-else-if="posState === 'OFFLINE'" class="absolute inset-0 bg-gray-900 flex flex-col items-center justify-center z-50">
                        <el-icon class="text-6xl mb-4 text-gray-600 animate-pulse"><Link /></el-icon>
                        <span class="font-bold text-gray-400 tracking-widest text-lg">系统连接中断</span>
                        <span class="text-gray-500 text-xs mt-2">正在尝试重新连接服务端...</span>
                    </div>

                    <template v-else-if="posState === 'STANDBY'">
                        <el-carousel height="100%" :interval="settings.interval * 1000" arrow="never" indicator-position="none" class="w-full h-full">
                            <el-carousel-item v-for="(url, index) in settings.playlist" :key="index"><img :src="url" class="w-full h-full object-cover" /></el-carousel-item>
                        </el-carousel>
                        <div class="absolute bottom-0 left-0 right-0 h-8 bg-black/70 backdrop-blur-md flex items-center z-20 overflow-hidden">
                            <div class="animate-marquee absolute whitespace-nowrap text-yellow-400 text-xs font-bold tracking-wide">
                                {{ settings.welcomeText || '欢迎光临！' }}
                            </div>
                        </div>
                    </template>

                    <template v-else-if="posState === 'CASHIER'">
                        <div class="absolute inset-0 bg-white flex">
                            <div class="w-[55%] h-full bg-gray-50 border-r border-gray-200 flex flex-col relative">
                                <div class="bg-blue-600 text-white p-2 flex justify-between items-center text-[9px] font-bold shadow-sm z-10 shrink-0">
                                    <span>清单 ({{ realCart.length }})</span>
                                    <span v-if="realMember" class="bg-yellow-400 text-blue-900 px-1 rounded">{{ maskName(realMember.name || realMember.phone) }}</span>
                                </div>

                                <div ref="cartContainerRef" class="flex-1 p-1 space-y-1 overflow-y-auto pb-8 scrollbar-hide scroll-smooth">
                                    <div v-for="(item, idx) in realCart" :key="idx" class="bg-white p-1 rounded shadow-sm border border-gray-100 flex justify-between items-center text-[9px]">
                                        <div class="flex flex-col overflow-hidden flex-1 pr-1">
                                            <span class="font-bold text-gray-800 truncate">{{ item.name || '商品' }}</span>
                                            <span class="text-[10px] text-gray-400 font-bold mt-0.5">x {{ getQty(item) }}</span>
                                        </div>
                                        <div class="flex flex-col items-end justify-center w-12 shrink-0 border-r border-gray-100 pr-1 mr-1">
                                            <span v-if="getOriginalPrice(item) > getPrice(item)" class="text-gray-400 line-through text-[7px]">￥{{ getOriginalPrice(item).toFixed(2) }}</span>
                                            <span class="text-gray-500 text-[8px]">￥{{ getPrice(item).toFixed(2) }}</span>
                                        </div>
                                        <div class="flex flex-col items-end justify-center w-14 shrink-0">
                                            <span class="font-mono font-bold" :class="getOriginalPrice(item) > getPrice(item) ? 'text-red-600' : 'text-gray-800'">￥{{ getSubtotal(item).toFixed(2) }}</span>
                                        </div>
                                    </div>
                                </div>

                                <div v-if="showParticipatingAmount" class="absolute bottom-0 left-0 right-0 border-t p-1.5 text-center bg-blue-50 border-blue-200 shrink-0">
                                    <span class="text-[9px] font-bold text-blue-600 flex items-center justify-center gap-1 px-1 truncate">
                                        <el-icon><Present /></el-icon> 当前符合满减活动的总额: ￥{{ realParticipatingAmount.toFixed(2) }}
                                    </span>
                                </div>
                            </div>

                            <div class="w-[45%] h-full flex flex-col items-center justify-center p-2 relative bg-white">
                                <template v-if="realPayment.change > 0">
                                    <div class="text-gray-500 text-[10px] font-bold mb-0.5">需找零</div>
                                    <div class="text-green-600 font-mono font-black text-3xl mb-2 leading-none">￥{{ realPayment.change.toFixed(2) }}</div>
                                    <div class="text-[9px] text-gray-500 font-bold bg-gray-50 px-2 py-1 rounded w-full text-center truncate mt-2">
                                        应收: ￥{{ realPayment.targetPay.toFixed(2) }} &nbsp; 实收: ￥{{ (realPayment.tendered + realPayment.aggregate).toFixed(2) }}
                                    </div>
                                </template>

                                <template v-else-if="realPayment.aggregate > 0">
                                    <div class="text-gray-500 text-[10px] font-bold mb-0.5">{{ realPayment.tendered > 0 ? '请扫码支付' : '请支付' }}</div>
                                    <div class="text-red-600 font-mono font-black text-3xl mb-2 leading-none">￥{{ realPayment.aggregate.toFixed(2) }}</div>
                                    <div v-if="realPayment.tendered > 0" class="text-[9px] text-orange-500 font-bold bg-orange-50 px-2 py-1 rounded w-full text-center truncate mt-1 mb-2">已收现金/余额: ￥{{ realPayment.tendered.toFixed(2) }}</div>

                                    <div class="flex gap-2 justify-center flex-wrap w-full mt-2">
                                        <div v-if="defaultPaymentCodes.length === 0" class="text-gray-300 text-[10px] border border-dashed border-gray-200 p-4 rounded text-center">未配置收款码</div>
                                        <div v-else v-for="(code, cidx) in defaultPaymentCodes" :key="cidx" class="flex flex-col items-center">
                                            <div class="p-1 border border-gray-200 shadow-sm rounded bg-white"><img :src="code.url" class="w-10 h-10 object-cover" /></div>
                                            <span class="text-[8px] text-gray-600 font-bold mt-1 scale-90">{{ code.name }}</span>
                                        </div>
                                    </div>
                                </template>

                                <template v-else-if="realPayment.tendered >= realPayment.targetPay && realPayment.targetPay > 0">
                                    <div class="text-gray-500 text-[10px] font-bold mb-0.5">已付清</div>
                                    <div class="text-blue-600 font-mono font-black text-3xl mb-2 leading-none">￥{{ realPayment.targetPay.toFixed(2) }}</div>
                                </template>

                                <template v-else>
                                    <div class="text-gray-500 text-[10px] font-bold mb-0.5">{{ realPayment.tendered > 0 ? '还差金额' : '订单总计' }}</div>
                                    <div class="text-red-600 font-mono font-black text-3xl mb-2 leading-none">￥{{ Math.max(realPayment.targetPay - realPayment.tendered, 0).toFixed(2) }}</div>
                                    <div v-if="realPayment.tendered > 0" class="text-[9px] text-orange-500 font-bold bg-orange-50 px-2 py-1 rounded w-full text-center truncate mt-2">已收: ￥{{ realPayment.tendered.toFixed(2) }}</div>
                                </template>
                            </div>
                        </div>
                    </template>

                    <template v-else-if="posState === 'SUCCESS'">
                        <div class="absolute inset-0 bg-white flex flex-col items-center justify-center z-50">
                            <el-icon class="text-green-500 text-6xl mb-4 animate-bounce"><SuccessFilled /></el-icon>
                            <div class="text-lg font-black text-gray-800 tracking-widest">支付成功</div>
                        </div>
                    </template>

                </div>
            </div>
            <div class="w-20 h-5 bg-gray-800 mx-auto rounded-b-lg shadow-inner flex justify-center items-end pb-1"><div class="w-10 h-1 bg-gray-900 rounded-full"></div></div>
        </div>
    </el-card>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { VideoCamera, Link, Monitor, Present, SuccessFilled } from '@element-plus/icons-vue'

const props = defineProps({
    settings: Object,
    posState: String,
    realCart: Array,
    realMember: Object,
    realParticipatingAmount: Number,
    realPayment: Object,
    getPrice: Function,
    getOriginalPrice: Function,
    getSubtotal: Function,
    getQty: Function
})

const cartContainerRef = ref(null)

const defaultPaymentCodes = computed(() => props.settings.paymentCodes.filter(c => c.isDefault))
const maskName = (str) => { return (!str) ? '会员' : (str.length >= 2 ? str.substring(0,1) + '**' : str); }

const showParticipatingAmount = computed(() => {
    return !!(props.realMember && Array.isArray(props.realMember.couponList) && props.realMember.couponList.length > 0 && props.realParticipatingAmount > 0)
})

watch(() => props.realCart, async () => {
    await nextTick();
    if (cartContainerRef.value) cartContainerRef.value.scrollTop = cartContainerRef.value.scrollHeight;
}, { deep: true });
</script>

<style scoped>
/* 🌟 修复：基于绝对定位的精准跑马灯动画 */
@keyframes marquee {
    0% { left: 100%; transform: translateX(0); }
    100% { left: 0%; transform: translateX(-100%); }
}
.animate-marquee {
    animation: marquee 15s linear infinite;
}

.scrollbar-hide::-webkit-scrollbar { display: none; }
.scrollbar-hide { -ms-overflow-style: none; scrollbar-width: none; }
.scroll-smooth { scroll-behavior: smooth; }
</style>