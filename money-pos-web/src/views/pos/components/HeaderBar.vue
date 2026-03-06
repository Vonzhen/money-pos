<template>
  <div :class="['px-5 py-2 flex justify-between items-center shadow-md shrink-0 text-white transition-colors duration-300', theme.header]">
    <div class="flex items-center gap-8">
      <div class="font-black text-xl flex items-center gap-2 tracking-widest">
        <el-icon><Monitor /></el-icon> 门店极速收银 <span class="text-xs opacity-50 ml-2">V4.4.2</span>
      </div>
      <div class="flex gap-6 text-base font-bold">
        <el-link underline="never" class="!text-white hover:opacity-80" @click="$emit('action', 'sales')"><el-icon class="mr-1 text-lg"><Document /></el-icon>销售单</el-link>
        <el-link underline="never" class="!text-white hover:opacity-80" @click="$emit('action', 'shift')"><el-icon class="mr-1 text-lg"><Switch /></el-icon>交接班</el-link>
        <el-link underline="never" class="!text-white hover:opacity-80" @click="$emit('action', 'addMember')"><el-icon class="mr-1 text-lg"><User /></el-icon>新增会员</el-link>
        <el-link underline="never" class="!text-white hover:opacity-80" @click="$emit('action', 'recharge')"><el-icon class="mr-1 text-lg"><Money /></el-icon>会员充值</el-link>
        <el-link underline="never" class="!text-white hover:opacity-80" @click="$emit('action', 'restock')"><el-icon class="mr-1 text-lg"><Box /></el-icon>极速补货</el-link>
      </div>
    </div>

    <div class="text-base flex items-center gap-6 font-bold tracking-wider">
      <el-tooltip content="在新标签页打开后台管理" placement="bottom">
          <el-button color="rgba(255,255,255,0.2)" class="!border-none !text-white hover:!bg-white/40" @click="$emit('action', 'admin')">
              <el-icon class="mr-1"><HomeFilled /></el-icon> 后台管理
          </el-button>
      </el-tooltip>

      <span class="flex items-center"><el-icon class="mr-1"><Avatar /></el-icon>收银员: {{ cashierName }}</span>
      <span class="font-mono text-lg">{{ currentTime }}</span>
      <el-dropdown trigger="click" @command="$emit('change-theme', $event)">
        <span class="text-white cursor-pointer flex items-center hover:bg-white/20 px-3 py-1 rounded transition-colors">
          <el-icon class="mr-1"><Brush /></el-icon> 换肤
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="orange">活力暖橙</el-dropdown-item>
            <el-dropdown-item command="blue">经典商务蓝</el-dropdown-item>
            <el-dropdown-item command="dark">暗夜极客黑</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
// 🌟 别忘了导入 HomeFilled 图标
import { Monitor, Document, Switch, User, Money, Box, Avatar, Brush, HomeFilled } from '@element-plus/icons-vue'

defineProps(['theme', 'cashierName', 'currentTime'])
defineEmits(['action', 'change-theme'])
</script>