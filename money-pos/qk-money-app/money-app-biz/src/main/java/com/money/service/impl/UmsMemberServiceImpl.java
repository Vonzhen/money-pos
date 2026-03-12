package com.money.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.web.exception.BaseException;
import com.money.web.vo.PageVO;
import com.money.dto.UmsMember.UmsMemberDTO;
import com.money.dto.UmsMember.UmsMemberQueryDTO;
import com.money.dto.UmsMember.UmsMemberVO;
import com.money.dto.Ums.RechargeDTO;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.entity.PosMemberCoupon;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderDetail;
import com.money.entity.UmsMemberBrandLevel;
import com.money.entity.GmsBrand;
import com.money.mapper.UmsMemberMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.mapper.GmsBrandMapper;
import com.money.service.UmsMemberService;
import com.money.util.PageUtil;
import com.alibaba.excel.EasyExcel;
import com.money.dto.UmsMember.UmsMemberImportExcelDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class UmsMemberServiceImpl extends ServiceImpl<UmsMemberMapper, UmsMember> implements UmsMemberService {

    private final UmsMemberLogMapper umsMemberLogMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderDetailMapper omsOrderDetailMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;
    private final GmsBrandMapper gmsBrandMapper;

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

            List<UmsMemberBrandLevel> allBrandLevels = umsMemberBrandLevelMapper.selectList(
                    new LambdaQueryWrapper<UmsMemberBrandLevel>().in(UmsMemberBrandLevel::getMemberId, memberIds)
            );
            Map<Long, List<UmsMemberBrandLevel>> blMap = allBrandLevels.stream().collect(Collectors.groupingBy(UmsMemberBrandLevel::getMemberId));

            for (UmsMemberVO vo : pageVO.getRecords()) {
                long count = posMemberCouponMapper.selectCount(
                        new LambdaQueryWrapper<PosMemberCoupon>()
                                .eq(PosMemberCoupon::getMemberId, vo.getId())
                                .eq(PosMemberCoupon::getStatus, "UNUSED")
                );
                vo.setVoucherCount((int) count);

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

    private void saveBrandLevels(Long memberId, Map<String, String> brandLevels) {
        umsMemberBrandLevelMapper.delete(new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, memberId));
        if (brandLevels != null && !brandLevels.isEmpty()) {
            brandLevels.forEach((brand, levelCode) -> {
                if (StrUtil.isNotBlank(levelCode)) {
                    UmsMemberBrandLevel bl = new UmsMemberBrandLevel();
                    bl.setMemberId(memberId);
                    bl.setBrand(brand);
                    bl.setLevelCode(levelCode);
                    umsMemberBrandLevelMapper.insert(bl);
                }
            });
        }
    }

    @Override
    public void add(UmsMemberDTO addDTO) {
        boolean exists = this.lambdaQuery().eq(UmsMember::getPhone, addDTO.getPhone()).exists();
        if (exists) throw new BaseException("手机号码已存在");
        UmsMember umsMember = new UmsMember();
        BeanUtil.copyProperties(addDTO, umsMember);
        umsMember.setCode(RandomUtil.randomNumbers(8));
        umsMember.setBalance(BigDecimal.ZERO);
        umsMember.setCoupon(BigDecimal.ZERO);
        this.save(umsMember);
        saveBrandLevels(umsMember.getId(), addDTO.getBrandLevels());
    }

    @Override
    public void update(UmsMemberDTO updateDTO) {
        boolean exists = this.lambdaQuery().ne(UmsMember::getId, updateDTO.getId()).eq(UmsMember::getPhone, updateDTO.getPhone()).exists();
        if (exists) throw new BaseException("手机号码已存在");
        UmsMember umsMember = this.getById(updateDTO.getId());
        BeanUtil.copyProperties(updateDTO, umsMember);
        this.updateById(umsMember);
        saveBrandLevels(umsMember.getId(), updateDTO.getBrandLevels());
    }

    @Override
    public List<MemberGoodsRankVO> getTop10Goods(Long memberId) {
        List<OmsOrder> orders = omsOrderMapper.selectList(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getMemberId, memberId).eq(OmsOrder::getStatus, "PAID"));
        if (orders.isEmpty()) return new ArrayList<>();
        List<String> orderNos = orders.stream().map(OmsOrder::getOrderNo).collect(Collectors.toList());
        List<OmsOrderDetail> details = omsOrderDetailMapper.selectList(new LambdaQueryWrapper<OmsOrderDetail>().in(OmsOrderDetail::getOrderNo, orderNos));
        Map<String, Integer> goodsCountMap = new HashMap<>();
        for (OmsOrderDetail detail : details) {
            int validQty = detail.getQuantity() - (detail.getReturnQuantity() != null ? detail.getReturnQuantity() : 0);
            if (validQty > 0) goodsCountMap.put(detail.getGoodsName(), goodsCountMap.getOrDefault(detail.getGoodsName(), 0) + validQty);
        }

        return goodsCountMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(20) // 🌟 已确认修改为 20
                .map(e -> new MemberGoodsRankVO(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public List<UmsMemberVO> getDormantMembers(Integer days) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        List<UmsMember> list = this.lambdaQuery().eq(UmsMember::getDeleted, false).isNotNull(UmsMember::getLastVisitTime)
                .le(UmsMember::getLastVisitTime, threshold).orderByDesc(UmsMember::getConsumeAmount).last("LIMIT 100").list();
        return cn.hutool.core.bean.BeanUtil.copyToList(list, UmsMemberVO.class);
    }

    @Override
    public void batchIssueVoucher(List<Long> memberIds, Long ruleId, Integer quantity) {
        if (memberIds == null || memberIds.isEmpty() || ruleId == null || quantity == null || quantity <= 0) throw new BaseException("发券参数错误");
        LocalDateTime now = LocalDateTime.now();

        List<PosMemberCoupon> coupons = new ArrayList<>(memberIds.size() * quantity);
        for (Long memberId : memberIds) {
            for (int i = 0; i < quantity; i++) {
                PosMemberCoupon pc = new PosMemberCoupon();
                pc.setMemberId(memberId);
                pc.setRuleId(ruleId);
                pc.setStatus("UNUSED");
                pc.setGetTime(now);
                coupons.add(pc);
            }
        }
        for (PosMemberCoupon coupon : coupons) posMemberCouponMapper.insert(coupon);

        List<PosMemberCoupon> allUnused = posMemberCouponMapper.selectList(
                new LambdaQueryWrapper<PosMemberCoupon>()
                        .in(PosMemberCoupon::getMemberId, memberIds)
                        .eq(PosMemberCoupon::getStatus, "UNUSED")
                        .select(PosMemberCoupon::getMemberId)
        );
        Map<Long, Long> memberVoucherCountMap = allUnused.stream()
                .collect(Collectors.groupingBy(PosMemberCoupon::getMemberId, Collectors.counting()));

        // 批量查询会员信息，用于快照
        Map<Long, UmsMember> memberMap = this.listByIds(memberIds).stream().collect(Collectors.toMap(UmsMember::getId, m -> m));

        for (Long memberId : memberIds) {
            long totalVouchers = memberVoucherCountMap.getOrDefault(memberId, 0L);
            UmsMember member = memberMap.get(memberId);

            UmsMemberLog log = new UmsMemberLog();
            log.setMemberId(memberId);
            if (member != null) {
                log.setMemberName(member.getName());
                log.setMemberPhone(member.getPhone());
            }
            log.setType("VOUCHER");
            log.setOperateType("ISSUE");
            log.setAmount(BigDecimal.valueOf(quantity));
            log.setAfterAmount(BigDecimal.valueOf(totalVouchers));
            log.setRemark("沉睡唤醒：系统批量派发专属满减券");
            log.setCreateTime(now);
            umsMemberLogMapper.insert(log);
        }
    }

    @Override
    public void delete(Set<Long> ids) {
        this.lambdaUpdate().set(UmsMember::getDeleted, true).in(UmsMember::getId, ids).update();
    }

    @Override
    public void consume(Long id, BigDecimal amount, BigDecimal coupon) {
        this.lambdaUpdate()
                .setSql("consume_amount = consume_amount + " + amount)
                .setSql("consume_coupon = consume_coupon + " + coupon)
                .setSql("coupon = coupon - " + coupon)
                .setSql("consume_times = consume_times + 1")
                .eq(UmsMember::getId, id).update();
    }

    @Override
    public void processReturn(Long id, BigDecimal amount, BigDecimal coupon, boolean increaseCancelTimes, String orderNo) {
        UmsMember member = this.getById(id);
        if (member == null) return;

        LocalDateTime now = LocalDateTime.now();
        BigDecimal currentCoupon = member.getCoupon() == null ? BigDecimal.ZERO : member.getCoupon();

        if (coupon != null && coupon.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal afterCoupon = currentCoupon.add(coupon);
            UmsMemberLog couponLog = new UmsMemberLog();
            couponLog.setMemberId(id);
            couponLog.setMemberName(member.getName());
            couponLog.setMemberPhone(member.getPhone());
            couponLog.setType("COUPON");
            couponLog.setOperateType("REFUND");
            couponLog.setAmount(coupon);
            couponLog.setAfterAmount(afterCoupon);
            couponLog.setRemark("售后退货：原路返还关联品牌会员券");
            couponLog.setOrderNo(orderNo);
            couponLog.setCreateTime(now);
            umsMemberLogMapper.insert(couponLog);

            member.setCoupon(afterCoupon);
        }

        this.lambdaUpdate()
                .set(UmsMember::getCoupon, member.getCoupon())
                .setSql("consume_amount = consume_amount - " + (amount != null ? amount : BigDecimal.ZERO))
                .setSql("consume_coupon = consume_coupon - " + (coupon != null ? coupon : BigDecimal.ZERO))
                .setSql(increaseCancelTimes, "cancel_times = cancel_times + 1")
                .eq(UmsMember::getId, id)
                .update();
    }

    @Override
    public void recharge(RechargeDTO dto) {
        UmsMember member = this.getById(dto.getMemberId());
        if (member == null) throw new BaseException("未找到该会员信息");
        if (member.getBalance() == null) member.setBalance(BigDecimal.ZERO);
        if (member.getCoupon() == null) member.setCoupon(BigDecimal.ZERO);
        LocalDateTime now = LocalDateTime.now();

        if ("BALANCE".equals(dto.getType())) {
            member.setBalance(member.getBalance().add(dto.getAmount()));
            UmsMemberLog log = new UmsMemberLog();
            log.setMemberId(member.getId());
            log.setMemberName(member.getName());
            log.setMemberPhone(member.getPhone());
            log.setType("BALANCE");
            log.setOperateType("RECHARGE");
            log.setAmount(dto.getAmount());
            log.setRealAmount(dto.getRealAmount() != null ? dto.getRealAmount() : dto.getAmount());
            log.setAfterAmount(member.getBalance());
            log.setRemark(StrUtil.isNotBlank(dto.getRemark()) ? dto.getRemark() : "前台办理充值会员余额");
            log.setCreateTime(now);
            umsMemberLogMapper.insert(log);

            if (dto.getGiftCoupon() != null && dto.getGiftCoupon().compareTo(BigDecimal.ZERO) > 0) {
                member.setCoupon(member.getCoupon().add(dto.getGiftCoupon()));
                UmsMemberLog giftLog = new UmsMemberLog();
                giftLog.setMemberId(member.getId());
                giftLog.setMemberName(member.getName());
                giftLog.setMemberPhone(member.getPhone());
                giftLog.setType("COUPON");
                giftLog.setOperateType("GIFT");
                giftLog.setAmount(dto.getGiftCoupon());
                giftLog.setRealAmount(BigDecimal.ZERO);
                giftLog.setAfterAmount(member.getCoupon());
                giftLog.setRemark("充值附送会员券");
                giftLog.setCreateTime(now);
                umsMemberLogMapper.insert(giftLog);
            }
            this.updateById(member);

        } else if ("COUPON".equals(dto.getType())) {
            member.setCoupon(member.getCoupon().add(dto.getAmount()));
            UmsMemberLog log = new UmsMemberLog();
            log.setMemberId(member.getId());
            log.setMemberName(member.getName());
            log.setMemberPhone(member.getPhone());
            log.setType("COUPON");
            log.setOperateType("RECHARGE");
            log.setAmount(dto.getAmount());
            log.setRealAmount(dto.getRealAmount());
            log.setAfterAmount(member.getCoupon());
            log.setRemark(StrUtil.isNotBlank(dto.getRemark()) ? dto.getRemark() : "前台直充会员券");
            log.setCreateTime(now);
            umsMemberLogMapper.insert(log);
            this.updateById(member);

        } else if ("VOUCHER".equals(dto.getType())) {
            if (dto.getRuleId() == null || dto.getQuantity() == null || dto.getQuantity() <= 0) throw new BaseException("发券参数错误");
            List<PosMemberCoupon> coupons = new ArrayList<>();
            for (int i = 0; i < dto.getQuantity(); i++) {
                PosMemberCoupon pc = new PosMemberCoupon(); pc.setMemberId(member.getId()); pc.setRuleId(dto.getRuleId()); pc.setStatus("UNUSED"); pc.setGetTime(now); coupons.add(pc);
            }
            for (PosMemberCoupon coupon : coupons) posMemberCouponMapper.insert(coupon);
            long totalVouchers = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>().eq(PosMemberCoupon::getMemberId, member.getId()).eq(PosMemberCoupon::getStatus, "UNUSED"));

            UmsMemberLog log = new UmsMemberLog();
            log.setMemberId(member.getId());
            log.setMemberName(member.getName());
            log.setMemberPhone(member.getPhone());
            log.setType("VOUCHER");
            log.setOperateType("ISSUE");
            log.setAmount(BigDecimal.valueOf(dto.getQuantity()));
            log.setAfterAmount(BigDecimal.valueOf(totalVouchers));
            log.setRemark(StrUtil.isNotBlank(dto.getRemark()) ? dto.getRemark() : "前台发放满减券");
            log.setCreateTime(now);
            umsMemberLogMapper.insert(log);
        }
    }

    @Override
    @SneakyThrows
    public void importMembers(MultipartFile file) {
        List<UmsMemberImportExcelDTO> list = EasyExcel.read(file.getInputStream()).head(UmsMemberImportExcelDTO.class).sheet().doReadSync();
        if (list.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();

        List<GmsBrand> brandList = gmsBrandMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, String> brandNameToIdMap = brandList.stream().collect(Collectors.toMap(GmsBrand::getName, b -> String.valueOf(b.getId())));

        List<String> phones = list.stream().map(UmsMemberImportExcelDTO::getPhone).filter(StrUtil::isNotBlank).collect(Collectors.toList());
        Map<String, UmsMember> existingMemberMap = new HashMap<>();
        if (!phones.isEmpty()) {
            List<UmsMember> existingMembers = this.lambdaQuery().in(UmsMember::getPhone, phones).list();
            existingMemberMap = existingMembers.stream().collect(Collectors.toMap(UmsMember::getPhone, m -> m));
        }

        for (UmsMemberImportExcelDTO dto : list) {
            if (StrUtil.isBlank(dto.getPhone())) continue;

            UmsMember member = existingMemberMap.get(dto.getPhone());

            if (member != null) {
                if (member.getDeleted()) {
                    member.setDeleted(false);
                    member.setName(dto.getName());
                    member.setType("MEMBER");
                    member.setBalance(dto.getBalance() != null ? dto.getBalance() : BigDecimal.ZERO);
                    member.setCoupon(dto.getCoupon() != null ? dto.getCoupon() : BigDecimal.ZERO);
                    member.setRemark(dto.getRemark());
                    this.updateById(member);

                    if (member.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                        UmsMemberLog log = new UmsMemberLog();
                        log.setMemberId(member.getId());
                        log.setMemberName(member.getName());
                        log.setMemberPhone(member.getPhone());
                        log.setType("BALANCE");
                        log.setOperateType("IMPORT");
                        log.setAmount(member.getBalance());
                        log.setAfterAmount(member.getBalance());
                        log.setRemark("老会员恢复及重新导入会员余额");
                        log.setCreateTime(now);
                        umsMemberLogMapper.insert(log);
                    }
                    if (member.getCoupon().compareTo(BigDecimal.ZERO) > 0) {
                        UmsMemberLog couponLog = new UmsMemberLog();
                        couponLog.setMemberId(member.getId());
                        couponLog.setMemberName(member.getName());
                        couponLog.setMemberPhone(member.getPhone());
                        couponLog.setType("COUPON");
                        couponLog.setOperateType("IMPORT");
                        couponLog.setAmount(member.getCoupon());
                        couponLog.setAfterAmount(member.getCoupon());
                        couponLog.setRemark("老会员恢复及重新导入会员券");
                        couponLog.setCreateTime(now);
                        umsMemberLogMapper.insert(couponLog);
                    }
                }
            } else {
                member = new UmsMember();
                member.setName(dto.getName());
                member.setPhone(dto.getPhone());
                member.setType("MEMBER");
                member.setBalance(dto.getBalance() != null ? dto.getBalance() : BigDecimal.ZERO);
                member.setCoupon(dto.getCoupon() != null ? dto.getCoupon() : BigDecimal.ZERO);
                member.setCode(RandomUtil.randomNumbers(8));
                member.setRemark(dto.getRemark());
                this.save(member); // 保存后即刻获取自增 ID

                if (member.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                    UmsMemberLog log = new UmsMemberLog();
                    log.setMemberId(member.getId());
                    log.setMemberName(member.getName());
                    log.setMemberPhone(member.getPhone());
                    log.setType("BALANCE");
                    log.setOperateType("IMPORT");
                    log.setAmount(member.getBalance());
                    log.setAfterAmount(member.getBalance());
                    log.setRemark("老会员初始会员余额导入");
                    log.setCreateTime(now);
                    umsMemberLogMapper.insert(log);
                }
                if (member.getCoupon().compareTo(BigDecimal.ZERO) > 0) {
                    UmsMemberLog couponLog = new UmsMemberLog();
                    couponLog.setMemberId(member.getId());
                    couponLog.setMemberName(member.getName());
                    couponLog.setMemberPhone(member.getPhone());
                    couponLog.setType("COUPON");
                    couponLog.setOperateType("IMPORT");
                    couponLog.setAmount(member.getCoupon());
                    couponLog.setAfterAmount(member.getCoupon());
                    couponLog.setRemark("老会员初始会员券导入");
                    couponLog.setCreateTime(now);
                    umsMemberLogMapper.insert(couponLog);
                }
            }

            if (StrUtil.isNotBlank(dto.getBrandPrivileges())) {
                Map<String, String> brandLevels = new HashMap<>();
                String[] parts = dto.getBrandPrivileges().split(",");
                for (String part : parts) {
                    String[] kv = part.replace("：", ":").split(":");
                    if (kv.length == 2) {
                        String bName = kv[0].trim();
                        String lCode = kv[1].trim();
                        String bId = brandNameToIdMap.getOrDefault(bName, bName);
                        brandLevels.put(bId, lCode);
                    }
                }
                saveBrandLevels(member.getId(), brandLevels);
            }
        }
    }
}