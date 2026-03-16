package com.money.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.constant.BizErrorStatus;
import com.money.web.exception.BaseException;
import com.money.web.vo.PageVO;
import com.money.dto.UmsMember.UmsMemberDTO;
import com.money.dto.UmsMember.UmsMemberQueryDTO;
import com.money.dto.UmsMember.UmsMemberVO;
import com.money.dto.Ums.RechargeDTO;
import com.money.entity.*;
import com.money.mapper.*;
import com.money.service.UmsMemberService;
import com.money.util.PageUtil;
import com.alibaba.excel.EasyExcel;
import com.money.dto.UmsMember.UmsMemberImportExcelDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UmsMemberServiceImpl extends ServiceImpl<UmsMemberMapper, UmsMember> implements UmsMemberService {

    private final UmsMemberLogMapper umsMemberLogMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderDetailMapper omsOrderDetailMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;
    private final GmsBrandMapper gmsBrandMapper;
    private final UmsRechargeOrderMapper umsRechargeOrderMapper;

    private static final String STATUS_UNUSED = "UNUSED";
    private static final String TYPE_BALANCE = "BALANCE";
    private static final String TYPE_COUPON = "COUPON";
    private static final String TYPE_VOUCHER = "VOUCHER";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_VOID = "VOID";

    @Data
    public static class MemberGoodsRankVO {
        private String goodsName;
        private Integer buyCount;
        public MemberGoodsRankVO(String goodsName, Integer buyCount) {
            this.goodsName = goodsName;
            this.buyCount = buyCount;
        }
    }

    @Override
    public PageVO<UmsMemberVO> list(UmsMemberQueryDTO queryDTO) {
        Page<UmsMember> page = this.lambdaQuery()
                .eq(StrUtil.isNotBlank(queryDTO.getCode()), UmsMember::getCode, queryDTO.getCode())
                .like(StrUtil.isNotBlank(queryDTO.getName()), UmsMember::getName, queryDTO.getName())
                .like(StrUtil.isNotBlank(queryDTO.getPhone()), UmsMember::getPhone, queryDTO.getPhone())
                .eq(StrUtil.isNotBlank(queryDTO.getType()), UmsMember::getType, queryDTO.getType())
                .eq(UmsMember::getDeleted, false)
                .orderByDesc(StrUtil.isBlank(queryDTO.getOrderBy()), UmsMember::getUpdateTime)
                .last(StrUtil.isNotBlank(queryDTO.getOrderBy()), queryDTO.getOrderBySql())
                .page(PageUtil.toPage(queryDTO));

        PageVO<UmsMemberVO> pageVO = PageUtil.toPageVO(page, UmsMemberVO::new);

        if (pageVO.getRecords() != null && !pageVO.getRecords().isEmpty()) {
            List<Long> memberIds = pageVO.getRecords().stream().map(UmsMemberVO::getId).collect(Collectors.toList());

            QueryWrapper<PosMemberCoupon> countQw = new QueryWrapper<>();
            countQw.select("member_id", "COUNT(id) as count")
                    .in("member_id", memberIds)
                    .eq("status", STATUS_UNUSED)
                    .groupBy("member_id");
            List<Map<String, Object>> counts = posMemberCouponMapper.selectMaps(countQw);
            Map<Long, Integer> voucherCountMap = counts.stream().collect(Collectors.toMap(
                    m -> ((Number) m.get("member_id")).longValue(),
                    m -> ((Number) m.get("count")).intValue()
            ));

            List<UmsMemberBrandLevel> allBrandLevels = umsMemberBrandLevelMapper.selectList(
                    new LambdaQueryWrapper<UmsMemberBrandLevel>().in(UmsMemberBrandLevel::getMemberId, memberIds)
            );
            Map<Long, List<UmsMemberBrandLevel>> blMap = allBrandLevels.stream().collect(Collectors.groupingBy(UmsMemberBrandLevel::getMemberId));

            for (UmsMemberVO vo : pageVO.getRecords()) {
                vo.setVoucherCount(voucherCountMap.getOrDefault(vo.getId(), 0));
                List<UmsMemberBrandLevel> levels = blMap.get(vo.getId());
                Map<String, String> levelMap = new HashMap<>();
                if (levels != null) {
                    for (UmsMemberBrandLevel bl : levels) {
                        levelMap.put(bl.getBrand(), bl.getLevelCode());
                    }
                }
                vo.setBrandLevels(levelMap);
            }
        }
        return pageVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(UmsMemberDTO addDTO) {
        boolean exists = this.lambdaQuery().eq(UmsMember::getPhone, addDTO.getPhone()).exists();
        if (exists) {
            throw new BaseException("手机号码已存在");
        }

        UmsMember umsMember = new UmsMember();
        BeanUtil.copyProperties(addDTO, umsMember);
        String newCode;
        do {
            newCode = RandomUtil.randomNumbers(8);
        } while (this.lambdaQuery().eq(UmsMember::getCode, newCode).exists());

        umsMember.setCode(newCode);
        umsMember.setBalance(BigDecimal.ZERO);
        umsMember.setCoupon(BigDecimal.ZERO);
        this.save(umsMember);
        saveBrandLevels(umsMember.getId(), addDTO.getBrandLevels());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UmsMemberDTO updateDTO) {
        UmsMember umsMember = this.getById(updateDTO.getId());
        if (umsMember == null) {
            throw new BaseException(BizErrorStatus.MEMBER_NOT_FOUND, "会员不存在或已被删除");
        }

        boolean exists = this.lambdaQuery()
                .ne(UmsMember::getId, updateDTO.getId())
                .eq(UmsMember::getPhone, updateDTO.getPhone())
                .exists();
        if (exists) {
            throw new BaseException("手机号码已与他人冲突");
        }

        BeanUtil.copyProperties(updateDTO, umsMember);
        this.updateById(umsMember);
        saveBrandLevels(umsMember.getId(), updateDTO.getBrandLevels());
    }

    @Override
    public List<MemberGoodsRankVO> getTop10Goods(Long memberId) {
        // 🌟 V3.0 升级：废弃 Java 内存循环，直接召唤 Mapper 底层聚合，全状态覆盖且自动扣减退货！
        List<Map<String, Object>> rankData = this.baseMapper.getTop10Goods(memberId);

        if (rankData == null || rankData.isEmpty()) {
            return new ArrayList<>();
        }

        return rankData.stream()
                .map(map -> new MemberGoodsRankVO(
                        (String) map.get("goodsName"),
                        ((Number) map.get("buyCount")).intValue()))
                .collect(Collectors.toList());
    }

    @Override
    public List<UmsMemberVO> getDormantMembers(Integer days) {
        if (days == null || days <= 0) throw new BaseException("天数参数异常");
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        List<UmsMember> list = this.lambdaQuery()
                .eq(UmsMember::getDeleted, false)
                .isNotNull(UmsMember::getLastVisitTime)
                .le(UmsMember::getLastVisitTime, threshold)
                .orderByDesc(UmsMember::getConsumeAmount)
                .last("LIMIT 100")
                .list();
        return cn.hutool.core.bean.BeanUtil.copyToList(list, UmsMemberVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchIssueVoucher(List<Long> memberIds, Long ruleId, Integer quantity) {
        if (memberIds == null || memberIds.isEmpty() || ruleId == null || quantity == null || quantity <= 0) {
            throw new BaseException("批量发券参数异常");
        }

        List<UmsMember> existMembers = this.listByIds(memberIds);
        if (existMembers.size() != memberIds.size()) {
            throw new BaseException("部分会员不存在，操作终止");
        }

        LocalDateTime now = LocalDateTime.now();
        for (Long memberId : memberIds) {
            for (int i = 0; i < quantity; i++) {
                PosMemberCoupon pc = new PosMemberCoupon();
                pc.setMemberId(memberId);
                pc.setRuleId(ruleId);
                pc.setStatus(STATUS_UNUSED);
                pc.setGetTime(now);
                posMemberCouponMapper.insert(pc);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        this.lambdaUpdate().set(UmsMember::getDeleted, true).in(UmsMember::getId, ids).update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consume(Long id, BigDecimal amount, BigDecimal couponAmount, String orderNo) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) throw new BaseException("扣款金额不能为负数");

        UmsMember member = this.getById(id);
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

        boolean success = this.update(updateWrapper);
        if (!success && couponAmount != null && couponAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new BaseException(BizErrorStatus.COUPON_NOT_ENOUGH, "扣款失败：券余额不足");
        }

        if (couponAmount != null && couponAmount.compareTo(BigDecimal.ZERO) > 0) {
            umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "CONSUME", couponAmount.negate(), BigDecimal.ZERO, beforeCoupon.subtract(couponAmount), orderNo, "订单自动抵扣会员券"));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductBalance(Long memberId, BigDecimal amount, String orderNo, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        UmsMember member = this.getById(memberId);
        if (member == null) throw new BaseException(BizErrorStatus.MEMBER_NOT_FOUND, "找不到会员信息");

        BigDecimal beforeBalance = member.getBalance() != null ? member.getBalance() : BigDecimal.ZERO;

        boolean success = this.lambdaUpdate()
                .setSql("balance = balance - " + amount)
                .eq(UmsMember::getId, memberId)
                .ge(UmsMember::getBalance, amount)
                .update();

        if (!success) {
            throw new BaseException(BizErrorStatus.BALANCE_INSUFFICIENT, "余额不足");
        }
        umsMemberLogMapper.insert(createLog(member, TYPE_BALANCE, "CONSUME", amount.negate(), BigDecimal.ZERO, beforeBalance.subtract(amount), orderNo, remark));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processReturn(Long id, BigDecimal amount, BigDecimal coupon, boolean increaseCancelTimes, String orderNo) {
        UmsMember member = this.getById(id);
        if (member == null) throw new BaseException("退货时会员不存在");

        BigDecimal beforeCoupon = member.getCoupon() == null ? BigDecimal.ZERO : member.getCoupon();

        if (coupon != null && coupon.compareTo(BigDecimal.ZERO) > 0) {
            umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "REFUND", coupon, BigDecimal.ZERO, beforeCoupon.add(coupon), orderNo, "售后退货返还会员券"));
        }

        boolean success = this.lambdaUpdate()
                .setSql("coupon = coupon + " + (coupon != null ? coupon : BigDecimal.ZERO))
                .setSql("consume_amount = consume_amount - " + (amount != null ? amount : BigDecimal.ZERO))
                .setSql("consume_coupon = consume_coupon - " + (coupon != null ? coupon : BigDecimal.ZERO))
                .setSql(increaseCancelTimes, "cancel_times = cancel_times + 1")
                .eq(UmsMember::getId, id)
                .update();
        if (!success) throw new BaseException("资产退回失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recharge(RechargeDTO dto) {
        UmsMember member = this.getById(dto.getMemberId());
        if (member == null) throw new BaseException("会员不存在");

        LocalDateTime now = LocalDateTime.now();
        String orderNo = "RC" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + RandomUtil.randomNumbers(4);

        UmsRechargeOrder order = new UmsRechargeOrder();
        order.setOrderNo(orderNo);
        order.setMemberId(member.getId());
        order.setType(dto.getType());
        order.setAmount(dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO);
        order.setGiftCoupon(dto.getGiftCoupon() != null ? dto.getGiftCoupon() : BigDecimal.ZERO);
        order.setRealAmount(dto.getRealAmount() != null ? dto.getRealAmount() : BigDecimal.ZERO);
        order.setStatus(STATUS_PAID);
        order.setRemark(dto.getRemark());
        order.setCreateTime(now);
        umsRechargeOrderMapper.insert(order);

        BigDecimal beforeBalance = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();
        BigDecimal beforeCoupon = member.getCoupon() == null ? BigDecimal.ZERO : member.getCoupon();

        if (TYPE_BALANCE.equals(dto.getType())) {
            this.lambdaUpdate().setSql("balance = balance + " + order.getAmount()).eq(UmsMember::getId, member.getId()).update();
            umsMemberLogMapper.insert(createLog(member, TYPE_BALANCE, "RECHARGE", order.getAmount(), order.getRealAmount(), beforeBalance.add(order.getAmount()), orderNo, dto.getRemark()));

            if (order.getGiftCoupon().compareTo(BigDecimal.ZERO) > 0) {
                this.lambdaUpdate().setSql("coupon = coupon + " + order.getGiftCoupon()).eq(UmsMember::getId, member.getId()).update();
                umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "GIFT", order.getGiftCoupon(), BigDecimal.ZERO, beforeCoupon.add(order.getGiftCoupon()), orderNo, "充值赠送券额"));
            }
        } else if (TYPE_COUPON.equals(dto.getType())) {
            this.lambdaUpdate().setSql("coupon = coupon + " + order.getAmount()).eq(UmsMember::getId, member.getId()).update();
            umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "RECHARGE", order.getAmount(), order.getRealAmount(), beforeCoupon.add(order.getAmount()), orderNo, dto.getRemark()));
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
            umsMemberLogMapper.insert(createLog(member, TYPE_VOUCHER, "ISSUE", BigDecimal.valueOf(dto.getQuantity()), BigDecimal.ZERO, BigDecimal.valueOf(total), orderNo, dto.getRemark()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidRecharge(String orderNo, String reason) {
        UmsRechargeOrder order = umsRechargeOrderMapper.selectOne(new LambdaQueryWrapper<UmsRechargeOrder>().eq(UmsRechargeOrder::getOrderNo, orderNo));
        if (order == null || STATUS_VOID.equals(order.getStatus())) {
            throw new BaseException("单据无效或已撤销");
        }

        UmsMember member = this.getById(order.getMemberId());
        if (member == null) throw new BaseException("会员丢失");

        BigDecimal beforeBalance = member.getBalance() != null ? member.getBalance() : BigDecimal.ZERO;
        BigDecimal beforeCoupon = member.getCoupon() != null ? member.getCoupon() : BigDecimal.ZERO;

        if (TYPE_BALANCE.equals(order.getType())) {
            boolean s1 = this.lambdaUpdate()
                    .setSql("balance = balance - " + order.getAmount())
                    .eq(UmsMember::getId, member.getId())
                    .ge(UmsMember::getBalance, order.getAmount())
                    .update();
            if (!s1) throw new BaseException("撤销失败：余额不足扣回");
            umsMemberLogMapper.insert(createLog(member, TYPE_BALANCE, "REVERSAL", order.getAmount().negate(), BigDecimal.ZERO, beforeBalance.subtract(order.getAmount()), orderNo, "【充值撤销】" + reason));

            if (order.getGiftCoupon().compareTo(BigDecimal.ZERO) > 0) {
                boolean s2 = this.lambdaUpdate()
                        .setSql("coupon = coupon - " + order.getGiftCoupon())
                        .eq(UmsMember::getId, member.getId())
                        .ge(UmsMember::getCoupon, order.getGiftCoupon())
                        .update();
                if (!s2) throw new BaseException("撤销失败：赠送券已被消耗");
                umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "REVERSAL", order.getGiftCoupon().negate(), BigDecimal.ZERO, beforeCoupon.subtract(order.getGiftCoupon()), orderNo, "【红冲赠送扣回】"));
            }
        } else if (TYPE_COUPON.equals(order.getType())) {
            boolean s3 = this.lambdaUpdate()
                    .setSql("coupon = coupon - " + order.getAmount())
                    .eq(UmsMember::getId, member.getId())
                    .ge(UmsMember::getCoupon, order.getAmount())
                    .update();
            if (!s3) throw new BaseException("撤销失败：券额已被消耗");
            umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "REVERSAL", order.getAmount().negate(), BigDecimal.ZERO, beforeCoupon.subtract(order.getAmount()), orderNo, "【充值撤销】" + reason));
        } else if (TYPE_VOUCHER.equals(order.getType())) {
            posMemberCouponMapper.delete(new LambdaQueryWrapper<PosMemberCoupon>()
                    .eq(PosMemberCoupon::getMemberId, member.getId())
                    .eq(PosMemberCoupon::getStatus, STATUS_UNUSED)
                    .last("LIMIT " + order.getAmount().intValue()));
            umsMemberLogMapper.insert(createLog(member, TYPE_VOUCHER, "REVERSAL", order.getAmount().negate(), BigDecimal.ZERO, BigDecimal.ZERO, orderNo, "【发券撤销】"));
        }

        order.setStatus(STATUS_VOID);
        order.setRemark(order.getRemark() + " | 撤销原因：" + reason);
        umsRechargeOrderMapper.updateById(order);
    }

    @Override
    @SneakyThrows
    @Transactional(rollbackFor = Exception.class)
    public void importMembers(MultipartFile file) {
        List<UmsMemberImportExcelDTO> list = EasyExcel.read(file.getInputStream()).head(UmsMemberImportExcelDTO.class).sheet().doReadSync();
        log.info("接收到 Excel 导入请求，数据量: {}", list.size());
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

    private void saveBrandLevels(Long memberId, Map<String, String> brandLevels) {
        umsMemberBrandLevelMapper.delete(new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, memberId));
        if (brandLevels != null) {
            brandLevels.forEach((b, level) -> {
                if (StrUtil.isNotBlank(level)) {
                    UmsMemberBrandLevel bl = new UmsMemberBrandLevel();
                    bl.setMemberId(memberId);
                    bl.setBrand(b);
                    bl.setLevelCode(level);
                    umsMemberBrandLevelMapper.insert(bl);
                }
            });
        }
    }
}