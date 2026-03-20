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

/**
 * 商品 Excel 批处理管家 (Manager 层)
 * 职责：专门负责沉重的 Excel 矩阵解析、字典逆向翻译、品牌策略兜底与批量 Upsert。
 * 彻底为 GmsGoodsServiceImpl 核心业务类减负。
 */
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
        log.info("开始执行动态智能 Excel 商品导入引擎(Manager版)...");

        List<GmsGoodsCategory> categoryList = gmsGoodsCategoryService.list();
        Map<String, Long> catName2IdMap = categoryList.stream().collect(Collectors.toMap(GmsGoodsCategory::getName, GmsGoodsCategory::getId, (k1, k2) -> k1));

        List<GmsBrand> brandList = gmsBrandService.list();
        Map<String, Long> brandName2IdMap = brandList.stream().collect(Collectors.toMap(GmsBrand::getName, GmsBrand::getId, (k1, k2) -> k1));

        List<SysDictDetail> dictList = sysDictDetailMapper.selectList(
                new LambdaQueryWrapper<SysDictDetail>().eq(SysDictDetail::getDict, "memberType")
        );
        Map<String, String> dictReverseMap = dictList.stream().collect(Collectors.toMap(SysDictDetail::getCnDesc, SysDictDetail::getValue, (k1, k2) -> k1));

        List<SysBrandConfig> brandConfigs = sysBrandConfigMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, Boolean> brandDualTrackRadar = new HashMap<>();
        if (brandConfigs != null) {
            for (SysBrandConfig config : brandConfigs) {
                if (config.getCouponEnabled() != null) {
                    brandDualTrackRadar.put(config.getBrand(), config.getCouponEnabled());
                }
            }
        }

        List<GmsGoods> parsedGoodsList = new ArrayList<>();
        Map<String, Map<String, BigDecimal>> skuLevelPriceMap = new HashMap<>();
        Map<String, Map<String, BigDecimal>> skuLevelCouponMap = new HashMap<>();
        Map<Integer, String> dynamicPriceColMap = new HashMap<>();

        int[] skipCount = new int[]{0};

        EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {
            @Override
            public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                for (Map.Entry<Integer, String> entry : headMap.entrySet()) {
                    String headName = entry.getValue();
                    if (headName != null && headName.startsWith("[会员特价] ")) {
                        String levelCode = dictReverseMap.get(headName.replace("[会员特价] ", "").trim());
                        if (StrUtil.isNotBlank(levelCode)) dynamicPriceColMap.put(entry.getKey(), levelCode);
                    }
                }
            }

            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                String barcode = data.get(0);
                String name = data.get(1);

                if (StrUtil.isBlank(barcode) || StrUtil.isBlank(name)) {
                    skipCount[0]++;
                    return;
                }

                GmsGoods goods = new GmsGoods();
                goods.setBarcode(barcode.trim());
                goods.setName(name.trim());
                goods.setMnemonicCode(PinyinUtil.getFirstLetter(goods.getName()));
                goods.setIsCombo(0);

                String catCn = data.get(2);
                if (StrUtil.isNotBlank(catCn)) goods.setCategoryId(catName2IdMap.get(catCn.trim()));

                String brandCn = data.get(3);
                if (StrUtil.isNotBlank(brandCn)) goods.setBrandId(brandName2IdMap.get(brandCn.trim()));

                boolean isDualTrackBrand = false;
                if (goods.getBrandId() != null) {
                    isDualTrackBrand = brandDualTrackRadar.getOrDefault(String.valueOf(goods.getBrandId()), false);
                }

                String statusStr = data.get(4);
                if (StrUtil.isNotBlank(statusStr) && statusStr.contains("SOLD_OUT")) {
                    goods.setStatus("SOLD_OUT");
                } else {
                    goods.setStatus("SALE");
                }

                goods.setUnit(data.get(5));
                goods.setSize(data.get(6));

                BigDecimal salePrice = new BigDecimal(StrUtil.isNotBlank(data.get(7)) ? data.get(7) : "0");
                goods.setSalePrice(salePrice);
                goods.setAvgCostPrice(new BigDecimal(StrUtil.isNotBlank(data.get(8)) ? data.get(8) : "0"));
                goods.setPurchasePrice(goods.getAvgCostPrice());
                goods.setStock(StrUtil.isNotBlank(data.get(9)) ? Long.parseLong(data.get(9).trim()) : 0L);

                parsedGoodsList.add(goods);

                Map<String, BigDecimal> currentPrices = new HashMap<>();
                for (Map.Entry<Integer, String> entry : dynamicPriceColMap.entrySet()) {
                    String priceStr = data.get(entry.getKey());
                    if (StrUtil.isNotBlank(priceStr)) {
                        try { currentPrices.put(entry.getValue(), new BigDecimal(priceStr.trim())); } catch (Exception ignored) {}
                    }
                }
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
        if (skipCount[0] > 0) {
            resultMsg += String.format(" 发现并自动跳过 %d 条无效数据(缺少条码或名称)。", skipCount[0]);
        }

        log.info(resultMsg);
        return resultMsg;
    }

    // 内部私有方法，专供批量导入时平滑更新价格矩阵
    private void saveLevelPrices(Long skuId, Map<String, BigDecimal> levelPrices, Map<String, BigDecimal> levelCoupons) {
        List<PosSkuLevelPrice> existList = posSkuLevelPriceMapper.selectList(
                new LambdaQueryWrapper<PosSkuLevelPrice>().eq(PosSkuLevelPrice::getSkuId, skuId)
        );
        Map<String, PosSkuLevelPrice> existMap = existList.stream().collect(Collectors.toMap(PosSkuLevelPrice::getLevelId, p -> p));
        Set<String> newLevels = levelPrices != null ? levelPrices.keySet() : new HashSet<>();

        List<Long> toDeleteIds = existList.stream()
                .filter(p -> !newLevels.contains(p.getLevelId()))
                .map(PosSkuLevelPrice::getId)
                .collect(Collectors.toList());
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