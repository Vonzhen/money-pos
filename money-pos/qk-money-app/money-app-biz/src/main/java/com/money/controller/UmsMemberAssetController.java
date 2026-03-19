package com.money.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.UmsMemberLog;
import com.money.entity.UmsRechargeOrder;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsRechargeOrderMapper;
import com.money.service.UmsMemberService;
import com.money.web.exception.BaseException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "umsMemberAsset", description = "会员资产与充值中心")
@RestController
@RequestMapping("/ums/member")
@RequiredArgsConstructor
public class UmsMemberAssetController {

    private final UmsMemberService umsMemberService;
    private final UmsRechargeOrderMapper umsRechargeOrderMapper;
    private final UmsMemberLogMapper umsMemberLogMapper;

    @GetMapping("/logs")
    @Operation(summary = "查询会员资产流水")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public List<UmsMemberLog> getMemberLogs(@RequestParam Long memberId) {
        return umsMemberLogMapper.selectList(
                new LambdaQueryWrapper<UmsMemberLog>()
                        .eq(UmsMemberLog::getMemberId, memberId)
                        .orderByDesc(UmsMemberLog::getCreateTime)
                        .last("LIMIT 100")
        );
    }

    @PostMapping("/recharge")
    @Operation(summary = "会员充值业务")
    @PreAuthorize("@rbac.hasPermission('umsMember:recharge')")
    public void recharge(@RequestBody com.money.dto.Ums.RechargeDTO dto) {
        umsMemberService.recharge(dto);
    }

    @Operation(summary = "根据单号获取充值订单详情")
    @GetMapping("/recharge/order/{orderNo}")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public UmsRechargeOrder getRechargeOrderDetail(@PathVariable String orderNo) {
        if (StrUtil.isBlank(orderNo)) throw new BaseException("单号不能为空");

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

    @Operation(summary = "执行红冲撤销")
    @PostMapping("/recharge/void")
    @PreAuthorize("@rbac.hasPermission('umsMember:void')")
    public void voidRechargeOrder(@RequestBody Map<String, String> params) {
        String orderNo = params.get("orderNo");
        String reason = params.get("reason");
        if (StrUtil.isBlank(orderNo)) throw new BaseException("单号不能为空");
        if (StrUtil.isBlank(reason)) throw new BaseException("红冲原因不能为空");

        umsMemberService.voidRecharge(orderNo, reason);
    }
}