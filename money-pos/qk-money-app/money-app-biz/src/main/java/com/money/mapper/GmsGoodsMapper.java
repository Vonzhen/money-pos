package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.entity.GmsGoods;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface GmsGoodsMapper extends BaseMapper<GmsGoods> {

    /**
     * 🌟 原子化库存扣减 (支持线下门店超卖场景)
     * 移除了 stock >= qty 的限制，允许库存跌穿至负数（负数代表系统欠实物的账，后续入库可抹平）。
     * 依然保留 UPDATE ... SET stock = stock - X 的行锁特性，确保并发下扣减数值绝对精准。
     */
    @Update("UPDATE gms_goods SET stock = stock - #{qty} WHERE id = #{id}")
    int deductStockAtomically(@Param("id") Long id, @Param("qty") BigDecimal qty);
}