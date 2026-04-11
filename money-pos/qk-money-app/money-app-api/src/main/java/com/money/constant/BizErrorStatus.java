package com.money.constant;

import com.money.web.response.IStatus; // 复用系统已有的响应标准接口

/**
 * @author : 架构组
 * @description : 全局业务错误状态码 (与 SysErrorStatus 的 1xxx 区分开)
 */
public enum BizErrorStatus implements IStatus {

    // ==========================================
    // 20xxx: POS 收银结算相关异常
    // ==========================================
    POS_SETTLE_REQ_EMPTY(20001, "结算请求主体为空"),
    POS_ITEM_EMPTY(20002, "订单明细不能为空"),
    POS_PAYMENT_INVALID(20003, "非法的支付方式或金额"),
    POS_PAYMENT_NOT_ENOUGH(20004, "实付金额不足"),
    POS_CASH_OUT_FORBIDDEN(20005, "禁止非现金超付套现"),
    POS_ORDER_DUPLICATED(20006, "订单已处理，请勿重复提交"),
    POS_MANUAL_DISCOUNT_EXCEED(20007, "手工优惠额超过上限"),

    // 🌟 核心新增：专门为退款防线定制的标准错误码
    POS_REFUND_NOT_FOUND(20008, "退货单或明细不存在，或已被篡改"),
    POS_REFUND_REPEAT(20009, "订单已退款或正在处理，请勿重复点击"),
    POS_REFUND_QTY_INVALID(20010, "可退数量不足或发生并发冲突"),
    // 🌟 统一命名：与 RefundStateGuard 保持一致
    POS_REFUND_STATUS_INVALID(20011, "当前订单状态不支持退款操作"),

    // ==========================================
    // 30xxx: 商品与库存相关异常
    // ==========================================
    GOODS_NOT_FOUND(30001, "商品不存在或已下架"),
    STOCK_NOT_ENOUGH(30002, "商品剩余库存不足或发生抢购"),
    COMBO_NOT_CONFIGURED(30003, "套餐商品未配置子明细"),
    STOCK_CALC_OVERFLOW(30004, "扣减总数超出系统安全上限"),

    // ==========================================
    // 40xxx: 会员与资产相关异常
    // ==========================================
    MEMBER_NOT_FOUND(40001, "未找到对应的会员信息"),
    BALANCE_INSUFFICIENT(40002, "会员余额不足"),
    COUPON_NOT_ENOUGH(40003, "满减券可用数量不足或未达门槛"),
    COUPON_OCCUPIED(40004, "优惠券已被其他订单抢占");

    final int code;
    final String message;

    BizErrorStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}