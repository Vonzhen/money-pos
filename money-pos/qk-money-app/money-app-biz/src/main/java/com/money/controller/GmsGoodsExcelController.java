package com.money.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.GmsBrand;
import com.money.entity.GmsGoodsCategory;
import com.money.entity.SysDictDetail;
import com.money.mapper.GmsBrandMapper;
import com.money.mapper.GmsGoodsCategoryMapper;
import com.money.mapper.SysDictDetailMapper;
import com.money.service.impl.GmsGoodsExcelManager;
import com.money.util.ExcelDropDownHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "gmsGoodsExcel", description = "商品导入导出中心")
@RestController
@RequiredArgsConstructor
public class GmsGoodsExcelController {

    // 🌟 完美切换：不再依赖臃肿的 Service，直接对接专属 Manager！
    private final GmsGoodsExcelManager gmsGoodsExcelManager;

    private final GmsGoodsCategoryMapper gmsGoodsCategoryMapper;
    private final GmsBrandMapper gmsBrandMapper;
    private final SysDictDetailMapper sysDictDetailMapper;

    @Operation(summary = "Excel智能矩阵批量导入商品")
    @PostMapping("/gms/goods/import")
    public String importGoods(@RequestParam("file") MultipartFile file) {
        // 🌟 直接调用 Manager 的解析引擎
        return gmsGoodsExcelManager.importGoods(file);
    }

    @GetMapping("/gms/goods/template")
    @Operation(summary = "下载智能商品导入模板 (分类品牌下拉+动态价格列)")
    public void downloadTemplate(HttpServletResponse response) throws IOException {

        List<List<String>> heads = new ArrayList<>();
        heads.add(Arrays.asList("*商品条码(必填且唯一)"));
        heads.add(Arrays.asList("*商品名称(必填)"));
        heads.add(Arrays.asList("所属分类(请选择)"));
        heads.add(Arrays.asList("所属品牌(请选择)"));
        heads.add(Arrays.asList("商品状态(请选择)"));
        heads.add(Arrays.asList("单位(如:件)"));
        heads.add(Arrays.asList("规格(如:500g)"));
        heads.add(Arrays.asList("建议零售价"));
        heads.add(Arrays.asList("加权平均成本价"));
        heads.add(Arrays.asList("初始库存"));

        Map<Integer, String[]> dropDownConfig = new HashMap<>();

        List<GmsGoodsCategory> categories = gmsGoodsCategoryMapper.selectList(new LambdaQueryWrapper<>());
        if (categories != null && !categories.isEmpty()) {
            String[] catOptions = categories.stream().map(GmsGoodsCategory::getName).toArray(String[]::new);
            dropDownConfig.put(2, catOptions);
        }

        List<GmsBrand> brands = gmsBrandMapper.selectList(new LambdaQueryWrapper<>());
        if (brands != null && !brands.isEmpty()) {
            String[] brandOptions = brands.stream().map(GmsBrand::getName).toArray(String[]::new);
            dropDownConfig.put(3, brandOptions);
        }

        dropDownConfig.put(4, new String[]{"上架 (SALE)", "下架 (SOLD_OUT)"});

        List<SysDictDetail> dictList = sysDictDetailMapper.selectList(
                new LambdaQueryWrapper<SysDictDetail>()
                        .eq(SysDictDetail::getDict, "memberType")
                        .ne(SysDictDetail::getValue, "MEMBER")
        );

        int colIndex = heads.size();
        for (SysDictDetail dict : dictList) {
            heads.add(Arrays.asList("[会员特价] " + dict.getCnDesc()));
            colIndex++;
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = java.net.URLEncoder.encode("智能商品导入模板", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        List<List<Object>> demoData = new ArrayList<>();
        List<Object> demoRow = new ArrayList<>(Arrays.asList(
                "690123456789", "农夫山泉500ml", "", "", "上架 (SALE)", "瓶", "500ml", "2.00", "1.15", "100"
        ));
        for (int i = 0; i < dictList.size(); i++) { demoRow.add(""); }
        demoData.add(demoRow);

        EasyExcel.write(response.getOutputStream())
                .head(heads)
                .registerWriteHandler(new ExcelDropDownHandler(dropDownConfig))
                .sheet("商品资料填写区")
                .doWrite(demoData);
    }
}