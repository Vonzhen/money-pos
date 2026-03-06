<template>
    <el-dialog v-model="visible" title="恢复挂单" width="600px" @closed="$emit('closed')">
        <el-table :data="suspendedList" border stripe>
            <el-table-column prop="time" label="时间" width="100" align="center" />
            <el-table-column label="会员" width="120" align="center">
                <template #default="{row}">{{ row.member.name || '普通散客' }}</template>
            </el-table-column>
            <el-table-column label="商品" min-width="150" show-overflow-tooltip>
                <template #default="{row}">{{ row.cart.map(c => c.name).join(', ') }}</template>
            </el-table-column>
            <el-table-column label="应收" width="90" align="right">
                <template #default="{row}"><span class="text-red-500 font-bold">￥{{ row.total.toFixed(2) }}</span></template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="center">
                <template #default="{$index}">
                    <el-button type="success" size="small" @click="retrieve($index)">取回</el-button>
                </template>
            </el-table-column>
        </el-table>
    </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps(['modelValue', 'suspendedList'])
const emit = defineEmits(['update:modelValue', 'retrieve', 'closed'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const retrieve = (index) => {
    emit('retrieve', index)
    visible.value = false
}
</script>