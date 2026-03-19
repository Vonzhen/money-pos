package com.money.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.GmsBrand;
import com.money.entity.SysDictDetail;
import com.money.mapper.GmsBrandMapper;
import com.money.mapper.SysDictDetailMapper;
import com.money.service.UmsMemberService;
import com.money.util.ExcelDropDownHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@Slf4j
@Tag(name = "umsMemberImport", description = "会员运营导入中心")
@RestController
@RequestMapping("/ums/member")
@RequiredArgsConstructor
public class UmsMemberImportController {

    private final UmsMemberService umsMemberService;
    private final GmsBrandMapper gmsBrandMapper;
    private final SysDictDetailMapper sysDictDetailMapper;

    @PostMapping("/import")
    @Operation(summary = "从 Excel 批量导入老会员")
    @PreAuthorize("@rbac.hasPermission('umsMember:add')")
    public String importMembers(@RequestPart("file") MultipartFile file) {
        return umsMemberService.importMembers(file); // 🌟 返回这句战报
    }

    @PostMapping("/batch-issue-voucher")
    @Operation(summary = "批量发放满减券")
    @PreAuthorize("@rbac.hasPermission('umsMember:edit')")
    public void batchIssueVoucher(@RequestBody com.money.dto.Ums.BatchIssueVoucherDTO dto) {
        umsMemberService.batchIssueVoucher(dto.getMemberIds(), dto.getRuleId(), dto.getQuantity());
    }

    @GetMapping("/template")
    @Operation(summary = "下载智能老会员导入模板 (动态品牌列+下拉框)")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {

        List<List<String>> heads = new ArrayList<>();
        heads.add(Arrays.asList("*会员姓名(必填)"));
        heads.add(Arrays.asList("*手机号(必填11位)"));
        heads.add(Arrays.asList("初始会员余额(本金)"));
        heads.add(Arrays.asList("初始会员券(赠送)"));
        heads.add(Arrays.asList("初始满减券(张数)"));

        List<GmsBrand> brands = gmsBrandMapper.selectList(new LambdaQueryWrapper<GmsBrand>());
        List<SysDictDetail> dictList = sysDictDetailMapper.selectList(
                new LambdaQueryWrapper<SysDictDetail>()
                        .eq(SysDictDetail::getDict, "memberType")
                        .ne(SysDictDetail::getValue, "MEMBER")
        );

        String[] levelOptions = dictList.stream().map(SysDictDetail::getCnDesc).toArray(String[]::new);

        Map<Integer, String[]> dropDownConfig = new HashMap<>();
        int colIndex = heads.size();

        if (brands != null) {
            for (GmsBrand brand : brands) {
                heads.add(Arrays.asList("[品牌特权] " + brand.getName()));
                if (levelOptions.length > 0) {
                    dropDownConfig.put(colIndex, levelOptions);
                }
                colIndex++;
            }
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = java.net.URLEncoder.encode("智能会员导入模板", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        List<List<Object>> demoData = new ArrayList<>();
        List<Object> demoRow = new ArrayList<>(Arrays.asList("张老板", "13800138000", "500.00", "50.00", "2"));
        if (brands != null) {
            for (int i = 0; i < brands.size(); i++) { demoRow.add(""); }
        }
        demoData.add(demoRow);

        EasyExcel.write(response.getOutputStream())
                .head(heads)
                .registerWriteHandler(new ExcelDropDownHandler(dropDownConfig))
                .sheet("会员数据填写区")
                .doWrite(demoData);
    }
}