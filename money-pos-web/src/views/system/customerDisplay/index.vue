<template>
    <PageWrapper>
        <div class="flex flex-col lg:flex-row gap-6">
            <div class="flex-1">
                <el-card shadow="never" class="border-none shadow-sm rounded-xl h-full">
                    <template #header>
                        <div class="flex items-center gap-2 font-bold text-gray-800 text-lg">
                            <el-icon class="text-blue-500"><Monitor /></el-icon> 客显播控与终端状态机配置
                        </div>
                    </template>
                    <el-form :model="settings" label-position="top" class="mt-2" v-loading="loading">
                        <div class="bg-blue-50/50 p-4 rounded-lg border border-blue-100 mb-6 flex justify-between items-center">
                            <div>
                                <h4 class="font-bold text-blue-800 text-base">启用客显系统驱动</h4>
                                <p class="text-xs text-gray-500 mt-1">总控开关。关闭后，客显屏将强制进入休眠状态。</p>
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
                                    <template #label><span class="font-bold text-gray-700 flex items-center gap-1"><el-icon><ChatDotRound /></el-icon> 底部滚动公告标语</span></template>
                                    <el-input v-model="settings.welcomeText" placeholder="请输入客显底部显示的滚动文字..." clearable />
                                </el-form-item>
                            </div>
                            <div class="mt-4 mb-6">
                                <div class="font-bold text-gray-700 flex items-center gap-1 mb-3"><el-icon class="text-green-500"><VideoPlay /></el-icon> 待机轮播列表 (排期)</div>
                                <div class="bg-gray-50 p-4 rounded-lg border border-gray-200 min-h-[120px] flex flex-wrap gap-4 items-center">
                                    <div v-if="settings.playlist.length === 0" class="text-gray-400 text-sm w-full text-center py-4">暂无播放内容 👇</div>
                                    <div v-for="(url, index) in settings.playlist" :key="'play_'+index" class="relative group cursor-pointer">
                                        <div class="absolute -top-2 -left-2 w-6 h-6 bg-green-500 text-white rounded-full flex items-center justify-center text-xs font-black z-10 shadow">{{ index + 1 }}</div>
                                        <el-image :src="url" class="w-24 h-16 rounded shadow-sm border border-gray-300 object-cover" />
                                        <div @click="removeFromPlaylist(index)" class="absolute inset-0 bg-red-500/80 text-white opacity-0 group-hover:opacity-100 transition-opacity rounded flex flex-col items-center justify-center"><el-icon><Delete /></el-icon></div>
                                    </div>
                                </div>
                            </div>
                            <div class="mb-8">
                                <div class="font-bold text-gray-700 flex items-center gap-1 mb-3"><el-icon class="text-orange-500"><PictureRounded /></el-icon> 门店海报素材库</div>
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
                                    <div class="font-bold text-gray-700 flex items-center gap-1"><el-icon class="text-blue-600"><CreditCard /></el-icon> 收款码资产映射与默认展示</div>
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
                                            <div class="flex flex-col gap-1"><el-switch v-model="item.isDefault" active-text="设为默认" size="small" /></div>
                                        </div>
                                    </div>
                                    <div @click="addPaymentCode" class="border-2 border-dashed rounded-lg flex flex-col items-center justify-center text-gray-400 hover:text-blue-500 cursor-pointer min-h-[110px] bg-white">
                                        <el-icon class="text-2xl"><Plus /></el-icon><span class="text-xs mt-2 font-bold">新增收款通道</span>
                                    </div>
                                </div>
                            </div>
                            <div class="mt-10 pt-6 border-t border-gray-100 flex items-center gap-4">
                                <el-button type="primary" size="large" class="w-full md:w-64 font-bold tracking-widest shadow-lg" @click="saveSettings" :loading="saving"><el-icon class="mr-1"><Select /></el-icon> 保存播控与结算规则</el-button>
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
                                    <div class="absolute bottom-0 left-0 right-0 h-8 bg-black/70 backdrop-blur-md flex items-center px-3 z-20 overflow-hidden">
                                        <div class="animate-marquee whitespace-nowrap text-yellow-400 text-xs font-bold tracking-wide">{{ settings.welcomeText || '欢迎光临！' }}</div>
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
                                                <div class="text-red-600 font-mono font-black text-3xl mb-2 leading-none">￥{{ (realPayment.targetPay - realPayment.tendered).toFixed(2) }}</div>
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
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import { Monitor, Timer, PictureRounded, Select, VideoCamera, VideoPlay, Delete, Plus, SuccessFilled, UploadFilled, Close, ChatDotRound, CreditCard, Present, Link } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/index.js'
import axios from 'axios'
import { getToken } from '@/composables/token.js'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)

let apiBaseUrl = import.meta.env.VITE_BASE_URL;
if (window.location.protocol === 'file:') {
    apiBaseUrl = 'http://127.0.0.1:9101/money-pos';
}

const uploadUrl = ref(apiBaseUrl + '/sys/oss/upload')
const headers = computed(() => ({ Authorization: 'Bearer ' + getToken() }))

const settings = reactive({ enabled: true, interval: 5, welcomeText: '欢迎光临！今日全场满99减20...', paymentCodes: [], library: [], playlist: [] })

const posState = ref('OFFLINE');

const realCart = ref([]);
const realMember = ref(null);
const realParticipatingAmount = ref(0);
const realPayment = ref({ targetPay: 0, tendered: 0, aggregate: 0, change: 0 });

const cartContainerRef = ref(null);
let receiverWs = null;

// ==========================================
// 🌟 核心修复：防御性数据字段解析，完美适配后端 Java DTO
// ==========================================
const getPrice = (item) => Number(item.price ?? item.unitRealPrice ?? 0);
const getOriginalPrice = (item) => Number(item.originalPrice ?? item.unitOriginalPrice ?? 0);
const getSubtotal = (item) => Number(item.subtotal ?? item.subTotalRetail ?? item.subTotalMember ?? 0);
const getQty = (item) => Number(item.qty ?? item.quantity ?? 1);

const initReceiver = () => {
    let wsHost = window.location.hostname || '127.0.0.1';
    let wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';

    if (window.location.protocol === 'file:') {
        wsHost = '127.0.0.1';
        wsProtocol = 'ws:';
    }

    const wsUrl = `${wsProtocol}//${wsHost}:9101/money-pos/ws/pos-sync`;

    receiverWs = new WebSocket(wsUrl);

    receiverWs.onopen = () => { posState.value = 'STANDBY'; };
    receiverWs.onclose = () => { posState.value = 'OFFLINE'; setTimeout(initReceiver, 5000); };
    receiverWs.onerror = () => { posState.value = 'OFFLINE'; };

    receiverWs.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data);
            const { state, cart, pAmount, member, payment } = data;

            if (!state) return;

            if (cart) realCart.value = cart;
            if (pAmount !== undefined) realParticipatingAmount.value = pAmount;
            if (member !== undefined) realMember.value = member;
            if (payment !== undefined) realPayment.value = payment;

            if (state === 'IDLE') posState.value = 'STANDBY';
            else if (state === 'CASHIER_UPDATE' || state === 'CHECKOUT_OPEN') posState.value = 'CASHIER';
            else if (state === 'PAY_SUCCESS') {
                posState.value = 'SUCCESS';
                setTimeout(() => {
                    posState.value = 'STANDBY';
                    realCart.value = [];
                    realMember.value = null;
                    realPayment.value = { targetPay: 0, tendered: 0, aggregate: 0, change: 0 };
                }, 3000);
            }
        } catch(e) {}
    };
}

watch(() => realCart.value, async () => {
    await nextTick();
    if (cartContainerRef.value) cartContainerRef.value.scrollTop = cartContainerRef.value.scrollHeight;
}, { deep: true });

const showParticipatingAmount = computed(() => {
    return realMember.value && Array.isArray(realMember.value.couponList) && realMember.value.couponList.length > 0 && realParticipatingAmount.value > 0;
});

const defaultPaymentCodes = computed(() => settings.paymentCodes.filter(c => c.isDefault))
const maskName = (str) => { return (!str) ? '会员' : (str.length >= 2 ? str.substring(0,1) + '**' : str); }

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
    if (raw?.url || typeof raw === 'string') {
        settings.paymentCodes[index].url = raw.url || raw;
        ElMessage.success('收款码已上传');
    }
}
const handleBeforeUpload = (file) => file.size / 1024 / 1024 < 10;
const handleUploadSuccess = (res) => {
    const raw = res.data?.data || res.data || res;
    if (raw?.url || typeof raw === 'string') {
        settings.library.unshift(raw.url || raw);
        ElMessage.success('素材上传成功！');
    }
}

const fetchSettings = async () => {
    loading.value = true;
    try {
        const res = await axios.get(`${apiBaseUrl}/common/display-settings`, { headers: headers.value });
        const rawData = res.data.data || res.data;
        if (rawData && rawData !== "{}") {
            const data = typeof rawData === 'string' ? JSON.parse(rawData) : rawData;
            if (!data.paymentCodes) data.paymentCodes = [];
            Object.assign(settings, data);
        }
    } catch (e) {
        console.error("获取设置失败", e);
    } finally {
        loading.value = false;
    }
}

const saveSettings = async () => {
    saving.value = true;
    try {
        await axios.put(`${apiBaseUrl}/common/display-settings`, settings, { headers: headers.value });
        ElMessage.success('客显与结算规则已永久保存！');
    } catch (e) {
        ElMessage.error('保存失败');
    } finally {
        saving.value = false;
    }
}

onMounted(() => { fetchSettings(); initReceiver(); })
onUnmounted(() => { if (receiverWs) receiverWs.close(); })
</script>

<style scoped>
@keyframes marquee { 0% { transform: translateX(100%); } 100% { transform: translateX(-100%); } }
.animate-marquee { display: inline-block; padding-left: 100%; animation: marquee 15s linear infinite; }
.scrollbar-hide::-webkit-scrollbar { display: none; }
.scrollbar-hide { -ms-overflow-style: none; scrollbar-width: none; }
.scroll-smooth { scroll-behavior: smooth; }
</style>