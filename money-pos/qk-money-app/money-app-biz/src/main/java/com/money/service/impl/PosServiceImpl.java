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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    public List<PosGoodsVO> listGoods(String barcode) {
        List<GmsGoods> gmsGoodsList = gmsGoodsService.lambdaQuery().like(StrUtil.isNotBlank(barcode), GmsGoods::getBarcode, barcode).list();
        List<PosGoodsVO> posGoodsVOS = BeanMapUtil.to(gmsGoodsList, PosGoodsVO::new);

        if (!posGoodsVOS.isEmpty()) {
            List<Long> skuIds = posGoodsVOS.stream().map(PosGoodsVO::getId).collect(Collectors.toList());
            List<PosSkuLevelPrice> levelPrices = posSkuLevelPriceMapper.selectList(new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, skuIds));

            Map<Long, Map<String, BigDecimal>> priceMap = levelPrices.stream()
                    .collect(Collectors.groupingBy(PosSkuLevelPrice::getSkuId, Collectors.toMap(PosSkuLevelPrice::getLevelId, PosSkuLevelPrice::getMemberPrice)));
            posGoodsVOS.forEach(vo -> vo.setLevelPrices(priceMap.getOrDefault(vo.getId(), new HashMap<>())));
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
            List<PosMemberCoupon> allUnusedCoupons = posMemberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>().in(PosMemberCoupon::getMemberId, memberIds).ne(PosMemberCoupon::getStatus, "USED"));
            for (PosMemberVO vo : posMemberVOS) vo.setCouponCount(0);

            if (!allUnusedCoupons.isEmpty()) {
                List<Long> ruleIds = allUnusedCoupons.stream().map(PosMemberCoupon::getRuleId).distinct().collect(Collectors.toList());
                Map<Long, PosCouponRule> ruleMap = posCouponRuleMapper.selectBatchIds(ruleIds).stream().collect(Collectors.toMap(PosCouponRule::getId, rule -> rule));

                for (PosMemberVO vo : posMemberVOS) {
                    List<PosMemberCoupon> hisCoupons = allUnusedCoupons.stream().filter(c -> c.getMemberId().equals(vo.getId())).collect(Collectors.toList());
                    vo.setCouponCount(hisCoupons.size());

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

        // --- 1. 组装明细，提取所有的基础财务数据 ---
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal couponAmount = BigDecimal.ZERO;
        BigDecimal costAmount = BigDecimal.ZERO; // 🌟 修复：补回成本计算

        List<OmsOrderDetail> orderDetails = settleAccountsDTO.getOrderDetail().stream().map(dto -> {
            GmsGoods goods = gmsGoodsService.getById(dto.getGoodsId());
            OmsOrderDetail detail = new OmsOrderDetail();
            detail.setOrderNo(orderNo);
            detail.setStatus(OrderStatusEnum.PAID.name());
            detail.setGoodsId(goods.getId());
            detail.setGoodsBarcode(goods.getBarcode());
            detail.setGoodsName(goods.getName());
            detail.setSalePrice(goods.getSalePrice());
            detail.setPurchasePrice(goods.getPurchasePrice() == null ? BigDecimal.ZERO : goods.getPurchasePrice());
            detail.setVipPrice(goods.getVipPrice());
            detail.setQuantity(dto.getQuantity());
            detail.setGoodsPrice(dto.getGoodsPrice());

            if (isVip && !Boolean.TRUE.equals(settleAccountsDTO.getWaiveCoupon())) {
                BigDecimal dynamicCoupon = goods.getSalePrice().subtract(dto.getGoodsPrice());
                detail.setCoupon(dynamicCoupon.compareTo(BigDecimal.ZERO) > 0 ? dynamicCoupon : BigDecimal.ZERO);
            } else {
                detail.setCoupon(BigDecimal.ZERO);
            }
            return detail;
        }).collect(Collectors.toList());

        for (OmsOrderDetail detail : orderDetails) {
            BigDecimal qty = new BigDecimal(detail.getQuantity());
            totalAmount = totalAmount.add(detail.getSalePrice().multiply(qty));
            couponAmount = couponAmount.add(detail.getCoupon().multiply(qty));
            costAmount = costAmount.add(detail.getPurchasePrice().multiply(qty)); // 🌟 修复：累加进货成本
        }

        order.setTotalAmount(totalAmount);
        order.setCouponAmount(couponAmount);
        order.setCostAmount(costAmount); // 🌟 修复：将成本填入订单主表

        // --- 2. 满减与会员卡逻辑 ---
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

            // 扣除会员券账户余额
            if (couponAmount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal consumeCoupon = member.getCoupon().subtract(couponAmount);
                if (consumeCoupon.compareTo(BigDecimal.ZERO) < 0) throw new BaseException("抵扣会员券余额不足");
                member.setCoupon(consumeCoupon);

                UmsMemberLog couponLog = new UmsMemberLog();
                couponLog.setMemberId(member.getId());
                couponLog.setType("COUPON");
                couponLog.setOperateType("CONSUME");
                couponLog.setAmount(couponAmount.negate());
                couponLog.setAfterAmount(member.getCoupon());
                couponLog.setOrderNo(orderNo);
                couponLog.setRemark("订单抵扣会员券");
                couponLog.setCreateTime(now);
                umsMemberLogMapper.insert(couponLog);
            }

            // 核销满减券
            if (settleAccountsDTO.getUsedCouponRuleId() != null && settleAccountsDTO.getUsedCouponCount() != null && settleAccountsDTO.getUsedCouponCount() > 0) {
                Long ruleId = settleAccountsDTO.getUsedCouponRuleId();
                Integer usedCount = settleAccountsDTO.getUsedCouponCount();
                PosCouponRule rule = posCouponRuleMapper.selectById(ruleId);
                if (rule == null) throw new BaseException("满减券异常或已停用");

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
                voucherLog.setRemark("核销满减券");
                voucherLog.setCreateTime(now);
                umsMemberLogMapper.insert(voucherLog);
            }
        }
        order.setUseVoucherAmount(voucherAmount);

        // --- 3. 🌟 贯彻解耦定律：后端的绝对财务配平中心 ---
        BigDecimal manualDiscount = settleAccountsDTO.getManualDiscountAmount() != null ? settleAccountsDTO.getManualDiscountAmount() : BigDecimal.ZERO;
        order.setManualDiscountAmount(manualDiscount);

        // 终极公式：实付 = 总价 - 会员券 - 满减券 - 整单优惠
        BigDecimal finalPayAmount = totalAmount.subtract(couponAmount).subtract(voucherAmount).subtract(manualDiscount);
        if (finalPayAmount.compareTo(BigDecimal.ZERO) < 0) finalPayAmount = BigDecimal.ZERO;

        order.setPayAmount(finalPayAmount);
        order.setFinalSalesAmount(finalPayAmount); // 🌟 修复：最终销售额落库

        // --- 4. 落地保存 ---
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
        for (OmsOrderDetail detail : orderDetails) {
            gmsGoodsService.sell(detail.getGoodsId(), detail.getQuantity());
            GmsGoods goods = gmsGoodsService.getById(detail.getGoodsId());
            com.money.entity.GmsStockLog stockLog = new com.money.entity.GmsStockLog();
            stockLog.setGoodsId(goods.getId());
            stockLog.setGoodsName(goods.getName());
            stockLog.setGoodsBarcode(goods.getBarcode());
            stockLog.setType("SALE");
            stockLog.setQuantity(-detail.getQuantity());
            stockLog.setAfterQuantity(goods.getStock() == null ? 0 : goods.getStock().intValue());
            stockLog.setOrderNo(orderNo);
            stockLog.setRemark("前台收银售出");
            stockLog.setCreateTime(now);
            gmsStockLogMapper.insert(stockLog);
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
                balanceLog.setRemark("订单支付扣款");
                balanceLog.setCreateTime(now);
                umsMemberLogMapper.insert(balanceLog);
            }
            member.setLastVisitTime(now);
            umsMemberService.updateById(member);
        }

        OmsOrderLog log = new OmsOrderLog();
        log.setOrderId(order.getId());
        log.setDescription("完成全动态组合支付订单");
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