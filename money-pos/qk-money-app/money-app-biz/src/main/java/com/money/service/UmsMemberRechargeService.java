package com.money.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.dto.Ums.RechargeDTO;
import com.money.entity.PosMemberCoupon;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.entity.UmsRechargeOrder;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.mapper.UmsRechargeOrderMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class UmsMemberRechargeService {

    private final UmsMemberMapper umsMemberMapper;
    private final UmsRechargeOrderMapper umsRechargeOrderMapper;
    private final UmsMemberLogMapper umsMemberLogMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;

    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_VOID = "VOID";
    private static final String STATUS_UNUSED = "UNUSED";
    private static final String TYPE_BALANCE = "BALANCE";
    private static final String TYPE_COUPON = "COUPON";
    private static final String TYPE_VOUCHER = "VOUCHER";

    @Transactional(rollbackFor = Exception.class)
    public void recharge(RechargeDTO dto) {
        UmsMember member = umsMemberMapper.selectById(dto.getMemberId());
        if (member == null) throw new BaseException("会员不存在");

        LocalDateTime now = LocalDateTime.now();
        String orderNo = "RC" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + RandomUtil.randomNumbers(4);

        UmsRechargeOrder order = new UmsRechargeOrder();
        order.setOrderNo(orderNo);
        order.setMemberId(member.getId());
        order.setType(dto.getType());
        order.setAmount(dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO);
        order.setGiftCoupon(dto.getGiftCoupon() != null ? dto.getGiftCoupon() : BigDecimal.ZERO);

        BigDecimal fallbackRealAmount = (dto.getRealAmount() != null && dto.getRealAmount().compareTo(BigDecimal.ZERO) > 0)
                ? dto.getRealAmount() : order.getAmount();
        order.setRealAmount(fallbackRealAmount);

        order.setStatus(STATUS_PAID);
        order.setRemark(dto.getRemark());
        order.setCreateTime(now);
        umsRechargeOrderMapper.insert(order);

        BigDecimal beforeBalance = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();
        BigDecimal beforeCoupon = member.getCoupon() == null ? BigDecimal.ZERO : member.getCoupon();

        if (TYPE_BALANCE.equals(dto.getType())) {
            umsMemberMapper.update(null, new LambdaUpdateWrapper<UmsMember>()
                    .setSql("balance = balance + " + order.getAmount()).eq(UmsMember::getId, member.getId()));
            umsMemberLogMapper.insert(createLog(member, TYPE_BALANCE, "RECHARGE", order.getAmount(), order.getRealAmount(), beforeBalance.add(order.getAmount()), orderNo, dto.getRemark()));

            if (order.getGiftCoupon().compareTo(BigDecimal.ZERO) > 0) {
                umsMemberMapper.update(null, new LambdaUpdateWrapper<UmsMember>()
                        .setSql("coupon = coupon + " + order.getGiftCoupon()).eq(UmsMember::getId, member.getId()));
                umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "GIFT", order.getGiftCoupon(), BigDecimal.ZERO, beforeCoupon.add(order.getGiftCoupon()), orderNo, "充值赠送券额"));
            }
        } else if (TYPE_COUPON.equals(dto.getType())) {
            umsMemberMapper.update(null, new LambdaUpdateWrapper<UmsMember>()
                    .setSql("coupon = coupon + " + order.getAmount()).eq(UmsMember::getId, member.getId()));
            umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "RECHARGE", order.getAmount(), order.getRealAmount(), beforeCoupon.add(order.getAmount()), orderNo, "购买会员券: " + dto.getRemark()));
        } else if (TYPE_VOUCHER.equals(dto.getType())) {
            for (int i = 0; i < dto.getQuantity(); i++) {
                PosMemberCoupon pc = new PosMemberCoupon();
                pc.setMemberId(member.getId());
                pc.setRuleId(dto.getRuleId());
                pc.setStatus(STATUS_UNUSED);
                pc.setGetTime(now);
                posMemberCouponMapper.insert(pc);
            }
            long total = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>()
                    .eq(PosMemberCoupon::getMemberId, member.getId())
                    .eq(PosMemberCoupon::getStatus, STATUS_UNUSED));
            umsMemberLogMapper.insert(createLog(member, TYPE_VOUCHER, "RECHARGE", BigDecimal.valueOf(dto.getQuantity()), order.getRealAmount(), BigDecimal.valueOf(total), orderNo, "购买满减券"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidRecharge(String orderNo, String reason) {
        UmsRechargeOrder order = umsRechargeOrderMapper.selectOne(new LambdaQueryWrapper<UmsRechargeOrder>().eq(UmsRechargeOrder::getOrderNo, orderNo));
        if (order == null || STATUS_VOID.equals(order.getStatus())) {
            throw new BaseException("单据无效或已撤销");
        }

        UmsMember member = umsMemberMapper.selectById(order.getMemberId());
        if (member == null) throw new BaseException("会员丢失");

        BigDecimal beforeBalance = member.getBalance() != null ? member.getBalance() : BigDecimal.ZERO;
        BigDecimal beforeCoupon = member.getCoupon() != null ? member.getCoupon() : BigDecimal.ZERO;

        // 🌟 核心修复 1：提取真实的退款金额（负数），如果为空则给 0。这是为了修正原来掩耳盗铃写死的 BigDecimal.ZERO
        BigDecimal negateRealAmount = order.getRealAmount() != null ? order.getRealAmount().negate() : BigDecimal.ZERO;

        if (TYPE_BALANCE.equals(order.getType())) {
            int s1 = umsMemberMapper.update(null, new LambdaUpdateWrapper<UmsMember>()
                    .setSql("balance = balance - " + order.getAmount())
                    .eq(UmsMember::getId, member.getId())
                    .ge(UmsMember::getBalance, order.getAmount()));
            if (s1 == 0) throw new BaseException("撤销失败：余额不足扣回");

            // 🌟 核心修复 2：真实记录退给客人的负数现金 negateRealAmount
            umsMemberLogMapper.insert(createLog(member, TYPE_BALANCE, "REVERSAL", order.getAmount().negate(), negateRealAmount, beforeBalance.subtract(order.getAmount()), orderNo, "【充值撤销】" + reason));

            if (order.getGiftCoupon().compareTo(BigDecimal.ZERO) > 0) {
                int s2 = umsMemberMapper.update(null, new LambdaUpdateWrapper<UmsMember>()
                        .setSql("coupon = coupon - " + order.getGiftCoupon())
                        .eq(UmsMember::getId, member.getId())
                        .ge(UmsMember::getCoupon, order.getGiftCoupon()));
                if (s2 == 0) throw new BaseException("撤销失败：赠送券已被消耗");
                // 赠送的金额本身没收现金，所以这里扣回的实收依然是 ZERO
                umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "REVERSAL", order.getGiftCoupon().negate(), BigDecimal.ZERO, beforeCoupon.subtract(order.getGiftCoupon()), orderNo, "【红冲赠送扣回】"));
            }
        } else if (TYPE_COUPON.equals(order.getType())) {
            int s3 = umsMemberMapper.update(null, new LambdaUpdateWrapper<UmsMember>()
                    .setSql("coupon = coupon - " + order.getAmount())
                    .eq(UmsMember::getId, member.getId())
                    .ge(UmsMember::getCoupon, order.getAmount()));
            if (s3 == 0) throw new BaseException("撤销失败：券额已被消耗");

            // 🌟 核心修复 3：应用 negateRealAmount
            umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "REVERSAL", order.getAmount().negate(), negateRealAmount, beforeCoupon.subtract(order.getAmount()), orderNo, "【充值撤销】" + reason));
        } else if (TYPE_VOUCHER.equals(order.getType())) {
            posMemberCouponMapper.delete(new LambdaQueryWrapper<PosMemberCoupon>()
                    .eq(PosMemberCoupon::getMemberId, member.getId())
                    .eq(PosMemberCoupon::getStatus, STATUS_UNUSED)
                    .last("LIMIT " + order.getAmount().intValue()));

            // 🌟 核心修复 4：应用 negateRealAmount
            umsMemberLogMapper.insert(createLog(member, TYPE_VOUCHER, "REVERSAL", order.getAmount().negate(), negateRealAmount, BigDecimal.ZERO, orderNo, "【发券撤销】"));
        }

        order.setStatus(STATUS_VOID);
        order.setRemark(order.getRemark() + " | 撤销原因：" + reason);
        umsRechargeOrderMapper.updateById(order);
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