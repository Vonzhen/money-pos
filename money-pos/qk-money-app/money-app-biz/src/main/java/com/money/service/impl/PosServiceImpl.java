package com.money.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.web.exception.BaseException;
import com.money.web.util.BeanMapUtil;
import com.money.constant.OrderStatusEnum;
import com.money.dto.OmsOrder.OmsOrderVO;
import com.money.dto.OmsOrderDetail.OmsOrderDetailDTO;
import com.money.dto.Pos.PosGoodsVO;
import com.money.dto.Pos.PosMemberVO;
import com.money.dto.Pos.SettleAccountsDTO;
import com.money.entity.*;
import com.money.mapper.*;
import com.money.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {

    private final UmsMemberService umsMemberService;
    private final GmsGoodsService gmsGoodsService;
    private final OmsOrderService omsOrderService;
    private final OmsOrderDetailService omsOrderDetailService;
    private final OmsOrderLogService omsOrderLogService;

    private final PosSkuLevelPriceMapper posSkuLevelPriceMapper;
    private final PosCouponRuleMapper posCouponRuleMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final OmsOrderPayMapper omsOrderPayMapper;
    private final UmsMemberLogMapper umsMemberLogMapper;
    private final com.money.mapper.GmsStockLogMapper gmsStockLogMapper;

    private final SysBrandConfigMapper sysBrandConfigMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;
    private final GmsGoodsComboMapper gmsGoodsComboMapper;

    // ==========================================
    // 🌟 核心重构：实现条码、名称、拼音三合一搜索
    // ==========================================
    @Override
    public List<PosGoodsVO> listGoods(String barcode) {
        // 使用 LambdaQuery 的 or 嵌套逻辑
        List<GmsGoods> gmsGoodsList = gmsGoodsService.lambdaQuery()
                .and(StrUtil.isNotBlank(barcode), w ->
                        w.like(GmsGoods::getBarcode, barcode)
                                .or().like(GmsGoods::getName, barcode)
                                .or().like(GmsGoods::getMnemonicCode, barcode.toUpperCase())
                ).list();

        List<PosGoodsVO> posGoodsVOS = BeanMapUtil.to(gmsGoodsList, PosGoodsVO::new);

        if (!posGoodsVOS.isEmpty()) {
            List<Long> skuIds = posGoodsVOS.stream().map(PosGoodsVO::getId).collect(Collectors.toList());
            List<PosSkuLevelPrice> levelPrices = posSkuLevelPriceMapper.selectList(new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, skuIds));
            Map<Long, List<PosSkuLevelPrice>> skuPriceMap = levelPrices.stream().collect(Collectors.groupingBy(PosSkuLevelPrice::getSkuId));

            for (PosGoodsVO vo : posGoodsVOS) {
                Map<String, BigDecimal> priceMap = new HashMap<>();
                Map<String, BigDecimal> couponMap = new HashMap<>();
                List<PosSkuLevelPrice> prices = skuPriceMap.get(vo.getId());
                if (prices != null) {
                    for (PosSkuLevelPrice p : prices) {
                        priceMap.put(p.getLevelId(), p.getMemberPrice());
                        couponMap.put(p.getLevelId(), p.getMemberCoupon() != null ? p.getMemberCoupon() : BigDecimal.ZERO);
                    }
                }
                vo.setLevelPrices(priceMap);
                vo.setLevelCoupons(couponMap);
            }
        }
        return posGoodsVOS;
    }

    @Override
    public List<PosMemberVO> listMember(String member) {
        List<UmsMember> memberList = umsMemberService.lambdaQuery().eq(UmsMember::getDeleted, false)
                .like(StrUtil.isNotBlank(member), UmsMember::getName, member).or().like(StrUtil.isNotBlank(member), UmsMember::getPhone, member).list();
        List<PosMemberVO> posMemberVOS = BeanMapUtil.to(memberList, PosMemberVO::new);

        if (!posMemberVOS.isEmpty()) {
            List<Long> memberIds = posMemberVOS.stream().map(PosMemberVO::getId).collect(Collectors.toList());

            List<UmsMemberBrandLevel> allBrandLevels = umsMemberBrandLevelMapper.selectList(
                    new LambdaQueryWrapper<UmsMemberBrandLevel>().in(UmsMemberBrandLevel::getMemberId, memberIds)
            );
            Map<Long, List<UmsMemberBrandLevel>> blMap = allBrandLevels.stream().collect(Collectors.groupingBy(UmsMemberBrandLevel::getMemberId));

            List<PosMemberCoupon> allUnusedCoupons = posMemberCouponMapper.selectList(
                    new LambdaQueryWrapper<PosMemberCoupon>()
                            .in(PosMemberCoupon::getMemberId, memberIds)
                            .eq(PosMemberCoupon::getStatus, "UNUSED")
            );

            final Map<Long, PosCouponRule> ruleMap = new HashMap<>();
            if (!allUnusedCoupons.isEmpty()) {
                List<Long> ruleIds = allUnusedCoupons.stream().map(PosMemberCoupon::getRuleId).distinct().collect(Collectors.toList());
                ruleMap.putAll(posCouponRuleMapper.selectBatchIds(ruleIds).stream().collect(Collectors.toMap(PosCouponRule::getId, rule -> rule)));
            }

            for (PosMemberVO vo : posMemberVOS) {
                List<UmsMemberBrandLevel> levels = blMap.get(vo.getId());
                Map<String, String> levelMap = new HashMap<>();
                if (levels != null) {
                    for (UmsMemberBrandLevel bl : levels) {
                        levelMap.put(bl.getBrand(), bl.getLevelCode());
                    }
                }
                vo.setBrandLevels(levelMap);

                List<PosMemberCoupon> hisCoupons = allUnusedCoupons.stream().filter(c -> c.getMemberId().equals(vo.getId())).collect(Collectors.toList());
                vo.setVoucherCount(hisCoupons.size());

                if (!hisCoupons.isEmpty()) {
                    Map<Long, Long> ruleCountMap = hisCoupons.stream().collect(Collectors.groupingBy(PosMemberCoupon::getRuleId, Collectors.counting()));
                    List<PosMemberVO.MemberCouponRuleVO> ruleVOList = ruleCountMap.entrySet().stream().filter(entry -> ruleMap.containsKey(entry.getKey())).map(entry -> {
                        PosCouponRule rule = ruleMap.get(entry.getKey());
                        PosMemberVO.MemberCouponRuleVO ruleVO = new PosMemberVO.MemberCouponRuleVO();
                        ruleVO.setRuleId(rule.getId());
                        ruleVO.setName("满" + rule.getThresholdAmount().stripTrailingZeros().toPlainString() + "减" + rule.getDiscountAmount().stripTrailingZeros().toPlainString());
                        ruleVO.setThreshold(rule.getThresholdAmount());
                        ruleVO.setDeduction(rule.getDiscountAmount());
                        ruleVO.setAvailableCount(entry.getValue().intValue());
                        return ruleVO;
                    }).collect(Collectors.toList());
                    vo.setCouponList(ruleVOList);
                }
            }
        }
        return posMemberVOS;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OmsOrderVO settleAccounts(SettleAccountsDTO settleAccountsDTO) {
        String orderNo = getOrderNo();
        OmsOrder order = new OmsOrder();
        order.setOrderNo(orderNo);
        LocalDateTime now = LocalDateTime.now();
        Long memberId = settleAccountsDTO.getMember();
        boolean isVip = memberId != null;

        List<Long> goodsIds = settleAccountsDTO.getOrderDetail().stream().map(OmsOrderDetailDTO::getGoodsId).collect(Collectors.toList());
        Map<Long, GmsGoods> goodsMap = gmsGoodsService.listByIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));

        Set<String> brandIdSet = goodsMap.values().stream()
                .map(g -> g.getBrandId() != null ? String.valueOf(g.getBrandId()) : "0")
                .collect(Collectors.toSet());

        Map<String, Boolean> brandCouponStrategyMap = new HashMap<>();
        if (!brandIdSet.isEmpty()) {
            List<SysBrandConfig> configs = sysBrandConfigMapper.selectList(new LambdaQueryWrapper<SysBrandConfig>().in(SysBrandConfig::getBrand, brandIdSet));
            for (SysBrandConfig config : configs) {
                brandCouponStrategyMap.put(config.getBrand(), config.getCouponEnabled());
            }
        }

        Map<String, String> memberBrandLevels = new HashMap<>();
        if (isVip && !brandIdSet.isEmpty()) {
            List<UmsMemberBrandLevel> brandLevels = umsMemberBrandLevelMapper.selectList(
                    new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, memberId).in(UmsMemberBrandLevel::getBrand, brandIdSet)
            );
            for (UmsMemberBrandLevel bl : brandLevels) {
                memberBrandLevels.put(bl.getBrand(), bl.getLevelCode());
            }
        }

        Map<Long, Map<String, PosSkuLevelPrice>> goodsLevelPriceMap = new HashMap<>();
        if (!goodsIds.isEmpty()) {
            List<PosSkuLevelPrice> prices = posSkuLevelPriceMapper.selectList(new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, goodsIds));
            for (PosSkuLevelPrice p : prices) {
                goodsLevelPriceMap.computeIfAbsent(p.getSkuId(), k -> new HashMap<>()).put(p.getLevelId(), p);
            }
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal couponAmount = BigDecimal.ZERO;
        BigDecimal costAmount = BigDecimal.ZERO;
        BigDecimal participatingAmount = BigDecimal.ZERO;
        List<OmsOrderDetail> orderDetails = new ArrayList<>();

        for (OmsOrderDetailDTO dto : settleAccountsDTO.getOrderDetail()) {
            GmsGoods goods = goodsMap.get(dto.getGoodsId());
            if (goods == null) throw new BaseException("购物车中存在无效商品，请重新扫码");

            String brandIdStr = goods.getBrandId() != null ? String.valueOf(goods.getBrandId()) : "0";
            Boolean isCouponEnabled = brandCouponStrategyMap.getOrDefault(brandIdStr, true);
            String levelCode = isVip ? memberBrandLevels.get(brandIdStr) : null;

            BigDecimal unitBasePrice = goods.getSalePrice();
            BigDecimal unitCoupon = BigDecimal.ZERO;

            if (StrUtil.isNotBlank(levelCode) && goodsLevelPriceMap.containsKey(goods.getId())) {
                PosSkuLevelPrice levelPrice = goodsLevelPriceMap.get(goods.getId()).get(levelCode);
                if (levelPrice != null) {
                    if (isCouponEnabled) {
                        unitBasePrice = goods.getSalePrice();
                        unitCoupon = levelPrice.getMemberCoupon() != null ? levelPrice.getMemberCoupon() : BigDecimal.ZERO;
                    } else {
                        unitBasePrice = levelPrice.getMemberPrice() != null ? levelPrice.getMemberPrice() : goods.getSalePrice();
                        unitCoupon = BigDecimal.ZERO;
                    }
                }
            }

            if (Boolean.TRUE.equals(settleAccountsDTO.getWaiveCoupon())) {
                unitCoupon = BigDecimal.ZERO;
            }

            OmsOrderDetail detail = new OmsOrderDetail();
            detail.setOrderNo(orderNo);
            detail.setStatus(OrderStatusEnum.PAID.name());
            detail.setGoodsId(goods.getId());
            detail.setGoodsBarcode(goods.getBarcode());
            detail.setGoodsName(goods.getName());

            detail.setSalePrice(unitBasePrice);
            detail.setPurchasePrice(goods.getPurchasePrice() == null ? BigDecimal.ZERO : goods.getPurchasePrice());
            detail.setVipPrice(goods.getVipPrice());
            detail.setQuantity(dto.getQuantity());

            BigDecimal finalGoodsPrice = unitBasePrice.subtract(unitCoupon);
            detail.setGoodsPrice(finalGoodsPrice);
            detail.setCoupon(unitCoupon);
            orderDetails.add(detail);

            BigDecimal qty = new BigDecimal(detail.getQuantity());
            totalAmount = totalAmount.add(unitBasePrice.multiply(qty));
            couponAmount = couponAmount.add(unitCoupon.multiply(qty));
            costAmount = costAmount.add(detail.getPurchasePrice().multiply(qty));

            if (goods.getIsDiscountParticipable() != null && goods.getIsDiscountParticipable() == 1) {
                participatingAmount = participatingAmount.add(finalGoodsPrice.multiply(qty));
            }
        }

        order.setTotalAmount(totalAmount);
        order.setCouponAmount(couponAmount);
        order.setCostAmount(costAmount);
        order.setVip(false);
        BigDecimal voucherAmount = BigDecimal.ZERO;
        UmsMember member = null;

        if (isVip) {
            member = umsMemberService.getById(memberId);
            if (member == null) throw new BaseException("未找到该会员");
            order.setMember(member.getName());
            order.setMemberId(memberId);
            order.setVip(true);
            order.setContact(member.getPhone());
            order.setProvince(member.getProvince());
            order.setCity(member.getCity());
            order.setDistrict(member.getDistrict());
            order.setAddress(member.getAddress());

            if (couponAmount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal consumeCoupon = member.getCoupon().subtract(couponAmount);
                if (consumeCoupon.compareTo(BigDecimal.ZERO) < 0) throw new BaseException("顾客账户内【会员券】余额不足！需扣: " + couponAmount);
                member.setCoupon(consumeCoupon);

                UmsMemberLog couponLog = new UmsMemberLog();
                couponLog.setMemberId(member.getId());
                couponLog.setType("COUPON");
                couponLog.setOperateType("CONSUME");
                couponLog.setAmount(couponAmount.negate());
                couponLog.setAfterAmount(member.getCoupon());
                couponLog.setOrderNo(orderNo);
                couponLog.setRemark("订单自动抵扣关联品牌会员券");
                couponLog.setCreateTime(now);
                umsMemberLogMapper.insert(couponLog);
            }

            if (settleAccountsDTO.getUsedCouponRuleId() != null && settleAccountsDTO.getUsedCouponCount() != null && settleAccountsDTO.getUsedCouponCount() > 0) {
                Long ruleId = settleAccountsDTO.getUsedCouponRuleId();
                Integer usedCount = settleAccountsDTO.getUsedCouponCount();
                PosCouponRule rule = posCouponRuleMapper.selectById(ruleId);
                if (rule == null) throw new BaseException("满减券异常或已停用");

                BigDecimal requiredThreshold = rule.getThresholdAmount().multiply(new BigDecimal(usedCount));
                if (participatingAmount.compareTo(requiredThreshold) < 0) {
                    throw new BaseException("购物车中【参与活动】的商品总额未达到用券门槛！");
                }

                List<PosMemberCoupon> availableCoupons = posMemberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>().eq(PosMemberCoupon::getMemberId, memberId).eq(PosMemberCoupon::getRuleId, ruleId).eq(PosMemberCoupon::getStatus, "UNUSED").last("LIMIT " + usedCount));
                if (availableCoupons.size() < usedCount) throw new BaseException("卡包内满减券可用张数不足！");

                List<Long> couponIds = availableCoupons.stream().map(PosMemberCoupon::getId).collect(Collectors.toList());
                PosMemberCoupon updateCoupon = new PosMemberCoupon();
                updateCoupon.setStatus("USED");
                updateCoupon.setOrderNo(orderNo);
                updateCoupon.setUseTime(now);
                posMemberCouponMapper.update(updateCoupon, new LambdaUpdateWrapper<PosMemberCoupon>().in(PosMemberCoupon::getId, couponIds));

                voucherAmount = rule.getDiscountAmount().multiply(new BigDecimal(usedCount));

                long remainVouchers = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>().eq(PosMemberCoupon::getMemberId, member.getId()).eq(PosMemberCoupon::getStatus, "UNUSED"));
                UmsMemberLog voucherLog = new UmsMemberLog();
                voucherLog.setMemberId(member.getId());
                voucherLog.setType("VOUCHER");
                voucherLog.setOperateType("CONSUME");
                voucherLog.setAmount(BigDecimal.valueOf(-usedCount));
                voucherLog.setAfterAmount(BigDecimal.valueOf(remainVouchers));
                voucherLog.setOrderNo(orderNo);
                voucherLog.setRemark("核销满减优惠券");
                voucherLog.setCreateTime(now);
                umsMemberLogMapper.insert(voucherLog);
            }
        }
        order.setUseVoucherAmount(voucherAmount);

        BigDecimal manualDiscount = settleAccountsDTO.getManualDiscountAmount() != null ? settleAccountsDTO.getManualDiscountAmount() : BigDecimal.ZERO;
        order.setManualDiscountAmount(manualDiscount);

        BigDecimal finalPayAmount = totalAmount.subtract(couponAmount).subtract(voucherAmount).subtract(manualDiscount);
        if (finalPayAmount.compareTo(BigDecimal.ZERO) < 0) finalPayAmount = BigDecimal.ZERO;

        order.setPayAmount(finalPayAmount);
        order.setFinalSalesAmount(finalPayAmount);
        order.setStatus(OrderStatusEnum.PAID.name());
        order.setPaymentTime(now);
        omsOrderService.save(order);

        BigDecimal actualBalanceCost = BigDecimal.ZERO;
        if (settleAccountsDTO.getPayments() != null) {
            for (SettleAccountsDTO.PaymentItem item : settleAccountsDTO.getPayments()) {
                OmsOrderPay payRecord = new OmsOrderPay();
                payRecord.setOrderNo(orderNo);
                payRecord.setPayMethodCode(item.getPayMethodCode());
                payRecord.setPayMethodName(item.getPayMethodName());
                payRecord.setPayAmount(item.getPayAmount());
                payRecord.setCreateTime(now);
                omsOrderPayMapper.insert(payRecord);
                if ("BALANCE".equals(item.getPayMethodCode())) actualBalanceCost = actualBalanceCost.add(item.getPayAmount());
            }
        }
        omsOrderDetailService.saveBatch(orderDetails);

        List<Long> comboGoodsIds = goodsMap.values().stream().filter(g -> g.getIsCombo() != null && g.getIsCombo() == 1).map(GmsGoods::getId).collect(Collectors.toList());
        Map<Long, List<GmsGoodsCombo>> comboMap = new HashMap<>();
        if (!comboGoodsIds.isEmpty()) {
            List<GmsGoodsCombo> allCombos = gmsGoodsComboMapper.selectList(new LambdaQueryWrapper<GmsGoodsCombo>().in(GmsGoodsCombo::getComboGoodsId, comboGoodsIds));
            comboMap = allCombos.stream().collect(Collectors.groupingBy(GmsGoodsCombo::getComboGoodsId));
        }

        for (OmsOrderDetail detail : orderDetails) {
            GmsGoods goods = goodsMap.get(detail.getGoodsId());

            if (goods.getIsCombo() != null && goods.getIsCombo() == 1) {
                List<GmsGoodsCombo> combos = comboMap.get(goods.getId());
                if (combos != null && !combos.isEmpty()) {
                    for (GmsGoodsCombo combo : combos) {
                        GmsGoods subGoods = gmsGoodsService.getById(combo.getSubGoodsId());
                        if (subGoods != null) {
                            int deductQty = detail.getQuantity() * combo.getSubGoodsQty();
                            subGoods.setStock((subGoods.getStock() == null ? 0 : subGoods.getStock()) - deductQty);
                            gmsGoodsService.updateById(subGoods);

                            com.money.entity.GmsStockLog stockLog = new com.money.entity.GmsStockLog();
                            stockLog.setGoodsId(subGoods.getId());
                            stockLog.setGoodsName(subGoods.getName() + " (套餐:" + goods.getName() + " 子件)");
                            stockLog.setGoodsBarcode(subGoods.getBarcode());
                            stockLog.setType("SALE");
                            stockLog.setQuantity(-deductQty);
                            stockLog.setAfterQuantity(subGoods.getStock().intValue());
                            stockLog.setOrderNo(orderNo);
                            stockLog.setRemark("前台套餐售出联动扣除");
                            stockLog.setCreateTime(now);
                            gmsStockLogMapper.insert(stockLog);
                        }
                    }
                }
            } else {
                goods.setStock((goods.getStock() == null ? 0 : goods.getStock()) - detail.getQuantity());
                gmsGoodsService.updateById(goods);

                com.money.entity.GmsStockLog stockLog = new com.money.entity.GmsStockLog();
                stockLog.setGoodsId(goods.getId());
                stockLog.setGoodsName(goods.getName());
                stockLog.setGoodsBarcode(goods.getBarcode());
                stockLog.setType("SALE");
                stockLog.setQuantity(-detail.getQuantity());
                stockLog.setAfterQuantity(goods.getStock().intValue());
                stockLog.setOrderNo(orderNo);
                stockLog.setRemark("前台智能收银售出");
                stockLog.setCreateTime(now);
                gmsStockLogMapper.insert(stockLog);
            }
        }

        if (member != null) {
            umsMemberService.consume(member.getId(), order.getPayAmount(), order.getCouponAmount());
            if (actualBalanceCost.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal newBalance = member.getBalance().subtract(actualBalanceCost);
                if (newBalance.compareTo(BigDecimal.ZERO) < 0) newBalance = BigDecimal.ZERO;
                member.setBalance(newBalance);

                UmsMemberLog balanceLog = new UmsMemberLog();
                balanceLog.setMemberId(member.getId());
                balanceLog.setType("BALANCE");
                balanceLog.setOperateType("CONSUME");
                balanceLog.setAmount(actualBalanceCost.negate());
                balanceLog.setAfterAmount(member.getBalance());
                balanceLog.setOrderNo(orderNo);
                balanceLog.setRemark("订单支付扣除会员余额");
                balanceLog.setCreateTime(now);
                umsMemberLogMapper.insert(balanceLog);
            }
            member.setLastVisitTime(now);
            umsMemberService.updateById(member);
        }

        OmsOrderLog log = new OmsOrderLog();
        log.setOrderId(order.getId());
        log.setDescription("执行强一致性 SettleEngine 结算");
        omsOrderLogService.saveBatch(ListUtil.of(log));

        return BeanMapUtil.to(order, OmsOrderVO::new);
    }

    private synchronized String getOrderNo() {
        String date = LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER);
        String random = RandomUtil.randomNumbers(1);
        return date + random;
    }

    @Override
    public List<PosCouponRule> getValidCouponRules() {
        return posCouponRuleMapper.selectList(new LambdaQueryWrapper<PosCouponRule>().orderByDesc(PosCouponRule::getId));
    }
}