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

        // 🌟 核心修复新增：资金池 A (专门用于累加数据库里严格定义的“应扣会员券额”)
        BigDecimal totalConfiguredCoupon = BigDecimal.ZERO;
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

        // 2. 价格轨计算：确定零售价、会员价与【券核销定额】
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
        ctx.totalConfiguredCoupon = BigDecimal.ZERO; // 初始化券资金池

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
            BigDecimal unitCoupon = BigDecimal.ZERO;      // 🌟 核心修复：默认商品不扣券
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
                        unitCoupon = sp.getMemberCoupon() != null ? sp.getMemberCoupon() : BigDecimal.ZERO; // 🌟 锁定数据库明确配置的券额 (单轨品牌这里拿到的是 0)
                        break;
                    }
                }
            }

            // 行汇总
            BigDecimal subTotalRetail = unitOriginalPrice.multiply(qty);
            BigDecimal subTotalMember = unitRealPrice.multiply(qty);
            BigDecimal subTotalPrivilege = subTotalRetail.subtract(subTotalMember); // 物理差价
            BigDecimal itemTotalCost = unitCost.multiply(qty);

            // 🌟 核心修复：计算本行商品基于后台配置【理论上必须扣除的券额】
            BigDecimal subTotalCoupon = unitCoupon.multiply(qty);

            // 🌟 防御性编程：扣除的券额绝不可能大于商品的物理价差
            if (subTotalCoupon.compareTo(subTotalPrivilege) > 0) {
                subTotalCoupon = subTotalPrivilege;
            }

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
            ctx.result.setPrivilegeAmount(ctx.result.getPrivilegeAmount().add(subTotalPrivilege)); // 物理差价总计
            ctx.result.setCostAmount(ctx.result.getCostAmount().add(itemTotalCost));

            // 🌟 核心修复：把合规的券核销额放进“资金池 A”
            ctx.totalConfiguredCoupon = ctx.totalConfiguredCoupon.add(subTotalCoupon);

            // 计算满减参与额 (基于会员价累加)
            if (goods.getIsDiscountParticipable() != null && goods.getIsDiscountParticipable() == 1) {
                ctx.result.setParticipatingAmount(ctx.result.getParticipatingAmount().add(subTotalMember));
            }
        }
    }

    private void dispatchSettlementTrack(CalcContext ctx) {
        // 🌟 核心修复：核销与免收的【精准分流】
        boolean isWaive = Boolean.TRUE.equals(ctx.req.getWaiveCoupon());
        BigDecimal totalPrivilege = ctx.result.getPrivilegeAmount(); // 总价差
        BigDecimal configuredCoupon = ctx.totalConfiguredCoupon;     // 资金池A：数据库规定要扣的券额

        if (isWaive) {
            // 收银员开启【免收】：全额不扣券，所有差价全部由店铺让利承担
            ctx.result.setActualCouponDeduct(BigDecimal.ZERO);
            ctx.result.setWaivedCouponAmount(totalPrivilege);
        } else {
            // 【正常结算流】
            // 1. 实际扣券额：只扣数据库里规定的额度（不再是粗暴地扣减所有差价）
            ctx.result.setActualCouponDeduct(configuredCoupon);

            // 2. 店铺承担额（单轨让利）：总差价 - 扣了券的钱，剩下的全是店家给会员贴的钱
            // 如果是纯单轨订单，configuredCoupon 是 0，这笔账就会完美流入 WaivedCouponAmount，财务极其清晰！
            ctx.result.setWaivedCouponAmount(totalPrivilege.subtract(configuredCoupon));
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