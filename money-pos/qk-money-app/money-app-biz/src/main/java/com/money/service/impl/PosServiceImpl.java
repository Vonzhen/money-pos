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
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {

    // --- 消除魔法字符串的业务常量 ---
    private static final String LOG_TYPE_COUPON = "COUPON";
    private static final String LOG_TYPE_VOUCHER = "VOUCHER";
    private static final String LOG_TYPE_BALANCE = "BALANCE";
    private static final String OPERATE_CONSUME = "CONSUME";
    private static final String COUPON_UNUSED = "UNUSED";
    private static final String COUPON_USED = "USED";
    private static final String STOCK_TYPE_SALE = "SALE";

    private final UmsMemberService umsMemberService;
    private final GmsGoodsService gmsGoodsService;
    private final OmsOrderService omsOrderService;
    private final OmsOrderDetailService omsOrderDetailService;
    private final OmsOrderLogService omsOrderLogService;

    private final GmsGoodsMapper gmsGoodsMapper; // 🌟 引入刚写的原子防超卖 Mapper
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
    // 基础查询模块 (保持不变)
    // ==========================================
    @Override
    public List<PosGoodsVO> listGoods(String barcode) {
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
                    new LambdaQueryWrapper<PosMemberCoupon>().in(PosMemberCoupon::getMemberId, memberIds).eq(PosMemberCoupon::getStatus, COUPON_UNUSED)
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
    public List<PosCouponRule> getValidCouponRules() {
        return posCouponRuleMapper.selectList(new LambdaQueryWrapper<PosCouponRule>().orderByDesc(PosCouponRule::getId));
    }

    // ==========================================
    // 🌟 核心重构：编排器模式 (Orchestrator Pattern)
    // ==========================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OmsOrderVO settleAccounts(SettleAccountsDTO dto) {
        // 1. 初始化结算上下文
        SettleContext ctx = buildContext(dto);

        // 2. 引擎 1：执行计价引擎 (计算明细、应收、成本、参与满减金额)
        processPricing(ctx, dto);

        // 3. 引擎 2：执行营销引擎 (核销会员券、核销满减活动)
        processMarketing(ctx, dto);

        // 4. 引擎 3：执行持久化与账务引擎 (落库订单、处理支付渠道)
        processPersistenceAndPayment(ctx, dto);

        // 5. 引擎 4：执行库存风控引擎 (递归扣减、生成防篡改流、防超卖)
        processInventory(ctx);

        // 6. 引擎 5：收尾更新 (扣减余额、记录日志)
        postProcessMember(ctx);

        return BeanMapUtil.to(ctx.order, OmsOrderVO::new);
    }

    // ==========================================
    // 🌟 内部上下文对象，穿梭于各个引擎之间
    // ==========================================
    @Data
    private static class SettleContext {
        String orderNo;
        LocalDateTime now = LocalDateTime.now();
        UmsMember member;
        boolean isVip;

        // 财务核算指标
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal couponAmount = BigDecimal.ZERO;
        BigDecimal costAmount = BigDecimal.ZERO;
        BigDecimal participatingAmount = BigDecimal.ZERO; // 参与满减的有效金额
        BigDecimal voucherAmount = BigDecimal.ZERO;
        BigDecimal manualDiscount = BigDecimal.ZERO;
        BigDecimal actualBalanceCost = BigDecimal.ZERO; // 实际使用的余额

        OmsOrder order = new OmsOrder();
        List<OmsOrderDetail> orderDetails = new ArrayList<>();

        // 高速缓存
        Map<Long, GmsGoods> goodsMap;
        Map<String, Boolean> brandCouponStrategyMap;
        Map<String, String> memberBrandLevels;
        Map<Long, Map<String, PosSkuLevelPrice>> goodsLevelPriceMap;
    }

    // --- 引擎 0: 组装上下文 ---
    private SettleContext buildContext(SettleAccountsDTO dto) {
        SettleContext ctx = new SettleContext();
        ctx.setOrderNo(getOrderNo());
        ctx.order.setOrderNo(ctx.getOrderNo());
        ctx.setVip(dto.getMember() != null);

        if (ctx.isVip()) {
            ctx.setMember(umsMemberService.getById(dto.getMember()));
            if (ctx.getMember() == null) throw new BaseException("未找到该会员");
            ctx.order.setMember(ctx.getMember().getName());
            ctx.order.setMemberId(ctx.getMember().getId());
            ctx.order.setVip(true);
            ctx.order.setContact(ctx.getMember().getPhone());
            ctx.order.setProvince(ctx.getMember().getProvince());
            ctx.order.setCity(ctx.getMember().getCity());
            ctx.order.setDistrict(ctx.getMember().getDistrict());
            ctx.order.setAddress(ctx.getMember().getAddress());
        } else {
            ctx.order.setVip(false);
        }

        // 缓存商品表
        List<Long> goodsIds = dto.getOrderDetail().stream().map(OmsOrderDetailDTO::getGoodsId).collect(Collectors.toList());
        ctx.setGoodsMap(gmsGoodsService.listByIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g)));

        // 缓存品牌营销策略
        Set<String> brandIdSet = ctx.getGoodsMap().values().stream().map(g -> g.getBrandId() != null ? String.valueOf(g.getBrandId()) : "0").collect(Collectors.toSet());
        ctx.setBrandCouponStrategyMap(new HashMap<>());
        if (!brandIdSet.isEmpty()) {
            sysBrandConfigMapper.selectList(new LambdaQueryWrapper<SysBrandConfig>().in(SysBrandConfig::getBrand, brandIdSet))
                    .forEach(config -> ctx.getBrandCouponStrategyMap().put(config.getBrand(), config.getCouponEnabled()));
        }

        // 缓存会员的多轨品牌等级
        ctx.setMemberBrandLevels(new HashMap<>());
        if (ctx.isVip() && !brandIdSet.isEmpty()) {
            umsMemberBrandLevelMapper.selectList(new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, ctx.getMember().getId()).in(UmsMemberBrandLevel::getBrand, brandIdSet))
                    .forEach(bl -> ctx.getMemberBrandLevels().put(bl.getBrand(), bl.getLevelCode()));
        }

        // 缓存 SKU 各个等级的价格矩阵
        ctx.setGoodsLevelPriceMap(new HashMap<>());
        if (!goodsIds.isEmpty()) {
            posSkuLevelPriceMapper.selectList(new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, goodsIds))
                    .forEach(p -> ctx.getGoodsLevelPriceMap().computeIfAbsent(p.getSkuId(), k -> new HashMap<>()).put(p.getLevelId(), p));
        }

        return ctx;
    }

    // --- 引擎 1: 计价 ---
    private void processPricing(SettleContext ctx, SettleAccountsDTO dto) {
        for (OmsOrderDetailDTO detailDTO : dto.getOrderDetail()) {
            GmsGoods goods = ctx.getGoodsMap().get(detailDTO.getGoodsId());
            if (goods == null) throw new BaseException("购物车中存在无效商品，请重新扫码");

            String brandIdStr = goods.getBrandId() != null ? String.valueOf(goods.getBrandId()) : "0";
            Boolean isCouponEnabled = ctx.getBrandCouponStrategyMap().getOrDefault(brandIdStr, true);
            String levelCode = ctx.isVip() ? ctx.getMemberBrandLevels().get(brandIdStr) : null;

            BigDecimal unitBasePrice = goods.getSalePrice();
            BigDecimal unitCoupon = BigDecimal.ZERO;

            if (StrUtil.isNotBlank(levelCode) && ctx.getGoodsLevelPriceMap().containsKey(goods.getId())) {
                PosSkuLevelPrice levelPrice = ctx.getGoodsLevelPriceMap().get(goods.getId()).get(levelCode);
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

            if (Boolean.TRUE.equals(dto.getWaiveCoupon())) {
                unitCoupon = BigDecimal.ZERO;
            }

            OmsOrderDetail detail = new OmsOrderDetail();
            detail.setOrderNo(ctx.getOrderNo());
            detail.setStatus(OrderStatusEnum.PAID.name());
            detail.setGoodsId(goods.getId());
            detail.setGoodsBarcode(goods.getBarcode());
            detail.setGoodsName(goods.getName());
            detail.setSalePrice(unitBasePrice);
            detail.setPurchasePrice(goods.getPurchasePrice() == null ? BigDecimal.ZERO : goods.getPurchasePrice());
            detail.setVipPrice(goods.getVipPrice());
            detail.setQuantity(detailDTO.getQuantity());

            BigDecimal finalGoodsPrice = unitBasePrice.subtract(unitCoupon);
            detail.setGoodsPrice(finalGoodsPrice);
            detail.setCoupon(unitCoupon);
            ctx.getOrderDetails().add(detail);

            BigDecimal qty = new BigDecimal(detail.getQuantity());
            ctx.setTotalAmount(ctx.getTotalAmount().add(unitBasePrice.multiply(qty)));
            ctx.setCouponAmount(ctx.getCouponAmount().add(unitCoupon.multiply(qty)));
            ctx.setCostAmount(ctx.getCostAmount().add(detail.getPurchasePrice().multiply(qty)));

            if (goods.getIsDiscountParticipable() != null && goods.getIsDiscountParticipable() == 1) {
                ctx.setParticipatingAmount(ctx.getParticipatingAmount().add(finalGoodsPrice.multiply(qty)));
            }
        }
        ctx.order.setTotalAmount(ctx.getTotalAmount());
        ctx.order.setCouponAmount(ctx.getCouponAmount());
        ctx.order.setCostAmount(ctx.getCostAmount());
    }

    // --- 引擎 2: 营销与核销 ---
    private void processMarketing(SettleContext ctx, SettleAccountsDTO dto) {
        if (!ctx.isVip()) return;

        UmsMember member = ctx.getMember();

        // 1. 核销会员券
        if (ctx.getCouponAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal consumeCoupon = member.getCoupon().subtract(ctx.getCouponAmount());
            if (consumeCoupon.compareTo(BigDecimal.ZERO) < 0) throw new BaseException("顾客账户内【会员券】余额不足！需扣: " + ctx.getCouponAmount());
            member.setCoupon(consumeCoupon);

            UmsMemberLog couponLog = new UmsMemberLog();
            couponLog.setMemberId(member.getId());
            couponLog.setType(LOG_TYPE_COUPON);
            couponLog.setOperateType(OPERATE_CONSUME);
            couponLog.setAmount(ctx.getCouponAmount().negate());
            couponLog.setAfterAmount(member.getCoupon());
            couponLog.setOrderNo(ctx.getOrderNo());
            couponLog.setRemark("订单自动抵扣关联品牌会员券");
            couponLog.setCreateTime(ctx.getNow());
            umsMemberLogMapper.insert(couponLog);
        }

        // 2. 核销满减券
        if (dto.getUsedCouponRuleId() != null && dto.getUsedCouponCount() != null && dto.getUsedCouponCount() > 0) {
            Long ruleId = dto.getUsedCouponRuleId();
            Integer usedCount = dto.getUsedCouponCount();
            PosCouponRule rule = posCouponRuleMapper.selectById(ruleId);
            if (rule == null) throw new BaseException("满减券异常或已停用");

            BigDecimal requiredThreshold = rule.getThresholdAmount().multiply(new BigDecimal(usedCount));
            if (ctx.getParticipatingAmount().compareTo(requiredThreshold) < 0) {
                throw new BaseException("购物车中【参与活动】的商品总额未达到用券门槛！");
            }

            List<PosMemberCoupon> availableCoupons = posMemberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>().eq(PosMemberCoupon::getMemberId, member.getId()).eq(PosMemberCoupon::getRuleId, ruleId).eq(PosMemberCoupon::getStatus, COUPON_UNUSED).last("LIMIT " + usedCount));
            if (availableCoupons.size() < usedCount) throw new BaseException("卡包内满减券可用张数不足！");

            List<Long> couponIds = availableCoupons.stream().map(PosMemberCoupon::getId).collect(Collectors.toList());
            PosMemberCoupon updateCoupon = new PosMemberCoupon();
            updateCoupon.setStatus(COUPON_USED);
            updateCoupon.setOrderNo(ctx.getOrderNo());
            updateCoupon.setUseTime(ctx.getNow());
            posMemberCouponMapper.update(updateCoupon, new LambdaUpdateWrapper<PosMemberCoupon>().in(PosMemberCoupon::getId, couponIds));

            ctx.setVoucherAmount(rule.getDiscountAmount().multiply(new BigDecimal(usedCount)));

            long remainVouchers = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>().eq(PosMemberCoupon::getMemberId, member.getId()).eq(PosMemberCoupon::getStatus, COUPON_UNUSED));
            UmsMemberLog voucherLog = new UmsMemberLog();
            voucherLog.setMemberId(member.getId());
            voucherLog.setType(LOG_TYPE_VOUCHER);
            voucherLog.setOperateType(OPERATE_CONSUME);
            voucherLog.setAmount(BigDecimal.valueOf(-usedCount));
            voucherLog.setAfterAmount(BigDecimal.valueOf(remainVouchers));
            voucherLog.setOrderNo(ctx.getOrderNo());
            voucherLog.setRemark("核销满减优惠券: " + rule.getName()); // 更清晰的溯源
            voucherLog.setCreateTime(ctx.getNow());
            umsMemberLogMapper.insert(voucherLog);
        }
        ctx.order.setUseVoucherAmount(ctx.getVoucherAmount());
    }

    // --- 引擎 3: 持久化与账务 ---
    private void processPersistenceAndPayment(SettleContext ctx, SettleAccountsDTO dto) {
        ctx.setManualDiscount(dto.getManualDiscountAmount() != null ? dto.getManualDiscountAmount() : BigDecimal.ZERO);
        ctx.order.setManualDiscountAmount(ctx.getManualDiscount());

        BigDecimal finalPay = ctx.getTotalAmount().subtract(ctx.getCouponAmount()).subtract(ctx.getVoucherAmount()).subtract(ctx.getManualDiscount());
        if (finalPay.compareTo(BigDecimal.ZERO) < 0) finalPay = BigDecimal.ZERO;

        ctx.order.setPayAmount(finalPay);
        ctx.order.setFinalSalesAmount(finalPay);
        ctx.order.setStatus(OrderStatusEnum.PAID.name());
        ctx.order.setPaymentTime(ctx.getNow());
        omsOrderService.save(ctx.order); // 落库主表

        if (dto.getPayments() != null) {
            for (SettleAccountsDTO.PaymentItem item : dto.getPayments()) {
                OmsOrderPay payRecord = new OmsOrderPay();
                payRecord.setOrderNo(ctx.getOrderNo());
                payRecord.setPayMethodCode(item.getPayMethodCode());
                payRecord.setPayMethodName(item.getPayMethodName());
                payRecord.setPayAmount(item.getPayAmount());
                payRecord.setCreateTime(ctx.getNow());
                omsOrderPayMapper.insert(payRecord);

                if (LOG_TYPE_BALANCE.equals(item.getPayMethodCode())) {
                    ctx.setActualBalanceCost(ctx.getActualBalanceCost().add(item.getPayAmount()));
                }
            }
        }
        omsOrderDetailService.saveBatch(ctx.getOrderDetails()); // 落库明细
    }

    // --- 引擎 4: 库存风控 (🌟 彻底解决高并发超卖漏洞) ---
    private void processInventory(SettleContext ctx) {
        List<Long> comboGoodsIds = ctx.getGoodsMap().values().stream().filter(g -> g.getIsCombo() != null && g.getIsCombo() == 1).map(GmsGoods::getId).collect(Collectors.toList());
        Map<Long, List<GmsGoodsCombo>> comboMap = new HashMap<>();
        if (!comboGoodsIds.isEmpty()) {
            List<GmsGoodsCombo> allCombos = gmsGoodsComboMapper.selectList(new LambdaQueryWrapper<GmsGoodsCombo>().in(GmsGoodsCombo::getComboGoodsId, comboGoodsIds));
            comboMap = allCombos.stream().collect(Collectors.groupingBy(GmsGoodsCombo::getComboGoodsId));
        }

        for (OmsOrderDetail detail : ctx.getOrderDetails()) {
            GmsGoods goods = ctx.getGoodsMap().get(detail.getGoodsId());

            if (goods.getIsCombo() != null && goods.getIsCombo() == 1) {
                List<GmsGoodsCombo> combos = comboMap.get(goods.getId());
                if (combos != null && !combos.isEmpty()) {
                    for (GmsGoodsCombo combo : combos) {
                        GmsGoods subGoods = gmsGoodsService.getById(combo.getSubGoodsId());
                        if (subGoods != null) {
                            int deductQty = detail.getQuantity() * combo.getSubGoodsQty();
                            // 🌟 防超卖：原子扣减
                            int rows = gmsGoodsMapper.deductStockAtomically(subGoods.getId(), deductQty);
                            if (rows == 0) throw new BaseException("库存不足或发生并发抢单: " + subGoods.getName());

                            writeStockLog(subGoods, -deductQty, ctx, "前台套餐售出联动扣除");
                        }
                    }
                }
            } else {
                // 🌟 防超卖：原子扣减
                int rows = gmsGoodsMapper.deductStockAtomically(goods.getId(), detail.getQuantity());
                if (rows == 0) throw new BaseException("库存不足或发生并发抢单: " + goods.getName());

                writeStockLog(goods, -detail.getQuantity(), ctx, "前台智能收银售出");
            }
        }
    }

    private void writeStockLog(GmsGoods goods, int changeQty, SettleContext ctx, String remark) {
        // 由于是原子扣减，我们需要重新查一下最新库存数量用于日志展示
        GmsGoods freshGoods = gmsGoodsService.getById(goods.getId());
        com.money.entity.GmsStockLog stockLog = new com.money.entity.GmsStockLog();
        stockLog.setGoodsId(freshGoods.getId());
        stockLog.setGoodsName(freshGoods.getName());
        stockLog.setGoodsBarcode(freshGoods.getBarcode());
        stockLog.setType(STOCK_TYPE_SALE);
        stockLog.setQuantity(changeQty);

        // 🌟 修复点：增加判空与 .intValue() 强转
        stockLog.setAfterQuantity(freshGoods.getStock() == null ? 0 : freshGoods.getStock().intValue());

        stockLog.setOrderNo(ctx.getOrderNo());
        stockLog.setRemark(remark);
        stockLog.setCreateTime(ctx.getNow());
        gmsStockLogMapper.insert(stockLog);
    }

    // --- 引擎 5: 结尾与溯源日志 ---
    private void postProcessMember(SettleContext ctx) {
        if (ctx.isVip()) {
            UmsMember member = ctx.getMember();
            umsMemberService.consume(member.getId(), ctx.order.getPayAmount(), ctx.order.getCouponAmount());

            if (ctx.getActualBalanceCost().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal newBalance = member.getBalance().subtract(ctx.getActualBalanceCost());
                if (newBalance.compareTo(BigDecimal.ZERO) < 0) newBalance = BigDecimal.ZERO;
                member.setBalance(newBalance);

                UmsMemberLog balanceLog = new UmsMemberLog();
                balanceLog.setMemberId(member.getId());
                balanceLog.setType(LOG_TYPE_BALANCE);
                balanceLog.setOperateType(OPERATE_CONSUME);
                balanceLog.setAmount(ctx.getActualBalanceCost().negate());
                balanceLog.setAfterAmount(member.getBalance());
                balanceLog.setOrderNo(ctx.getOrderNo());
                balanceLog.setRemark("订单支付扣除会员余额");
                balanceLog.setCreateTime(ctx.getNow());
                umsMemberLogMapper.insert(balanceLog);
            }
            member.setLastVisitTime(ctx.getNow());
            umsMemberService.updateById(member);
        }

        OmsOrderLog log = new OmsOrderLog();
        log.setOrderId(ctx.order.getId());
        log.setDescription("执行强一致性 SettleEngine 结算 (防超卖重构版)");
        omsOrderLogService.saveBatch(ListUtil.of(log));
    }

    private synchronized String getOrderNo() {
        String date = LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER);
        String random = RandomUtil.randomNumbers(1);
        return date + random;
    }
}