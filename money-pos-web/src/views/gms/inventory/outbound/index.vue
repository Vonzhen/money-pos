<template>
    <PageWrapper>
        <div class="flex flex-col gap-4 h-full">
            <el-card shadow="hover" class="w-full" :body-style="{ padding: '20px' }">
                <div class="flex items-center gap-4">
                    <el-icon class="text-3xl text-danger"><Delete /></el-icon>

                    <SmartGoodsSelector
                        ref="scanInputRef"
                        v-model="scanBarcode"
                        mode="report"
                        placeholder="扫码枪滴入要报损的商品，或拼音联想点选"
                        class="!w-1/2"
                        @select="handleSelectGoods"
                        @search="handleScan"
                    />

                    <el-button type="danger" @click="handleScan(scanBarcode)">定位坏件</el-button>

                    <div class="ml-auto text-gray-500 text-sm">
                        记录过期或破损的商品，系统将自动扣减库存并核算损失金额
                    </div>
                </div>
            </el-card>

            <el-card shadow="hover" class="flex-1 overflow-hidden" :body-style="{ padding: '10px' }">
                <el-table :data="outboundList" border stripe height="calc(100vh - 380px)" size="large" empty-text="等待扫描报损...">
                    <el-table-column type="index" label="序号" width="60" align="center" />
                    <el-table-column prop="barcode" label="条码" width="160" />
                    <el-table-column prop="name" label="商品名称" min-width="200" />
                    <el-table-column label="当前账面库存" width="120" align="center">
                        <template #default="{row}">
                            <el-tag type="info">{{ row.currentStock }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="成本进价 (元)" width="150">
                        <template #default="{row}">
                            <el-input-number v-model="row.price" :min="0" :precision="2" :step="0.1" size="small" class="!w-full" />
                        </template>
                    </el-table-column>
                    <el-table-column label="报损数量" width="150">
                        <template #default="{row}">
                            <el-input-number v-model="row.qty" :min="1" :max="row.currentStock" size="small" class="!w-full border-danger" />
                        </template>
                    </el-table-column>
                    <el-table-column label="损失小计 (元)" width="120" align="right">
                        <template #default="{row}">
                            <span class="text-red-500 font-bold">￥{{ (row.price * row.qty).toFixed(2) }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column label="操作" width="80" align="center" fixed="right">
                        <template #default="scope">
                            <el-button type="danger" link @click="removeItem(scope.$index)">移除</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-card>

            <el-card shadow="hover" :body-style="{ padding: '15px 20px' }">
                <div class="flex items-center justify-between gap-8">
                    <div class="flex items-center gap-4 flex-1">
                        <span class="text-gray-600 whitespace-nowrap">报损原因：</span>
                        <el-input v-model="remark" placeholder="如：过期扔掉 / 包装破损 / 鼠咬" />
                    </div>
                    <div class="flex items-center gap-6 shrink-0 whitespace-nowrap">
                        <div class="text-lg">
                            共计报损 <span class="text-blue-600 font-bold text-2xl mx-1">{{ totalQty }}</span> 件商品
                        </div>
                        <div class="text-lg">
                            本次共计亏损：<span class="text-red-600 font-bold text-3xl ml-2">- ￥{{ totalAmount.toFixed(2) }}</span>
                        </div>
                        <el-button type="danger" size="large" class="!px-10 !text-lg" :loading="submitLoading" :disabled="outboundList.length === 0" @click="submitOrder">
                            确认扣减库存
                        </el-button>
                    </div>
                </div>
            </el-card>
        </div>
    </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import { ref, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Delete } from '@element-plus/icons-vue';
import goodsApi from "@/api/gms/goods.js";
import inventoryApi from "@/api/gms/inventory.js";

import SmartGoodsSelector from "@/components/common/SmartGoodsSelector.vue"

const scanInputRef = ref(null);
const scanBarcode = ref('');
const outboundList = ref([]);
const remark = ref('');
const submitLoading = ref(false);

const totalQty = computed(() => outboundList.value.reduce((sum, item) => sum + item.qty, 0));
const totalAmount = computed(() => outboundList.value.reduce((sum, item) => sum + (item.price * item.qty), 0));

const addGoodsToList = (goods) => {
    const existItem = outboundList.value.find(item => item.barcode === goods.barcode || item.goodsId === goods.id);
    if (existItem) {
        if (existItem.qty < existItem.currentStock) {
            existItem.qty += 1;
        } else {
            ElMessage.warning('报损数量不能超过当前账面库存！');
        }
    } else {
        if (goods.stock <= 0) {
            ElMessage.warning('该商品当前库存为 0，无需报损！');
        } else {
            outboundList.value.push({
                goodsId: goods.id,
                barcode: goods.barcode,
                name: goods.name,
                currentStock: goods.stock,
                price: goods.purchasePrice || 0,
                qty: 1
            });
        }
    }
    scanInputRef.value?.resetScanner();
};

let isSelecting = false;

const handleSelectGoods = (item) => {
    isSelecting = true;
    addGoodsToList(item);
    setTimeout(() => { isSelecting = false; }, 200);
};

const handleScan = async (val) => {
    if (isSelecting) return;
    const barcode = typeof val === 'string' ? val : scanBarcode.value;
    if (!barcode) return;

    const existItem = outboundList.value.find(item => item.barcode === barcode);
    if (existItem) {
        if (existItem.qty < existItem.currentStock) {
            existItem.qty += 1;
        } else {
            ElMessage.warning('报损数量不能超过当前账面库存！');
        }
        scanInputRef.value?.resetScanner();
        return;
    }

    try {
        const res = await goodsApi.list({ barcode: barcode, isCombo: 0, current: 1, size: 1 });
        if (res.data.records && res.data.records.length > 0) {
            addGoodsToList(res.data.records[0]);
        } else {
            ElMessage.warning('未找到该商品！');
            scanInputRef.value?.resetScanner();
        }
    } catch (e) {
        console.error(e);
    }
};

const removeItem = (index) => outboundList.value.splice(index, 1);

const submitOrder = async () => {
    if (outboundList.value.length === 0) return;
    if (!remark.value) return ElMessage.warning('请务必填写报损原因（如：过期），方便日后查账');

    await ElMessageBox.confirm(`即将扣除这批报损商品的库存，并计入亏损成本，确认提交吗？`, '报损确认', { type: 'warning', confirmButtonText: '确认报损', cancelButtonText: '取消' });

    submitLoading.value = true;
    try {
        const payload = {
            type: 'OUTBOUND',
            remark: remark.value,
            details: outboundList.value.map(item => ({ goodsId: item.goodsId, qty: item.qty, price: item.price }))
        };
        await inventoryApi.createOutbound(payload);
        ElMessage.success('报损完成！库存已扣减！');
        outboundList.value = [];
        remark.value = '';
        scanInputRef.value?.resetScanner();
    } catch (e) {
        ElMessage.error('报损失败，请重试');
    } finally {
        submitLoading.value = false;
    }
};
</script>