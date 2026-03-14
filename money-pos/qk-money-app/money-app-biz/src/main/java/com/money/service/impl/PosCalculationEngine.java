package com.money.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.web.exception.BaseException;
import com.money.dto.pos.SettleTrialReqDTO;
import com.money.dto.pos.SettleTrialResVO;
import com.money.entity.*;
import com.money.mapper.*;
import com.money.service.GmsGoodsService;
import com.money.service.UmsMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PosCalculationEngine {
    private final GmsGoodsService gmsGoodsService;
    private final UmsMemberService umsMemberService;
    private final SysBrandConfigMapper sysBrandConfigMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;
    private final PosSkuLevelPriceMapper posSkuLevelPriceMapper;
    private final PosCouponRuleMapper posCouponRuleMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;

    public SettleTrialResVO calculate(SettleTrialReqDTO req) {
        SettleTrialResVO res = new SettleTrialResVO();
        boolean isVip = req.getMember() != null;
        UmsMember member = isVip ? umsMemberService.getById(req.getMember()) : null;

        List<Long> goodsIds = req.getItems().stream().map(SettleTrialReqDTO.TrialItem::getGoodsId).collect(Collectors.toList());
        if (goodsIds.isEmpty()) return res;
        Map<Long, GmsGoods> goodsMap = gmsGoodsService.listByIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));

        for (SettleTrialReqDTO.TrialItem itemReq : req.getItems()) {
            GmsGoods goods = goodsMap.get(itemReq.getGoodsId());
            if (goods == null) continue;
            BigDecimal unitBasePrice = goods.getSalePrice();
            BigDecimal qty = new BigDecimal(itemReq.getQuantity());
            res.setTotalAmount(res.getTotalAmount().add(unitBasePrice.multiply(qty)));
            // ... (简化的核心计算逻辑)
            SettleTrialResVO.ItemRes itemRes = new SettleTrialResVO.ItemRes();
            itemRes.setGoodsId(goods.getId());
            itemRes.setOriginalPrice(unitBasePrice);
            itemRes.setRealPrice(unitBasePrice);
            itemRes.setQuantity(itemReq.getQuantity());
            itemRes.setSubTotal(unitBasePrice.multiply(qty));
            res.getItems().add(itemRes);
        }
        res.setFinalPayAmount(res.getTotalAmount().subtract(res.getManualDeduct()).setScale(2, RoundingMode.HALF_UP));
        return res;
    }
}