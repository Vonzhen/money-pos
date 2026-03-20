package com.money.controller;

import com.money.dto.OmsOrder.OmsOrderVO;
import com.money.dto.pos.PosGoodsVO;
import com.money.dto.pos.PosMemberVO;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.dto.pos.SettleResultVO;
import com.money.dto.pos.SettleTrialReqDTO;
import com.money.dto.pos.PricingResult; // 🌟 引入全新的标准契约对象
import com.money.service.PosService;
import com.money.service.impl.PosCalculationEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "pos", description = "收银")
@RestController
@RequestMapping("/pos")
@RequiredArgsConstructor
public class PosController {

    private final PosService posService;
    private final PosCalculationEngine posCalculationEngine;

    @Operation(summary = "商品列表")
    @GetMapping("/goods")
    @PreAuthorize("@rbac.hasPermission('pos:cashier')")
    public List<PosGoodsVO> listGoods(String barcode) {
        return posService.listGoods(barcode);
    }

    @Operation(summary = "会员列表")
    @GetMapping("/members")
    @PreAuthorize("@rbac.hasPermission('pos:cashier')")
    public List<PosMemberVO> listMember(String member) {
        return posService.listMember(member);
    }

    @Operation(summary = "收款 (正式落库)")
    @PostMapping("/settleAccounts")
    @PreAuthorize("@rbac.hasPermission('pos:cashier')")
    public SettleResultVO settleAccounts(@Validated @RequestBody SettleAccountsDTO settleAccountsDTO) {
        return posService.settleAccounts(settleAccountsDTO);
    }

    @Operation(summary = "收银台实时试算 (不落库/防抖调用)")
    @PostMapping("/trial")
    @PreAuthorize("@rbac.hasPermission('pos:cashier')")
    // 🌟 核心修改：将返回值从 SettleTrialResVO 替换为 PricingResult
    public PricingResult trialCalculate(@Validated @RequestBody SettleTrialReqDTO req) {
        return posCalculationEngine.calculate(req);
    }

}