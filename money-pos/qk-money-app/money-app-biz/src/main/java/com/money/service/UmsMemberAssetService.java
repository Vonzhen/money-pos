package com.money.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.constant.BizErrorStatus;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 领域服务：会员资产子域
 * 职责：专注处理会员的纯资产变动 (消费扣款、余额抵扣、退货返还及对应的流水审计)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UmsMemberAssetService {

    private final UmsMemberMapper umsMemberMapper;
    private final UmsMemberLogMapper umsMemberLogMapper;

    private static final String TYPE_BALANCE = "BALANCE";
    private static final String TYPE_COUPON = "COUPON";

    public void consume(Long id, BigDecimal amount, BigDecimal couponAmount, String orderNo) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) throw new BaseException("扣款金额不能为负数");

        UmsMember member = umsMemberMapper.selectById(id);
        if (member == null) throw new BaseException(BizErrorStatus.MEMBER_NOT_FOUND, "会员不存在");

        BigDecimal beforeCoupon = member.getCoupon() != null ? member.getCoupon() : BigDecimal.ZERO;

        LambdaUpdateWrapper<UmsMember> updateWrapper = new LambdaUpdateWrapper<UmsMember>()
                .setSql("consume_amount = consume_amount + " + (amount != null ? amount : BigDecimal.ZERO))
                .setSql("consume_times = consume_times + 1")
                .eq(UmsMember::getId, id);

        if (couponAmount != null && couponAmount.compareTo(BigDecimal.ZERO) > 0) {
            updateWrapper.setSql("consume_coupon = consume_coupon + " + couponAmount)
                    .setSql("coupon = coupon - " + couponAmount)
                    .ge(UmsMember::getCoupon, couponAmount);
        }

        int rows = umsMemberMapper.update(null, updateWrapper);
        if (rows == 0 && couponAmount != null && couponAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new BaseException(BizErrorStatus.COUPON_NOT_ENOUGH, "扣款失败：券余额不足");
        }

        if (couponAmount != null && couponAmount.compareTo(BigDecimal.ZERO) > 0) {
            umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "CONSUME", couponAmount.negate(), BigDecimal.ZERO, beforeCoupon.subtract(couponAmount), orderNo, "订单自动抵扣会员券"));
        }
    }

    public void deductBalance(Long memberId, BigDecimal amount, String orderNo, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        UmsMember member = umsMemberMapper.selectById(memberId);
        if (member == null) throw new BaseException(BizErrorStatus.MEMBER_NOT_FOUND, "找不到会员信息");

        BigDecimal beforeBalance = member.getBalance() != null ? member.getBalance() : BigDecimal.ZERO;

        int rows = umsMemberMapper.update(null, new LambdaUpdateWrapper<UmsMember>()
                .setSql("balance = balance - " + amount)
                .eq(UmsMember::getId, memberId)
                .ge(UmsMember::getBalance, amount));

        if (rows == 0) {
            throw new BaseException(BizErrorStatus.BALANCE_INSUFFICIENT, "余额不足");
        }

        umsMemberLogMapper.insert(createLog(member, TYPE_BALANCE, "CONSUME", amount.negate(), BigDecimal.ZERO, beforeBalance.subtract(amount), orderNo, remark));
    }

    public void processReturn(Long id, BigDecimal amount, BigDecimal coupon, boolean increaseCancelTimes, String orderNo) {
        UmsMember member = umsMemberMapper.selectById(id);
        if (member == null) throw new BaseException("退货时会员不存在");

        BigDecimal beforeCoupon = member.getCoupon() == null ? BigDecimal.ZERO : member.getCoupon();

        if (coupon != null && coupon.compareTo(BigDecimal.ZERO) > 0) {
            umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "REFUND", coupon, BigDecimal.ZERO, beforeCoupon.add(coupon), orderNo, "售后退货返还会员券"));
        }

        int rows = umsMemberMapper.update(null, new LambdaUpdateWrapper<UmsMember>()
                .setSql("coupon = coupon + " + (coupon != null ? coupon : BigDecimal.ZERO))
                .setSql("consume_amount = consume_amount - " + (amount != null ? amount : BigDecimal.ZERO))
                .setSql("consume_coupon = consume_coupon - " + (coupon != null ? coupon : BigDecimal.ZERO))
                .setSql(increaseCancelTimes, "cancel_times = cancel_times + 1")
                .eq(UmsMember::getId, id));

        if (rows == 0) throw new BaseException("资产退回失败");
    }

    // 独立闭环的私有流水生成器
    private UmsMemberLog createLog(UmsMember m, String type, String opType, BigDecimal amt, BigDecimal realAmt, BigDecimal afterAmt, String orderNo, String remark) {
        UmsMemberLog l = new UmsMemberLog();
        l.setMemberId(m.getId());
        l.setMemberName(m.getName());
        l.setMemberPhone(m.getPhone());
        l.setType(type);
        l.setOperateType(opType);
        l.setAmount(amt);
        l.setRealAmount(realAmt);
        l.setAfterAmount(afterAmt);
        l.setOrderNo(orderNo);
        l.setRemark(remark);
        l.setCreateTime(LocalDateTime.now());
        return l;
    }
}