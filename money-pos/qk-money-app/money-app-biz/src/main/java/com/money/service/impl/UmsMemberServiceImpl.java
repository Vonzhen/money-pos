package com.money.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.dto.Ums.RechargeDTO;
import com.money.dto.UmsMember.UmsMemberDTO;
import com.money.dto.UmsMember.UmsMemberQueryDTO;
import com.money.dto.UmsMember.UmsMemberVO;
import com.money.entity.UmsMember;
import com.money.mapper.UmsMemberMapper;
import com.money.service.*;
import com.money.web.vo.PageVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 会员领域 核心门面枢纽 (Facade / Application Orchestrator)
 * 🌟 极致解耦：只保留向四大专职子域转发指令的能力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UmsMemberServiceImpl extends ServiceImpl<UmsMemberMapper, UmsMember> implements UmsMemberService {

    // ==========================================
    // 🌟 核心修复：把误删的内部类补回来！
    // ==========================================
    @Data
    public static class MemberGoodsRankVO {
        private String goodsName;
        private Integer buyCount;
        public MemberGoodsRankVO(String goodsName, Integer buyCount) {
            this.goodsName = goodsName;
            this.buyCount = buyCount;
        }
    }

    // 🌟 注入刚拆分完毕的 4 大核心子域
    private final UmsMemberProfileService memberProfileService;
    private final UmsMemberAssetService memberAssetService;
    private final UmsMemberRechargeService memberRechargeService;
    private final UmsMemberImportService memberImportService;

    // ==========================================
    // 1. 会员档案与查询域
    // ==========================================
    @Override
    public PageVO<UmsMemberVO> list(UmsMemberQueryDTO queryDTO) {
        return memberProfileService.list(queryDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(UmsMemberDTO addDTO) {
        memberProfileService.add(addDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UmsMemberDTO updateDTO) {
        memberProfileService.update(updateDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Set<Long> ids) {
        memberProfileService.delete(ids);
    }

    @Override
    public List<MemberGoodsRankVO> getTop20Goods(Long memberId) {
        return memberProfileService.getTop20Goods(memberId);
    }

    @Override
    public List<UmsMemberVO> getDormantMembers(Integer days) {
        return memberProfileService.getDormantMembers(days);
    }

    // ==========================================
    // 2. 会员资产域 (消费、扣款、退货)
    // ==========================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consume(Long id, BigDecimal amount, BigDecimal couponAmount, String orderNo) {
        memberAssetService.consume(id, amount, couponAmount, orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductBalance(Long memberId, BigDecimal amount, String orderNo, String remark) {
        memberAssetService.deductBalance(memberId, amount, orderNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processReturn(Long id, BigDecimal amount, BigDecimal coupon, boolean increaseCancelTimes, String orderNo) {
        memberAssetService.processReturn(id, amount, coupon, increaseCancelTimes, orderNo);
    }

    // ==========================================
    // 3. 会员充值域
    // ==========================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recharge(RechargeDTO dto) {
        memberRechargeService.recharge(dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidRecharge(String orderNo, String reason) {
        memberRechargeService.voidRecharge(orderNo, reason);
    }

    // ==========================================
    // 4. 会员导入与发券运营域
    // ==========================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchIssueVoucher(List<Long> memberIds, Long ruleId, Integer quantity) {
        memberImportService.batchIssueVoucher(memberIds, ruleId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importMembers(MultipartFile file) {
        return memberImportService.importMembers(file); // 🌟 加上 return
    }
}