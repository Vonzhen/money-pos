<template>
    <PageWrapper>
        <div class="flex">
            <div class="mr-6 w-2/12 hidden lg:block">
                <GoodsCategory @node-click="selectGoodsCategory" />
            </div>

            <div class="grid gap-6 flex-1">
                <MoneyRR :money-crud="moneyCrud">
                    <SmartGoodsSelector
                        class="w-full md:!w-[350px]"
                        size="default"
                        placeholder="智能搜商品(支持名称/条码/拼音)"
                        @select="handleGoodsSelect"
                        @clear="handleGoodsClear"
                    />

                    <el-select v-model="moneyCrud.query.brandId" clearable class="w-full md:!w-48" placeholder="品牌" @change="moneyCrud.doQuery">
                        <el-option v-for="item in brands" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                    <el-select v-model="moneyCrud.query.categoryId" clearable class="w-full md:!w-48 md:!hidden" placeholder="分类" @change="moneyCrud.doQuery">
                        <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                    <el-select v-model="moneyCrud.query.status" clearable placeholder="状态" class="md:!w-48" @change="moneyCrud.doQuery">
                        <el-option v-for="item in dict.goodsStatus" :key="item.value" :label="item.desc" :value="item.value" />
                    </el-select>
                </MoneyRR>

                <div class="flex items-center gap-2 mb-[-10px]">
                    <MoneyCUD :money-crud="moneyCrud" />
                </div>

                <MoneyCrudTable :money-crud="moneyCrud">
                    <template #brand="{scope}">{{ brandsKv[scope.row.brandId] }}</template>
                    <template #status="{scope}"><el-tag :type="statusColor[scope.row.status] || 'primary'">{{ dict.goodsStatusKv[scope.row.status] }}</el-tag></template>

                    <template #salePrice="{scope}">
                        <MoneyDisplay :value="scope.row.salePrice" color="text-red-600" />
                    </template>
                    <template #purchasePrice="{scope}">
                        <MoneyDisplay :value="scope.row.purchasePrice" color="text-gray-400" />
                    </template>

                    <template #stock="{scope}">
                        <el-input v-if="editCell.id === scope.row.id" v-model="scope.row.stock" style="width: 55px" @change="value => updateCell(value, scope.row)" @focusout="updateCell(null, scope.row)" />
                        <span v-else @click="startEditCell(scope.row.id, 'stock', scope.row.stock)" class="font-mono font-bold cursor-pointer hover:text-blue-500">{{ scope.row.stock }}</span>
                    </template>
                    <template #opt="{scope}"><MoneyUD :money-crud="moneyCrud" :scope="scope" /></template>
                </MoneyCrudTable>

                <MoneyForm :money-crud="moneyCrud" :rules="rules" :dialog-class="'!w-11/12 md:!w-9/12 !mt-6'">

                    <div class="bg-gray-50 p-5 rounded-lg border border-gray-200 mb-5 shadow-sm">
                        <h4 class="font-bold text-gray-700 mb-4 flex items-center gap-2"><el-icon><Goods /></el-icon> 基础档案</h4>
                        <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
                            <el-form-item label="品牌归属" prop="brandId" class="mb-0 col-span-2 lg:col-span-1">
                                <el-select v-model="moneyCrud.form.brandId" class="w-full" placeholder="【必选】拉取策略">
                                    <el-option v-for="item in brands" :key="item.value" :label="item.label" :value="item.value" />
                                </el-select>
                            </el-form-item>
                            <el-form-item label="所属分类" prop="categoryId" class="mb-0 col-span-2 lg:col-span-1">
                                <el-select v-model="moneyCrud.form.categoryId" class="w-full" placeholder="请选择" clearable>
                                    <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
                                </el-select>
                            </el-form-item>
                            <el-form-item label="商品条码" prop="barcode" class="mb-0 col-span-2 lg:col-span-1">
                                <el-input v-model.trim="moneyCrud.form.barcode" placeholder="扫码枪录入或手输" />
                            </el-form-item>
                            <el-form-item label="商品名称" prop="name" class="mb-0 col-span-2 lg:col-span-1">
                                <el-input v-model.trim="moneyCrud.form.name" placeholder="请输入商品完整名称" />
                            </el-form-item>
                        </div>
                    </div>

                    <div class="bg-blue-50/40 p-5 rounded-lg border border-blue-200 mb-5 relative shadow-sm" v-loading="matrix.loading">

                        <div v-if="moneyCrud.form.brandId && !matrix.loading && validLevels.length > 0" class="absolute right-4 top-4 flex items-center gap-2 text-xs font-bold px-3 py-1 rounded bg-white shadow-sm border border-blue-100"
                             :class="matrix.couponEnabled ? 'text-green-600' : 'text-blue-600'">
                            <div :class="matrix.couponEnabled ? 'bg-green-500' : 'bg-blue-500'" class="w-2 h-2 rounded-full animate-pulse"></div>
                            {{ matrix.couponEnabled ? '已开启【价+券】双轨' : '已开启【仅会员价】单轨' }}
                        </div>

                        <h4 class="font-bold text-blue-800 mb-4 flex items-center gap-2"><el-icon><Money /></el-icon> 价格与策略配置</h4>

                        <div class="grid grid-cols-3 gap-6 border-b border-blue-100 pb-5 mb-5 items-center">
                            <el-form-item label="进货成本" prop="purchasePrice" class="mb-0">
                                <el-input v-model="moneyCrud.form.purchasePrice" placeholder="￥ 0.00" />
                            </el-form-item>
                            <el-form-item label="系统零售价" prop="salePrice" class="mb-0">
                                <el-input v-model="moneyCrud.form.salePrice" class="font-black text-red-600" placeholder="￥ 0.00" @input="syncAllCoupons" />
                            </el-form-item>
                            <el-form-item label="参与满减" prop="isDiscountParticipable" class="mb-0">
                                <el-switch v-model="moneyCrud.form.isDiscountParticipable" :active-value="1" :inactive-value="0" active-text="允许" inactive-text="禁止" inline-prompt style="--el-switch-on-color: #f97316;" />
                            </el-form-item>
                        </div>

                        <div v-if="moneyCrud.form.brandId && !matrix.loading">
                            <div v-if="validLevels.length > 0">
                                <div class="text-sm font-bold text-gray-500 mb-3">该品牌配置的会员特权定价槽位：</div>
                                <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
                                    <div v-for="level in validLevels" :key="level.value" class="bg-white p-3 rounded-md border border-gray-200 shadow-sm hover:border-blue-300 transition-colors flex flex-col justify-center">
                                        <div class="font-bold text-blue-700 text-sm border-b border-gray-100 pb-1 mb-2 flex justify-between">
                                            <span>{{ level.desc }}</span>
                                        </div>
                                        <div class="flex items-center gap-2" :class="matrix.couponEnabled ? 'mb-2' : ''">
                                            <span class="text-xs text-gray-500 w-10 shrink-0">售价:</span>
                                            <el-input v-model="matrix.prices[level.value]" size="small" placeholder="￥0.00" class="flex-1 font-bold text-gray-800" @input="() => calcCoupon(level.value)" />
                                        </div>
                                        <div v-if="matrix.couponEnabled" class="flex items-center gap-2">
                                            <span class="text-xs text-green-600 font-bold w-10 shrink-0">专券:</span>
                                            <el-input v-model="matrix.coupons[level.value]" size="small" placeholder="差额" class="flex-1 text-green-600" @input="() => calcPrice(level.value)" />
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div v-else-if="matrix.rawCodes.length > 0" class="text-center text-red-500 py-6 border-2 border-red-200 border-dashed rounded bg-white">
                                <el-icon class="text-3xl mb-1"><Warning /></el-icon><br/>
                                <span class="font-bold">⚠️ 字典匹配失败！请检查系统字典配置。</span><br/>
                                <span class="text-xs text-gray-500 mt-2 block">品牌策略下发代码: {{ matrix.rawCodes.join(', ') }}</span>
                                <span class="text-xs text-gray-500 block">系统字典可用代码: {{ dict.memberType?.map(d => d.value).join(', ') || '字典正在加载...' }}</span>
                            </div>

                            <div v-else class="text-center text-orange-500 py-6 border-2 border-orange-200 border-dashed rounded bg-white">
                                <span class="font-bold">⚠️ 该品牌尚未配置有效的会员特权。</span><br/>
                                <span class="text-xs text-gray-400">如需配置，请前往左侧菜单【品牌管理 -> 定价策略】进行勾选。</span>
                            </div>
                        </div>
                        <div v-else-if="!moneyCrud.form.brandId" class="text-center text-gray-400 py-6 border-2 border-dashed rounded bg-white">
                            请先在上方选择【品牌归属】，系统将拉取对应的等级定价框。
                        </div>
                    </div>

                    <div class="bg-gray-50 p-5 rounded-lg border border-gray-200 shadow-sm">
                        <div class="grid grid-cols-3 gap-6 mb-4">
                            <el-form-item label="上架状态" prop="status" class="mb-0">
                                <el-select v-model="moneyCrud.form.status" placeholder="请选择" class="w-full">
                                    <el-option v-for="item in dict.goodsStatus" :key="item.value" :label="item.desc" :value="item.value" />
                                </el-select>
                            </el-form-item>
                            <el-form-item label="当前库存" prop="stock" class="mb-0">
                                <ComputeInput v-model="moneyCrud.form.stock" />
                            </el-form-item>
                            <el-form-item label="计量单位" prop="unit" class="mb-0">
                                <el-input v-model.trim="moneyCrud.form.unit" placeholder="如: 盒" />
                            </el-form-item>
                        </div>
                        <div class="mb-4">
                            <el-form-item label="规格尺寸" prop="size" class="mb-0">
                                <el-input v-model.trim="moneyCrud.form.size" placeholder="选填" />
                            </el-form-item>
                        </div>
                        <el-form-item label="商品描述" class="mb-0">
                            <el-input v-model.trim="moneyCrud.form.description" type="textarea" :rows="2" maxlength="250" show-word-limit />
                        </el-form-item>
                    </div>
                </MoneyForm>
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import MoneyCrud from '@/components/crud/MoneyCrud.js'
import MoneyCrudTable from "@/components/crud/MoneyCrudTable.vue";
import MoneyRR from "@/components/crud/MoneyRR.vue";
import MoneyCUD from "@/components/crud/MoneyCUD.vue";
import MoneyUD from "@/components/crud/MoneyUD.vue";
import MoneyForm from "@/components/crud/MoneyForm.vue";
import ComputeInput from "@/components/ComputeInput.vue";
import GoodsCategory from "@/views/gms/goods/GoodsCategory.vue";

// 🌟 引入刚刚归入 Common 的智能搜货组件
import SmartGoodsSelector from "@/components/common/SmartGoodsSelector.vue";

import { ref, watch, reactive, computed } from "vue";
import { useUserStore } from "@/store/index.js";
import brandApi from "@/api/gms/brand.js"
import goodsApi from "@/api/gms/goods.js"
import dictApi from "@/api/system/dict.js";
import goodsCategoryApi from "@/api/gms/goodsCategory.js";
import { req } from '@/api/index.js'
import { Goods, Money, Warning } from '@element-plus/icons-vue'
import NP from 'number-precision'

const userStore = useUserStore()
const dict = ref({})
const brands = ref([])
const brandsKv = ref({})
const categories = ref([])
const statusColor = { 'SOLD_OUT': 'warning', 'UN_SHELVE': 'info' }

const matrix = reactive({
    loading: false,
    couponEnabled: false,
    rawCodes: [],
    prices: {},
    coupons: {}
});

const dbSnapshot = reactive({
    brandId: null,
    prices: {},
    coupons: {}
});

const parseCodes = (data) => {
    if (!data) return [];
    if (Array.isArray(data)) return data.map(String);
    return String(data).replace(/[\[\]"'\s]/g, '').split(',').filter(Boolean);
};

const validLevels = computed(() => {
    const allDict = dict.value.memberType || [];
    const codes = matrix.rawCodes || [];
    return allDict.filter(item => codes.includes(String(item.value)));
});

const getCleanedMap = (sourceObj) => {
    const result = {};
    const allowed = validLevels.value.map(l => String(l.value));
    Object.keys(sourceObj).forEach(key => {
        if (sourceObj[key] != null && sourceObj[key] !== '' && allowed.includes(String(key))) {
            result[key] = sourceObj[key];
        }
    });
    return result;
}

const calcCoupon = (code) => {
    if (!matrix.couponEnabled) return;
    const saleP = Number(moneyCrud.value.form.salePrice) || 0;
    const memberP = Number(matrix.prices[code]) || 0;
    if (saleP > 0 && memberP > 0) matrix.coupons[code] = NP.minus(saleP, memberP);
}
const calcPrice = (code) => {
    if (!matrix.couponEnabled) return;
    const saleP = Number(moneyCrud.value.form.salePrice) || 0;
    const couponP = Number(matrix.coupons[code]) || 0;
    if (saleP > 0 && couponP >= 0) matrix.prices[code] = NP.minus(saleP, couponP);
}
const syncAllCoupons = () => {
    if (!matrix.couponEnabled) return;
    Object.keys(matrix.prices).forEach(code => calcCoupon(code));
}

const hookedGoodsApi = {
    ...goodsApi,
    add: (data) => {
        const payload = { ...data };
        payload.levelPrices = getCleanedMap(matrix.prices);
        payload.levelCoupons = matrix.couponEnabled ? getCleanedMap(matrix.coupons) : {};
        return goodsApi.add(payload);
    },
    edit: (data) => {
        const payload = { ...data };
        if (payload.barcode !== undefined) {
            payload.levelPrices = getCleanedMap(matrix.prices);
            payload.levelCoupons = matrix.couponEnabled ? getCleanedMap(matrix.coupons) : {};
        }
        return goodsApi.edit(payload);
    }
}

const columns = [
    {prop: 'barcode', label: '条码', width: 140},
    {prop: 'brand', label: '品牌', show: false},
    {prop: 'name', label: '名称', minWidth: 140},
    {prop: 'status', label: '状态', width: 80},
    {prop: 'salePrice', label: '零售价', minWidth: 90, align: 'right'},
    {prop: 'purchasePrice', label: '进货成本', minWidth: 90, align: 'right'},
    {prop: 'stock', label: '库存', width: 80, align: 'center'},
    {prop: 'sales', label: '销量', width: 80, align: 'center', sortable: 'custom'},
    {prop: 'opt', label: '操作', width: 120, align: 'center', fixed: 'right', isMoneyUD: true },
]
const rules = {
    brandId: [{required: true, message: '请选择品牌'}],
    barcode: [{required: true, message: '请输入条码'}],
    name: [{required: true, message: '请输入名称'}],
    purchasePrice: [{required: true, message: '请输入进价'}],
    salePrice: [{required: true, message: '请输入售价'}],
    stock: [{required: true, message: '请输入库存'}],
    status: [{required: true, message: '请选择状态'}]
}

const moneyCrud = ref(new MoneyCrud({
    columns,
    crudMethod: hookedGoodsApi,
    optShow: {
        checkbox: userStore.hasPermission(['gmsGoods:edit', 'gmsGoods:del']),
        add: userStore.hasPermission('gmsGoods:add'),
        edit: userStore.hasPermission('gmsGoods:edit'),
        del: userStore.hasPermission('gmsGoods:del')
    },
    defaultForm: { status: 'SALE', stock: 0, isDiscountParticipable: 1 }
}))

// 🌟 智能管货组件的选中与清除联动
const handleGoodsSelect = (goods) => {
    // 选中商品后，利用条码进行精准查询
    moneyCrud.value.query.barcode = goods.barcode;
    moneyCrud.value.query.name = null;
    moneyCrud.value.doQuery();
}
const handleGoodsClear = () => {
    moneyCrud.value.query.barcode = null;
    moneyCrud.value.query.name = null;
    moneyCrud.value.doQuery();
}

watch(() => moneyCrud.value.box, (isOpen) => {
    if (!isOpen) {
        dbSnapshot.brandId = null;
        dbSnapshot.prices = {};
        dbSnapshot.coupons = {};
        matrix.rawCodes = [];
        matrix.prices = {};
        matrix.coupons = {};
    }
});

watch(() => moneyCrud.value.form.brandId, async (newBrandId) => {
    if (!newBrandId) {
        matrix.rawCodes = [];
        matrix.prices = {};
        matrix.coupons = {};
        return;
    }

    if (moneyCrud.value.form.id && dbSnapshot.brandId === null) {
        dbSnapshot.brandId = String(newBrandId);
        dbSnapshot.prices = JSON.parse(JSON.stringify(moneyCrud.value.form.levelPrices || {}));
        dbSnapshot.coupons = JSON.parse(JSON.stringify(moneyCrud.value.form.levelCoupons || {}));
    }

    matrix.loading = true;
    try {
        const res = await req({ url: '/gms/brand/config', method: 'GET', params: { brandId: newBrandId } }).catch(() => null);
        const data = res?.data || {};
        const raw = data.levelCodes || data.levelCode || data.levels || data.levelIds;

        if (raw != null && raw !== '') {
            matrix.couponEnabled = data.couponEnabled === 1 || data.couponEnabled === true;
            matrix.rawCodes = parseCodes(raw);
        } else {
            matrix.rawCodes = [];
        }
    } catch (e) {
        matrix.rawCodes = [];
    } finally {
        matrix.loading = false;
    }

    matrix.prices = {};
    matrix.coupons = {};

    if (String(newBrandId) === String(dbSnapshot.brandId)) {
        Object.keys(dbSnapshot.prices).forEach(k => matrix.prices[k] = dbSnapshot.prices[k]);
        Object.keys(dbSnapshot.coupons).forEach(k => matrix.coupons[k] = dbSnapshot.coupons[k]);
    }
}, { immediate: true });

moneyCrud.value.init(moneyCrud, async () => {
    brands.value = await brandApi.getSelect().then(res => res.data)
    brands.value.forEach(e => { brandsKv.value[e.value] = e.label })
    categories.value = await goodsCategoryApi.getSelect().then(res => res.data)
    dict.value = await dictApi.loadDict(["goodsStatus", "memberType"])
})

function selectGoodsCategory(data) {
    moneyCrud.value.query.categoryId = data.id !== 0 ? data.id : null
    moneyCrud.value.doQuery()
}

const editCell = ref({})
function startEditCell(id, field, origin) { editCell.value.id = id; editCell.value.field = field; editCell.value.origin = origin; }
function updateCell(value, row) {
    if (value === editCell.value.origin || !/^\d+$/.test(value)) {
        row[editCell.value.field] = editCell.value.origin; editCell.value = {}; return;
    }
    goodsApi.edit({ id: row.id, [editCell.value.field]: value })
        .then(() => moneyCrud.value.messageOk())
        .catch(() => row[editCell.value.field] = editCell.value.origin)
    editCell.value = {}
}
</script>

<style scoped>
:deep(.el-input-number .el-input__inner) { text-align: left; }
</style>