package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.BizErrorStatus;
import com.money.dto.pos.SettleTrialReqDTO;
import com.money.dto.pos.SettleTrialResVO;
import com.money.entity.GmsGoods;
import com.money.entity.PosCouponRule;
import com.money.entity.PosSkuLevelPrice;
import com.money.entity.UmsMemberBrandLevel;
import com.money.mapper.PosCouponRuleMapper;
import com.money.mapper.PosSkuLevelPriceMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.service.GmsGoodsService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosCalculationEngine {

    private final GmsGoodsService gmsGoodsService;
    private final UmsMemberBrandLevelMapper brandLevelMapper;
    private final PosSkuLevelPriceMapper skuLevelPriceMapper;
    private final PosCouponRuleMapper couponRuleMapper;

    public SettleTrialResVO calculate(SettleTrialReqDTO req) {
        SettleTrialResVO res = new SettleTrialResVO();
        res.setTotalAmount(BigDecimal.ZERO);
        res.setFinalPayAmount(BigDecimal.ZERO);
        res.setParticipatingAmount(BigDecimal.ZERO);
        res.setCostAmount(BigDecimal.ZERO);
        res.setMemberCouponDeduct(BigDecimal.ZERO);
        res.setVoucherDeduct(BigDecimal.ZERO);
        res.setManualDeduct(req.getManualDiscountAmount() != null ? req.getManualDiscountAmount() : BigDecimal.ZERO);

        if (req.getItems() == null || req.getItems().isEmpty()) return res;

        List<Long> goodsIds = req.getItems().stream().map(SettleTrialReqDTO.TrialItem::getGoodsId).collect(Collectors.toList());
        Map<Long, GmsGoods> goodsMap = gmsGoodsService.listByIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));

        Map<String, String> memberBrandLevels = new HashMap<>();
        Map<Long, List<PosSkuLevelPrice>> skuPriceMap = new HashMap<>();

        if (req.getMember() != null) {
            List<UmsMemberBrandLevel> levels = brandLevelMapper.selectList(new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, req.getMember()));
            levels.forEach(l -> {
                if (l.getBrand() != null) {
                    memberBrandLevels.put(l.getBrand().trim(), l.getLevelCode());
                }
            });

            skuPriceMap = skuLevelPriceMapper.selectList(new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, goodsIds))
                    .stream().collect(Collectors.groupingBy(PosSkuLevelPrice::getSkuId));
        }

        for (SettleTrialReqDTO.TrialItem reqItem : req.getItems()) {
            GmsGoods goods = goodsMap.get(reqItem.getGoodsId());
            if (goods == null) {
                throw new BaseException("【试算拦截】发现不存在或已下架的商品ID: " + reqItem.getGoodsId());
            }

            // 🌟🌟🌟 修复版：带有 {0} 和 {1} 的库存前置拦截 🌟🌟🌟
            long currentStock = goods.getStock() != null ? goods.getStock() : 0L;
            if (reqItem.getQuantity() > currentStock) {
                log.warn("【计价引擎拦截】商品 {} 缺货。请求数量: {}, 实际库存: {}", goods.getName(), reqItem.getQuantity(), currentStock);

                throw new BaseException(BizErrorStatus.STOCK_NOT_ENOUGH,
                        "结算中断！商品【{0}】库存不足，当前仅剩 {1} 件，请修改购物车数量后再结账。",
                        goods.getName(), currentStock).withData(currentStock);
            }
            // 🌟🌟🌟 ==================================== 🌟🌟🌟

            BigDecimal qty = new BigDecimal(reqItem.getQuantity());

            BigDecimal unitOriginalPrice = goods.getSalePrice() != null ? goods.getSalePrice() : BigDecimal.ZERO;
            BigDecimal unitRealPrice = unitOriginalPrice;
            BigDecimal unitCoupon = BigDecimal.ZERO;
            BigDecimal unitCost = goods.getAvgCostPrice() != null ? goods.getAvgCostPrice() :
                    (goods.getPurchasePrice() != null ? goods.getPurchasePrice() : BigDecimal.ZERO);

            String brandKey = goods.getBrandId() != null ? String.valueOf(goods.getBrandId()) : "";
            String levelCode = memberBrandLevels.get(brandKey);

            if (levelCode != null && skuPriceMap.containsKey(goods.getId())) {
                for (PosSkuLevelPrice sp : skuPriceMap.get(goods.getId())) {
                    if (levelCode.equals(sp.getLevelId())) {
                        if (sp.getMemberPrice() == null || sp.getMemberPrice().compareTo(BigDecimal.ZERO) < 0) {
                            throw new BaseException("商品「" + goods.getName() + "」的会员价配置异常，请联系管理员核实");
                        }
                        unitRealPrice = sp.getMemberPrice();
                        if (!Boolean.TRUE.equals(req.getWaiveCoupon()) && sp.getMemberCoupon() != null) {
                            unitCoupon = sp.getMemberCoupon();
                        }
                        break;
                    }
                }
            }

            BigDecimal itemTotalReal = unitRealPrice.multiply(qty);
            BigDecimal itemTotalCoupon = unitCoupon.multiply(qty);
            BigDecimal itemTotalCost = unitCost.multiply(qty);

            SettleTrialResVO.ItemRes itemRes = new SettleTrialResVO.ItemRes();
            itemRes.setGoodsId(goods.getId());
            itemRes.setOriginalPrice(unitOriginalPrice);
            itemRes.setRealPrice(unitRealPrice);
            itemRes.setCouponDeduct(itemTotalCoupon);
            itemRes.setQuantity(reqItem.getQuantity());
            itemRes.setSubTotal(itemTotalReal);
            res.getItems().add(itemRes);

            res.setTotalAmount(res.getTotalAmount().add(itemTotalReal));
            res.setCostAmount(res.getCostAmount().add(itemTotalCost));
            res.setMemberCouponDeduct(res.getMemberCouponDeduct().add(itemTotalCoupon));

            if (goods.getIsDiscountParticipable() != null && goods.getIsDiscountParticipable() == 1) {
                res.setParticipatingAmount(res.getParticipatingAmount().add(itemTotalReal));
            }
        }

        if (req.getUsedCouponRuleId() != null && req.getUsedCouponCount() != null && req.getUsedCouponCount() > 0) {
            PosCouponRule rule = couponRuleMapper.selectById(req.getUsedCouponRuleId());
            if (rule == null) {
                throw new BaseException("【试算拦截】选用的满减券规则不存在");
            }

            BigDecimal requiredAmount = rule.getThresholdAmount().multiply(new BigDecimal(req.getUsedCouponCount()));
            if (res.getParticipatingAmount().compareTo(requiredAmount) < 0) {
                // 🌟🌟🌟 修复版：带有 {0} 的满减券风控拦截
                throw new BaseException(BizErrorStatus.COUPON_NOT_ENOUGH,
                        "【券风控拦截】参与满减活动商品总额未达到使用 {0} 张券的门槛！",
                        req.getUsedCouponCount());
            }

            BigDecimal voucherDeduct = rule.getDiscountAmount().multiply(new BigDecimal(req.getUsedCouponCount()));
            res.setVoucherDeduct(voucherDeduct);
        }

        BigDecimal maxAllowedManual = res.getTotalAmount().subtract(res.getVoucherDeduct());
        if (res.getManualDeduct().compareTo(maxAllowedManual) > 0) {
            throw new BaseException(BizErrorStatus.POS_MANUAL_DISCOUNT_EXCEED, "【风控拦截】手工优惠额超过了本单可优惠上限");
        }

        BigDecimal finalPay = res.getTotalAmount()
                .subtract(res.getVoucherDeduct())
                .subtract(res.getManualDeduct());

        res.setFinalPayAmount(finalPay.compareTo(BigDecimal.ZERO) > 0 ? finalPay : BigDecimal.ZERO);

        // 精度封印
        res.setTotalAmount(res.getTotalAmount().setScale(2, RoundingMode.HALF_UP));
        res.setFinalPayAmount(res.getFinalPayAmount().setScale(2, RoundingMode.HALF_UP));
        res.setParticipatingAmount(res.getParticipatingAmount().setScale(2, RoundingMode.HALF_UP));
        res.setCostAmount(res.getCostAmount().setScale(2, RoundingMode.HALF_UP));
        res.setMemberCouponDeduct(res.getMemberCouponDeduct().setScale(2, RoundingMode.HALF_UP));
        res.setVoucherDeduct(res.getVoucherDeduct().setScale(2, RoundingMode.HALF_UP));
        res.setManualDeduct(res.getManualDeduct().setScale(2, RoundingMode.HALF_UP));

        for (SettleTrialResVO.ItemRes item : res.getItems()) {
            item.setOriginalPrice(item.getOriginalPrice().setScale(2, RoundingMode.HALF_UP));
            item.setRealPrice(item.getRealPrice().setScale(2, RoundingMode.HALF_UP));
            item.setCouponDeduct(item.getCouponDeduct().setScale(2, RoundingMode.HALF_UP));
            item.setSubTotal(item.getSubTotal().setScale(2, RoundingMode.HALF_UP));
        }

        return res;
    }
}