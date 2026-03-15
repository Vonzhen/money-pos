package com.money.controller;

import com.money.web.dto.ValidGroup;
import com.money.web.vo.PageVO;
import com.money.dto.UmsMember.UmsMemberDTO;
import com.money.dto.UmsMember.UmsMemberQueryDTO;
import com.money.dto.UmsMember.UmsMemberVO;
import com.money.service.UmsMemberService;
import com.money.service.PosService;
import com.money.service.impl.UmsMemberServiceImpl.MemberGoodsRankVO;
import com.money.entity.UmsRechargeOrder;
import com.money.entity.UmsMemberLog;
import com.money.mapper.UmsRechargeOrderMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.web.exception.BaseException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.StrUtil;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.alibaba.excel.EasyExcel;
import com.money.dto.UmsMember.UmsMemberImportExcelDTO;

@Slf4j
@Tag(name = "umsMember", description = "会员管理中心")
@RestController
@RequestMapping("/ums/member")
@RequiredArgsConstructor
public class UmsMemberController {

    private final UmsMemberService umsMemberService;
    private final PosService posService;
    private final UmsRechargeOrderMapper umsRechargeOrderMapper;
    private final UmsMemberLogMapper umsMemberLogMapper;

    @Operation(summary = "分页查询")
    @GetMapping
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public PageVO<UmsMemberVO> list(@Validated UmsMemberQueryDTO queryDTO) {
        return umsMemberService.list(queryDTO);
    }

    @Operation(summary = "获取会员画像商品排行")
    @GetMapping("/top10Goods")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public List<MemberGoodsRankVO> top10Goods(@RequestParam Long memberId) {
        return umsMemberService.getTop10Goods(memberId);
    }

    @Operation(summary = "添加会员")
    @PostMapping
    @PreAuthorize("@rbac.hasPermission('umsMember:add')")
    public void add(@Validated(ValidGroup.Save.class) @RequestBody UmsMemberDTO addDTO) {
        umsMemberService.add(addDTO);
    }

    @Operation(summary = "修改会员")
    @PutMapping
    @PreAuthorize("@rbac.hasPermission('umsMember:edit')")
    public void update(@Validated(ValidGroup.Update.class) @RequestBody UmsMemberDTO updateDTO) {
        umsMemberService.update(updateDTO);
    }

    @Operation(summary = "删除会员")
    @DeleteMapping
    @PreAuthorize("@rbac.hasPermission('umsMember:del')")
    public void delete(@RequestBody Set<Long> ids) {
        umsMemberService.delete(ids);
    }

    @GetMapping("/logs")
    @Operation(summary = "查询会员资产流水")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')") // 🌟 审计权限
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
    @PreAuthorize("@rbac.hasPermission('umsMember:recharge')") // 🌟 充值专有权限
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
    @PreAuthorize("@rbac.hasPermission('umsMember:void')") // 🌟 红冲极高权限
    public void voidRechargeOrder(@RequestBody Map<String, String> params) {
        String orderNo = params.get("orderNo");
        String reason = params.get("reason");
        if (StrUtil.isBlank(orderNo)) throw new BaseException("单号不能为空");
        if (StrUtil.isBlank(reason)) throw new BaseException("红冲原因不能为空");

        umsMemberService.voidRecharge(orderNo, reason);
    }

    @PostMapping("/import")
    @Operation(summary = "从 Excel 批量导入老会员")
    @PreAuthorize("@rbac.hasPermission('umsMember:add')")
    public void importMembers(@RequestPart("file") MultipartFile file) {
        umsMemberService.importMembers(file);
    }

    @GetMapping("/template")
    @Operation(summary = "下载老会员导入模板")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("老会员导入模板", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), UmsMemberImportExcelDTO.class)
                .sheet("导入模板")
                .doWrite(new ArrayList<>());
    }

    @GetMapping("/pos-search")
    @Operation(summary = "POS搜会员")
    public java.util.List<com.money.dto.pos.PosMemberVO> posSearchMember(@RequestParam String keyword) {
        return posService.listMember(keyword);
    }

    @GetMapping("/coupon-rules")
    @Operation(summary = "获取满减券规则")
    public java.util.List<com.money.entity.PosCouponRule> getCouponRules() {
        return posService.getValidCouponRules();
    }

    @GetMapping("/dormant")
    @Operation(summary = "查询沉睡会员")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public List<UmsMemberVO> dormantList(@RequestParam Integer days) {
        return umsMemberService.getDormantMembers(days);
    }

    @PostMapping("/batch-issue-voucher")
    @Operation(summary = "批量发放满减券")
    @PreAuthorize("@rbac.hasPermission('umsMember:edit')")
    public void batchIssueVoucher(@RequestBody com.money.dto.Ums.BatchIssueVoucherDTO dto) {
        umsMemberService.batchIssueVoucher(dto.getMemberIds(), dto.getRuleId(), dto.getQuantity());
    }
}