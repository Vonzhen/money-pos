package com.money.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.CouponStatusEnum;
import com.money.constant.OrderStatusEnum;
import com.money.constant.PayMethodEnum;
import com.money.dto.pos.*;
import com.money.entity.*;
import com.money.mapper.*;
import com.money.service.*;
import com.money.web.exception.BaseException;
import com.money.web.util.BeanMapUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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

    private final UmsMemberService umsMemberService;
    private final GmsGoodsService gmsGoodsService;
    private final OmsOrderService omsOrderService;
    private final OmsOrderDetailService omsOrderDetailService;
    private final OmsOrderLogService omsOrderLogService;
    private final PosCalculationEngine posCalculationEngine;
    private final OmsOrderPayMapper omsOrderPayMapper;

    private final PosSkuLevelPriceMapper posSkuLevelPriceMapper;
    private final PosCouponRuleMapper posCouponRuleMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;

    private final PosInventoryActionService inventoryActionService;
    private final PosAssetActionService assetActionService;

    // ==========================================
    // 基础查询模块
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
                .and(StrUtil.isNotBlank(member), w -> w.like(UmsMember::getName, member).or().like(UmsMember::getPhone, member)).list();
        List<PosMemberVO> posMemberVOS = BeanMapUtil.to(memberList, PosMemberVO::new);

        if (!posMemberVOS.isEmpty()) {
            List<Long> memberIds = posMemberVOS.stream().map(PosMemberVO::getId).collect(Collectors.toList());
            List<UmsMemberBrandLevel> allBrandLevels = umsMemberBrandLevelMapper.selectList(
                    new LambdaQueryWrapper<UmsMemberBrandLevel>().in(UmsMemberBrandLevel::getMemberId, memberIds)
            );
            Map<Long, List<UmsMemberBrandLevel>> blMap = allBrandLevels.stream().collect(Collectors.groupingBy(UmsMemberBrandLevel::getMemberId));

            List<PosMemberCoupon> allUnusedCoupons = posMemberCouponMapper.selectList(
                    new LambdaQueryWrapper<PosMemberCoupon>().in(PosMemberCoupon::getMemberId, memberIds).eq(PosMemberCoupon::getStatus, CouponStatusEnum.UNUSED.name())
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
    // 🌟 终极交响乐指挥：settleAccounts
    // ==========================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SettleResultVO settleAccounts(SettleAccountsDTO dto) {
        if (dto == null) throw new BaseException("结算请求主体不能为空");

        // 1. 🌟 任务 18：一次查询，全链路复用！安检部直接返回核实过的会员对象
        UmsMember verifiedMember = validateSettleRequest(dto);

        // 2. 试算裁决
        SettleTrialResVO trialRes = executeTrial(dto);
        BigDecimal finalPayAmount = trialRes.getFinalPayAmount().setScale(2, RoundingMode.HALF_UP);

        // 3. 财务脱水清洗
        NormalizedPaymentResult payResult = normalizePayments(dto.getPayments(), finalPayAmount);

        // 4. 物资核实
        List<Long> goodsIds = trialRes.getItems().stream().map(SettleTrialResVO.ItemRes::getGoodsId).collect(Collectors.toList());
        Map<Long, GmsGoods> goodsMap = gmsGoodsService.listByIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));
        for (Long gid : goodsIds) {
            if (!goodsMap.containsKey(gid)) throw new BaseException("【异常】商品不存在或已被删除: " + gid);
        }

        // 5. 组装并强防重落库 (直接使用已核实的会员)
        OmsOrder order = assembleOrder(dto, trialRes, verifiedMember);
        try {
            omsOrderService.save(order);
        } catch (DuplicateKeyException e) {
            throw new BaseException("【订单已处理】请勿重复点击提交！");
        }

        // 6. 明细与流水落库
        List<OmsOrderDetail> orderDetails = saveOrderDetails(trialRes, goodsMap, order.getOrderNo());
        saveNormalizedPayments(payResult, order.getOrderNo());

        // 7. 委托仓储部：处理物理库存
        inventoryActionService.deduct(orderDetails, goodsMap, order.getOrderNo());

        // 8. 委托资产部：处理虚拟资产
        if (order.getVip() && verifiedMember != null) {
            assetActionService.consume(verifiedMember.getId(), dto, trialRes, payResult, order.getOrderNo());
        }

        // 9. 🌟 任务 14 & 15：结构化高可用审计日志
        int totalItemCount = orderDetails.stream().mapToInt(OmsOrderDetail::getQuantity).sum();
        String distinctPayMethods = payResult.getValidItems().stream()
                .map(p -> StrUtil.isNotBlank(p.getPayTag()) ? p.getMethodCode() + ":" + p.getPayTag() : p.getMethodCode())
                .distinct()
                .collect(Collectors.joining(","));

        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());
        Map<String, Object> auditMap = new LinkedHashMap<>(); // 保持插入顺序，日志更好看
        auditMap.put("action", "SETTLE_SUCCESS");
        auditMap.put("orderNo", order.getOrderNo());
        auditMap.put("memberId", dto.getMember());
        auditMap.put("itemCount", totalItemCount);          // 真实的商品件数
        auditMap.put("detailCount", orderDetails.size());   // 明细行数
        auditMap.put("payMethods", distinctPayMethods);     // 去重且带tag的支付组合
        auditMap.put("finalPay", finalPayAmount);
        auditMap.put("totalPaid", payResult.getTotalPaid());
        auditMap.put("change", payResult.getChangeAmount());
        auditMap.put("net", payResult.getNetReceived());
        orderLog.setDescription(JSONUtil.toJsonStr(auditMap));
        omsOrderLogService.save(orderLog);

        // 10. 组装增强版返回值 (小票友好)
        SettleResultVO resultVO = new SettleResultVO();
        resultVO.setOrderNo(order.getOrderNo());
        resultVO.setTotalAmount(order.getTotalAmount());
        resultVO.setFinalPayAmount(order.getPayAmount());
        resultVO.setTotalPaid(payResult.getTotalPaid());
        resultVO.setChangeAmount(payResult.getChangeAmount());
        resultVO.setNetReceived(payResult.getNetReceived());
        resultVO.setPaymentTime(order.getPaymentTime());
        resultVO.setMemberName(order.getMember());
        resultVO.setCouponDeduct(order.getCouponAmount());
        resultVO.setVoucherDeduct(order.getUseVoucherAmount());
        resultVO.setManualDeduct(order.getManualDiscountAmount());

        return resultVO;
    }

    // ==========================================
    // 内部协助组装与清洗方法
    // ==========================================

    /**
     * 校验请求并查询会员（收口防重复查询）
     */
    private UmsMember validateSettleRequest(SettleAccountsDTO dto) {
        if (StrUtil.isBlank(dto.getReqId())) throw new BaseException("缺少请求单号");
        if (dto.getOrderDetail() == null || dto.getOrderDetail().isEmpty()) throw new BaseException("明细为空");
        for (com.money.dto.OmsOrderDetail.OmsOrderDetailDTO item : dto.getOrderDetail()) {
            if (item.getGoodsId() == null) throw new BaseException("含无效商品ID");
            if (item.getQuantity() == null || item.getQuantity() <= 0) throw new BaseException("数量必须大于0");
        }
        if (dto.getUsedCouponCount() != null && dto.getUsedCouponCount() < 0) throw new BaseException("券数量不可为负");
        if (dto.getManualDiscountAmount() != null && dto.getManualDiscountAmount().compareTo(BigDecimal.ZERO) < 0) throw new BaseException("手工优惠不可为负");
        if (dto.getPayments() == null || dto.getPayments().isEmpty()) throw new BaseException("支付明细为空");

        UmsMember verifiedMember = null;
        if (dto.getMember() != null) {
            verifiedMember = umsMemberService.getById(dto.getMember());
            if (verifiedMember == null) throw new BaseException("【风控拦截】未找到对应的会员实体信息");
        }

        boolean hasValid = false;
        for (SettleAccountsDTO.PaymentItem p : dto.getPayments()) {
            if (StrUtil.isBlank(p.getPayMethodCode())) throw new BaseException("缺少支付编码");
            if (p.getPayAmount() != null && p.getPayAmount().compareTo(BigDecimal.ZERO) < 0) throw new BaseException("支付金额不可为负");
            if (p.getPayAmount() != null && p.getPayAmount().compareTo(BigDecimal.ZERO) > 0) hasValid = true;

            PayMethodEnum methodEnum = PayMethodEnum.fromCode(p.getPayMethodCode());
            if (methodEnum == null) throw new BaseException("【风控拦截】不支持的未知支付方式: " + p.getPayMethodCode());

            // 🌟 任务 13：正反盲防，非聚合坚决不允许带 tag
            if (methodEnum == PayMethodEnum.AGGREGATE && StrUtil.isBlank(p.getPayTag())) {
                throw new BaseException("【风控拦截】聚合支付(AGGREGATE)必须明确具体的渠道标签(如 WECHAT)");
            }
            if (methodEnum != PayMethodEnum.AGGREGATE && StrUtil.isNotBlank(p.getPayTag())) {
                throw new BaseException("【风控拦截】非聚合支付不允许传递附加渠道标签");
            }

            if (methodEnum.isAsset() && verifiedMember == null) {
                throw new BaseException("【风控拦截】非会员禁用会员资产类支付");
            }
        }
        if (!hasValid) throw new BaseException("请录入有效支付金额");

        return verifiedMember;
    }

    private SettleTrialResVO executeTrial(SettleAccountsDTO dto) {
        SettleTrialReqDTO trialReq = new SettleTrialReqDTO();
        trialReq.setMember(dto.getMember());
        trialReq.setUsedCouponRuleId(dto.getUsedCouponRuleId());
        trialReq.setUsedCouponCount(dto.getUsedCouponCount());
        trialReq.setWaiveCoupon(dto.getWaiveCoupon());
        trialReq.setManualDiscountAmount(dto.getManualDiscountAmount());
        trialReq.setItems(dto.getOrderDetail().stream().map(d -> {
            SettleTrialReqDTO.TrialItem item = new SettleTrialReqDTO.TrialItem();
            item.setGoodsId(d.getGoodsId());
            item.setQuantity(d.getQuantity());
            return item;
        }).collect(Collectors.toList()));
        return posCalculationEngine.calculate(trialReq);
    }

    private NormalizedPaymentResult normalizePayments(List<SettleAccountsDTO.PaymentItem> rawPayments, BigDecimal finalPayAmount) {
        NormalizedPaymentResult result = new NormalizedPaymentResult();
        for (SettleAccountsDTO.PaymentItem p : rawPayments) {
            if (p.getPayAmount() == null || p.getPayAmount().compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal itemPay = p.getPayAmount().setScale(2, RoundingMode.HALF_UP);
            PayMethodEnum methodEnum = PayMethodEnum.fromCode(p.getPayMethodCode());
            boolean isCash = methodEnum.isAllowChange();

            NormalizedPaymentResult.StandardPayItem sItem = new NormalizedPaymentResult.StandardPayItem();
            sItem.setMethodCode(p.getPayMethodCode());
            sItem.setMethodName(p.getPayMethodName());
            sItem.setPayTag(p.getPayTag());
            sItem.setCash(isCash);
            sItem.setOriginalAmount(itemPay);
            sItem.setNetAmount(itemPay);
            result.getValidItems().add(sItem);

            result.setTotalPaid(result.getTotalPaid().add(itemPay));
            if (isCash) result.setCashPaid(result.getCashPaid().add(itemPay));
            else result.setNonCashPaid(result.getNonCashPaid().add(itemPay));
        }

        if (result.getTotalPaid().compareTo(finalPayAmount) < 0) throw new BaseException(String.format("实付不足. 应收: %s", finalPayAmount));
        if (result.getNonCashPaid().compareTo(finalPayAmount) > 0) throw new BaseException("非现金支付总额超限，禁止套现！");

        result.setChangeAmount(result.getTotalPaid().subtract(finalPayAmount));
        result.setNetReceived(result.getTotalPaid().subtract(result.getChangeAmount()));

        BigDecimal remainChange = result.getChangeAmount();
        for (NormalizedPaymentResult.StandardPayItem item : result.getValidItems()) {
            if (item.isCash() && remainChange.compareTo(BigDecimal.ZERO) > 0) {
                if (item.getNetAmount().compareTo(remainChange) >= 0) {
                    item.setNetAmount(item.getNetAmount().subtract(remainChange));
                    remainChange = BigDecimal.ZERO;
                } else {
                    remainChange = remainChange.subtract(item.getNetAmount());
                    item.setNetAmount(BigDecimal.ZERO);
                }
            }
            item.setNetAmount(item.getNetAmount().setScale(2, RoundingMode.HALF_UP));
            item.setOriginalAmount(item.getOriginalAmount().setScale(2, RoundingMode.HALF_UP));
        }

        // 🌟 任务 17：最终结果在此统一打上精度封印，严防末端漂移
        result.setTotalPaid(result.getTotalPaid().setScale(2, RoundingMode.HALF_UP));
        result.setCashPaid(result.getCashPaid().setScale(2, RoundingMode.HALF_UP));
        result.setNonCashPaid(result.getNonCashPaid().setScale(2, RoundingMode.HALF_UP));
        result.setChangeAmount(result.getChangeAmount().setScale(2, RoundingMode.HALF_UP));
        result.setNetReceived(result.getNetReceived().setScale(2, RoundingMode.HALF_UP));

        return result;
    }

    private OmsOrder assembleOrder(SettleAccountsDTO dto, SettleTrialResVO trialRes, UmsMember verifiedMember) {
        OmsOrder order = new OmsOrder();
        order.setOrderNo(dto.getReqId());
        order.setTotalAmount(trialRes.getTotalAmount());
        order.setCouponAmount(trialRes.getMemberCouponDeduct());
        order.setUseVoucherAmount(trialRes.getVoucherDeduct());
        order.setManualDiscountAmount(trialRes.getManualDeduct());
        order.setCostAmount(trialRes.getCostAmount());

        // 🌟 任务 16：明确主表金额语义，均代表"本单业务应收"
        order.setPayAmount(trialRes.getFinalPayAmount());
        order.setFinalSalesAmount(trialRes.getFinalPayAmount());

        order.setStatus(OrderStatusEnum.PAID.name());
        order.setPaymentTime(LocalDateTime.now());

        // 🌟 任务 18：复用已查出的会员实体，不瞎猜
        if (verifiedMember != null) {
            order.setVip(true);
            order.setMemberId(verifiedMember.getId());
            order.setMember(verifiedMember.getName());
            order.setContact(verifiedMember.getPhone());
        } else {
            order.setVip(false);
        }
        return order;
    }

    private List<OmsOrderDetail> saveOrderDetails(SettleTrialResVO trialRes, Map<Long, GmsGoods> goodsMap, String orderNo) {
        List<OmsOrderDetail> details = new ArrayList<>();
        for (SettleTrialResVO.ItemRes itemRes : trialRes.getItems()) {
            GmsGoods goods = goodsMap.get(itemRes.getGoodsId());
            if (goods == null) throw new BaseException("【异常】明细商品丢失，ID:" + itemRes.getGoodsId());

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
            detail.setGoodsPrice(itemRes.getRealPrice());
            detail.setCoupon(itemRes.getCouponDeduct() != null ? itemRes.getCouponDeduct() : BigDecimal.ZERO);
            details.add(detail);
        }
        omsOrderDetailService.saveBatch(details);
        return details;
    }

    private void saveNormalizedPayments(NormalizedPaymentResult payResult, String orderNo) {
        LocalDateTime now = LocalDateTime.now();
        for (NormalizedPaymentResult.StandardPayItem item : payResult.getValidItems()) {
            if (item.getNetAmount().compareTo(BigDecimal.ZERO) == 0) continue;
            OmsOrderPay payRecord = new OmsOrderPay();
            payRecord.setOrderNo(orderNo);
            payRecord.setPayMethodCode(item.getMethodCode());
            payRecord.setPayMethodName(item.getMethodName());
            payRecord.setPayTag(item.getPayTag());
            payRecord.setPayAmount(item.getNetAmount());
            payRecord.setCreateTime(now);
            omsOrderPayMapper.insert(payRecord);
        }
    }
}