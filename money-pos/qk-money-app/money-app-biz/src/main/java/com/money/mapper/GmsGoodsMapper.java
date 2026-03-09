package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.entity.GmsGoods;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface GmsGoodsMapper extends BaseMapper<GmsGoods> {

    /**
     * 原子化库存扣减 (利用 MySQL 行锁防超卖)
     * 只有当 stock >= qty 时才能更新成功，返回 1；否则返回 0
     */
    @Update("UPDATE gms_goods SET stock = stock - #{qty} WHERE id = #{id} AND stock >= #{qty}")
    int deductStockAtomically(@Param("id") Long id, @Param("qty") Integer qty);
}