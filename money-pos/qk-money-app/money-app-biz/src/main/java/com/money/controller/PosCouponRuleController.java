package com.money.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.money.entity.PosCouponRule;
import com.money.entity.PosMemberCoupon;
import com.money.mapper.PosCouponRuleMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.web.vo.PageVO;
import com.money.util.PageUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "posCouponRule", description = "满减券规则配置")
@RestController
@RequestMapping("/pos/couponRule")
@RequiredArgsConstructor
public class PosCouponRuleController {

    private final PosCouponRuleMapper posCouponRuleMapper;
    // 【新增】引入会员卡包底层管家
    private final PosMemberCouponMapper posMemberCouponMapper;

    @GetMapping
    public PageVO<PosCouponRule> list(@RequestParam(defaultValue = "1") Integer current,
                                      @RequestParam(defaultValue = "10") Integer size,
                                      String name) {
        LambdaQueryWrapper<PosCouponRule> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(name), PosCouponRule::getName, name)
                .orderByDesc(PosCouponRule::getCreateTime);
        Page<PosCouponRule> pageResult = posCouponRuleMapper.selectPage(new Page<>(current, size), queryWrapper);
        return PageUtil.toPageVO(pageResult, entity -> entity);
    }

    @PostMapping
    public void add(@RequestBody PosCouponRule rule) {
        posCouponRuleMapper.insert(rule);
    }

    @PutMapping
    public void update(@RequestBody PosCouponRule rule) {
        posCouponRuleMapper.updateById(rule);
    }

    @DeleteMapping
    public void delete(@RequestBody List<Long> ids) {
        posCouponRuleMapper.deleteBatchIds(ids);
    }

    // ==================== 【全新核心：供收银台查询顾客可用卡包】 ====================
    @GetMapping("/memberCoupons/{memberId}")
    public List<Map<String, Object>> getMemberCoupons(@PathVariable("memberId") Long memberId) {
        // 1. 查出该会员所有状态为“未使用”的满减券
        LambdaQueryWrapper<PosMemberCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PosMemberCoupon::getMemberId, memberId)
                .eq(PosMemberCoupon::getStatus, "UNUSED");
        List<PosMemberCoupon> coupons = posMemberCouponMapper.selectList(wrapper);

        // 如果口袋空空，直接返回空列表
        if (coupons == null || coupons.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 智能统计：每种规则(ruleId)具体拥有几张
        Map<Long, Long> ruleCountMap = coupons.stream()
                .collect(Collectors.groupingBy(PosMemberCoupon::getRuleId, Collectors.counting()));

        // 3. 查出这些券的具体门槛和减免金额，打包返回给前台收银机
        List<PosCouponRule> rules = posCouponRuleMapper.selectBatchIds(ruleCountMap.keySet());
        List<Map<String, Object>> result = new ArrayList<>();

        for (PosCouponRule rule : rules) {
            Map<String, Object> map = new HashMap<>();
            map.put("ruleId", rule.getId());
            map.put("name", rule.getName());
            map.put("thresholdAmount", rule.getThresholdAmount());
            map.put("discountAmount", rule.getDiscountAmount());
            map.put("ownedCount", ruleCountMap.get(rule.getId())); // 这个就是顾客拥有的真实张数
            result.add(map);
        }
        return result;
    }
}