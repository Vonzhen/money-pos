package com.money.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.GmsGoods;
import com.money.entity.PosSkuLevelPrice;
import com.money.mapper.PosSkuLevelPriceMapper;
import com.money.web.dto.ValidGroup;
import com.money.web.vo.PageVO;
import com.money.dto.GmsGoods.GmsGoodsDTO;
import com.money.dto.GmsGoods.GmsGoodsQueryDTO;
import com.money.dto.GmsGoods.GmsGoodsVO;
import com.money.service.GmsGoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品表 前端控制器
 * </p>
 */
@Tag(name = "gmsGoods", description = "商品表")
@RestController
@RequestMapping("/gms/goods")
@RequiredArgsConstructor
public class GmsGoodsController {

    private final GmsGoodsService gmsGoodsService;

    // 🌟 核心增援：把等级价 Mapper 引进来，让搜索接口具备查高级价格的能力！
    private final PosSkuLevelPriceMapper posSkuLevelPriceMapper;

    @Operation(summary = "分页查询")
    @GetMapping
    @PreAuthorize("@rbac.hasPermission('gmsGoods:list')")
    public PageVO<GmsGoodsVO> list(@Validated GmsGoodsQueryDTO queryDTO) {
        return gmsGoodsService.list(queryDTO);
    }

    @Operation(summary = "添加")
    @PostMapping
    @PreAuthorize("@rbac.hasPermission('gmsGoods:add')")
    public void add(@Validated(ValidGroup.Save.class) @RequestPart("goods") GmsGoodsDTO addDTO,
                    @RequestPart(required = false) MultipartFile pic) {
        gmsGoodsService.add(addDTO, pic);
    }

    @Operation(summary = "修改")
    @PutMapping
    @PreAuthorize("@rbac.hasPermission('gmsGoods:edit')")
    public void update(@Validated(ValidGroup.Update.class) @RequestPart("goods") GmsGoodsDTO updateDTO,
                       @RequestPart(required = false) MultipartFile pic) {
        gmsGoodsService.update(updateDTO, pic);
    }

    @Operation(summary = "删除")
    @DeleteMapping
    @PreAuthorize("@rbac.hasPermission('gmsGoods:del')")
    public void delete(@RequestBody Set<Long> ids) {
        gmsGoodsService.delete(ids);
    }

    @Operation(summary = "Excel极速批量导入商品")
    @PostMapping("/import")
    public void importGoods(@RequestParam("file") MultipartFile file) {
        gmsGoodsService.importGoods(file);
    }

    @GetMapping("/template")
    @Operation(summary = "下载商品导入模板")
    public void downloadTemplate(javax.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = java.net.URLEncoder.encode("商品导入模板", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        com.alibaba.excel.EasyExcel.write(response.getOutputStream(), com.money.dto.Goods.GmsGoodsExcelDTO.class)
                .sheet("商品导入模板")
                .doWrite(new java.util.ArrayList<>());
    }

    @GetMapping("/pos-search")
    @Operation(summary = "POS全能搜索(条码/名称/助记码) - 已强化会员价返回")
    public List<GmsGoodsVO> posSearchGoods(@RequestParam String keyword) {
        // 1. 先查出基础商品信息
        List<GmsGoods> goodsList = gmsGoodsService.lambdaQuery()
                .like(GmsGoods::getBarcode, keyword)
                .or().like(GmsGoods::getName, keyword)
                .or().like(GmsGoods::getMnemonicCode, keyword)
                .eq(GmsGoods::getStatus, "SALE")
                .list();

        if (goodsList == null || goodsList.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 提取出所有被扫到的商品 ID
        List<Long> goodsIds = goodsList.stream().map(GmsGoods::getId).collect(Collectors.toList());

        // 3. 🌟 核心修复：去附属表批量把这些商品的黄金、铂金价格都捞出来
        List<PosSkuLevelPrice> allLevelPrices = posSkuLevelPriceMapper.selectList(
                new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, goodsIds)
        );

        // 4. 按商品 ID 对价格进行分组打包
        Map<Long, List<PosSkuLevelPrice>> priceMap = allLevelPrices.stream()
                .collect(Collectors.groupingBy(PosSkuLevelPrice::getSkuId));

        // 5. 组装成带 levelPrices 的高级对象 (VO) 返回给收银台前端
        return goodsList.stream().map(goods -> {
            GmsGoodsVO vo = new GmsGoodsVO();
            BeanUtil.copyProperties(goods, vo);

            Map<String, BigDecimal> lpMap = new HashMap<>();
            List<PosSkuLevelPrice> prices = priceMap.get(goods.getId());
            if (prices != null) {
                for (PosSkuLevelPrice p : prices) {
                    lpMap.put(p.getLevelId(), p.getMemberPrice());
                }
            }
            vo.setLevelPrices(lpMap); // 把高级会员价塞进去！
            return vo;
        }).collect(Collectors.toList());
    }
}