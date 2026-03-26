package com.money.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.*;
import com.money.mapper.PosSkuLevelPriceMapper;
import com.money.mapper.SysBrandConfigMapper;
import com.money.mapper.SysDictDetailMapper;
import com.money.service.GmsBrandService;
import com.money.service.GmsGoodsCategoryService;
import com.money.service.GmsGoodsService;
import com.money.utils.PinyinUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmsGoodsExcelManager {

    private final GmsGoodsService gmsGoodsService;
    private final GmsGoodsCategoryService gmsGoodsCategoryService;
    private final GmsBrandService gmsBrandService;
    private final SysDictDetailMapper sysDictDetailMapper;
    private final SysBrandConfigMapper sysBrandConfigMapper;
    private final PosSkuLevelPriceMapper posSkuLevelPriceMapper;

    @SneakyThrows
    @Transactional(rollbackFor = Exception.class)
    public String importGoods(MultipartFile file) {
        log.info("开始执行动态智能 Excel 商品导入引擎(智能表头版)...");

        List<GmsGoodsCategory> categoryList = gmsGoodsCategoryService.list();
        Map<String, Long> catName2IdMap = categoryList.stream().collect(Collectors.toMap(GmsGoodsCategory::getName, GmsGoodsCategory::getId, (k1, k2) -> k1));

        List<GmsBrand> brandList = gmsBrandService.list();
        Map<String, Long> brandName2IdMap = brandList.stream().collect(Collectors.toMap(GmsBrand::getName, GmsBrand::getId, (k1, k2) -> k1));

        List<SysDictDetail> dictList = sysDictDetailMapper.selectList(new LambdaQueryWrapper<SysDictDetail>().eq(SysDictDetail::getDict, "memberType"));
        Map<String, String> dictReverseMap = dictList.stream().collect(Collectors.toMap(SysDictDetail::getCnDesc, SysDictDetail::getValue, (k1, k2) -> k1));

        List<SysBrandConfig> brandConfigs = sysBrandConfigMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, Boolean> brandDualTrackRadar = new HashMap<>();
        if (brandConfigs != null) {
            for (SysBrandConfig config : brandConfigs) {
                if (config.getCouponEnabled() != null) brandDualTrackRadar.put(config.getBrand(), config.getCouponEnabled());
            }
        }

        List<GmsGoods> parsedGoodsList = new ArrayList<>();
        Map<String, Map<String, BigDecimal>> skuLevelPriceMap = new HashMap<>();
        Map<String, Map<String, BigDecimal>> skuLevelCouponMap = new HashMap<>();

        // 🌟 核心升级：动态表头嗅探器
        Map<String, Integer> headerIndexMap = new HashMap<>();
        Map<Integer, String> dynamicPriceColMap = new HashMap<>();
        int[] skipCount = new int[]{0};

        EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {
            @Override
            public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                for (Map.Entry<Integer, String> entry : headMap.entrySet()) {
                    String headName = entry.getValue();
                    if (StrUtil.isBlank(headName)) continue;
                    headName = headName.trim();
                    headerIndexMap.put(headName, entry.getKey()); // 记录列名对应的真实索引

                    if (headName.startsWith("[会员特价] ")) {
                        String levelCode = dictReverseMap.get(headName.replace("[会员特价] ", "").trim());
                        if (StrUtil.isNotBlank(levelCode)) dynamicPriceColMap.put(entry.getKey(), levelCode);
                    }
                }
            }

            // 智能取值器：不管列怎么移动，只要名字对就能拿到值
            private String getVal(Map<Integer, String> data, String... possibleNames) {
                for (String name : possibleNames) {
                    Integer idx = headerIndexMap.get(name);
                    if (idx != null && StrUtil.isNotBlank(data.get(idx))) {
                        return data.get(idx).trim();
                    }
                }
                return null;
            }

            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                String barcode = getVal(data, "商品条码", "条码");
                String name = getVal(data, "商品名称", "名称");

                if (StrUtil.isBlank(barcode) || StrUtil.isBlank(name)) {
                    skipCount[0]++;
                    return;
                }

                GmsGoods goods = new GmsGoods();
                goods.setBarcode(barcode);
                goods.setName(name);
                goods.setMnemonicCode(PinyinUtil.getFirstLetter(goods.getName()));
                goods.setIsCombo(0);

                // 🌟 精准翻译分类与品牌
                String catCn = getVal(data, "商品分类", "所属分类");
                if (catCn != null) goods.setCategoryId(catName2IdMap.get(catCn));

                String brandCn = getVal(data, "品牌归属", "商品品牌");
                if (brandCn != null) goods.setBrandId(brandName2IdMap.get(brandCn));

                // 🌟 解析满减参与状态
                String discountStr = getVal(data, "参与满减", "是否满减");
                if ("允许".equals(discountStr) || "是".equals(discountStr) || "1".equals(discountStr)) {
                    goods.setIsDiscountParticipable(1);
                } else {
                    goods.setIsDiscountParticipable(0); // 默认不参与或禁止
                }

                String statusStr = getVal(data, "状态", "上架状态");
                goods.setStatus((statusStr != null && statusStr.contains("SOLD_OUT")) ? "SOLD_OUT" : "SALE");

                goods.setUnit(getVal(data, "单位", "计量单位"));
                goods.setSize(getVal(data, "规格", "规格尺寸"));

                String salePriceStr = getVal(data, "零售价", "系统零售价");
                BigDecimal salePrice = new BigDecimal(salePriceStr != null ? salePriceStr : "0");
                goods.setSalePrice(salePrice);

                String costPriceStr = getVal(data, "进货价", "进货成本");
                goods.setAvgCostPrice(new BigDecimal(costPriceStr != null ? costPriceStr : "0"));
                goods.setPurchasePrice(goods.getAvgCostPrice());

                String stockStr = getVal(data, "当前库存", "库存");
                goods.setStock(stockStr != null ? Long.parseLong(stockStr) : 0L);

                parsedGoodsList.add(goods);

                boolean isDualTrackBrand = false;
                if (goods.getBrandId() != null) {
                    isDualTrackBrand = brandDualTrackRadar.getOrDefault(String.valueOf(goods.getBrandId()), false);
                }

                Map<String, BigDecimal> currentPrices = new HashMap<>();
                for (Map.Entry<Integer, String> entry : dynamicPriceColMap.entrySet()) {
                    String priceStr = data.get(entry.getKey());
                    if (StrUtil.isNotBlank(priceStr)) {
                        try { currentPrices.put(entry.getValue(), new BigDecimal(priceStr.trim())); } catch (Exception ignored) {}
                    }
                }

                // 处理固定表头的会员价
                String vipP = getVal(data, "普通会员价"); if (vipP != null) currentPrices.put("VIP", new BigDecimal(vipP));
                String goldP = getVal(data, "黄金会员价"); if (goldP != null) currentPrices.put("GOLD", new BigDecimal(goldP));
                String platP = getVal(data, "铂金会员价"); if (platP != null) currentPrices.put("PLATINUM", new BigDecimal(platP));
                String intP = getVal(data, "内部会员价"); if (intP != null) currentPrices.put("INTERNAL", new BigDecimal(intP));

                skuLevelPriceMap.put(goods.getBarcode(), currentPrices);

                Map<String, BigDecimal> currentCoupons = new HashMap<>();
                if (isDualTrackBrand) {
                    for (Map.Entry<String, BigDecimal> entry : currentPrices.entrySet()) {
                        String levelCode = entry.getKey();
                        BigDecimal mPrice = entry.getValue();
                        BigDecimal autoCoupon = salePrice.subtract(mPrice);
                        if (autoCoupon.compareTo(BigDecimal.ZERO) < 0) autoCoupon = BigDecimal.ZERO;
                        currentCoupons.put(levelCode, autoCoupon);
                    }
                }
                skuLevelCouponMap.put(goods.getBarcode(), currentCoupons);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {}
        }).sheet().doRead();

        if (parsedGoodsList.isEmpty()) {
            return "未发现有效数据。若有空行，已自动跳过 " + skipCount[0] + " 条。";
        }

        List<String> barcodes = parsedGoodsList.stream().map(GmsGoods::getBarcode).collect(Collectors.toList());
        Map<String, GmsGoods> existGoodsMap = new HashMap<>();
        if (!barcodes.isEmpty()) {
            List<GmsGoods> existList = gmsGoodsService.lambdaQuery().in(GmsGoods::getBarcode, barcodes).list();
            existGoodsMap = existList.stream().collect(Collectors.toMap(GmsGoods::getBarcode, g -> g));
        }

        List<GmsGoods> toSaveList = new ArrayList<>();
        List<GmsGoods> toUpdateList = new ArrayList<>();

        for (GmsGoods parsedGoods : parsedGoodsList) {
            GmsGoods existObj = existGoodsMap.get(parsedGoods.getBarcode());
            if (existObj != null) {
                parsedGoods.setId(existObj.getId());
                parsedGoods.setTenantId(existObj.getTenantId());

                if (!Objects.equals(existObj.getBrandId(), parsedGoods.getBrandId())) {
                    if (parsedGoods.getBrandId() != null) gmsBrandService.updateGoodsCount(parsedGoods.getBrandId(), 1);
                    if (existObj.getBrandId() != null) gmsBrandService.updateGoodsCount(existObj.getBrandId(), -1);
                }
                if (!Objects.equals(existObj.getCategoryId(), parsedGoods.getCategoryId())) {
                    if (parsedGoods.getCategoryId() != null) gmsGoodsCategoryService.updateGoodsCount(parsedGoods.getCategoryId(), 1);
                    if (existObj.getCategoryId() != null) gmsGoodsCategoryService.updateGoodsCount(existObj.getCategoryId(), -1);
                }
                toUpdateList.add(parsedGoods);
            } else {
                if (parsedGoods.getBrandId() != null) gmsBrandService.updateGoodsCount(parsedGoods.getBrandId(), 1);
                if (parsedGoods.getCategoryId() != null) gmsGoodsCategoryService.updateGoodsCount(parsedGoods.getCategoryId(), 1);
                toSaveList.add(parsedGoods);
            }
        }

        if (!toSaveList.isEmpty()) gmsGoodsService.saveBatch(toSaveList);
        if (!toUpdateList.isEmpty()) gmsGoodsService.updateBatchById(toUpdateList);

        List<GmsGoods> allProcessed = new ArrayList<>();
        allProcessed.addAll(toSaveList);
        allProcessed.addAll(toUpdateList);

        for (GmsGoods g : allProcessed) {
            Map<String, BigDecimal> prices = skuLevelPriceMap.get(g.getBarcode());
            Map<String, BigDecimal> coupons = skuLevelCouponMap.get(g.getBarcode());
            if (prices != null && !prices.isEmpty()) {
                saveLevelPrices(g.getId(), prices, coupons);
            }
        }

        String resultMsg = String.format("🎉 导入完成！成功新增 %d 条，更新 %d 条记录。", toSaveList.size(), toUpdateList.size());
        if (skipCount[0] > 0) resultMsg += String.format(" 发现并跳过 %d 条无效数据。", skipCount[0]);
        return resultMsg;
    }

    private void saveLevelPrices(Long skuId, Map<String, BigDecimal> levelPrices, Map<String, BigDecimal> levelCoupons) {
        List<PosSkuLevelPrice> existList = posSkuLevelPriceMapper.selectList(new LambdaQueryWrapper<PosSkuLevelPrice>().eq(PosSkuLevelPrice::getSkuId, skuId));
        Map<String, PosSkuLevelPrice> existMap = existList.stream().collect(Collectors.toMap(PosSkuLevelPrice::getLevelId, p -> p));
        Set<String> newLevels = levelPrices != null ? levelPrices.keySet() : new HashSet<>();

        List<Long> toDeleteIds = existList.stream().filter(p -> !newLevels.contains(p.getLevelId())).map(PosSkuLevelPrice::getId).collect(Collectors.toList());
        if (!toDeleteIds.isEmpty()) posSkuLevelPriceMapper.deleteBatchIds(toDeleteIds);

        if (levelPrices != null) {
            levelPrices.forEach((levelId, price) -> {
                if (price != null) {
                    BigDecimal coupon = (levelCoupons != null && levelCoupons.containsKey(levelId)) ? levelCoupons.get(levelId) : BigDecimal.ZERO;
                    if (coupon == null) coupon = BigDecimal.ZERO;

                    if (existMap.containsKey(levelId)) {
                        PosSkuLevelPrice existObj = existMap.get(levelId);
                        existObj.setMemberPrice(price);
                        existObj.setMemberCoupon(coupon);
                        posSkuLevelPriceMapper.updateById(existObj);
                    } else {
                        PosSkuLevelPrice newObj = new PosSkuLevelPrice();
                        newObj.setSkuId(skuId);
                        newObj.setLevelId(levelId);
                        newObj.setMemberPrice(price);
                        newObj.setMemberCoupon(coupon);
                        posSkuLevelPriceMapper.insert(newObj);
                    }
                }
            });
        }
    }
}