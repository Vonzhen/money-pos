package com.money.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.CouponStatusEnum;
import com.money.dto.pos.*;
import com.money.entity.*;
import com.money.mapper.*;
import com.money.service.*;
import com.money.service.checkout.CheckoutOrchestrator;
import com.money.web.util.BeanMapUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {

    // 🌟 原有查询所需的依赖 (只保留查数据用的)
    private final UmsMemberService umsMemberService;
    private final GmsGoodsService gmsGoodsService;
    private final PosSkuLevelPriceMapper posSkuLevelPriceMapper;
    private final PosCouponRuleMapper posCouponRuleMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;

    // 🌟 核心新增：引入刚刚建好的车间主任
    private final CheckoutOrchestrator checkoutOrchestrator;

    // ==========================================
    // 基础查询模块 (保持不变，现在它专心做查询)
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
    // 🌟 终极交响乐指挥：settleAccounts (解耦后)
    // ==========================================
    @Override
    public SettleResultVO settleAccounts(SettleAccountsDTO dto) {

        // 🌟 彻底剥夺实权：PosServiceImpl 不再包含任何复杂的业务流转逻辑
        // 就像收银前台，只负责接收前端请求，然后原封不动地交给背后的流水线处理，最后把小票还给顾客。

        return checkoutOrchestrator.orchestrate(dto);

    }
}