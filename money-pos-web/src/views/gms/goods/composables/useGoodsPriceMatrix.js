import { reactive, computed } from 'vue';
import { req } from '@/api/index.js';
import NP from 'number-precision';
import { ElMessageBox } from 'element-plus';

export function useGoodsPriceMatrix(dictRef) {
    // 核心响应式状态
    const matrix = reactive({
        loading: false,
        couponEnabled: false,
        rawCodes: [],
        prices: {},
        coupons: {}
    });

    // 数据库原始快照（用于比对和防串台）
    const dbSnapshot = reactive({
        brandId: null,
        prices: {},
        coupons: {}
    });

    // 解析品牌策略返回的等级 codes
    const parseCodes = (data) => {
        if (!data) return [];
        if (Array.isArray(data)) return data.map(String);
        return String(data).replace(/[\[\]"'\s]/g, '').split(',').filter(Boolean);
    };

    // 计算当前可用的有效特权等级
    const validLevels = computed(() => {
        const allDict = dictRef.value?.memberType || [];
        const codes = matrix.rawCodes || [];
        return allDict.filter(item => codes.includes(String(item.value)));
    });

    // 🌟 物理级重置状态
    const resetMatrixState = () => {
        matrix.loading = false;
        matrix.couponEnabled = false;
        matrix.rawCodes = [];
        matrix.prices = {};
        matrix.coupons = {};
    };

    // 初始化快照（在编辑打开时调用一次）
    const initSnapshot = (brandId, prices, coupons) => {
        dbSnapshot.brandId = brandId ? String(brandId) : null;
        dbSnapshot.prices = JSON.parse(JSON.stringify(prices || {}));
        dbSnapshot.coupons = JSON.parse(JSON.stringify(coupons || {}));
    };

    // 🌟 核心：加载品牌策略
    const initMatrixForBrand = async (brandId, isInitialLoad = false) => {
        if (!brandId) {
            resetMatrixState();
            return;
        }

        matrix.loading = true;
        try {
            const res = await req({ url: '/gms/brand/config', method: 'GET', params: { brandId } });
            const data = res?.data || {};
            const raw = data.levelCodes || data.levelCode || data.levels || data.levelIds;

            if (raw != null && raw !== '') {
                matrix.couponEnabled = data.couponEnabled === 1 || data.couponEnabled === true;
                matrix.rawCodes = parseCodes(raw);
            } else {
                matrix.rawCodes = [];
            }
        } catch (e) {
            console.error("品牌策略读取异常", e);
            matrix.rawCodes = [];
        } finally {
            matrix.loading = false;
        }

        matrix.prices = {};
        matrix.coupons = {};

        // 仅在首次拉取且品牌匹配时，灌入历史配置
        if (isInitialLoad && String(brandId) === dbSnapshot.brandId) {
            Object.keys(dbSnapshot.prices).forEach(k => matrix.prices[k] = dbSnapshot.prices[k]);
            Object.keys(dbSnapshot.coupons).forEach(k => matrix.coupons[k] = dbSnapshot.coupons[k]);
        }
    };

    // 🌟 核心：提交前的数据最后通牒审查
    const validateMatrixValues = (sourceObj) => {
        const result = {};
        const allowed = validLevels.value.map(l => String(l.value));
        for (const key of Object.keys(sourceObj)) {
            if (allowed.includes(key)) {
                const val = Number(sourceObj[key]);
                if (!isNaN(val) && val >= 0) {
                    result[key] = NP.round(val, 2); // 强制规范为至多两位小数
                } else {
                    throw new Error(`等级[${key}]的配置金额 [${sourceObj[key]}] 不合法，必须为非负数`);
                }
            }
        }
        return result;
    };

    // 🌟 边界钳制公式：算券
    const calcCoupon = (code, currentSalePrice) => {
        if (!matrix.couponEnabled) return;
        const saleP = Number(currentSalePrice) || 0;
        let memberP = Number(matrix.prices[code]) || 0;

        if (memberP < 0) memberP = 0;
        if (memberP > saleP) memberP = saleP;
        matrix.prices[code] = memberP;
        matrix.coupons[code] = NP.minus(saleP, memberP);
    };

    // 🌟 边界钳制公式：算价
    const calcPrice = (code, currentSalePrice) => {
        if (!matrix.couponEnabled) return;
        const saleP = Number(currentSalePrice) || 0;
        let couponP = Number(matrix.coupons[code]) || 0;

        if (couponP < 0) couponP = 0;
        if (couponP > saleP) couponP = saleP;
        matrix.coupons[code] = couponP;
        matrix.prices[code] = NP.minus(saleP, couponP);
    };

    const syncAllCoupons = (currentSalePrice) => {
        if (!matrix.couponEnabled) return;
        Object.keys(matrix.prices).forEach(code => calcCoupon(code, currentSalePrice));
    };

    // 🌟 防误触确认拦截器
    const checkBrandSwitch = async () => {
        if (Object.keys(matrix.prices).length > 0) {
            await ElMessageBox.confirm('切换品牌将清空当前已配置的价格矩阵，是否继续？', '防误触警告', { type: 'warning' });
            return true; // 同意切换
        }
        return true;
    };

    return {
        matrix,
        dbSnapshot,
        validLevels,
        initSnapshot,
        resetMatrixState,
        initMatrixForBrand,
        validateMatrixValues,
        calcCoupon,
        calcPrice,
        syncAllCoupons,
        checkBrandSwitch
    };
}