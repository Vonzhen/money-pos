package com.money.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存变动流水台账 (数量与资产双轨账本)
 */
@Data
public class GmsStockLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商品ID
     */
    private Long goodsId;

    /**
     * 商品名称
     */
    private String goodsName;

    /**
     * 商品条码
     */
    private String goodsBarcode;

    /**
     * 变动类型(SALE销售, RETURN退货, INBOUND入库, SCRAP报损, CHECK盘点)
     */
    private String type;

    /**
     * 变动数量(正负数)
     */
    private Integer quantity;

    /**
     * 变动后结余库存
     */
    private Integer afterQuantity;

    // ==========================================
    // 🌟 新增：财务显性化字段
    // ==========================================
    /**
     * 成本单价快照 (变动发生时的平均成本，或本次的实际入库价)
     */
    private BigDecimal costPriceSnapshot;

    /**
     * 资产影响金额 = quantity * costPriceSnapshot
     * (正数代表库存资产增加，负数代表库存资产流失)
     */
    private BigDecimal impactAmount;
    // ==========================================

    /**
     * 关联单号
     */
    private String orderNo;

    /**
     * 备注
     */
    private String remark;

    /**
     * 发生时间
     */
    private LocalDateTime createTime;

    /**
     * 操作人
     */
    private String creator;

    /**
     * 租户ID
     */
    private Long tenantId;
}