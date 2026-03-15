<template>
    <div class="bg-blue-50/40 p-5 rounded-lg border border-blue-200 mb-5 relative shadow-sm" v-loading="matrix.loading">
        <div v-if="form.brandId && !matrix.loading && validLevels.length > 0"
             class="absolute right-4 top-4 flex items-center gap-2 text-xs font-bold px-3 py-1 rounded bg-white shadow-sm border border-blue-100"
             :class="matrix.couponEnabled ? 'text-green-600' : 'text-blue-600'">
            <div :class="matrix.couponEnabled ? 'bg-green-500' : 'bg-blue-500'" class="w-2 h-2 rounded-full animate-pulse"></div>
            {{ matrix.couponEnabled ? '已开启【价+券】双轨' : '已开启【仅会员价】单轨' }}
        </div>

        <h4 class="font-bold text-blue-800 mb-4 flex items-center gap-2"><el-icon><MoneyIcon /></el-icon> 会员价格与策略配置</h4>

        <div v-if="form.brandId && !matrix.loading">
            <div v-if="validLevels.length > 0">
                <div class="text-sm font-bold text-gray-500 mb-3">该品牌配置的会员特权定价槽位：</div>
                <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <div v-for="level in validLevels" :key="level.value" class="bg-white p-3 rounded-md border border-gray-200 shadow-sm hover:border-blue-300 transition-colors flex flex-col justify-center">
                        <div class="font-bold text-blue-700 text-sm border-b border-gray-100 pb-1 mb-2 flex justify-between">
                            <span>{{ level.desc }}</span>
                        </div>
                        <div class="flex items-center gap-2" :class="matrix.couponEnabled ? 'mb-2' : ''">
                            <span class="text-xs text-gray-500 w-10 shrink-0">售价:</span>
                            <el-input v-model="matrix.prices[level.value]" size="small" placeholder="￥0.00" class="flex-1 font-bold text-gray-800" @input="() => handleCalcCoupon(level.value)" />
                        </div>
                        <div v-if="matrix.couponEnabled" class="flex items-center gap-2">
                            <span class="text-xs text-green-600 font-bold w-10 shrink-0">专券:</span>
                            <el-input v-model="matrix.coupons[level.value]" size="small" placeholder="差额" class="flex-1 text-green-600" @input="() => handleCalcPrice(level.value)" />
                        </div>
                    </div>
                </div>
            </div>
            <div v-else-if="matrix.rawCodes.length > 0" class="text-center text-red-500 py-6 border-2 border-red-200 border-dashed rounded bg-white">
                <el-icon class="text-3xl mb-1"><Warning /></el-icon><br/>
                <span class="font-bold">⚠️ 品牌下发了等级代码，但系统字典未配置。</span>
            </div>
            <div v-else class="text-center text-orange-500 py-6 border-2 border-orange-200 border-dashed rounded bg-white">
                <span class="font-bold">⚠️ 该品牌尚未配置有效的会员定价策略。</span>
            </div>
        </div>
        <div v-else-if="!form.brandId" class="text-center text-gray-400 py-6 border-2 border-dashed rounded bg-white">
            请先在上方选择【品牌归属】
        </div>
    </div>
</template>

<script setup>
import { watch, onMounted, computed } from 'vue';
import { Money as MoneyIcon, Warning } from '@element-plus/icons-vue';
import { useGoodsPriceMatrix } from '../composables/useGoodsPriceMatrix.js';

const props = defineProps({
    form: { type: Object, required: true },
    dict: { type: Object, required: true }
});

const dictRef = computed(() => props.dict);
const {
    matrix, validLevels, initSnapshot, initMatrixForBrand,
    calcCoupon, calcPrice, syncAllCoupons, checkBrandSwitch
} = useGoodsPriceMatrix(dictRef);

let isRevertingBrand = false;

// 🌟 强效同步：将本地状态深度同步给主表单载荷
const syncToFormPayload = () => {
    if (!props.form) return;
    props.form._matrixPrices = JSON.parse(JSON.stringify(matrix.prices));
    props.form._matrixCoupons = JSON.parse(JSON.stringify(matrix.coupons));
    props.form._couponEnabled = matrix.couponEnabled;
    props.form._validLevels = validLevels.value;
};

// 监听本地变动
watch(matrix, syncToFormPayload, { deep: true });

// 🌟 核心公式联动：零售价一变，所有分摊券额自动重算
watch(() => props.form.salePrice, (newVal) => {
    if (newVal) syncAllCoupons(newVal);
});

// 品牌切换保护
watch(() => props.form.brandId, async (newBrandId, oldBrandId) => {
    if (isRevertingBrand) { isRevertingBrand = false; return; }
    if (!newBrandId) { await initMatrixForBrand(null); syncToFormPayload(); return; }

    if (oldBrandId && oldBrandId !== newBrandId && Object.keys(matrix.prices).length > 0) {
        try {
            await checkBrandSwitch();
        } catch {
            isRevertingBrand = true;
            props.form.brandId = oldBrandId;
            return;
        }
    }
    await initMatrixForBrand(newBrandId, false);
    syncToFormPayload();
});

onMounted(async () => {
    const brandId = props.form.brandId;
    if (props.form.id) {
        initSnapshot(brandId, props.form.levelPrices, props.form.levelCoupons);
    }
    await initMatrixForBrand(brandId, true);
    // 🌟 强制同步一次，确保初始状态也被捕获
    syncToFormPayload();
});

const handleCalcCoupon = (code) => calcCoupon(code, props.form.salePrice);
const handleCalcPrice = (code) => calcPrice(code, props.form.salePrice);
</script>