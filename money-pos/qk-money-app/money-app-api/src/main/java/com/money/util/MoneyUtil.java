package com.money.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author : 架构组
 * @description : 财务级金额计算标准组件 (防精度漂移、防 Null 指针)
 */
public class MoneyUtil {

    // 🌟 全局法定精度：2位小数
    private static final int SCALE = 2;
    // 🌟 全局法定舍入规则：四舍五入
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * 安全处理 null 值，所有 null 均视作 0
     */
    public static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 终极标准化：强制按法定精度格式化金额
     */
    public static BigDecimal format(BigDecimal value) {
        return safe(value).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 加法 (a + b)
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return format(safe(a).add(safe(b)));
    }

    /**
     * 减法 (a - b)
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return format(safe(a).subtract(safe(b)));
    }

    /**
     * 乘法 (a * b)
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return format(safe(a).multiply(safe(b)));
    }

    /**
     * 乘法 (a * 整数数量)
     */
    public static BigDecimal multiply(BigDecimal a, int quantity) {
        return format(safe(a).multiply(new BigDecimal(quantity)));
    }

    /**
     * 除法 (a / b) - 必须带精度防止除不尽报 ArithmeticException
     */
    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (safe(b).compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("金额计算除数不能为0");
        }
        return safe(a).divide(safe(b), SCALE, ROUNDING_MODE);
    }

    // --- 安全比较方法 ---

    public static boolean isZero(BigDecimal value) {
        return safe(value).compareTo(BigDecimal.ZERO) == 0;
    }

    public static boolean isGreaterThan(BigDecimal a, BigDecimal b) {
        return safe(a).compareTo(safe(b)) > 0;
    }

    public static boolean isLessThan(BigDecimal a, BigDecimal b) {
        return safe(a).compareTo(safe(b)) < 0;
    }
}