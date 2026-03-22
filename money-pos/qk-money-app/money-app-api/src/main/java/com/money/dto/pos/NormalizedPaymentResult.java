package com.money.dto.pos;

import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 🌟 统一支付试算/结算结果对象 (V5.2 修正版)
 * 职责：承载从前端录入到后端计算后的完整支付快照，支持找零精确溯源。
 */
@Data
public class NormalizedPaymentResult {
    // ==========================================
    // 🌟 整单层面的金额快照
    // ==========================================
    /** 顾客总递交金额 (totalPaid) */
    private BigDecimal totalPaid = BigDecimal.ZERO;

    /** 其中现金部分 */
    private BigDecimal cashPaid = BigDecimal.ZERO;

    /** 其中非现金部分 */
    private BigDecimal nonCashPaid = BigDecimal.ZERO;

    /** 核算出的找零 (changeAmount) */
    private BigDecimal changeAmount = BigDecimal.ZERO;

    /** 财务净入账 = totalPaid - changeAmount (netReceived) */
    private BigDecimal netReceived = BigDecimal.ZERO;

    /** 有效的支付明细列表 */
    private List<StandardPayItem> validItems = new ArrayList<>();

    /**
     * 内部类：标准支付项明细
     */
    @Data
    public static class StandardPayItem {
        /** 支付方式代码 (如: CASH, BALANCE, AGGREGATE) */
        private String methodCode;

        /** 支付方式名称 */
        private String methodName;

        /** 支付子标签 (如: WECHAT, ALIPAY) */
        private String payTag;

        /** 是否为现金类 */
        private boolean isCash;

        // ==========================================
        // 🌟 P1-2 核心修复：明细层面的金额胶囊
        // ==========================================

        /** 原始填入金额 (收银员录入的金额) */
        private BigDecimal originalAmount = BigDecimal.ZERO;

        /** * 🌟 修复符号缺失：找零分配额 (该项产生的找零)
         * 增加此字段以匹配 CheckoutPaymentService 中的 setChangeAmount 调用
         */
        private BigDecimal changeAmount = BigDecimal.ZERO;

        /** 扣除找零后的净额 (实际入账金额) */
        private BigDecimal netAmount = BigDecimal.ZERO;
    }
}