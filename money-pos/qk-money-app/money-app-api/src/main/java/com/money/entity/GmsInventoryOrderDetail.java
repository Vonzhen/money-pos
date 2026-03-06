package com.money.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GmsInventoryOrderDetail {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;   // 关联主单据
    private Long goodsId;   // 关联商品
    private Integer qty;    // 变动数量
    private BigDecimal price; // 进价/损耗价
    private LocalDateTime createTime;
    private Long tenantId;
}