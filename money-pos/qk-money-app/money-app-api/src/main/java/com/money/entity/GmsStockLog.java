package com.money.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 库存变动流水台账
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