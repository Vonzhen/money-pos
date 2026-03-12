<template>
    <span :class="['inline-flex items-baseline font-mono', customClass, colorClass]">
        <span v-if="showSign && isPositive" class="mr-[1px]">+</span
        ><span v-if="isNegative" class="mr-[1px]">-</span>

        <span v-if="showSymbol" class="currency-symbol mr-[2px]" :class="symbolSizeClass">{{ symbol }}</span>

        <template v-if="split">
            <span class="integer-part font-bold" :class="integerSizeClass">{{ integerPart }}</span>
            <span class="decimal-part font-bold" :class="decimalSizeClass">.{{ decimalPart }}</span>
        </template>

        <template v-else>
            <span class="amount-part font-bold" :class="integerSizeClass">{{ absFormattedValue }}</span>
        </template>
    </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
    // 核心数值
    value: { type: [Number, String], default: 0 },

    // UI 样式控制
    size: { type: String, default: 'base' }, // 尺寸选项：xs, sm, base, lg, xl, 2xl, 3xl, 4xl
    color: { type: String, default: '' }, // 自定义 Tailwind 颜色类 (如 text-red-500)
    customClass: { type: String, default: '' }, // 其他自定义类名

    // 格式化规则
    symbol: { type: String, default: '￥' }, // 货币符号
    showSymbol: { type: Boolean, default: true }, // 是否显示符号
    thousands: { type: Boolean, default: true }, // 是否开启千分位 (1,234.00)
    split: { type: Boolean, default: false }, // 是否开启电商风 (整数大，小数小)

    // 财务规则
    showSign: { type: Boolean, default: false }, // 强制显示正号 (+)
    autoColor: { type: Boolean, default: false }, // 是否根据正负自动变色 (正数绿/红，负数红/绿，视业务而定)
})

// 🌟 安全的数值转换，彻底杜绝 NaN 和 undefined 报错
const numericValue = computed(() => {
    if (props.value === null || props.value === undefined || props.value === '') return 0;
    const val = Number(props.value);
    return isNaN(val) ? 0 : val;
})

const isNegative = computed(() => numericValue.value < 0)
const isPositive = computed(() => numericValue.value > 0)
const absValue = computed(() => Math.abs(numericValue.value))

// 🌟 核心格式化：处理千分位和保留两位小数
const absFormattedValue = computed(() => {
    if (props.thousands) {
        return absValue.value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    return absValue.value.toFixed(2);
})

// 拆分整数和小数 (用于电商风)
const integerPart = computed(() => absFormattedValue.value.split('.')[0])
const decimalPart = computed(() => absFormattedValue.value.split('.')[1])

// 🌟 尺寸计算引擎 (动态计算符号、整数、小数的对应字号)
const sizeMap = {
    'xs': { sym: 'text-[10px]', int: 'text-xs', dec: 'text-[10px]' },
    'sm': { sym: 'text-xs', int: 'text-sm', dec: 'text-xs' },
    'base': { sym: 'text-sm', int: 'text-base', dec: 'text-sm' },
    'lg': { sym: 'text-base', int: 'text-lg', dec: 'text-sm' },
    'xl': { sym: 'text-lg', int: 'text-xl', dec: 'text-base' },
    '2xl': { sym: 'text-xl', int: 'text-2xl', dec: 'text-lg' },
    '3xl': { sym: 'text-2xl', int: 'text-3xl', dec: 'text-xl' },
    '4xl': { sym: 'text-3xl', int: 'text-4xl', dec: 'text-2xl' },
}

const symbolSizeClass = computed(() => props.split ? sizeMap[props.size]?.sym : '')
const integerSizeClass = computed(() => sizeMap[props.size]?.int || `text-${props.size}`)
const decimalSizeClass = computed(() => props.split ? sizeMap[props.size]?.dec : '')

// 🌟 自动财务变色引擎 (默认业务逻辑：正数盈利蓝色/绿色，负数亏损红色)
const colorClass = computed(() => {
    if (props.color) return props.color; // 传入的颜色优先级最高
    if (props.autoColor) {
        if (isPositive.value) return 'text-emerald-600'; // 盈利绿
        if (isNegative.value) return 'text-rose-600'; // 亏损红
        return 'text-gray-400'; // 零
    }
    return 'text-gray-800'; // 默认纯黑色
})

</script>