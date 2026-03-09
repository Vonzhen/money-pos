<template>
    <PageWrapper>
        <div class="flex flex-col gap-4 h-full">
            <el-card shadow="hover" class="w-full" :body-style="{ padding: '20px' }">
                <div class="flex items-center gap-4">
                    <el-icon class="text-3xl text-warning"><Aim /></el-icon>

                    <SmartGoodsSelector
                        ref="scanInputRef"
                        v-model="scanBarcode"
                        mode="report"
                        placeholder="扫码枪滴入要盘点的商品，或拼音联想点选"
                        class="!w-1/2"
                        @select="handleSelectGoods"
                        @search="handleScan"
                    />

                    <el-button type="warning" @click="handleScan(scanBarcode)">定位商品</el-button>

                    <div class="ml-auto text-gray-500 text-sm">
                        输入货架上的【实际数量】，系统将直接用该数量覆盖账面库存
                    </div>
                </div>
            </el-card>

            <el-card shadow="hover" class="flex-1 overflow-hidden" :body-style="{ padding: '10px' }">
                <el-table :data="checkList" border stripe height="calc(100vh - 380px)" size="large" empty-text="等待扫描盘点...">
                    <el-table-column type="index" label="序号" width="60" align="center" />
                    <el-table-column prop="barcode" label="条码" width="160" />
                    <el-table-column prop="name" label="商品名称" min-width="200" />
                    <el-table-column label="系统账面库存" width="150" align="center">
                        <template #default="{row}">
                            <el-tag type="info" size="large" class="!text-lg">{{ row.currentStock }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="差异 (盈亏)" width="120" align="center">
                        <template #default="{row}">
                            <span :class="row.qty > row.currentStock ? 'text-green-500 font-bold' : (row.qty < row.currentStock ? 'text-red-500 font-bold' : 'text-gray-400')">
                                {{ row.qty > row.currentStock ? '+' : '' }}{{ row.qty - row.currentStock }}
                            </span>
                        </template>
                    </el-table-column>
                    <el-table-column label="货架实际数量" width="180">
                        <template #default="{row}">
                            <el-input-number v-model="row.qty" :min="0" size="large" class="!w-full border-warning" />
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
                        <span class="text-gray-600 whitespace-nowrap">盘点备注：</span>
                        <el-input v-model="remark" placeholder="如：2026年3月月底例行盘点" />
                    </div>
                    <div class="flex items-center gap-6">
                        <div class="text-lg">
                            本次共盘点 <span class="text-warning font-bold text-2xl mx-1">{{ checkList.length }}</span> 款商品
                        </div>
                        <el-button type="warning" size="large" class="!px-10 !text-lg" :loading="submitLoading" :disabled="checkList.length === 0" @click="submitOrder">
                            确认覆盖库存
                        </el-button>
                    </div>
                </div>
            </el-card>
        </div>
    </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Aim } from '@element-plus/icons-vue';
import goodsApi from "@/api/gms/goods.js";
import inventoryApi from "@/api/gms/inventory.js";

// 🌟 引入刚封装好的智能神器
import SmartGoodsSelector from '@/components/SmartGoodsSelector.vue';

const scanInputRef = ref(null);
const scanBarcode = ref('');
const checkList = ref([]);
const remark = ref('');
const submitLoading = ref(false);

// 🌟 核心抽离：将商品塞进盘点单的逻辑
const addGoodsToList = (goods) => {
    const existItem = checkList.value.find(item => item.barcode === goods.barcode || item.goodsId === goods.id);
    if (existItem) {
        ElMessage.success(`该商品已在下方列表中，请直接修改实际数量`);
    } else {
        checkList.value.unshift({
            goodsId: goods.id,
            barcode: goods.barcode,
            name: goods.name,
            currentStock: goods.stock || 0,
            qty: goods.stock || 0 // 盘点默认带出系统数量，方便微调
        });
    }
    // 添加完毕后，清空搜索框准备查下一个
    scanInputRef.value?.resetScanner();
};

let isSelecting = false; // 防重复双杀拦截器

// 1. 鼠标点选联想列表触发
const handleSelectGoods = (item) => {
    isSelecting = true;
    addGoodsToList(item);
    setTimeout(() => { isSelecting = false; }, 200);
};

// 2. 扫码枪或回车键直接触发
const handleScan = async (val) => {
    if (isSelecting) return;
    const barcode = typeof val === 'string' ? val : scanBarcode.value;
    if (!barcode) return;

    // 先查本地列表
    const existItem = checkList.value.find(item => item.barcode === barcode);
    if (existItem) {
        ElMessage.success(`该商品已在下方列表中，请直接修改实际数量`);
        scanInputRef.value?.resetScanner();
        return;
    }

    // 若不在列表中，去后台穿透查询
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

const removeItem = (index) => {
    checkList.value.splice(index, 1);
};

// 提交盘点单
const submitOrder = async () => {
    if (checkList.value.length === 0) return;

    await ElMessageBox.confirm('盘点后，系统的库存将被强制替换为【货架实际数量】，此操作不可逆，确定执行吗？', '盘点确认', {
        type: 'warning',
        confirmButtonText: '确定覆盖',
        cancelButtonText: '再核对一下'
    });

    submitLoading.value = true;
    try {
        const payload = {
            type: 'CHECK',
            remark: remark.value,
            details: checkList.value.map(item => ({
                goodsId: item.goodsId,
                qty: item.qty // 核心：把人工确认的实际数量发给后端
            }))
        };

        await inventoryApi.createCheck(payload);

        ElMessage.success('🎉 盘点完成！库存已按实际数量修正！');

        checkList.value = [];
        remark.value = '';
        scanInputRef.value?.resetScanner();

    } catch (e) {
        console.error(e);
        ElMessage.error('盘点提交失败，请重试');
    } finally {
        submitLoading.value = false;
    }
};
</script>