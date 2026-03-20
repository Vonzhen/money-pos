<template>
    <el-dialog v-model="visible" :title="'会员业务办理 - ' + memberInfo.name" width="550px" destroy-on-close @close="handleClose">
        <el-form :model="rechargeForm" ref="formRef" label-width="110px">
            <el-form-item label="业务类型">
                <el-radio-group v-model="rechargeForm.type">
                    <el-radio label="BALANCE">充值余额</el-radio>
                    <el-radio label="COUPON">直充会员券</el-radio>
                    <el-radio label="VOUCHER">发满减券</el-radio>
                </el-radio-group>
            </el-form-item>

            <template v-if="rechargeForm.type === 'BALANCE'">
                <el-form-item label="充值金额" prop="amount" :rules="[{required: true, type: 'number', message: '请输入金额'}]">
                    <el-input-number v-model="rechargeForm.amount" :precision="2" :step="100" class="!w-full" />
                </el-form-item>
                <el-form-item label="赠送会员券" prop="giftCoupon">
                    <el-input-number v-model="rechargeForm.giftCoupon" :precision="2" class="!w-full" />
                </el-form-item>
            </template>

            <template v-if="rechargeForm.type === 'COUPON'">
                <el-form-item label="充入会员券" prop="amount" :rules="[{required: true, type: 'number', message: '请输入额度'}]">
                    <el-input-number v-model="rechargeForm.amount" :precision="2" class="!w-full" />
                </el-form-item>
                <el-form-item label="实收现金" prop="realAmount" :rules="[{required: true, type: 'number', message: '请输入实收'}]">
                    <el-input-number v-model="rechargeForm.realAmount" :precision="2" class="!w-full" />
                </el-form-item>
            </template>

            <template v-if="rechargeForm.type === 'VOUCHER'">
                <el-form-item label="选择满减券" prop="ruleId" :rules="[{required: true, message: '请选择券'}]">
                    <el-select v-model="rechargeForm.ruleId" placeholder="请选择" class="!w-full">
                        <el-option v-for="item in ruleList" :key="item.id" :label="item.name" :value="item.id">
                            <span style="float: left">{{ item.name }}</span>
                            <span style="float: right; color: #8492a6; font-size: 12px">满{{ item.thresholdAmount }}减{{ item.discountAmount }}</span>
                        </el-option>
                    </el-select>
                </el-form-item>
                <el-form-item label="发放张数" prop="quantity">
                    <el-input-number v-model="rechargeForm.quantity" :min="1" :precision="0" class="!w-full" />
                </el-form-item>
            </template>

            <el-form-item label="备注说明" prop="remark" :rules="[{required: true, message: '必填'}]">
                <el-input v-model="rechargeForm.remark" type="textarea" maxlength="100" />
            </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="visible = false">取消</el-button>
            <el-button type="primary" @click="submit" :loading="loading">确定办理</el-button>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import memberApi from "@/api/ums/member.js";
import { req } from "@/api/index.js";

const props = defineProps({
    modelValue: Boolean,
    memberInfo: Object
});
const emit = defineEmits(['update:modelValue', 'success']);

const visible = ref(false);
const loading = ref(false);
const formRef = ref();
const ruleList = ref([]);
const rechargeForm = ref({ type: 'BALANCE', amount: undefined, giftCoupon: undefined, realAmount: undefined, ruleId: undefined, quantity: 1, remark: '' });

watch(() => props.modelValue, (val) => {
    visible.value = val;
    if (val) initData();
});

async function initData() {
    rechargeForm.value = { memberId: props.memberInfo.id, type: 'BALANCE', amount: undefined, quantity: 1, remark: '' };
    if (ruleList.value.length === 0) {
        const res = await req({ url: '/ums/member/coupon-rules', method: 'GET' });
        ruleList.value = res.data || [];
    }
}

function handleClose() { emit('update:modelValue', false); }

function submit() {
    formRef.value.validate(async (valid) => {
        if (!valid) return;
        loading.value = true;
        try {
            await memberApi.recharge(rechargeForm.value);
            ElMessage.success('办理成功！');
            emit('success');
            visible.value = false;
        } finally {
            loading.value = false;
        }
    });
}
</script>