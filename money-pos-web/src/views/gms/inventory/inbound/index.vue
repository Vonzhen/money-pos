<template>
    <PageWrapper>
        <div class="flex flex-col gap-4 h-full">
            <el-card shadow="hover" class="w-full" :body-style="{ padding: '20px' }">
                <div class="flex items-center gap-4">
                    <el-icon class="text-3xl text-primary"><Aim /></el-icon>
                    <el-input
                        ref="scanInputRef"
                        v-model.trim="scanBarcode"
                        placeholder="请使用扫码枪滴入商品条码，或手动输入按回车键"
                        class="!w-1/2 !text-lg"
                        size="large"
                        clearable
                        @keyup.enter="handleScan"
                    >
                        <template #append>
                            <el-button type="primary" @click="handleScan">录入商品</el-button>
                        </template>
                    </el-input>
                    <div class="ml-auto text-gray-500 text-sm">
                        支持扫码枪连续极速扫码，同商品自动叠加数量
                    </div>
                </div>
            </el-card>

            <el-card shadow="hover" class="flex-1 overflow-hidden" :body-style="{ padding: '10px' }">
                <el-table :data="inboundList" border stripe height="calc(100vh - 380px)" size="large" empty-text="等待扫码进货...">
                    <el-table-column type="index" label="序号" width="60" align="center" />
                    <el-table-column prop="barcode" label="条码" width="160" />
                    <el-table-column prop="name" label="商品名称" min-width="200" />
                    <el-table-column label="当前账面库存" width="120" align="center">
                        <template #default="{row}">
                            <el-tag type="info">{{ row.currentStock }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="本次进价 (元)" width="150">
                        <template #default="{row}">
                            <el-input-number v-model="row.price" :min="0" :precision="2" :step="0.1" size="small" class="!w-full" />
                        </template>
                    </el-table-column>
                    <el-table-column label="进货数量" width="150">
                        <template #default="{row}">
                            <el-input-number v-model="row.qty" :min="1" size="small" class="!w-full" />
                        </template>
                    </el-table-column>
                    <el-table-column label="小计 (元)" width="120" align="right">
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
                <div class="flex items-center justify-between">
                    <div class="flex items-center gap-4 w-1/2">
                        <span class="text-gray-600 whitespace-nowrap">进货备注：</span>
                        <el-input v-model="remark" placeholder="如：中秋节旺旺大礼包补货" />
                    </div>
                    <div class="flex items-center gap-6">
                        <div class="text-lg">
                            共计进货 <span class="text-blue-600 font-bold text-2xl mx-1">{{ totalQty }}</span> 件商品
                        </div>
                        <div class="text-lg">
                            本次进货总额：<span class="text-red-600 font-bold text-3xl ml-2">￥{{ totalAmount.toFixed(2) }}</span>
                        </div>
                        <el-button type="primary" size="large" class="!px-10 !text-lg" :loading="submitLoading" :disabled="inboundList.length === 0" @click="submitOrder">
                            确认入库
                        </el-button>
                    </div>
                </div>
            </el-card>
        </div>
    </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import { ref, computed, nextTick } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Aim } from '@element-plus/icons-vue';
import goodsApi from "@/api/gms/goods.js";
import inventoryApi from "@/api/gms/inventory.js";

const scanInputRef = ref(null);
const scanBarcode = ref('');
const inboundList = ref([]);
const remark = ref('');
const submitLoading = ref(false);

// 自动计算总数量和总金额
const totalQty = computed(() => {
    return inboundList.value.reduce((sum, item) => sum + item.qty, 0);
});
const totalAmount = computed(() => {
    return inboundList.value.reduce((sum, item) => sum + (item.price * item.qty), 0);
});

// 🌟 核心：处理扫码枪回车事件
const handleScan = async () => {
    if (!scanBarcode.value) return;

    const barcode = scanBarcode.value;
    scanBarcode.value = ''; // 扫完立马清空，准备扫下一个

    // 1. 检查是不是已经在列表里了？如果在了，数量直接 +1
    const existItem = inboundList.value.find(item => item.barcode === barcode);
    if (existItem) {
        existItem.qty += 1;
        focusInput();
        return;
    }

    // 2. 如果不在列表里，去后台查这个商品
    try {
        const res = await goodsApi.list({ barcode: barcode, isCombo: 0, current: 1, size: 1 });
        if (res.data.records && res.data.records.length > 0) {
            const goods = res.data.records[0];
            // 把查到的商品塞进进货篮子
            inboundList.value.push({
                goodsId: goods.id,
                barcode: goods.barcode,
                name: goods.name,
                currentStock: goods.stock, // 显示系统里现在的库存
                price: goods.purchasePrice || 0, // 默认带出上次的进价
                qty: 1
            });
        } else {
            ElMessage.warning('未找到条码为 ' + barcode + ' 的普通商品！请先在商品库建档。');
        }
    } catch (e) {
        console.error(e);
    }
    focusInput(); // 保证焦点永远在输入框，扫码枪可以无缝连续扫
};

const removeItem = (index) => {
    inboundList.value.splice(index, 1);
};

const focusInput = () => {
    nextTick(() => {
        if (scanInputRef.value) {
            scanInputRef.value.focus();
        }
    });
};

// 提交入库单
const submitOrder = async () => {
    if (inboundList.value.length === 0) return;

    // 检查是否有进价为 0 的商品
    const hasZeroPrice = inboundList.value.some(item => item.price <= 0);
    if (hasZeroPrice) {
        await ElMessageBox.confirm('您有商品的本次进价为 0 元，确定要继续入库吗？', '提示', {
            type: 'warning',
            confirmButtonText: '继续入库',
            cancelButtonText: '我回去改改'
        });
    }

    submitLoading.value = true;
    try {
        // 组装发给 Java 后端的数据 (按照我们之前写的 DTO 格式)
        const payload = {
            type: 'INBOUND',
            remark: remark.value,
            details: inboundList.value.map(item => ({
                goodsId: item.goodsId,
                qty: item.qty,
                price: item.price
            }))
        };

        await inventoryApi.createInbound(payload);

        ElMessage.success('🎉 进货入库成功！库存已自动更新！');

        // 清空战场，准备下一单
        inboundList.value = [];
        remark.value = '';
        focusInput();

    } catch (e) {
        console.error(e);
        ElMessage.error('入库失败，请稍后重试');
    } finally {
        submitLoading.value = false;
    }
};
</script>