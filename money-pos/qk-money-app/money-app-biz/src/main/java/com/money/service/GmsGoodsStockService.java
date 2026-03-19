package com.money.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.money.entity.GmsGoods;
import com.money.entity.GmsGoodsCombo;
import com.money.mapper.GmsGoodsComboMapper;
import com.money.mapper.GmsGoodsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 领域服务：商品库存高敏子域 (Domain Service)
 * 职责：统一管控库存的扣减、退还、联动计算与大盘货值统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GmsGoodsStockService {

    private final GmsGoodsMapper gmsGoodsMapper;
    private final GmsGoodsComboMapper gmsGoodsComboMapper;

    /**
     * 核心变更：增减库存 (智能支持套餐联动拆解计算)
     */
    public void updateStock(Long goodsId, Integer qty) {
        if (goodsId == null || qty == null || qty == 0) return;

        GmsGoods goods = gmsGoodsMapper.selectById(goodsId);
        if (goods == null) return;

        if (goods.getIsCombo() != null && goods.getIsCombo() == 1) {
            // 如果是套餐，必须拆解成子商品，扣减子商品的真实库存
            List<GmsGoodsCombo> combos = gmsGoodsComboMapper.selectList(
                    new LambdaQueryWrapper<GmsGoodsCombo>().eq(GmsGoodsCombo::getComboGoodsId, goodsId)
            );
            if (combos != null && !combos.isEmpty()) {
                for (GmsGoodsCombo combo : combos) {
                    GmsGoods subGoods = gmsGoodsMapper.selectById(combo.getSubGoodsId());
                    if (subGoods != null) {
                        int addQty = qty * (combo.getSubGoodsQty() != null ? combo.getSubGoodsQty() : 1);
                        long currentStock = subGoods.getStock() == null ? 0 : subGoods.getStock();
                        subGoods.setStock(currentStock + addQty);
                        gmsGoodsMapper.updateById(subGoods);
                    }
                }
            }
        } else {
            // 普通商品，直接增减库存
            long currentStock = goods.getStock() == null ? 0 : goods.getStock();
            goods.setStock(currentStock + qty);
            gmsGoodsMapper.updateById(goods);
        }
    }

    /**
     * 核心统计：计算全盘实时库存总货值
     */
    public BigDecimal getCurrentStockValue() {
        try {
            QueryWrapper<GmsGoods> wrapper = new QueryWrapper<>();
            wrapper.select("IFNULL(SUM(stock * purchase_price), 0) AS totalValue").gt("stock", 0);
            Map<String, Object> map = gmsGoodsMapper.selectMaps(wrapper).stream().findFirst().orElse(null);
            if (map != null && map.get("totalValue") != null) {
                return new BigDecimal(map.get("totalValue").toString());
            }
        } catch (Exception e) {
            log.error("库存总货值计算异常: ", e);
        }
        return BigDecimal.ZERO;
    }
}