package com.money.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应链周转预警每日快照实体类
 */
@Data
@TableName(value = "gms_turnover_warning_snapshot", autoResultMap = true)
public class GmsTurnoverWarningSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 快照归属日期
     */
    private LocalDate snapshotDate;

    /**
     * 触发【紧急补货】的商品总数 (红线)
     */
    private Integer replenishCount;

    /**
     * 触发【积压清仓】的商品总数 (蓝线)
     */
    private Integer deadStockCount;

    /**
     * 补货压力商品黑榜Top20 (JSON数组)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String topReplenishGoodsJson;

    /**
     * 积压死库存商品黑榜Top20 (JSON数组)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String topDeadStockGoodsJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}