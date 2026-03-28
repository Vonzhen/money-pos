<template>
    <el-dialog
        v-model="visible"
        title="✨ 极速新建商品 (全策略版)"
        width="850px"
        top="5vh"
        destroy-on-close
        :close-on-click-modal="false"
        class="quick-add-dialog"
        @open="initForm"
        @closed="$emit('closed')"
    >
        <div v-loading="loading" class="px-2 max-h-[65vh] overflow-y-auto overflow-x-hidden">
            <el-alert
                title="扫码未找到该商品，请补全档案信息后即可入库/收银"
                type="warning"
                show-icon
                :closable="false"
                class="mb-2 font-bold"
            />

            <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
                <div class="form-flat-container">
                    <GoodsBaseForm :form="form" :brands="brands" :categories="categories" />
                    <GoodsPriceMatrix :form="form" :dict="dict" />
                    <GoodsStatusForm :form="form" :dict="dict" />
                </div>
            </el-form>
        </div>

        <template #footer>
            <div class="flex justify-end gap-3 pt-2">
                <el-button @click="visible = false" size="large">取 消</el-button>
                <el-button type="primary" @click="submitForm" :loading="loading" size="large" class="font-bold px-8 shadow-sm">
                    建档并载入业务 (Enter)
                </el-button>
            </div>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import NP from 'number-precision'
import { req } from '@/api/index.js' // 🌟 引入万能 req 请求

import GoodsBaseForm from "@/views/gms/goods/components/GoodsBaseForm.vue"
import GoodsPriceMatrix from "@/views/gms/goods/components/GoodsPriceMatrix.vue"
import GoodsStatusForm from "@/views/gms/goods/components/GoodsStatusForm.vue"

import goodsApi from "@/api/gms/goods.js"
import brandApi from "@/api/gms/brand.js"
import goodsCategoryApi from "@/api/gms/goodsCategory.js"
import dictApi from "@/api/system/dict.js"

const props = defineProps(['modelValue', 'initBarcode'])
const emit = defineEmits(['update:modelValue', 'closed', 'success'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const loading = ref(false)
const formRef = ref(null)

const dict = ref({})
const brands = ref([])
const categories = ref([])

const form = ref({
    barcode: '', name: '', brandId: null, categoryId: null,
    purchasePrice: '', salePrice: '', stock: 0, status: 'SALE', isDiscountParticipable: 1,
    _matrixPrices: {}, _matrixCoupons: {}, _couponEnabled: false, _validLevels: []
})

const checkPrice = (rule, value, callback) => {
    const num = Number(value);
    if (isNaN(num) || num < 0) callback(new Error('必须为非负金额')); else callback();
};

const rules = {
    brandId: [{required: true, message: '请选择品牌'}],
    barcode: [{required: true, message: '请输入条码'}],
    name: [{required: true, message: '请输入名称'}],
    purchasePrice: [{required: true, message: '进价必填'}, {validator: checkPrice, trigger: 'blur'}],
    salePrice: [{required: true, message: '售价必填'}, {validator: checkPrice, trigger: 'blur'}],
    stock: [{required: true, message: '库存必填'}],
    status: [{required: true, message: '状态必填'}]
}

const validatePayloadMatrix = (prices, coupons, isDouble, validLevels) => {
    const check = (source) => {
        const res = {};
        const allowed = (validLevels || []).map(l => String(l.value));
        for (const key of Object.keys(source || {})) {
            if (allowed.includes(key)) {
                const val = Number(source[key]);
                if (isNaN(val) || val < 0) throw new Error(`等级[${key}]的价格不合法，必须为非负数`);
                res[key] = NP.round(val, 2);
            }
        }
        return res;
    };
    return {
        levelPrices: check(prices),
        levelCoupons: isDouble ? check(coupons) : {}
    };
};

const initForm = async () => {
    loading.value = true
    try {
        form.value = {
            barcode: props.initBarcode || '', name: '', brandId: null, categoryId: null,
            purchasePrice: '', salePrice: '', stock: 0, status: 'SALE', isDiscountParticipable: 1,
            _matrixPrices: {}, _matrixCoupons: {}, _couponEnabled: false, _validLevels: []
        }

        const [brandRes, catRes, dictRes] = await Promise.all([
            brandApi.getSelect(),
            goodsCategoryApi.getSelect(),
            dictApi.loadDict(["goodsStatus", "memberType"])
        ])
        brands.value = brandRes.data || []
        categories.value = catRes.data || []
        dict.value = dictRes || {}

    } catch (e) {
        ElMessage.error("基础数据加载失败")
    } finally {
        loading.value = false
    }
}

const submitForm = async () => {
    if (!formRef.value) return
    await formRef.value.validate(async (valid) => {
        if (valid) {
            try {
                loading.value = true
                const payload = { ...form.value }

                if (payload._validLevels && payload._validLevels.length > 0) {
                    const { levelPrices, levelCoupons } = validatePayloadMatrix(
                        payload._matrixPrices, payload._matrixCoupons, payload._couponEnabled, payload._validLevels
                    );
                    payload.levelPrices = levelPrices;
                    payload.levelCoupons = levelCoupons;
                }

                delete payload._matrixPrices; delete payload._matrixCoupons;
                delete payload._couponEnabled; delete payload._validLevels;

                // 1. 发送建档请求
                await goodsApi.add(payload)
                ElMessage.success('极速建档成功！')

                // 2. 🌟 修复崩溃点：使用万能的 /pos/goods 接口把刚建好的商品拉回来
                const res = await req({ url: '/pos/goods', method: 'GET', params: { barcode: payload.barcode } });
                if (res.data && res.data.length > 0) {
                    emit('success', res.data[0]);
                }

                // 3. 完美关闭弹窗
                visible.value = false
            } catch (e) {
                ElMessage.error(e.message || '建档失败')
            } finally {
                loading.value = false
            }
        }
    })
}
</script>

<style scoped>
/* 🌟 化骨绵掌：拍扁后台原生组件里的嵌套卡片，让弹窗清爽无比 */
.form-flat-container :deep(.el-card) {
    border: none !important;
    box-shadow: none !important;
    background: transparent !important;
    border-bottom: 1px dashed #e5e7eb !important;
    margin-bottom: 10px !important;
    border-radius: 0 !important;
}
.form-flat-container :deep(.el-card__header) {
    padding: 10px 0 !important;
    border-bottom: none !important;
}
.form-flat-container :deep(.el-card__body) {
    padding: 10px 0 0 0 !important;
}
/* 去掉最后一个虚线底边 */
.form-flat-container > div:last-child :deep(.el-card) {
    border-bottom: none !important;
}
</style>