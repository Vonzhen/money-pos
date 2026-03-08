<template>
    <PageWrapper>
        <MoneyRR :money-crud="moneyCrud">
            <el-input v-model="moneyCrud.query.code" placeholder="会员号" class="md:!w-48" @keyup.enter.native="moneyCrud.doQuery" />
            <el-input v-model="moneyCrud.query.name" placeholder="会员名称" class="md:!w-48" @keyup.enter.native="moneyCrud.doQuery" />
            <el-input v-model="moneyCrud.query.phone" placeholder="手机号码" class="md:!w-48" @keyup.enter.native="moneyCrud.doQuery" />
        </MoneyRR>

        <div class="flex items-center gap-2 mb-3 mt-2">
            <MoneyCUD :money-crud="moneyCrud" />
            <el-button type="info" @click="handleDownloadTemplate"><el-icon class="mr-1"><Download /></el-icon>下载模板</el-button>
            <el-upload action="#" :auto-upload="false" :show-file-list="false" :on-change="handleImport" accept=".xlsx, .xls">
                <el-button type="success" :loading="importLoading"><el-icon class="mr-1"><Upload /></el-icon>导入老会员</el-button>
            </el-upload>
        </div>

        <MoneyCrudTable :money-crud="moneyCrud">
            <template #name="{scope}">
                <el-link type="primary" :underline="false" @click="openMember360(scope.row)">
                    <span class="font-bold text-blue-600 flex items-center gap-1 hover:text-blue-800 tracking-widest">
                        <el-icon><DataLine /></el-icon> {{ scope.row.name }}
                    </span>
                </el-link>
            </template>
            <template #brandLevels="{scope}">
                <div class="flex flex-wrap gap-1">
                    <template v-if="scope.row.brandLevels && Object.keys(scope.row.brandLevels).length > 0">
                        <el-tag v-for="(levelCode, brandId) in scope.row.brandLevels" :key="brandId" size="small" type="success" effect="light" class="font-bold">
                            {{ getBrandName(brandId) }}: {{ dict.memberTypeKv[levelCode] || levelCode }}
                        </el-tag>
                    </template>
                    <el-tag v-else size="small" type="info" class="text-gray-400 border-dashed border-gray-300 bg-transparent">普通零售客</el-tag>
                </div>
            </template>
            <template #address="{scope}">{{ scope.row.province + scope.row.city + scope.row.district + scope.row.address }}</template>
            <template #opt="{scope}">
                <el-button type="success" link @click="openRecharge(scope.row)"><el-icon class="mr-1"><Money /></el-icon>充值/发券</el-button>
                <MoneyUD :money-crud="moneyCrud" :scope="scope" />
            </template>
        </MoneyCrudTable>

        <MoneyForm :money-crud="moneyCrud" :rules="rules" dialog-class="!w-11/12 md:!w-5/12 !mt-12">
            <div class="bg-gray-50 p-4 rounded-lg border border-gray-100 mb-4">
                <h4 class="font-bold text-gray-700 mb-3 flex items-center gap-2"><el-icon><User /></el-icon> 基础档案</h4>
                <div class="flex gap-4">
                    <el-form-item label="会员名称" prop="name" class="!w-1/2"><el-input v-model.trim="moneyCrud.form.name" placeholder="请输入姓名" /></el-form-item>
                    <el-form-item label="手机号码" prop="phone" class="!w-1/2"><el-input v-model="moneyCrud.form.phone" placeholder="用于结账识别" /></el-form-item>
                </div>
                <el-form-item label="备注说明" prop="remark" class="mb-0"><el-input v-model="moneyCrud.form.remark" placeholder="非必填" /></el-form-item>
            </div>

            <div class="bg-blue-50/50 p-4 rounded-lg border border-blue-100 relative">
                <h4 class="font-bold text-blue-800 mb-4 flex items-center gap-2"><el-icon><Trophy /></el-icon> 品牌专属身份 (特权矩阵)</h4>
                <div class="w-full text-xs text-gray-500 mb-4 border-b border-blue-100/50 pb-2">
                    不选择的品牌，该客户将被系统默认视为【普通零售客】，无法享受对应品牌的会员价与折扣券。
                </div>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <el-form-item v-for="brand in brands" :key="brand.value" :label="brand.label" class="!mb-0 font-bold">
                        <el-select v-model="uiBrandLevels[brand.value]" placeholder="默认零售 (无特权)" clearable class="w-full">
                            <el-option v-for="item in dict.memberType" :key="item.value" :label="item.desc" :value="item.value">
                                <span style="float: left">{{ item.desc }}</span>
                                <span style="float: right; color: #8492a6; font-size: 13px">{{ item.value }}</span>
                            </el-option>
                        </el-select>
                    </el-form-item>
                </div>
            </div>
        </MoneyForm>

        <el-drawer v-model="drawerVisible" :title="`🌟 ${current360Member.name} 的消费画像`" size="420px" destroy-on-close>
            <div class="flex flex-col gap-5 px-1">
                <div class="bg-gradient-to-br from-blue-50 to-indigo-50 p-5 rounded-xl border border-blue-100 shadow-sm relative overflow-hidden">
                    <div class="absolute right-[-10px] top-[-10px] opacity-10 text-6xl"><el-icon><Trophy /></el-icon></div>
                    <div class="text-sm text-gray-500 font-bold mb-1">历史累计消费金额</div>
                    <div class="text-3xl font-black text-blue-600 mb-3">￥{{ (current360Member.consumeAmount || 0).toFixed(2) }}</div>

                    <div class="flex flex-col gap-1 text-xs text-gray-500 border-t border-blue-100/50 pt-3">
                        <div class="flex justify-between">
                            <span>📱 手机号码:</span> <span class="font-mono text-gray-700">{{ current360Member.phone }}</span>
                        </div>
                        <div class="flex justify-between">
                            <span>💳 会员余额:</span> <span class="font-bold text-gray-700">￥{{ (current360Member.balance || 0).toFixed(2) }}</span>
                        </div>
                        <div class="flex justify-between">
                            <span>🎫 剩余满减券:</span> <span class="font-bold text-orange-500">{{ current360Member.voucherCount || 0 }} 张</span>
                        </div>

                        <div class="flex justify-between mt-2 pt-2 border-t border-blue-100/50 items-center">
                            <span class="text-blue-500 font-bold">特权版图:</span>
                            <div class="flex flex-wrap gap-1 justify-end max-w-[200px]">
                                <template v-if="current360Member.brandLevels && Object.keys(current360Member.brandLevels).length > 0">
                                    <el-tag v-for="(levelCode, brandId) in current360Member.brandLevels" :key="brandId" size="small" effect="dark" type="success" class="border-0 shadow-sm">
                                        {{ getBrandName(brandId) }}: {{ dict.memberTypeKv[levelCode] || levelCode }}
                                    </el-tag>
                                </template>
                                <span v-else class="text-gray-400 border border-gray-300 border-dashed px-2 py-0.5 rounded">仅限普通零售</span>
                            </div>
                        </div>

                        <div class="flex justify-between mt-2 pt-2 border-t border-blue-100/50">
                            <span class="text-blue-500">最后到店时间:</span>
                            <span class="font-bold text-blue-600">{{ current360Member.lastVisitTime || '暂无消费记录' }}</span>
                        </div>
                    </div>
                </div>

                <div>
                    <h3 class="font-bold text-gray-800 mb-3 flex items-center gap-2 text-base">
                        <el-icon class="text-orange-500"><Trophy /></el-icon> 最爱买的商品 Top 10
                    </h3>
                    <el-table :data="top10List" v-loading="top10Loading" size="default" stripe border class="w-full">
                        <el-table-column type="index" label="排" width="45" align="center">
                            <template #default="scope">
                                <span :class="{'text-red-500 font-black text-lg': scope.$index === 0, 'text-orange-500 font-bold text-base': scope.$index === 1, 'text-yellow-500 font-bold': scope.$index === 2, 'text-gray-400': scope.$index > 2}">
                                    {{ scope.$index + 1 }}
                                </span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="goodsName" label="商品名称" show-overflow-tooltip />
                        <el-table-column prop="buyCount" label="购买件数" width="80" align="center">
                            <template #default="{row}"><span class="font-black text-gray-700">{{ row.buyCount }}</span></template>
                        </el-table-column>
                    </el-table>
                </div>
            </div>
        </el-drawer>

        <el-dialog v-model="rechargeVisible" :title="'会员业务 - ' + currentMember.name" width="550px" destroy-on-close>
            <el-form :model="rechargeForm" ref="rechargeFormRef" label-width="110px">
                <el-form-item label="业务类型">
                    <el-radio-group v-model="rechargeForm.type">
                        <el-radio label="BALANCE">充值会员余额</el-radio>
                        <el-radio label="COUPON">直充会员券</el-radio>
                        <el-radio label="VOUCHER">发满减优惠券</el-radio>
                    </el-radio-group>
                </el-form-item>

                <template v-if="rechargeForm.type === 'BALANCE'">
                    <el-form-item label="充值金额" prop="amount" :rules="[{required: true, type: 'number', message: '请输入实际收款金额'}]">
                        <el-input-number v-model="rechargeForm.amount" :precision="2" :step="100" class="!w-full" placeholder="顾客实际支付充值的金额(元)" />
                    </el-form-item>
                    <el-form-item label="赠送会员券" prop="giftCoupon">
                        <el-input-number v-model="rechargeForm.giftCoupon" :precision="2" :step="10" class="!w-full" placeholder="额外赠送的会员抵用券(无则留空)" />
                    </el-form-item>
                </template>

                <template v-if="rechargeForm.type === 'COUPON'">
                    <el-form-item label="充入会员券" prop="amount" :rules="[{required: true, type: 'number', message: '请输入充值额度'}]">
                        <el-input-number v-model="rechargeForm.amount" :precision="2" :step="100" class="!w-full" placeholder="系统账户增加的虚拟券额 (如：1000)" />
                    </el-form-item>
                    <el-form-item label="实收金额" prop="realAmount" :rules="[{required: true, type: 'number', message: '必须输入顾客实际支付现金'}]">
                        <el-input-number v-model="rechargeForm.realAmount" :precision="2" :step="10" class="!w-full" placeholder="顾客实际支付的现金 (如：100)" />
                    </el-form-item>
                </template>

                <template v-if="rechargeForm.type === 'VOUCHER'">
                    <el-form-item label="选择满减券" prop="ruleId" :rules="[{required: true, message: '请选择要发放的满减券'}]">
                        <el-select v-model="rechargeForm.ruleId" placeholder="请选择生效中的满减券" class="!w-full">
                            <el-option v-for="item in ruleList" :key="item.id" :label="item.name" :value="item.id">
                                <span style="float: left">{{ item.name }}</span>
                                <span style="float: right; color: #8492a6; font-size: 13px">满{{ item.thresholdAmount }}减{{ item.discountAmount }}</span>
                            </el-option>
                        </el-select>
                    </el-form-item>
                    <el-form-item label="发放张数" prop="quantity" :rules="[{required: true, type: 'number', message: '请输入发放张数'}]">
                        <el-input-number v-model="rechargeForm.quantity" :min="1" :max="100" :step="1" :precision="0" class="!w-full" placeholder="发放几张" />
                    </el-form-item>
                </template>

                <el-form-item label="备注说明" prop="remark" :rules="[{required: true, message: '必填，方便日后查账'}]">
                    <el-input v-model="rechargeForm.remark" type="textarea" placeholder="例如：开业大酬宾充500送50，或 购买满减券" maxlength="100" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="rechargeVisible = false">取消</el-button>
                <el-button type="primary" @click="submitRecharge" :loading="rechargeLoading">确定办理</el-button>
            </template>
        </el-dialog>
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

import { ref, watch, reactive } from "vue";
import { useUserStore } from "@/store/index.js";
import memberApi from "@/api/ums/member.js";
import dictApi from "@/api/system/dict.js";
import brandApi from "@/api/gms/brand.js";
import { req } from "@/api/index.js";
import { ElMessage } from "element-plus";
import { Money, Upload, Download, DataLine, Trophy, User } from "@element-plus/icons-vue";

const userStore = useUserStore()

const uiBrandLevels = reactive({})
const brands = ref([])
const brandsKv = ref({})

function getCleanedMap(sourceObj) {
    const result = {};
    Object.keys(sourceObj).forEach(key => {
        const val = sourceObj[key];
        if (val !== '' && val !== null && val !== undefined) result[key] = val;
    });
    return result;
}

const hookedMemberApi = {
    ...memberApi,
    add: (data) => {
        data.brandLevels = getCleanedMap(uiBrandLevels);
        data.type = 'MEMBER';
        return memberApi.add(data);
    },
    edit: (data) => {
        data.brandLevels = getCleanedMap(uiBrandLevels);
        return memberApi.edit(data);
    }
}

const columns = [
    {prop: 'code', label: '会员号', show: false},
    {prop: 'name', label: '会员名称', width: 120},
    {prop: 'phone', label: '手机号码', width: 130},
    {prop: 'brandLevels', label: '品牌专属身份', minWidth: 260},
    {prop: 'balance', label: '会员余额', minWidth: 100},
    {prop: 'coupon', label: '会员券', minWidth: 110},
    {prop: 'voucherCount', label: '满减券(张)', minWidth: 100},
    {prop: 'consumeAmount', label: '总消费金额', sortable: 'custom', show: false},
    {prop: 'address', label: '地址', show: false},
    {
        prop: 'opt',
        label: '操作',
        width: 250,
        align: 'center',
        fixed: 'right',
        showOverflowTooltip: false,
        isMoneyUD: true
    },
]
const rules = {
    name: [{required: true, message: '请输入会员名称'}],
    phone: [
        {required: true, message: '请输入手机号'},
        {pattern: /^1([38][0-9]|4[014-9]|[59][0-35-9]|6[2567]|7[0-8])\d{8}$/, message: '格式错误'}
    ]
}

const moneyCrud = ref(new MoneyCrud({
    columns,
    crudMethod: hookedMemberApi,
    optShow: {
        checkbox: true, add: true, edit: true, del: true
    },
    defaultForm: { type: 'MEMBER', coupon: 0 }
}))

watch(() => moneyCrud.value.form, (newForm) => {
    Object.keys(uiBrandLevels).forEach(key => delete uiBrandLevels[key]);
    if (newForm && newForm.id && newForm.brandLevels) {
        Object.assign(uiBrandLevels, newForm.brandLevels);
    }
}, { deep: true, immediate: true })

const dict = ref({})
moneyCrud.value.init(moneyCrud, async () => {
    dict.value = await dictApi.loadDict(["memberType"])
    if (dict.value.memberType) {
        dict.value.memberType = dict.value.memberType.filter(item => item.value !== 'MEMBER')
    }
    const brandRes = await brandApi.getSelect()
    brands.value = brandRes.data || []
    brands.value.forEach(e => { brandsKv.value[e.value] = e.label })
})

const getBrandName = (brandId) => {
    return brandsKv.value[brandId] || '未知品牌'
}

const drawerVisible = ref(false)
const current360Member = ref({})
const top10Loading = ref(false)
const top10List = ref([])

const openMember360 = async (row) => {
    current360Member.value = row;
    drawerVisible.value = true;
    top10Loading.value = true;
    top10List.value = [];
    try {
        const res = await req({ url: '/ums/member/top10Goods', method: 'GET', params: { memberId: row.id } });
        top10List.value = res.data || res || [];
    } catch (e) {
        console.warn("Top 10 接口暂未实现");
    } finally {
        top10Loading.value = false;
    }
}

const handleDownloadTemplate = async () => {
    try {
        const res = await memberApi.downloadTemplate()
        const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.setAttribute('download', '会员导入模板.xlsx')
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
    } catch (e) {
        ElMessage.error('模板下载失败，请联系管理员')
    }
}

const importLoading = ref(false)
const handleImport = async (fileOptions) => {
    importLoading.value = true
    try {
        ElMessage.info('正在解析并导入老会员数据...')
        await memberApi.importMembers(fileOptions.file || fileOptions.raw)
        ElMessage.success('导入老会员成功！')
        moneyCrud.value.doQuery()
    } catch (error) {
        ElMessage.error('导入失败，请检查 Excel 格式或数据是否合规')
    } finally {
        importLoading.value = false
    }
}

const rechargeVisible = ref(false)
const rechargeLoading = ref(false)
const currentMember = ref({})
const rechargeFormRef = ref()
const ruleList = ref([])
const rechargeForm = ref({ type: 'BALANCE', amount: undefined, giftCoupon: undefined, realAmount: undefined, ruleId: undefined, quantity: 1, remark: '' })

async function openRecharge(row) {
    currentMember.value = row;
    // 🌟 重置表单，包含 realAmount
    rechargeForm.value = {
        memberId: row.id,
        type: 'BALANCE',
        amount: undefined,
        giftCoupon: undefined,
        realAmount: undefined,
        ruleId: undefined,
        quantity: 1,
        remark: ''
    };

    if (ruleList.value.length === 0) {
        try {
            const res = await req({ url: '/ums/member/coupon-rules', method: 'GET' });
            ruleList.value = res.data || [];
        } catch(e) {}
    }
    rechargeVisible.value = true;
}

function submitRecharge() {
    rechargeFormRef.value.validate(async (valid) => {
        if (valid) {
            rechargeLoading.value = true;
            try {
                // 提交表单
                await memberApi.recharge(rechargeForm.value);
                ElMessage.success('业务办理成功，财务台账已自动同步！');
                rechargeVisible.value = false;
                moneyCrud.value.doQuery();
            } catch(e) {
                console.error(e)
            } finally {
                rechargeLoading.value = false;
            }
        }
    });
}
</script>