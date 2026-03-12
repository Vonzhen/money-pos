<template>
    <PageWrapper>
        <div class="flex flex-col lg:flex-row gap-6">
            <div class="flex-1">
                <el-card shadow="never" class="border-none shadow-sm rounded-xl h-full">
                    <template #header>
                        <div class="flex items-center gap-2 font-bold text-gray-800 text-lg">
                            <el-icon class="text-blue-500"><Monitor /></el-icon> 客显播控与结算规则中心
                        </div>
                    </template>

                    <el-form :model="settings" label-position="top" class="mt-2" v-loading="loading">

                        <div class="bg-blue-50/50 p-4 rounded-lg border border-blue-100 mb-6 flex justify-between items-center">
                            <div>
                                <h4 class="font-bold text-blue-800 text-base">启用客显系统驱动</h4>
                                <p class="text-xs text-gray-500 mt-1">总控开关。关闭后，客显屏将进入全黑休眠状态。</p>
                            </div>
                            <el-switch v-model="settings.enabled" active-text="启动" inactive-text="停用" inline-prompt style="--el-switch-on-color: #3b82f6;" />
                        </div>

                        <div :class="{'opacity-50 pointer-events-none grayscale transition-all': !settings.enabled}">

                            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <el-form-item>
                                    <template #label><span class="font-bold text-gray-700 flex items-center gap-1"><el-icon><Timer /></el-icon> 海报轮播间隔 (秒)</span></template>
                                    <el-input-number v-model="settings.interval" :min="3" :max="60" class="!w-full" controls-position="right" />
                                </el-form-item>

                                <el-form-item>
                                    <template #label>
                                        <span class="font-bold text-gray-700 flex items-center gap-1"><el-icon><ChatDotRound /></el-icon> 底部滚动公告标语</span>
                                    </template>
                                    <el-input v-model="settings.welcomeText" placeholder="请输入客显底部显示的滚动文字..." clearable />
                                </el-form-item>
                            </div>

                            <div class="mt-4 mb-6">
                                <div class="font-bold text-gray-700 flex items-center gap-1 mb-3">
                                    <el-icon class="text-green-500"><VideoPlay /></el-icon> 待机轮播列表 (排期)
                                </div>
                                <div class="bg-gray-50 p-4 rounded-lg border border-gray-200 min-h-[120px] flex flex-wrap gap-4 items-center">
                                    <div v-if="settings.playlist.length === 0" class="text-gray-400 text-sm w-full text-center py-4">暂无播放内容，请从下方素材库添加 👇</div>
                                    <div v-for="(url, index) in settings.playlist" :key="'play_'+index" class="relative group cursor-pointer">
                                        <div class="absolute -top-2 -left-2 w-6 h-6 bg-green-500 text-white rounded-full flex items-center justify-center text-xs font-black z-10 shadow">{{ index + 1 }}</div>
                                        <el-image :src="url" class="w-24 h-16 rounded shadow-sm border border-gray-300 object-cover" />
                                        <div @click="removeFromPlaylist(index)" class="absolute inset-0 bg-red-500/80 text-white opacity-0 group-hover:opacity-100 transition-opacity rounded flex flex-col items-center justify-center"><el-icon><Delete /></el-icon><span class="text-[10px] mt-1">移出</span></div>
                                    </div>
                                </div>
                            </div>

                            <div class="mb-8">
                                <div class="font-bold text-gray-700 flex items-center gap-1 mb-3">
                                    <el-icon class="text-orange-500"><PictureRounded /></el-icon> 门店海报素材库
                                </div>
                                <div class="flex flex-wrap gap-3 items-start">
                                    <div v-for="(url, index) in settings.library" :key="'lib_'+index" class="relative group cursor-pointer border-2 border-transparent hover:border-blue-400 rounded transition-all p-1">
                                        <el-image :src="url" class="w-16 h-16 rounded object-cover shadow-sm border border-gray-200" />
                                        <div v-if="settings.playlist.includes(url)" class="absolute top-1 right-1 text-green-500 bg-white rounded-full flex"><el-icon><SuccessFilled /></el-icon></div>
                                        <div v-else @click="addToPlaylist(url)" class="absolute inset-1 bg-blue-500/80 text-white opacity-0 group-hover:opacity-100 transition-opacity rounded flex flex-col items-center justify-center"><el-icon><Plus /></el-icon></div>
                                        <div @click.stop="deleteFromLibrary(index)" class="absolute -top-2 -right-2 w-5 h-5 bg-gray-500 hover:bg-red-500 text-white rounded-full flex items-center justify-center text-xs opacity-0 group-hover:opacity-100 z-20"><el-icon><Close /></el-icon></div>
                                    </div>
                                    <el-upload :action="uploadUrl" :headers="headers" :show-file-list="false" :before-upload="handleBeforeUpload" :on-success="handleUploadSuccess" accept=".jpg,.png,.webp" class="w-16 h-16">
                                        <div class="w-16 h-16 border-2 border-dashed border-gray-300 rounded hover:border-blue-500 flex flex-col items-center justify-center text-gray-400"><el-icon class="text-xl"><UploadFilled /></el-icon></div>
                                    </el-upload>
                                </div>
                            </div>

                            <div class="mt-8 pt-6 border-t border-gray-100">
                                <div class="flex justify-between items-end mb-4">
                                    <div class="font-bold text-gray-700 flex items-center gap-1">
                                        <el-icon class="text-blue-600"><CreditCard /></el-icon> 收款码资产映射与默认展示
                                    </div>
                                    <div class="text-[10px] text-gray-400">勾选"默认显示"的收款码，在结算初期将自动并排展示</div>
                                </div>

                                <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                    <div v-for="(item, idx) in settings.paymentCodes" :key="idx" class="border border-gray-200 rounded-lg p-3 bg-gray-50 relative group">
                                        <el-icon @click="removePaymentCode(idx)" class="absolute top-2 right-2 cursor-pointer text-gray-300 hover:text-red-500"><Close /></el-icon>
                                        <el-input v-model="item.name" size="small" placeholder="渠道名：如 农行聚合" class="mb-3 w-4/5" />
                                        <div class="flex items-center gap-3">
                                            <el-upload :action="uploadUrl" :headers="headers" :show-file-list="false" :on-success="(res) => handleQrUploadSuccess(res, idx)" accept=".jpg,.png" class="shrink-0 w-16 h-16 border border-dashed rounded flex items-center justify-center hover:border-blue-500 bg-white overflow-hidden">
                                                <img v-if="item.url" :src="item.url" class="w-full h-full object-cover" />
                                                <el-icon v-else class="text-xl text-gray-400"><Plus /></el-icon>
                                            </el-upload>
                                            <div class="flex flex-col gap-1">
                                                <el-switch v-model="item.isDefault" active-text="设为默认" size="small" />
                                            </div>
                                        </div>
                                    </div>
                                    <div @click="addPaymentCode" class="border-2 border-dashed rounded-lg flex flex-col items-center justify-center text-gray-400 hover:text-blue-500 cursor-pointer min-h-[110px] bg-white">
                                        <el-icon class="text-2xl"><Plus /></el-icon><span class="text-xs mt-2 font-bold">新增收款通道</span>
                                    </div>
                                </div>
                            </div>

                            <div class="mt-10 pt-6 border-t border-gray-100 flex items-center gap-4">
                                <el-button type="primary" size="large" class="w-full md:w-64 font-bold tracking-widest shadow-lg" @click="saveSettings" :loading="saving">
                                    <el-icon class="mr-1"><Select /></el-icon> 保存播控与结算规则
                                </el-button>
                            </div>
                        </div>
                    </el-form>
                </el-card>
            </div>

            <div class="w-full lg:w-[450px] shrink-0">
                <el-card shadow="never" class="border-none shadow-sm rounded-xl h-full bg-gray-50/50">
                    <template #header>
                        <div class="flex items-center justify-between">
                            <span class="font-bold text-gray-800 text-lg flex items-center gap-2">
                                <el-icon class="text-orange-500"><VideoCamera /></el-icon> 客显屏实时映射
                            </span>
                            <div class="flex items-center gap-2">
                                <span class="flex h-2 w-2 relative">
                                    <span v-if="wsConnected" class="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                                    <span class="relative inline-flex rounded-full h-2 w-2" :class="wsConnected ? 'bg-green-500' : 'bg-red-500'"></span>
                                </span>
                                <span class="text-xs font-mono text-gray-500">{{ wsConnected ? '通讯直连中' : '离线' }}</span>
                            </div>
                        </div>
                    </template>

                    <div class="mt-4 relative pointer-events-none">
                        <div class="relative bg-gray-900 rounded-xl p-3 shadow-2xl border-b-8 border-gray-800 aspect-[4/3] flex flex-col">
                            <div class="w-full h-full bg-black rounded overflow-hidden relative border border-gray-950">

                                <div v-if="!settings.enabled" class="absolute inset-0 bg-black flex flex-col items-center justify-center text-gray-800 z-50">
                                    <el-icon class="text-5xl mb-2 opacity-30"><Monitor /></el-icon>
                                    <span class="font-mono text-xs tracking-widest opacity-30">DISPLAY OFF</span>
                                </div>

                                <template v-else-if="posState === 'IDLE'">
                                    <el-carousel height="100%" :interval="settings.interval * 1000" arrow="never" indicator-position="none" class="w-full h-full">
                                        <el-carousel-item v-for="(url, index) in settings.playlist" :key="index">
                                            <img :src="url" class="w-full h-full object-cover" />
                                        </el-carousel-item>
                                    </el-carousel>
                                    <div class="absolute bottom-0 left-0 right-0 h-8 bg-black/70 backdrop-blur-md flex items-center px-3 z-20 overflow-hidden">
                                        <div class="animate-marquee whitespace-nowrap text-yellow-400 text-xs font-bold tracking-wide">
                                            {{ settings.welcomeText || '欢迎光临麦尼收银系统！' }}
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
                                                <div v-for="(item, idx) in realCart" :key="idx" class="bg-white p-1 rounded shadow-sm border border-gray-100 flex justify-between text-[9px]">
                                                    <div class="flex flex-col overflow-hidden pr-1">
                                                        <span class="font-bold text-gray-800 truncate">{{ item.name || '商品' }}</span>
                                                        <span class="text-[10px] text-gray-400 font-bold mt-0.5">x {{ item.qty }}</span>
                                                    </div>
                                                    <div class="flex flex-col items-end justify-center shrink-0">
                                                        <span v-if="item.originalPrice > item.price" class="text-gray-400 line-through text-[8px]">￥{{ item.originalPrice.toFixed(2) }}</span>
                                                        <span class="font-mono font-bold" :class="item.originalPrice > item.price ? 'text-red-600' : 'text-gray-800'">
                                                            ￥{{ item.price.toFixed(2) }}
                                                        </span>
                                                    </div>
                                                </div>
                                            </div>

                                            <div v-if="showParticipatingAmount" class="absolute bottom-0 left-0 right-0 border-t p-1.5 text-center bg-blue-50 border-blue-200 shrink-0">
                                                <span class="text-[9px] font-bold text-blue-600 flex items-center justify-center gap-1 px-1 truncate">
                                                    <el-icon><Present /></el-icon>
                                                    当前符合满减活动的总额: ￥{{ realParticipatingAmount.toFixed(2) }}
                                                </span>
                                            </div>
                                        </div>

                                        <div class="w-[45%] h-full flex flex-col items-center justify-center p-2 relative bg-white">
                                            <div class="text-gray-500 text-[10px] font-bold mb-0.5">请支付</div>
                                            <div class="text-red-600 font-mono font-black text-2xl mb-4 leading-none">
                                                ￥{{ (realFinalPay > 0 ? realFinalPay : realTotal).toFixed(2) }}
                                            </div>

                                            <div v-if="isCashOnly" class="flex flex-col items-center w-full px-2 border-t border-dashed pt-3 mt-2">
                                                <div class="flex justify-between w-full text-[10px] text-gray-600 font-bold mb-1">
                                                    <span>实收现金：</span>
                                                    <span class="font-mono text-gray-900">￥{{ cashPayment.amount.toFixed(2) }}</span>
                                                </div>
                                                <div class="flex justify-between w-full text-[12px] font-black mt-1">
                                                    <span class="text-green-600">找零：</span>
                                                    <span class="font-mono text-green-600 text-lg">￥{{ changeAmount.toFixed(2) }}</span>
                                                </div>
                                            </div>

                                            <div v-else class="flex gap-2 justify-center flex-wrap w-full mb-2">
                                                <div v-if="defaultPaymentCodes.length === 0" class="text-gray-300 text-[10px] border border-dashed border-gray-200 p-4 rounded text-center">未配置收款码</div>
                                                <div v-else v-for="(code, cidx) in defaultPaymentCodes" :key="cidx" class="flex flex-col items-center">
                                                    <div class="p-1 border border-gray-200 shadow-sm rounded bg-white"><img :src="code.url" class="w-10 h-10 object-cover" /></div>
                                                    <span class="text-[8px] text-gray-600 font-bold mt-1 scale-90">{{ code.name }}</span>
                                                </div>
                                            </div>
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
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import { Monitor, Timer, PictureRounded, Select, VideoCamera, VideoPlay, Delete, Plus, SuccessFilled, UploadFilled, Close, ChatDotRound, CreditCard, Present } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/index.js'
import axios from 'axios'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)

const baseUrl = import.meta.env.VITE_BASE_URL || '/api'
const uploadUrl = ref(baseUrl + '/common/upload')
const headers = computed(() => ({ Authorization: 'Bearer ' + userStore.token }))

const settings = reactive({
    enabled: true,
    interval: 5,
    welcomeText: '欢迎光临！今日全场满99减20...',
    paymentCodes: [],
    library: [],
    playlist: []
})

// ==========================================
// 🌟 接收器引擎与动态交互
// ==========================================
const wsConnected = ref(false);
const posState = ref('IDLE');
const realCart = ref([]);
const realTotal = ref(0);
const realMember = ref(null);
const realParticipatingAmount = ref(0);

const realPayments = ref([]);
const realFinalPay = ref(0);

const cartContainerRef = ref(null);
let receiverWs = null;

const initReceiver = () => {
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const hostname = window.location.hostname;
    const wsUrl = `${wsProtocol}//${hostname}:9101/money-pos/ws/pos-sync`;

    receiverWs = new WebSocket(wsUrl);
    receiverWs.onopen = () => { wsConnected.value = true; };
    receiverWs.onclose = () => { wsConnected.value = false; setTimeout(initReceiver, 5000); };
    receiverWs.onerror = () => { wsConnected.value = false; };

    receiverWs.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data);
            const { state, cart, total, pAmount, member, payments, finalPay } = data;

            if (cart) realCart.value = cart;
            if (total !== undefined) realTotal.value = total;
            if (pAmount !== undefined) realParticipatingAmount.value = pAmount;
            if (member !== undefined) realMember.value = member;
            if (payments !== undefined) realPayments.value = payments;
            if (finalPay !== undefined) realFinalPay.value = finalPay;

            if (state === 'IDLE') posState.value = 'IDLE';
            else if (state === 'CASHIER_UPDATE' || state === 'CHECKOUT_OPEN') posState.value = 'CASHIER';
            else if (state === 'PAY_SUCCESS') {
                posState.value = 'SUCCESS';
                setTimeout(() => {
                    posState.value = 'IDLE';
                    realCart.value = [];
                    realMember.value = null;
                    realPayments.value = [];
                }, 3000);
            }
        } catch(e) {}
    };
}

// 自动触底滚动
watch(() => realCart.value, async () => {
    await nextTick();
    if (cartContainerRef.value) {
        cartContainerRef.value.scrollTop = cartContainerRef.value.scrollHeight;
    }
}, { deep: true });

// ==========================================
// 🌟 核心判断阀门：是否具备展示满减提示的资格？
// ==========================================
const showParticipatingAmount = computed(() => {
    return realMember.value &&                                   // 必须登录了会员
           Array.isArray(realMember.value.couponList) &&         // 且携带了真实的券列表数据
           realMember.value.couponList.length > 0 &&             // 且手里确实有券 (数量 > 0)
           realParticipatingAmount.value > 0;                    // 且当前购物车里包含参与满减的商品
});

// ==========================================
// 🌟 现金找零智能判定逻辑
// ==========================================
const cashPayment = computed(() => {
    if (!realPayments.value || realPayments.value.length === 0) return null;
    return realPayments.value.find(p => p.code && p.code.includes('CASH') && p.amount > 0);
});

const isCashOnly = computed(() => {
    if (!realPayments.value || realPayments.value.length === 0) return false;
    const hasCash = realPayments.value.some(p => p.code && p.code.includes('CASH') && p.amount > 0);
    const hasOther = realPayments.value.some(p => p.code && !p.code.includes('CASH') && p.amount > 0);
    return hasCash && !hasOther;
});

const changeAmount = computed(() => {
    if (!cashPayment.value) return 0;
    const totalPaid = realPayments.value.reduce((sum, p) => sum + (p.amount || 0), 0);
    const targetPay = realFinalPay.value > 0 ? realFinalPay.value : realTotal.value;
    return totalPaid > targetPay ? totalPaid - targetPay : 0;
});
// ==========================================


const defaultPaymentCodes = computed(() => settings.paymentCodes.filter(c => c.isDefault))

const maskName = (str) => {
    if (!str) return '会员';
    return str.length >= 2 ? str.substring(0,1) + '**' : str;
}

const addToPlaylist = (url) => { if (!settings.playlist.includes(url)) settings.playlist.push(url); }
const removeFromPlaylist = (index) => settings.playlist.splice(index, 1);
const deleteFromLibrary = (index) => {
    ElMessageBox.confirm('彻底删除素材？', '警告', { type: 'warning' }).then(() => {
        const url = settings.library[index];
        settings.library.splice(index, 1);
        const pIdx = settings.playlist.indexOf(url);
        if (pIdx > -1) settings.playlist.splice(pIdx, 1);
    }).catch(() => {});
}
const addPaymentCode = () => settings.paymentCodes.push({ name: '', url: '', isDefault: false });
const removePaymentCode = (index) => settings.paymentCodes.splice(index, 1);
const handleQrUploadSuccess = (res, index) => {
    const raw = res.data?.data || res.data || res;
    if (raw?.url) { settings.paymentCodes[index].url = raw.url; ElMessage.success('收款码已上传'); }
}
const handleBeforeUpload = (file) => file.size / 1024 / 1024 < 10;
const handleUploadSuccess = (res) => {
    const raw = res.data?.data || res.data || res;
    if (raw?.url) { settings.library.unshift(raw.url); ElMessage.success('素材上传成功！'); }
}

const fetchSettings = async () => {
    loading.value = true;
    try {
        const res = await axios.get(`${baseUrl}/common/display-settings`, { headers: headers.value });
        const rawData = res.data.data || res.data;
        if (rawData && rawData !== "{}") {
            const data = typeof rawData === 'string' ? JSON.parse(rawData) : rawData;
            if (!data.paymentCodes) data.paymentCodes = [];
            Object.assign(settings, data);
        }
    } finally { loading.value = false; }
}

const saveSettings = async () => {
    saving.value = true;
    try {
        await axios.put(`${baseUrl}/common/display-settings`, settings, { headers: headers.value });
        ElMessage.success('客显与结算规则已永久保存！');
    } catch (e) { ElMessage.error('保存失败'); }
    finally { saving.value = false; }
}

onMounted(() => {
    fetchSettings();
    initReceiver();
})

onUnmounted(() => {
    if (receiverWs) receiverWs.close();
})
</script>

<style scoped>
@keyframes marquee { 0% { transform: translateX(100%); } 100% { transform: translateX(-100%); } }
.animate-marquee { display: inline-block; padding-left: 100%; animation: marquee 15s linear infinite; }

.scrollbar-hide::-webkit-scrollbar { display: none; }
.scrollbar-hide { -ms-overflow-style: none; scrollbar-width: none; }
.scroll-smooth { scroll-behavior: smooth; }
</style>