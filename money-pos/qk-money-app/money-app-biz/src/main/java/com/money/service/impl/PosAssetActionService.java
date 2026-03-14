package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.constant.CouponStatusEnum;
import com.money.constant.PayMethodEnum;
import com.money.dto.pos.NormalizedPaymentResult;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.dto.pos.SettleTrialResVO;
import com.money.entity.PosMemberCoupon;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.service.UmsMemberService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j // 🌟 引入日志门面
@Service
@RequiredArgsConstructor
public class PosAssetActionService {
    private static final String LOG_TYPE_VOUCHER = "VOUCHER";
    private static final String OPERATE_CONSUME = "CONSUME";

    private final UmsMemberService umsMemberService;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final UmsMemberLogMapper umsMemberLogMapper;

    public void consume(Long memberId, SettleAccountsDTO dto, SettleTrialResVO trialRes, NormalizedPaymentResult payResult, String orderNo) {
        LocalDateTime now = LocalDateTime.now();
        // 1. 单品券
        umsMemberService.consume(memberId, trialRes.getFinalPayAmount(), trialRes.getMemberCouponDeduct(), orderNo);

        // 2. 满减券
        if (dto.getUsedCouponRuleId() != null && dto.getUsedCouponCount() != null && dto.getUsedCouponCount() > 0) {
            int count = dto.getUsedCouponCount();

            // 🌟 任务 10：在进行 SQL 尾拼接前，加一道死锁防御，杜绝语法错误或恶意取数
            if (count > 500) {
                throw new BaseException("【风控拦截】单次核销满减券数量超出安全阈值");
            }

            List<PosMemberCoupon> availableCoupons = posMemberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>()
                    .eq(PosMemberCoupon::getMemberId, memberId)
                    .eq(PosMemberCoupon::getRuleId, dto.getUsedCouponRuleId())
                    .eq(PosMemberCoupon::getStatus, CouponStatusEnum.UNUSED.name())
                    .orderByAsc(PosMemberCoupon::getGetTime, PosMemberCoupon::getId)
                    .last("LIMIT " + count));

            if (availableCoupons.size() < count) throw new BaseException("【满减券异常】可用数量不足或已被使用。");

            List<Long> couponIds = availableCoupons.stream().map(PosMemberCoupon::getId).collect(Collectors.toList());
            PosMemberCoupon updateCoupon = new PosMemberCoupon();
            updateCoupon.setStatus(CouponStatusEnum.USED.name());
            updateCoupon.setOrderNo(orderNo);
            updateCoupon.setUseTime(now);

            int rows = posMemberCouponMapper.update(updateCoupon, new LambdaUpdateWrapper<PosMemberCoupon>()
                    .in(PosMemberCoupon::getId, couponIds).eq(PosMemberCoupon::getStatus, CouponStatusEnum.UNUSED.name()));
            if (rows != count) throw new BaseException("【并发拦截】优惠券已被抢占，操作回滚。");

            long remainVouchers = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>().eq(PosMemberCoupon::getMemberId, memberId).eq(PosMemberCoupon::getStatus, CouponStatusEnum.UNUSED.name()));
            UmsMemberLog voucherLog = new UmsMemberLog();
            voucherLog.setMemberId(memberId);
            voucherLog.setType(LOG_TYPE_VOUCHER);
            voucherLog.setOperateType(OPERATE_CONSUME);
            voucherLog.setAmount(BigDecimal.valueOf(-count));
            voucherLog.setAfterAmount(BigDecimal.valueOf(remainVouchers));
            voucherLog.setOrderNo(orderNo);
            voucherLog.setRemark(String.format("核销满减券 | 规则ID:%s | 张数:%s", dto.getUsedCouponRuleId(), count));
            voucherLog.setCreateTime(now);
            umsMemberLogMapper.insert(voucherLog);
        }

        // 3. 余额扣减
        BigDecimal balanceCost = payResult.getValidItems().stream()
                .filter(p -> PayMethodEnum.BALANCE.getCode().equals(p.getMethodCode()))
                .map(NormalizedPaymentResult.StandardPayItem::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (balanceCost.compareTo(BigDecimal.ZERO) > 0) {
            // 🌟 任务 11：扣减前的终极防线，确保余额花费不能大于本单财务净入账
            if (balanceCost.compareTo(payResult.getNetReceived()) > 0) {
                log.error("【资产安全拦截】尝试扣减的余额({})大于订单净入账({})，单号: {}", balanceCost, payResult.getNetReceived(), orderNo);
                throw new BaseException("【风控拦截】余额支付金额异常，超出了本单应收底线！");
            }
            umsMemberService.deductBalance(memberId, balanceCost, orderNo, "收银台支付扣除");
        }

        // 🌟 任务 12：更新到店时间，弱依赖不阻断主流程，仅做监控告警
        boolean updateSuccess = umsMemberService.lambdaUpdate()
                .set(UmsMember::getLastVisitTime, now)
                .eq(UmsMember::getId, memberId)
                .update();

        if (!updateSuccess) {
            log.warn("【数据一致性预警】订单 {} 结算成功，但更新会员(ID:{})最后到店时间失败，该会员可能刚刚被删除或冻结。", orderNo, memberId);
        }
    }
}