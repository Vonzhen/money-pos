package com.money.dto.Pos;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * @author : money
 * @version : 1.0.0
 * @description : pos成员VO
 * @createTime : 2022-04-14 22:06:22
 */
@Data
public class PosMemberVO {

    private Long id;

    /**
     * 卡号
     */
    private String code;

    /**
     * 会员名称
     */
    private String name;

    /**
     * 会员类型 (前端用于直接显示或字典匹配)
     */
    private String type;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 抵用券
     */
    private BigDecimal coupon;

    @io.swagger.v3.oas.annotations.media.Schema(description="本金余额")
    private BigDecimal balance;

    // ==========================================
    // 🌟 必须加上的 3 个字段！否则前端永远收不到满减券和等级！
    // ==========================================
    @io.swagger.v3.oas.annotations.media.Schema(description="会员等级ID")
    private Long levelId;

    @io.swagger.v3.oas.annotations.media.Schema(description="拥有的满减券总数")
    private Integer couponCount;

    @io.swagger.v3.oas.annotations.media.Schema(description="具体的满减券规则列表(给下拉框用)")
    private List<MemberCouponRuleVO> couponList;

    // 定义发给前端的满减券明细结构
    @Data
    public static class MemberCouponRuleVO {
        private Long ruleId;
        private String name;
        private BigDecimal threshold;
        private BigDecimal deduction;
        private Integer availableCount;
    }
}