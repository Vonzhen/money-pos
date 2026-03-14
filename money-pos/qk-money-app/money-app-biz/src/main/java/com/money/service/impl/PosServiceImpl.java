package com.money.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.web.exception.BaseException;
import com.money.web.util.BeanMapUtil;
import com.money.constant.OrderStatusEnum;
import com.money.dto.OmsOrder.OmsOrderVO;
import com.money.dto.Pos.PosGoodsVO;
import com.money.dto.Pos.PosMemberVO;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.dto.pos.SettleTrialReqDTO;
import com.money.dto.pos.SettleTrialResVO;
import com.money.entity.*;
import com.money.mapper.*;
import com.money.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {

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

    // 🌟 注入我们刚切分出去的“计价大脑”
    private final PosCalculationEngine posCalculationEngine;

    // 基础查询依然需要这些 Mapper
    private final GmsGoodsMapper gmsGoodsMapper;
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
    // 基础查询模块 (保持原样)
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
    // 核心重构：事务大管家 (完全信任计价大脑)
    // ==========================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OmsOrderVO settleAccounts(SettleAccountsDTO dto) {
        log.info("【POS发起结算】请求单号: {}, 入参明细: {}", dto.getReqId(), JSONUtil.toJsonStr(dto));

        // 1. 幂等拦截
        if (StrUtil.isBlank(dto.getReqId())) {
            throw new BaseException("【请求异常】缺少交易唯一单号，请刷新收银台后重试。");
        }
        if (omsOrderService.lambdaQuery().eq(OmsOrder::getOrderNo, dto.getReqId()).count() > 0) {
            log.warn("【POS重复提单拦截】单号: {}", dto.getReqId());
            throw new BaseException("【订单已处理】该订单已成功落库，请勿重复点击提交！");
        }

        // 🌟 2. 复用计价引擎，获取绝对真理 (前后端共用大脑)
        SettleTrialReqDTO trialReq = new SettleTrialReqDTO();
        trialReq.setMember(dto.getMember());
        trialReq.setUsedCouponRuleId(dto.getUsedCouponRuleId());
        trialReq.setUsedCouponCount(dto.getUsedCouponCount());
        trialReq.setWaiveCoupon(dto.getWaiveCoupon());
        trialReq.setManualDiscountAmount(dto.getManualDiscountAmount());
        if (dto.getOrderDetail() != null) {
            trialReq.setItems(dto.getOrderDetail().stream().map(d -> {
                SettleTrialReqDTO.TrialItem item = new SettleTrialReqDTO.TrialItem();
                item.setGoodsId(d.getGoodsId());
                item.setQuantity(d.getQuantity());
                return item;
            }).collect(Collectors.toList()));
        }

        SettleTrialResVO trialRes = posCalculationEngine.calculate(trialReq);

        // 3. 🛡️ 校验账务红线
        BigDecimal totalPaid = BigDecimal.ZERO;
        if (dto.getPayments() != null) {
            totalPaid = dto.getPayments().stream()
                    .filter(p -> p.getPayAmount() != null)
                    .map(SettleAccountsDTO.PaymentItem::getPayAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        if (trialRes.getFinalPayAmount().compareTo(totalPaid) != 0) {
            log.warn("【POS对账熔断】单号: {}, 后端应收: {}, 前端实收: {}", dto.getReqId(), trialRes.getFinalPayAmount(), totalPaid);
            throw new BaseException(String.format("【金额核对异常】系统计算应收(￥%s)与输入实付总和(￥%s)不匹配。建议：请清空支付金额后重新输入。",
                    trialRes.getFinalPayAmount().toPlainString(), totalPaid.toPlainString()));
        }

        // 获取商品基底信息 (用于落库明细和扣库存)
        List<Long> goodsIds = trialReq.getItems().stream().map(SettleTrialReqDTO.TrialItem::getGoodsId).collect(Collectors.toList());
        Map<Long, GmsGoods> goodsMap = gmsGoodsService.listByIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));

        // 4. 组装主订单落库
        OmsOrder order = new OmsOrder();
        order.setOrderNo(dto.getReqId());
        order.setTotalAmount(trialRes.getTotalAmount());
        order.setCouponAmount(trialRes.getMemberCouponDeduct());
        order.setUseVoucherAmount(trialRes.getVoucherDeduct());
        order.setManualDiscountAmount(trialRes.getManualDeduct());
        order.setCostAmount(trialRes.getCostAmount());
        order.setPayAmount(trialRes.getFinalPayAmount());
        order.setFinalSalesAmount(trialRes.getFinalPayAmount());
        order.setStatus(OrderStatusEnum.PAID.name());
        order.setPaymentTime(LocalDateTime.now());

        UmsMember member = null;
        if (dto.getMember() != null) {
            member = umsMemberService.getById(dto.getMember());
            if (member == null) throw new BaseException("【会员异常】未找到该会员信息。");
            order.setVip(true);
            order.setMemberId(member.getId());
            order.setMember(member.getName());
            order.setContact(member.getPhone());
            order.setProvince(member.getProvince());
            order.setCity(member.getCity());
            order.setDistrict(member.getDistrict());
            order.setAddress(member.getAddress());
        } else {
            order.setVip(false);
        }
        omsOrderService.save(order);

        // 5. 编排落地附属表
        List<OmsOrderDetail> orderDetails = saveOrderDetails(trialRes, goodsMap, order.getOrderNo());
        saveOrderPayments(dto, order.getOrderNo());
        processInventory(orderDetails, goodsMap, order.getOrderNo());

        // 6. 原子核销营销与资金资产
        if (order.getVip()) {
            consumeMarketingAssets(member, dto, trialRes, order.getOrderNo());
        }

        // 7. 写入大一统结账日志
        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());
        orderLog.setDescription("执行强一致性结算 (计价引擎抽离/账务红线/原子资产防超扣/幂等防护 V2.0)");
        omsOrderLogService.saveBatch(ListUtil.of(orderLog));

        log.info("【POS结算成功】单号: {}, 实收: {}", order.getOrderNo(), order.getPayAmount());
        return BeanMapUtil.to(order, OmsOrderVO::new);
    }

    // ==========================================
    // 内部协助方法：仅负责数据库原子化操作
    // ==========================================
    private List<OmsOrderDetail> saveOrderDetails(SettleTrialResVO trialRes, Map<Long, GmsGoods> goodsMap, String orderNo) {
        List<OmsOrderDetail> details = new ArrayList<>();
        for (SettleTrialResVO.ItemRes itemRes : trialRes.getItems()) {
            GmsGoods goods = goodsMap.get(itemRes.getGoodsId());
            OmsOrderDetail detail = new OmsOrderDetail();
            detail.setOrderNo(orderNo);
            detail.setStatus(OrderStatusEnum.PAID.name());
            detail.setGoodsId(goods.getId());
            detail.setBrandId(goods.getBrandId());
            detail.setGoodsBarcode(goods.getBarcode());
            detail.setGoodsName(goods.getName());
            detail.setSalePrice(itemRes.getOriginalPrice());
            detail.setPurchasePrice(goods.getPurchasePrice() == null ? BigDecimal.ZERO : goods.getPurchasePrice());
            detail.setVipPrice(goods.getVipPrice());
            detail.setQuantity(itemRes.getQuantity());
            // 🌟 直接信任试算引擎算出的实价和分摊的券
            detail.setGoodsPrice(itemRes.getRealPrice());
            detail.setCoupon(itemRes.getCouponDeduct());
            details.add(detail);
        }
        omsOrderDetailService.saveBatch(details);
        return details;
    }

    private void saveOrderPayments(SettleAccountsDTO dto, String orderNo) {
        if (dto.getPayments() == null) return;
        LocalDateTime now = LocalDateTime.now();
        for (SettleAccountsDTO.PaymentItem item : dto.getPayments()) {
            if (item.getPayAmount() == null || item.getPayAmount().compareTo(BigDecimal.ZERO) == 0) continue;
            OmsOrderPay payRecord = new OmsOrderPay();
            payRecord.setOrderNo(orderNo);
            payRecord.setPayMethodCode(item.getPayMethodCode());
            payRecord.setPayMethodName(item.getPayMethodName());
            payRecord.setPayTag(item.getPayTag());
            payRecord.setPayAmount(item.getPayAmount());
            payRecord.setCreateTime(now);
            omsOrderPayMapper.insert(payRecord);
        }
    }

    private void processInventory(List<OmsOrderDetail> orderDetails, Map<Long, GmsGoods> goodsMap, String orderNo) {
        LocalDateTime now = LocalDateTime.now();
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
                            int rows = gmsGoodsMapper.deductStockAtomically(subGoods.getId(), new BigDecimal(deductQty));
                            if (rows == 0) throw new BaseException("【库存不足拦截】套餐子商品「" + subGoods.getName() + "」系统剩余库存不足或发生抢购。");
                            writeStockLog(subGoods, -deductQty, orderNo, "前台套餐售出联动扣除", now);
                        }
                    }
                }
            } else {
                int rows = gmsGoodsMapper.deductStockAtomically(goods.getId(), new BigDecimal(detail.getQuantity()));
                if (rows == 0) throw new BaseException("【库存不足拦截】商品「" + goods.getName() + "」系统剩余库存不足或发生抢购。");
                writeStockLog(goods, -detail.getQuantity(), orderNo, "前台智能收银售出", now);
            }
        }
    }

    private void writeStockLog(GmsGoods goods, int changeQty, String orderNo, String remark, LocalDateTime now) {
        GmsGoods freshGoods = gmsGoodsService.getById(goods.getId());
        com.money.entity.GmsStockLog stockLog = new com.money.entity.GmsStockLog();
        stockLog.setGoodsId(freshGoods.getId());
        stockLog.setGoodsName(freshGoods.getName());
        stockLog.setGoodsBarcode(freshGoods.getBarcode());
        stockLog.setType(STOCK_TYPE_SALE);
        stockLog.setQuantity(changeQty);
        stockLog.setAfterQuantity(freshGoods.getStock() == null ? 0 : freshGoods.getStock().intValue());
        stockLog.setOrderNo(orderNo);
        stockLog.setRemark(remark);
        stockLog.setCreateTime(now);
        gmsStockLogMapper.insert(stockLog);
    }

    private void consumeMarketingAssets(UmsMember member, SettleAccountsDTO dto, SettleTrialResVO trialRes, String orderNo) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 原子扣减单品券 & 累加消费次数 (调用 UmsMemberService.consume)
        umsMemberService.consume(member.getId(), trialRes.getFinalPayAmount(), trialRes.getMemberCouponDeduct(), orderNo);

        // 2. 扣减满减券并写券流水
        if (dto.getUsedCouponRuleId() != null && dto.getUsedCouponCount() != null && dto.getUsedCouponCount() > 0) {
            List<PosMemberCoupon> availableCoupons = posMemberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>()
                    .eq(PosMemberCoupon::getMemberId, member.getId()).eq(PosMemberCoupon::getRuleId, dto.getUsedCouponRuleId()).eq(PosMemberCoupon::getStatus, COUPON_UNUSED).last("LIMIT " + dto.getUsedCouponCount()));

            if (!availableCoupons.isEmpty()) {
                List<Long> couponIds = availableCoupons.stream().map(PosMemberCoupon::getId).collect(Collectors.toList());
                PosMemberCoupon updateCoupon = new PosMemberCoupon();
                updateCoupon.setStatus(COUPON_USED);
                updateCoupon.setOrderNo(orderNo);
                updateCoupon.setUseTime(now);
                posMemberCouponMapper.update(updateCoupon, new LambdaUpdateWrapper<PosMemberCoupon>().in(PosMemberCoupon::getId, couponIds));

                long remainVouchers = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>()
                        .eq(PosMemberCoupon::getMemberId, member.getId()).eq(PosMemberCoupon::getStatus, COUPON_UNUSED));

                UmsMemberLog voucherLog = new UmsMemberLog();
                voucherLog.setMemberId(member.getId());
                voucherLog.setMemberName(member.getName());
                voucherLog.setMemberPhone(member.getPhone());
                voucherLog.setType(LOG_TYPE_VOUCHER);
                voucherLog.setOperateType(OPERATE_CONSUME);
                voucherLog.setAmount(BigDecimal.valueOf(-dto.getUsedCouponCount()));
                voucherLog.setAfterAmount(BigDecimal.valueOf(remainVouchers));
                voucherLog.setOrderNo(orderNo);

                PosCouponRule rule = posCouponRuleMapper.selectById(dto.getUsedCouponRuleId());
                voucherLog.setRemark("核销满减优惠券: " + (rule != null ? rule.getName() : "未知活动"));
                voucherLog.setCreateTime(now);
                umsMemberLogMapper.insert(voucherLog);
            }
        }

        // 3. 🛡️ 原子扣除余额：计算实际使用了多少余额支付
        if (dto.getPayments() != null) {
            BigDecimal balanceCost = dto.getPayments().stream()
                    .filter(p -> LOG_TYPE_BALANCE.equals(p.getPayMethodCode()) && p.getPayAmount() != null)
                    .map(SettleAccountsDTO.PaymentItem::getPayAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (balanceCost.compareTo(BigDecimal.ZERO) > 0) {
                // 彻底交还给会员网关执行防超扣 CAS
                umsMemberService.deductBalance(member.getId(), balanceCost, orderNo, "订单支付扣除会员余额");
            }
        }

        // 4. 更新最后访问时间
        umsMemberService.lambdaUpdate()
                .set(UmsMember::getLastVisitTime, now)
                .eq(UmsMember::getId, member.getId())
                .update();
    }
}