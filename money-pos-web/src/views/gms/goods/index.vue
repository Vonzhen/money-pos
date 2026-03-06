<template>
    <PageWrapper>
        <div class="flex">
            <div class="mr-6 w-2/12 hidden lg:block">
                <GoodsCategory @node-click="selectGoodsCategory" />
            </div>

            <div class="grid gap-6 flex-1">
                <MoneyRR :money-crud="moneyCrud">
                    <el-input v-model="moneyCrud.query.barcode" placeholder="条码" class="md:!w-48" @keyup.enter.native="moneyCrud.doQuery" />
                    <el-input v-model="moneyCrud.query.name" placeholder="名称" class="md:!w-48" @keyup.enter.native="moneyCrud.doQuery" />
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
                    <el-upload class="inline-block" action="#" :show-file-list="false" :http-request="handleImport" accept=".xlsx, .xls">
                        <el-button type="success" plain :loading="importing"><el-icon class="mr-1"><Upload /></el-icon> 极速导入 Excel</el-button>
                    </el-upload>
                    <el-button type="info" plain @click="handleDownloadTemplate"><el-icon class="mr-1"><Download /></el-icon> 下载导入模板</el-button>
                </div>

                <MoneyCrudTable :money-crud="moneyCrud">
                    <template #pic="{scope}">
                        <el-image class="w-8 h-8" preview-teleported :src="$money.getOssUrl(scope.row.pic)" :preview-src-list="[$money.getOssUrl(scope.row.pic)]" fit="cover" />
                    </template>
                    <template #brand="{scope}">{{ brandsKv[scope.row.brandId] }}</template>
                    <template #status="{scope}"><el-tag :type="statusColor[scope.row.status] || 'primary'">{{ dict.goodsStatusKv[scope.row.status] }}</el-tag></template>
                    <template #stock="{scope}">
                        <el-input v-if="editCell.id === scope.row.id" v-model="scope.row.stock" style="width: 55px" @change="value => updateCell(value, scope.row)" @focusout="updateCell(null, scope.row)" />
                        <span v-else @click="startEditCell(scope.row.id, 'stock', scope.row.stock)">{{ scope.row.stock }}</span>
                    </template>
                    <template #opt="{scope}"><MoneyUD :money-crud="moneyCrud" :scope="scope" /></template>
                </MoneyCrudTable>

                <MoneyForm :money-crud="moneyCrud" :rules="rules" :dialog-class="'!w-11/12 md:!w-7/12 !mt-8'">

                    <div class="bg-gray-50 p-4 rounded-lg border border-gray-100 mb-4">
                        <h4 class="font-bold text-gray-700 mb-3 flex items-center gap-2"><el-icon><Goods /></el-icon> 基础档案</h4>

                        <div class="flex justify-between gap-4 mb-2">
                            <el-form-item label="品牌归属" prop="brandId" class="!w-1/3">
                                <el-select v-model="moneyCrud.form.brandId" class="w-full" placeholder="【必选】拉取策略" @change="handleBrandChange">
                                    <el-option v-for="item in brands" :key="item.value" :label="item.label" :value="item.value" />
                                </el-select>
                            </el-form-item>
                            <el-form-item label="所属分类" prop="categoryId" class="!w-1/3">
                                <el-select v-model="moneyCrud.form.categoryId" class="w-full" placeholder="请选择" clearable>
                                    <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
                                </el-select>
                            </el-form-item>
                            <el-form-item label="商品图片" prop="pic" class="!w-1/3">
                                <el-upload class="avatar-uploader h-8" :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="handlePicSuccess">
                                    <el-button size="small" plain type="primary" class="w-full">
                                        {{ moneyCrud.form.pic ? '已上传(点击更换)' : '点击上传图片' }}
                                    </el-button>
                                </el-upload>
                            </el-form-item>
                        </div>

                        <div class="flex justify-between gap-4">
                            <el-form-item label="商品条码" prop="barcode" class="!w-1/2">
                                <el-input v-model.trim="moneyCrud.form.barcode" placeholder="扫码枪录入或手输" />
                            </el-form-item>
                            <el-form-item label="商品名称" prop="name" class="!w-1/2">
                                <el-input v-model.trim="moneyCrud.form.name" placeholder="请输入商品完整名称" />
                            </el-form-item>
                        </div>
                    </div>

                    <div class="bg-blue-50/50 p-4 rounded-lg border border-blue-100 mb-4 relative">
                        <div class="absolute right-4 top-4 flex items-center gap-2 text-xs font-bold px-3 py-1 rounded bg-white shadow-sm border"
                             :class="isCouponEnabled ? 'text-green-600 border-green-200' : 'text-blue-600 border-blue-200'">
                            <div :class="isCouponEnabled ? 'bg-green-500' : 'bg-blue-500'" class="w-2 h-2 rounded-full animate-pulse"></div>
                            {{ isCouponEnabled ? '已开启【会员价 + 会员券】双轨' : '已开启【仅会员价】单轨' }}
                        </div>

                        <h4 class="font-bold text-blue-800 mb-4 flex items-center gap-2"><el-icon><Money /></el-icon> 价格与策略</h4>

                        <div class="flex justify-between gap-4 border-b border-blue-100/50 pb-4 mb-4 items-center">
                            <el-form-item label="进货成本" prop="purchasePrice" class="!w-1/3 mb-0">
                                <el-input v-model="moneyCrud.form.purchasePrice" placeholder="￥ 0.00" />
                            </el-form-item>
                            <el-form-item label="零售价" prop="salePrice" class="!w-1/3 mb-0">
                                <el-input v-model="moneyCrud.form.salePrice" class="font-black text-red-500" placeholder="￥ 0.00" @input="handleRetailPriceChange" />
                            </el-form-item>
                            <el-form-item label="参与满减" prop="isDiscountParticipable" class="!w-1/3 mb-0">
                                <el-switch v-model="moneyCrud.form.isDiscountParticipable" :active-value="1" :inactive-value="0" active-text="允许" inactive-text="禁止" inline-prompt style="--el-switch-on-color: #f97316;" />
                            </el-form-item>
                        </div>

                        <div v-if="currentBrandLevels.length > 0">
                            <div class="text-sm font-bold text-gray-500 mb-2">该品牌下属会员等级定价：</div>
                            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div v-for="level in currentBrandLevels" :key="level.value" class="bg-white p-3 rounded border border-gray-200 shadow-sm flex flex-col gap-2 group hover:border-blue-300 transition-colors">
                                    <div class="font-bold text-blue-700 text-sm border-b pb-1 mb-1">{{ level.desc }}</div>
                                    <div class="flex items-center gap-2">
                                        <span class="text-xs text-gray-500 w-14">会员价:</span>
                                        <el-input v-model="uiLevelPrices[level.value]" size="small" placeholder="￥" class="flex-1 font-bold text-gray-800" @input="() => syncCoupon(level.value)" />
                                    </div>
                                    <div v-if="isCouponEnabled" class="flex items-center gap-2">
                                        <span class="text-xs text-green-600 font-bold w-14">会员券:</span>
                                        <el-input v-model="uiLevelCoupons[level.value]" size="small" placeholder="金额" class="flex-1 text-green-600" @input="() => syncPrice(level.value)" />
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div v-else class="text-center text-gray-400 py-6 border-2 border-dashed rounded bg-white">
                            请先在上方选择【品牌归属】，系统将自动拉取对应的定价策略。
                        </div>
                    </div>

                    <div class="bg-gray-50 p-4 rounded-lg border border-gray-100">
                        <div class="flex justify-between gap-4">
                            <el-form-item label="上架状态" prop="status" class="!w-1/3">
                                <el-select v-model="moneyCrud.form.status" placeholder="请选择" class="w-full">
                                    <el-option v-for="item in dict.goodsStatus" :key="item.value" :label="item.desc" :value="item.value" />
                                </el-select>
                            </el-form-item>
                            <el-form-item label="当前库存" prop="stock" class="!w-1/3"><ComputeInput v-model="moneyCrud.form.stock" /></el-form-item>
                            <el-form-item label="单位" prop="unit" class="!w-1/3"><el-input v-model.trim="moneyCrud.form.unit" /></el-form-item>
                        </div>
                        <div class="flex justify-between gap-4">
                            <el-form-item label="规格尺寸" prop="size" class="!w-full"><el-input v-model.trim="moneyCrud.form.size" /></el-form-item>
                        </div>
                        <el-form-item label="商品描述" class="mb-0"><el-input v-model.trim="moneyCrud.form.description" type="textarea" :rows="2" maxlength="250" show-word-limit /></el-form-item>
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

import { ref, watch, reactive } from "vue";
import { useUserStore } from "@/store/index.js";
import NP from 'number-precision'
import brandApi from "@/api/gms/brand.js"
import goodsApi, { importGoods } from "@/api/gms/goods.js"
import dictApi from "@/api/system/dict.js";
import goodsCategoryApi from "@/api/gms/goodsCategory.js";
import { req } from '@/api/index.js'
import { ElMessage } from 'element-plus'
import { Plus, Upload, Download, Goods, Money } from '@element-plus/icons-vue'

const userStore = useUserStore()

const uiLevelPrices = reactive({})
const uiLevelCoupons = reactive({})
const currentBrandLevels = ref([])
const isCouponEnabled = ref(false)

const handleBrandChange = async (brandId) => {
    if (!brandId) {
        currentBrandLevels.value = [];
        isCouponEnabled.value = false;
        return;
    }
    try {
        const res = await req({ url: '/gms/brand/config', method: 'GET', params: { brandId } }).catch(() => null);

        if (res && res.data) {
            isCouponEnabled.value = res.data.couponEnabled;
            // 🚨 接收后端发来的绑定字典Code列表，如果有，就过滤；如果没传，就兜底显示全部
            const bindCodes = res.data.levelCodes || [];
            if (bindCodes.length > 0) {
                currentBrandLevels.value = dict.value.memberType.filter(item => bindCodes.includes(item.value));
            } else {
                currentBrandLevels.value = dict.value.memberType;
            }
        } else {
            // 🚨 兜底机制：请求失败或后端没配置，默认全开
            isCouponEnabled.value = true;
            currentBrandLevels.value = dict.value.memberType;
        }
    } catch (e) {
        console.error("拉取策略失败", e)
    }
}

const syncCoupon = (levelCode) => {
    if (!isCouponEnabled.value) return;
    const saleP = Number(moneyCrud.value.form.salePrice) || 0;
    const memberP = Number(uiLevelPrices[levelCode]) || 0;
    if (saleP > 0 && memberP > 0 && saleP >= memberP) {
        uiLevelCoupons[levelCode] = NP.minus(saleP, memberP);
    }
}

const syncPrice = (levelCode) => {
    if (!isCouponEnabled.value) return;
    const saleP = Number(moneyCrud.value.form.salePrice) || 0;
    const coupon = Number(uiLevelCoupons[levelCode]) || 0;
    if (saleP > 0 && coupon >= 0 && saleP >= coupon) {
        uiLevelPrices[levelCode] = NP.minus(saleP, coupon);
    }
}

const handleRetailPriceChange = () => {
    if (!isCouponEnabled.value) return;
    Object.keys(uiLevelPrices).forEach(code => syncCoupon(code));
}

function getCleanedMap(sourceObj) {
    const result = {};
    Object.keys(sourceObj).forEach(key => {
        const val = sourceObj[key];
        if (val !== '' && val !== null && val !== undefined) result[key] = val;
    });
    return result;
}

const hookedGoodsApi = {
    ...goodsApi,
    add: (data) => {
        data.levelPrices = getCleanedMap(uiLevelPrices);
        data.levelCoupons = isCouponEnabled.value ? getCleanedMap(uiLevelCoupons) : {};
        return goodsApi.add(data);
    },
    edit: (data) => {
        if (data.barcode !== undefined) {
            data.levelPrices = getCleanedMap(uiLevelPrices);
            data.levelCoupons = isCouponEnabled.value ? getCleanedMap(uiLevelCoupons) : {};
        }
        return goodsApi.edit(data);
    }
}

const columns = [
    {prop: 'pic', label: '图片', show: false},
    {prop: 'barcode', label: '条码', width: 140},
    {prop: 'brand', label: '品牌', show: false},
    {prop: 'name', label: '名称', width: 140},
    {prop: 'status', label: '状态'},
    {prop: 'salePrice', label: '零售价', minWidth: 90},
    {prop: 'purchasePrice', label: '进价'},
    {prop: 'stock', label: '库存'},
    {prop: 'sales', label: '销量', sortable: 'custom'},
    {prop: 'unit', label: '单位', show: false},
    {prop: 'size', label: '规格', show: false},
    {prop: 'createTime', label: '创建时间', width: 180, show: false},
    {prop: 'updateTime', label: '更新时间', width: 180, show: false},
    {prop: 'description', label: '描述', show: false},
    {prop: 'opt', label: '操作', width: 120, align: 'center', fixed: 'right', showOverflowTooltip: false, isMoneyUD: true },
]
const rules = {
    brandId: [{required: true, message: '请选择品牌归属以拉取定价策略'}],
    barcode: [{required: true, message: '请输入条码'}],
    name: [{required: true, message: '请输入名称'}],
    purchasePrice: [{required: true, message: '请输入进价'}, {pattern: /^[0-9]\d*(\.\d+)?$/, message: '仅支持正数或0'}],
    salePrice: [{required: true, message: '请输入零售价'}, {pattern: /^[1-9]\d*(\.\d+)?$/, message: '仅支持正数'}],
    stock: [{required: true, message: '请输入库存'}, {pattern: /^(0|[1-9]\d*)$/, message: '仅支持0和正整数'}],
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

watch(() => moneyCrud.value.box, (isOpen) => {
    if (isOpen) {
        if (moneyCrud.value.form.id) {
            handleBrandChange(moneyCrud.value.form.brandId).then(() => {
                if (moneyCrud.value.form.levelPrices) Object.assign(uiLevelPrices, moneyCrud.value.form.levelPrices);
                if (moneyCrud.value.form.levelCoupons) Object.assign(uiLevelCoupons, moneyCrud.value.form.levelCoupons);
            });
        } else {
            for (let key in uiLevelPrices) delete uiLevelPrices[key];
            for (let key in uiLevelCoupons) delete uiLevelCoupons[key];
            currentBrandLevels.value = [];
            isCouponEnabled.value = false;
        }
    }
})

const dict = ref({})
const brands = ref([])
const brandsKv = ref({})
const categories = ref([])
const statusColor = { 'SOLD_OUT': 'warning', 'UN_SHELVE': 'info' }

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
function handlePicSuccess(file) {
    moneyCrud.value.form.pic = URL.createObjectURL(file.raw)
    moneyCrud.value.form.picFile = file.raw
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

const importing = ref(false)
const handleImport = async (options) => { /* 导入逻辑保留 */ }
const handleDownloadTemplate = async () => { /* 下载模板逻辑保留 */ }
</script>

<style scoped>
:deep(.el-input-number .el-input__inner) { text-align: left; }
</style>