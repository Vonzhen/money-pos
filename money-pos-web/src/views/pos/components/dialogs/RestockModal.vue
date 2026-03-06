<template>
    <el-dialog v-model="visible" title="前台批量极速补货" width="700px" @closed="$emit('closed')">
        <div class="flex gap-2 mb-4">
            <el-autocomplete v-model="tempBarcode" :fetch-suggestions="querySearch" placeholder="扫码联想" class="w-full !text-lg" size="large" clearable highlight-first-item value-key="displayValue" @select="handleSelect" @keyup.enter="handleEnter">
                <template #prefix><el-icon class="text-xl font-black ml-1"><Search /></el-icon></template>
                <template #default="{ item }"><div class="flex justify-between items-center w-full"><span class="font-bold">{{ item.name }}</span><span class="text-gray-400 text-sm">{{ item.barcode }}</span></div></template>
            </el-autocomplete>
        </div>
        <el-table :data="list" border stripe height="300px">
            <el-table-column prop="barcode" label="条码" width="160" />
            <el-table-column prop="name" label="商品名称" />
            <el-table-column label="数量" width="160" align="center"><template #default="{row}"><el-input-number v-model="row.quantity" :min="1" size="small" /></template></el-table-column>
            <el-table-column label="操作" width="80" align="center"><template #default="{$index}"><el-button type="danger" link @click="list.splice($index, 1)">移除</el-button></template></el-table-column>
        </el-table>
        <template #footer><el-button type="success" size="large" class="w-full font-bold tracking-widest text-lg" @click="submit" :loading="loading">确认入库</el-button></template>
    </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import goodsApi from "@/api/gms/goods.js"
import { req } from "@/api/index.js"

const props = defineProps(['modelValue'])
const emit = defineEmits(['update:modelValue', 'closed'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const tempBarcode = ref(''); const list = ref([]); const loading = ref(false)

const querySearch = async (qs, cb) => { if(!qs) return cb([]); const res = await goodsApi.posSearch(qs); cb(res.data.map(i => ({...i, displayValue: i.barcode}))) }
const handleSelect = (item) => { const exist = list.value.find(i => i.barcode === item.barcode); if (exist) exist.quantity += 1; else list.value.push({ goodsId: item.id, barcode: item.barcode, name: item.name, quantity: 1 }); setTimeout(() => tempBarcode.value = '', 50) }
const handleEnter = async () => { if (!tempBarcode.value) return; try { const res = await goodsApi.posSearch(tempBarcode.value); if (res.data?.length > 0) handleSelect(res.data[0]) } catch(e){} finally { tempBarcode.value = '' } }

const submit = async () => {
    if(list.value.length === 0) return;
    loading.value = true;
    try {
        await req({ url: '/gms/inventory/inbound', method: 'POST', data: { type: 'INBOUND', remark: '前台极速补货', details: list.value.map(i => ({ goodsId: i.goodsId, qty: i.quantity })) } });
        ElMessage.success('批量入库单创建成功！'); visible.value = false; list.value = [];
    } catch (e) { ElMessage.error('入库失败'); } finally { loading.value = false; }
}
</script>