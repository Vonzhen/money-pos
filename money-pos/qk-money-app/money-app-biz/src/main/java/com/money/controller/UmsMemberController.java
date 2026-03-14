package com.money.controller;

import com.money.web.dto.ValidGroup;
import com.money.web.vo.PageVO;
import com.money.dto.UmsMember.UmsMemberDTO;
import com.money.dto.UmsMember.UmsMemberQueryDTO;
import com.money.dto.UmsMember.UmsMemberVO;
import com.money.service.UmsMemberService;
import com.money.service.PosService;
import com.money.service.impl.UmsMemberServiceImpl.MemberGoodsRankVO; // 🌟 引入强类型VO
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.alibaba.excel.EasyExcel;
import com.money.dto.UmsMember.UmsMemberImportExcelDTO;

@Tag(name = "umsMember", description = "会员表")
@RestController
@RequestMapping("/ums/member")
@RequiredArgsConstructor
public class UmsMemberController {

    private final UmsMemberService umsMemberService;
    private final PosService posService;

    @Operation(summary = "分页查询")
    @GetMapping
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public PageVO<UmsMemberVO> list(@Validated UmsMemberQueryDTO queryDTO) {
        return umsMemberService.list(queryDTO);
    }

    // 🌟 修复点：返回类型改为 List<MemberGoodsRankVO>
    @Operation(summary = "获取会员最爱购买的Top10商品")
    @GetMapping("/top10Goods")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public List<MemberGoodsRankVO> top10Goods(@RequestParam Long memberId) {
        return umsMemberService.getTop10Goods(memberId);
    }

    @Operation(summary = "添加")
    @PostMapping
    @PreAuthorize("@rbac.hasPermission('umsMember:add')")
    public void add(@Validated(ValidGroup.Save.class) @RequestBody UmsMemberDTO addDTO) {
        umsMemberService.add(addDTO);
    }

    @Operation(summary = "修改")
    @PutMapping
    @PreAuthorize("@rbac.hasPermission('umsMember:edit')")
    public void update(@Validated(ValidGroup.Update.class) @RequestBody UmsMemberDTO updateDTO) {
        umsMemberService.update(updateDTO);
    }

    @Operation(summary = "删除")
    @DeleteMapping
    @PreAuthorize("@rbac.hasPermission('umsMember:del')")
    public void delete(@RequestBody Set<Long> ids) {
        umsMemberService.delete(ids);
    }

    @PostMapping("/recharge")
    @Operation(summary = "会员充值发券业务")
    public void recharge(@RequestBody com.money.dto.Ums.RechargeDTO dto) {
        umsMemberService.recharge(dto);
    }

    @PostMapping("/import")
    @Operation(summary = "从 Excel 批量导入老会员")
    public void importMembers(@RequestPart("file") MultipartFile file) {
        umsMemberService.importMembers(file);
    }

    @GetMapping("/template")
    @Operation(summary = "下载老会员导入模板")
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
    @Operation(summary = "POS搜会员(手机号/会员码/姓名模糊联想)")
    public java.util.List<com.money.dto.pos.PosMemberVO> posSearchMember(@RequestParam String keyword) {
        return posService.listMember(keyword);
    }

    @GetMapping("/coupon-rules")
    @Operation(summary = "获取前台发券的满减券规则下拉列表")
    public java.util.List<com.money.entity.PosCouponRule> getCouponRules() {
        return posService.getValidCouponRules();
    }

    @GetMapping("/dormant")
    @Operation(summary = "沉睡雷达：查询流失会员(按LTV降序)")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public List<UmsMemberVO> dormantList(@RequestParam Integer days) {
        return umsMemberService.getDormantMembers(days);
    }

    @PostMapping("/batch-issue-voucher")
    @Operation(summary = "批量发放满减券 (沉睡唤醒)")
    @PreAuthorize("@rbac.hasPermission('umsMember:edit')")
    public void batchIssueVoucher(@RequestBody com.money.dto.Ums.BatchIssueVoucherDTO dto) {
        umsMemberService.batchIssueVoucher(dto.getMemberIds(), dto.getRuleId(), dto.getQuantity());
    }
}