package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.BizErrorStatus;
import com.money.dto.pos.PricingItemResult;
import com.money.dto.pos.PricingResult;
import com.money.dto.pos.SettleTrialReqDTO;
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

    /**
     * 🌟 内部计算上下文：避免私有方法之间传参混乱
     */
    private static class CalcContext {
        SettleTrialReqDTO req;
        PricingResult result;
        Map<Long, GmsGoods> goodsMap;
        Map<String, String> memberBrandLevels = new HashMap<>();
        Map<Long, List<PosSkuLevelPrice>> skuPriceMap = new HashMap<>();
    }

    /**
     * 🌟 唯一计价入口：管道流处理模式
     */
    public PricingResult calculate(SettleTrialReqDTO req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            return new PricingResult();
        }

        // 1. 初始化上下文 (查库准备数据)
        CalcContext ctx = initContext(req);

        // 2. 价格轨计算：确定零售价与会员价
        calculateBasePrices(ctx);

        // 3. 核销轨计算：分流特权差额 (真实核销 vs 店铺承担)
        dispatchSettlementTrack(ctx);

        // 4. 营销轨计算：叠加外挂促销 (满减/手工)
        calculateMarketingDeduct(ctx);

        // 5. 格式化并返回
        return formatScale(ctx.result);
    }

    // ================= 私有流水线步骤 =================

    private CalcContext initContext(SettleTrialReqDTO req) {
        CalcContext ctx = new CalcContext();
        ctx.req = req;
        ctx.result = new PricingResult();
        ctx.result.setManualDeduct(req.getManualDiscountAmount() != null ? req.getManualDiscountAmount() : BigDecimal.ZERO);

        List<Long> goodsIds = req.getItems().stream().map(SettleTrialReqDTO.TrialItem::getGoodsId).collect(Collectors.toList());
        ctx.goodsMap = gmsGoodsService.listByIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));

        if (req.getMember() != null) {
            List<UmsMemberBrandLevel> levels = brandLevelMapper.selectList(new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, req.getMember()));
            levels.forEach(l -> {
                if (l.getBrand() != null) ctx.memberBrandLevels.put(l.getBrand().trim(), l.getLevelCode());
            });
            ctx.skuPriceMap = skuLevelPriceMapper.selectList(new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, goodsIds))
                    .stream().collect(Collectors.groupingBy(PosSkuLevelPrice::getSkuId));
        }
        return ctx;
    }

    private void calculateBasePrices(CalcContext ctx) {
        for (SettleTrialReqDTO.TrialItem reqItem : ctx.req.getItems()) {
            GmsGoods goods = ctx.goodsMap.get(reqItem.getGoodsId());
            if (goods == null) throw new BaseException("【试算拦截】发现不存在或已下架的商品ID: " + reqItem.getGoodsId());

            // 库存前置拦截
            long currentStock = goods.getStock() != null ? goods.getStock() : 0L;
            if (reqItem.getQuantity() > currentStock) {
                log.warn("【计价引擎拦截】商品 {} 缺货。请求: {}, 实际: {}", goods.getName(), reqItem.getQuantity(), currentStock);
                throw new BaseException(BizErrorStatus.STOCK_NOT_ENOUGH, "结算中断！商品【{0}】库存不足，当前仅剩 {1} 件。", goods.getName(), currentStock).withData(currentStock);
            }

            BigDecimal qty = new BigDecimal(reqItem.getQuantity());
            BigDecimal unitOriginalPrice = goods.getSalePrice() != null ? goods.getSalePrice() : BigDecimal.ZERO;
            BigDecimal unitRealPrice = unitOriginalPrice; // 默认零售价
            BigDecimal unitCost = goods.getAvgCostPrice() != null ? goods.getAvgCostPrice() : (goods.getPurchasePrice() != null ? goods.getPurchasePrice() : BigDecimal.ZERO);

            // 匹配会员价
            String brandKey = goods.getBrandId() != null ? String.valueOf(goods.getBrandId()) : "";
            String levelCode = ctx.memberBrandLevels.get(brandKey);

            if (levelCode != null && ctx.skuPriceMap.containsKey(goods.getId())) {
                for (PosSkuLevelPrice sp : ctx.skuPriceMap.get(goods.getId())) {
                    if (levelCode.equals(sp.getLevelId())) {
                        if (sp.getMemberPrice() == null || sp.getMemberPrice().compareTo(BigDecimal.ZERO) < 0) {
                            throw new BaseException("商品「" + goods.getName() + "」的会员价配置异常");
                        }
                        unitRealPrice = sp.getMemberPrice(); // 🌟 锁定会员价
                        break;
                    }
                }
            }

            // 行汇总
            BigDecimal subTotalRetail = unitOriginalPrice.multiply(qty);
            BigDecimal subTotalMember = unitRealPrice.multiply(qty);
            BigDecimal subTotalPrivilege = subTotalRetail.subtract(subTotalMember);
            BigDecimal itemTotalCost = unitCost.multiply(qty);

            // 封装明细
            PricingItemResult itemRes = new PricingItemResult();
            itemRes.setGoodsId(goods.getId());
            itemRes.setQuantity(reqItem.getQuantity());
            itemRes.setUnitOriginalPrice(unitOriginalPrice);
            itemRes.setUnitRealPrice(unitRealPrice);
            itemRes.setCostPrice(unitCost);
            itemRes.setSubTotalRetail(subTotalRetail);
            itemRes.setSubTotalMember(subTotalMember);
            itemRes.setSubTotalPrivilege(subTotalPrivilege);
            ctx.result.getItems().add(itemRes);

            // 累加总计
            ctx.result.setRetailAmount(ctx.result.getRetailAmount().add(subTotalRetail));
            ctx.result.setMemberAmount(ctx.result.getMemberAmount().add(subTotalMember));
            ctx.result.setPrivilegeAmount(ctx.result.getPrivilegeAmount().add(subTotalPrivilege));
            ctx.result.setCostAmount(ctx.result.getCostAmount().add(itemTotalCost));

            // 计算满减参与额 (基于会员价累加)
            if (goods.getIsDiscountParticipable() != null && goods.getIsDiscountParticipable() == 1) {
                ctx.result.setParticipatingAmount(ctx.result.getParticipatingAmount().add(subTotalMember));
            }
        }
    }

    private void dispatchSettlementTrack(CalcContext ctx) {
        // 🌟 核心：核销与免收的精准分流
        boolean isWaive = Boolean.TRUE.equals(ctx.req.getWaiveCoupon());

        if (isWaive) {
            ctx.result.setActualCouponDeduct(BigDecimal.ZERO); // 免收：真实核销为 0
            ctx.result.setWaivedCouponAmount(ctx.result.getPrivilegeAmount()); // 店铺全额承担
        } else {
            ctx.result.setActualCouponDeduct(ctx.result.getPrivilegeAmount()); // 正常：全额核销会员资产
            ctx.result.setWaivedCouponAmount(BigDecimal.ZERO);
        }
    }

    private void calculateMarketingDeduct(CalcContext ctx) {
        // 满减券计算
        if (ctx.req.getUsedCouponRuleId() != null && ctx.req.getUsedCouponCount() != null && ctx.req.getUsedCouponCount() > 0) {
            PosCouponRule rule = couponRuleMapper.selectById(ctx.req.getUsedCouponRuleId());
            if (rule == null) throw new BaseException("【试算拦截】选用的满减券规则不存在");

            BigDecimal requiredAmount = rule.getThresholdAmount().multiply(new BigDecimal(ctx.req.getUsedCouponCount()));
            if (ctx.result.getParticipatingAmount().compareTo(requiredAmount) < 0) {
                throw new BaseException(BizErrorStatus.COUPON_NOT_ENOUGH, "【风控】参与满减活动商品总额(会员价计)未达到 {0} 张券门槛！", ctx.req.getUsedCouponCount());
            }
            ctx.result.setVoucherDeduct(rule.getDiscountAmount().multiply(new BigDecimal(ctx.req.getUsedCouponCount())));
        }

        // 手工折扣风控校验
        BigDecimal maxAllowedManual = ctx.result.getMemberAmount().subtract(ctx.result.getVoucherDeduct());
        if (ctx.result.getManualDeduct().compareTo(maxAllowedManual) > 0) {
            throw new BaseException(BizErrorStatus.POS_MANUAL_DISCOUNT_EXCEED, "【风控拦截】手工优惠额超过了本单可优惠上限");
        }

        // 🌟 最终应收 = 会员价总计 - 满减 - 手工
        BigDecimal finalPay = ctx.result.getMemberAmount()
                .subtract(ctx.result.getVoucherDeduct())
                .subtract(ctx.result.getManualDeduct());

        ctx.result.setFinalPayAmount(finalPay.compareTo(BigDecimal.ZERO) > 0 ? finalPay : BigDecimal.ZERO);
    }

    private PricingResult formatScale(PricingResult res) {
        res.setRetailAmount(res.getRetailAmount().setScale(2, RoundingMode.HALF_UP));
        res.setMemberAmount(res.getMemberAmount().setScale(2, RoundingMode.HALF_UP));
        res.setPrivilegeAmount(res.getPrivilegeAmount().setScale(2, RoundingMode.HALF_UP));
        res.setActualCouponDeduct(res.getActualCouponDeduct().setScale(2, RoundingMode.HALF_UP));
        res.setWaivedCouponAmount(res.getWaivedCouponAmount().setScale(2, RoundingMode.HALF_UP));
        res.setVoucherDeduct(res.getVoucherDeduct().setScale(2, RoundingMode.HALF_UP));
        res.setManualDeduct(res.getManualDeduct().setScale(2, RoundingMode.HALF_UP));
        res.setFinalPayAmount(res.getFinalPayAmount().setScale(2, RoundingMode.HALF_UP));
        return res;
    }
}