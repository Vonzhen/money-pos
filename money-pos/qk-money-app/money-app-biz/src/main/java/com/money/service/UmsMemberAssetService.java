package com.money.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.constant.BizErrorStatus;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.entity.UmsRechargeOrder; // 🌟 新增引入
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.mapper.UmsRechargeOrderMapper; // 🌟 新增引入
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List; // 🌟 新增引入

/**
 * 领域服务：会员资产子域
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UmsMemberAssetService {

    private final UmsMemberMapper umsMemberMapper;
    private final UmsMemberLogMapper umsMemberLogMapper;
    private final UmsRechargeOrderMapper umsRechargeOrderMapper; // 🌟 补齐底层的 Mapper 注入

    private static final String TYPE_BALANCE = "BALANCE";
    private static final String TYPE_COUPON = "COUPON";
    private static final String TYPE_VOUCHER = "VOUCHER";

    // ==========================================
    // 🌟 新增：由 Service 彻底接管的查询防线 (方便后续加脱敏/缓存/审计)
    // ==========================================
    public List<UmsMemberLog> getMemberLogs(Long memberId) {
        return umsMemberLogMapper.selectList(
                new LambdaQueryWrapper<UmsMemberLog>()
                        .eq(UmsMemberLog::getMemberId, memberId)
                        .orderByDesc(UmsMemberLog::getCreateTime)
                        .last("LIMIT 100")
        );
    }

    public UmsRechargeOrder getRechargeOrderDetail(String orderNo) {
        UmsRechargeOrder order = umsRechargeOrderMapper.selectOne(
                new LambdaQueryWrapper<UmsRechargeOrder>()
                        .eq(UmsRechargeOrder::getOrderNo, orderNo)
                        .last("LIMIT 1")
        );

        if (order == null) {
            log.error("未找到充值凭证，单号：{}", orderNo);
            throw new BaseException("单据档案不存在，可能已被物理删除");
        }
        return order;
    }

    // ==========================================
    // 正向交易：严格校验，找不到会员必须阻断！
    // ==========================================
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

    // ==========================================
    // 逆向退款：优雅降级，放过散客与死号，保全退款主流程！
    // ==========================================
    public void addBalance(Long memberId, BigDecimal amount, String orderNo, String remark) {
        if (memberId == null || memberId <= 0 || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        UmsMember member = umsMemberMapper.selectById(memberId);
        if (member == null) {
            log.warn("【优雅降级】退还余额时未找到会员(ID:{})，可能为散客或已被删除，跳过资产回退...", memberId);
            return;
        }

        BigDecimal beforeBalance = member.getBalance() != null ? member.getBalance() : BigDecimal.ZERO;

        umsMemberMapper.update(null, new LambdaUpdateWrapper<UmsMember>()
                .setSql("balance = balance + " + amount)
                .eq(UmsMember::getId, memberId));

        umsMemberLogMapper.insert(createLog(member, TYPE_BALANCE, "REFUND", amount, BigDecimal.ZERO, beforeBalance.add(amount), orderNo, remark));
    }

    public void logVoucherRefund(Long memberId, BigDecimal amount, BigDecimal afterAmount, String orderNo) {
        if (memberId == null || memberId <= 0 || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        UmsMember member = umsMemberMapper.selectById(memberId);
        if (member != null) {
            umsMemberLogMapper.insert(createLog(member, TYPE_VOUCHER, "REFUND", amount, BigDecimal.ZERO, afterAmount, orderNo, "整单退款:满减券退回"));
        } else {
            log.warn("【优雅降级】退还满减券时未找到会员(ID:{})，跳过...", memberId);
        }
    }

    public void processReturn(Long id, BigDecimal amount, BigDecimal coupon, boolean increaseCancelTimes, String orderNo) {
        if (id == null || id <= 0) return;

        UmsMember member = umsMemberMapper.selectById(id);
        if (member == null) {
            log.warn("【优雅降级】退货时未找到会员(ID:{})，跳过资产回退，保障资金与库存退库顺畅！", id);
            return;
        }

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

        if (rows == 0) {
            log.warn("【数据预警】会员(ID:{})资产退回 SQL 未命中任何行", id);
        }
    }

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