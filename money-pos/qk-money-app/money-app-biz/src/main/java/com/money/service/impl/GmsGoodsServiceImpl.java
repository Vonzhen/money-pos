package com.money.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.constant.GoodsStatus;
import com.money.dto.GmsGoods.GmsGoodsComboDTO;
import com.money.dto.GmsGoods.GmsGoodsDTO;
import com.money.dto.GmsGoods.GmsGoodsQueryDTO;
import com.money.dto.GmsGoods.GmsGoodsVO;
import com.money.dto.Goods.GmsGoodsExcelDTO;
import com.money.entity.GmsGoods;
import com.money.entity.GmsGoodsCombo;
import com.money.entity.PosSkuLevelPrice;
import com.money.mapper.GmsGoodsComboMapper;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.PosSkuLevelPriceMapper;
import com.money.oss.OSSDelegate;
import com.money.oss.core.FileNameStrategy;
import com.money.oss.core.FolderPath;
import com.money.oss.local.LocalOSS;
import com.money.service.GmsBrandService;
import com.money.service.GmsGoodsCategoryService;
import com.money.service.GmsGoodsService;
import com.money.util.PageUtil;
import com.money.utils.PinyinUtil;
import com.money.web.exception.BaseException;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class GmsGoodsServiceImpl extends ServiceImpl<GmsGoodsMapper, GmsGoods> implements GmsGoodsService {

    private final OSSDelegate<LocalOSS> localOSS;
    private final GmsBrandService gmsBrandService;
    private final GmsGoodsCategoryService gmsGoodsCategoryService;
    private final PosSkuLevelPriceMapper posSkuLevelPriceMapper;
    private final GmsGoodsComboMapper gmsGoodsComboMapper;

    @Override
    public PageVO<GmsGoodsVO> list(GmsGoodsQueryDTO queryDTO) {
        LambdaQueryChainWrapper<GmsGoods> queryChainWrapper = this.lambdaQuery();
        if (queryDTO.getCategoryId() != null) {
            List<Long> categoryIds = gmsGoodsCategoryService.getAllSubId(queryDTO.getCategoryId());
            queryChainWrapper.in(GmsGoods::getCategoryId, categoryIds);
        }
        Page<GmsGoods> page = queryChainWrapper
                .eq(ObjectUtil.isNotNull(queryDTO.getBrandId()), GmsGoods::getBrandId, queryDTO.getBrandId())
                .eq(ObjectUtil.isNotNull(queryDTO.getStatus()), GmsGoods::getStatus, queryDTO.getStatus())
                .eq(ObjectUtil.isNotNull(queryDTO.getIsCombo()), GmsGoods::getIsCombo, queryDTO.getIsCombo())
                .like(StrUtil.isNotBlank(queryDTO.getBarcode()), GmsGoods::getBarcode, queryDTO.getBarcode())
                .like(StrUtil.isNotBlank(queryDTO.getName()), GmsGoods::getName, queryDTO.getName())
                .orderByDesc(StrUtil.isBlank(queryDTO.getOrderBy()), GmsGoods::getUpdateTime)
                .last(StrUtil.isNotBlank(queryDTO.getOrderBy()), queryDTO.getOrderBySql())
                .page(PageUtil.toPage(queryDTO));

        PageVO<GmsGoodsVO> pageVO = PageUtil.toPageVO(page, GmsGoodsVO::new);

        if (!pageVO.getRecords().isEmpty()) {
            List<Long> goodsIds = pageVO.getRecords().stream().map(GmsGoodsVO::getId).collect(Collectors.toList());

            List<PosSkuLevelPrice> allLevelPrices = posSkuLevelPriceMapper.selectList(
                    new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, goodsIds)
            );
            Map<Long, List<PosSkuLevelPrice>> priceMap = allLevelPrices.stream().collect(Collectors.groupingBy(PosSkuLevelPrice::getSkuId));

            for (GmsGoodsVO vo : pageVO.getRecords()) {
                List<PosSkuLevelPrice> prices = priceMap.get(vo.getId());
                Map<String, BigDecimal> lpMap = new HashMap<>();
                Map<String, BigDecimal> lcMap = new HashMap<>(); // 🌟 新增：装载券额的 Map

                if (prices != null) {
                    for (PosSkuLevelPrice p : prices) {
                        lpMap.put(p.getLevelId(), p.getMemberPrice());
                        lcMap.put(p.getLevelId(), p.getMemberCoupon()); // 🌟 提取专属券额
                    }
                }
                vo.setLevelPrices(lpMap);
                vo.setLevelCoupons(lcMap); // 🌟 统一返回给前端

                if (queryDTO.getIsCombo() != null && queryDTO.getIsCombo() == 1) {
                    List<GmsGoodsCombo> comboList = gmsGoodsComboMapper.selectList(new LambdaQueryWrapper<GmsGoodsCombo>().in(GmsGoodsCombo::getComboGoodsId, goodsIds));
                    if (!comboList.isEmpty()) {
                        List<Long> subGoodsIds = comboList.stream().map(GmsGoodsCombo::getSubGoodsId).distinct().collect(Collectors.toList());
                        Map<Long, String> subGoodsNameMap = this.listByIds(subGoodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, GmsGoods::getName));

                        String comboDesc = comboList.stream()
                                .filter(c -> c.getComboGoodsId().equals(vo.getId()))
                                .map(c -> subGoodsNameMap.getOrDefault(c.getSubGoodsId(), "未知商品") + " x" + c.getSubGoodsQty())
                                .collect(Collectors.joining(", "));
                        vo.setComboDesc(comboDesc);

                        List<GmsGoodsComboDTO> subList = comboList.stream()
                                .filter(c -> c.getComboGoodsId().equals(vo.getId()))
                                .map(c -> {
                                    GmsGoodsComboDTO dto = new GmsGoodsComboDTO();
                                    dto.setSubGoodsId(c.getSubGoodsId());
                                    dto.setSubGoodsQty(c.getSubGoodsQty());
                                    return dto;
                                }).collect(Collectors.toList());
                        vo.setSubGoodsList(subList);
                    }
                }
            }
        }
        return pageVO;
    }

    @Override
    public void add(GmsGoodsDTO addDTO, MultipartFile pic) {
        boolean exists = this.lambdaQuery().eq(GmsGoods::getBarcode, addDTO.getBarcode()).exists();
        if (exists) throw new BaseException("条码已存在");

        GmsGoods gmsGoods = new GmsGoods();
        BeanUtil.copyProperties(addDTO, gmsGoods);
        gmsGoods.setIsCombo(addDTO.getIsCombo() == null ? 0 : addDTO.getIsCombo());

        if (StrUtil.isNotBlank(gmsGoods.getName())) {
            gmsGoods.setMnemonicCode(PinyinUtil.getFirstLetter(gmsGoods.getName()));
        }

        if (pic != null) {
            String picUrl = localOSS.upload(pic, FolderPath.builder().cd("goods").build(), FileNameStrategy.TIMESTAMP);
            gmsGoods.setPic(picUrl);
        }
        gmsBrandService.updateGoodsCount(gmsGoods.getBrandId(), 1);
        gmsGoodsCategoryService.updateGoodsCount(gmsGoods.getCategoryId(), 1);

        this.save(gmsGoods);
        // 🌟 将价格和券额同时传入底层落库
        saveLevelPrices(gmsGoods.getId(), addDTO.getLevelPrices(), addDTO.getLevelCoupons());
        saveComboDetails(gmsGoods.getId(), addDTO.getIsCombo(), addDTO.getSubGoodsList());
    }

    @Override
    public void update(GmsGoodsDTO updateDTO, MultipartFile pic) {
        boolean exists = this.lambdaQuery().ne(GmsGoods::getId, updateDTO.getId()).eq(GmsGoods::getBarcode, updateDTO.getBarcode()).exists();
        if (exists) throw new BaseException("条码已存在");

        GmsGoods gmsGoods = this.getById(updateDTO.getId());

        if (!Objects.equals(gmsGoods.getBrandId(), updateDTO.getBrandId())) {
            gmsBrandService.updateGoodsCount(updateDTO.getBrandId(), 1);
            gmsBrandService.updateGoodsCount(gmsGoods.getBrandId(), -1);
        }
        if (!Objects.equals(gmsGoods.getCategoryId(), updateDTO.getCategoryId())) {
            gmsGoodsCategoryService.updateGoodsCount(updateDTO.getCategoryId(), 1);
            gmsGoodsCategoryService.updateGoodsCount(gmsGoods.getCategoryId(), -1);
        }

        BeanUtil.copyProperties(updateDTO, gmsGoods);
        if (gmsGoods.getIsCombo() == null) gmsGoods.setIsCombo(0);

        if (StrUtil.isNotBlank(gmsGoods.getName())) {
            gmsGoods.setMnemonicCode(PinyinUtil.getFirstLetter(gmsGoods.getName()));
        }

        if (GoodsStatus.SOLD_OUT.name().equals(gmsGoods.getStatus()) && updateDTO.getStock() > 0) gmsGoods.setStatus(GoodsStatus.SALE.name());
        if (GoodsStatus.SALE.name().equals(gmsGoods.getStatus()) && updateDTO.getStock() <= 0) gmsGoods.setStatus(GoodsStatus.SOLD_OUT.name());

        if (pic != null) {
            localOSS.delete(gmsGoods.getPic());
            String picUrl = localOSS.upload(pic, FolderPath.builder().cd("goods").build(), FileNameStrategy.TIMESTAMP);
            gmsGoods.setPic(picUrl);
        }
        this.updateById(gmsGoods);

        posSkuLevelPriceMapper.delete(new LambdaQueryWrapper<PosSkuLevelPrice>().eq(PosSkuLevelPrice::getSkuId, gmsGoods.getId()));
        // 🌟 更新时同步传入价格和券额
        saveLevelPrices(gmsGoods.getId(), updateDTO.getLevelPrices(), updateDTO.getLevelCoupons());

        gmsGoodsComboMapper.delete(new LambdaQueryWrapper<GmsGoodsCombo>().eq(GmsGoodsCombo::getComboGoodsId, gmsGoods.getId()));
        saveComboDetails(gmsGoods.getId(), updateDTO.getIsCombo(), updateDTO.getSubGoodsList());
    }

    @Override
    public void delete(Set<Long> ids) {
        List<GmsGoods> gmsGoodsList = this.listByIds(ids);
        this.removeByIds(ids);
        gmsGoodsList.forEach(gmsGoods -> { if (StrUtil.isNotBlank(gmsGoods.getPic())) localOSS.delete(gmsGoods.getPic()); });
        posSkuLevelPriceMapper.delete(new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, ids));
        gmsGoodsComboMapper.delete(new LambdaQueryWrapper<GmsGoodsCombo>().in(GmsGoodsCombo::getComboGoodsId, ids));
    }

    @Override public void sell(Long goodsId, Integer qty) { }
    @Override public void updateStock(Long goodsId, Integer qty) { }
    @Override public BigDecimal getCurrentStockValue() { return BigDecimal.ZERO; }

    // 🌟 核心升级：同时处理价格与券额落库
    private void saveLevelPrices(Long skuId, Map<String, BigDecimal> levelPrices, Map<String, BigDecimal> levelCoupons) {
        if (levelPrices != null && !levelPrices.isEmpty()) {
            levelPrices.forEach((levelDictValue, price) -> {
                if (price != null) {
                    PosSkuLevelPrice levelPrice = new PosSkuLevelPrice();
                    levelPrice.setSkuId(skuId);
                    levelPrice.setLevelId(levelDictValue);
                    levelPrice.setMemberPrice(price);

                    // 提取对应的券额，如果没有传，则默认写入 0
                    BigDecimal coupon = BigDecimal.ZERO;
                    if (levelCoupons != null && levelCoupons.containsKey(levelDictValue)) {
                        coupon = levelCoupons.get(levelDictValue);
                        if (coupon == null) coupon = BigDecimal.ZERO;
                    }
                    levelPrice.setMemberCoupon(coupon);

                    posSkuLevelPriceMapper.insert(levelPrice);
                }
            });
        }
    }

    private void saveComboDetails(Long comboGoodsId, Integer isCombo, List<GmsGoodsComboDTO> subGoodsList) {
        if (isCombo != null && isCombo == 1 && subGoodsList != null && !subGoodsList.isEmpty()) {
            for (GmsGoodsComboDTO sub : subGoodsList) {
                GmsGoodsCombo comboObj = new GmsGoodsCombo();
                comboObj.setComboGoodsId(comboGoodsId);
                comboObj.setSubGoodsId(sub.getSubGoodsId());
                comboObj.setSubGoodsQty(sub.getSubGoodsQty() != null ? sub.getSubGoodsQty() : 1);
                gmsGoodsComboMapper.insert(comboObj);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean importGoods(MultipartFile file) {
        try {
            List<GmsGoodsExcelDTO> list = EasyExcel.read(file.getInputStream()).head(GmsGoodsExcelDTO.class).sheet().doReadSync();
            for (GmsGoodsExcelDTO dto : list) {
                if (StrUtil.isBlank(dto.getBarcode()) || StrUtil.isBlank(dto.getName())) continue;
                String mnemonic = PinyinUtil.getFirstLetter(dto.getName());

                Map<String, BigDecimal> levelPriceMap = new HashMap<>();
                Map<String, BigDecimal> levelCouponMap = new HashMap<>(); // 导入场景默认无券，后续可再扩充

                if (dto.getGoldPrice() != null) levelPriceMap.put("HJ_VIP", dto.getGoldPrice());
                if (dto.getPlatinumPrice() != null) levelPriceMap.put("BJ_VIP", dto.getPlatinumPrice());
                if (dto.getInternalPrice() != null) levelPriceMap.put("INNER", dto.getInternalPrice());

                GmsGoods existGoods = this.lambdaQuery().eq(GmsGoods::getBarcode, dto.getBarcode()).one();
                if (existGoods != null) {
                    existGoods.setName(dto.getName());
                    if (dto.getPurchasePrice() != null) existGoods.setPurchasePrice(dto.getPurchasePrice());
                    if (dto.getSalePrice() != null) existGoods.setSalePrice(dto.getSalePrice());
                    if (dto.getVipPrice() != null) existGoods.setVipPrice(dto.getVipPrice());
                    if (dto.getStock() != null) existGoods.setStock(dto.getStock().longValue());
                    existGoods.setMnemonicCode(mnemonic);
                    this.updateById(existGoods);
                    posSkuLevelPriceMapper.delete(new LambdaQueryWrapper<PosSkuLevelPrice>().eq(PosSkuLevelPrice::getSkuId, existGoods.getId()));
                    saveLevelPrices(existGoods.getId(), levelPriceMap, levelCouponMap);
                } else {
                    GmsGoods newGoods = new GmsGoods();
                    newGoods.setBarcode(dto.getBarcode());
                    newGoods.setName(dto.getName());
                    newGoods.setPurchasePrice(dto.getPurchasePrice() != null ? dto.getPurchasePrice() : BigDecimal.ZERO);
                    newGoods.setSalePrice(dto.getSalePrice() != null ? dto.getSalePrice() : BigDecimal.ZERO);
                    newGoods.setVipPrice(dto.getVipPrice() != null ? dto.getVipPrice() : BigDecimal.ZERO);
                    newGoods.setStock(dto.getStock() != null ? dto.getStock().longValue() : 0L);
                    newGoods.setMnemonicCode(mnemonic);
                    newGoods.setIsCombo(0);
                    this.save(newGoods);
                    saveLevelPrices(newGoods.getId(), levelPriceMap, levelCouponMap);
                }
            }
            return true;
        } catch (Exception e) {
            log.error("Excel导入失败: ", e);
            throw new BaseException("导入失败，请检查Excel文件格式是否正确");
        }
    }
}