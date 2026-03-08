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

            // 1. 装载多维价格矩阵回显
            List<PosSkuLevelPrice> allLevelPrices = posSkuLevelPriceMapper.selectList(
                    new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, goodsIds)
            );
            Map<Long, List<PosSkuLevelPrice>> priceMap = allLevelPrices.stream().collect(Collectors.groupingBy(PosSkuLevelPrice::getSkuId));

            // 2. 装载套餐包含关系
            List<GmsGoodsCombo> allCombos = gmsGoodsComboMapper.selectList(
                    new LambdaQueryWrapper<GmsGoodsCombo>().in(GmsGoodsCombo::getComboGoodsId, goodsIds)
            );
            Map<Long, List<GmsGoodsCombo>> comboMap = allCombos.stream().collect(Collectors.groupingBy(GmsGoodsCombo::getComboGoodsId));

            // 3. 提取所有关联子商品的名称
            List<Long> allSubIds = allCombos.stream().map(GmsGoodsCombo::getSubGoodsId).distinct().collect(Collectors.toList());
            Map<Long, String> subNameMap = allSubIds.isEmpty() ? new HashMap<>() :
                    this.listByIds(allSubIds).stream().collect(Collectors.toMap(GmsGoods::getId, GmsGoods::getName));

            for (GmsGoodsVO vo : pageVO.getRecords()) {
                // 处理价格矩阵
                List<PosSkuLevelPrice> prices = priceMap.get(vo.getId());
                Map<String, BigDecimal> lpMap = new HashMap<>();
                Map<String, BigDecimal> lcMap = new HashMap<>();
                if (prices != null) {
                    for (PosSkuLevelPrice p : prices) {
                        lpMap.put(p.getLevelId(), p.getMemberPrice());
                        lcMap.put(p.getLevelId(), p.getMemberCoupon());
                    }
                }
                vo.setLevelPrices(lpMap);
                vo.setLevelCoupons(lcMap);

                // 处理套餐明细与描述文字
                List<GmsGoodsCombo> mySubItems = comboMap.get(vo.getId());
                if (mySubItems != null && !mySubItems.isEmpty()) {
                    String desc = mySubItems.stream()
                            .map(c -> subNameMap.getOrDefault(c.getSubGoodsId(), "未知") + "x" + c.getSubGoodsQty())
                            .collect(Collectors.joining(", "));
                    vo.setComboDesc(desc);

                    List<GmsGoodsComboDTO> dtoList = mySubItems.stream().map(c -> {
                        GmsGoodsComboDTO dto = new GmsGoodsComboDTO();
                        dto.setSubGoodsId(c.getSubGoodsId());
                        dto.setSubGoodsQty(c.getSubGoodsQty());
                        return dto;
                    }).collect(Collectors.toList());
                    vo.setSubGoodsList(dtoList);
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

        gmsBrandService.updateGoodsCount(gmsGoods.getBrandId(), 1);
        gmsGoodsCategoryService.updateGoodsCount(gmsGoods.getCategoryId(), 1);

        this.save(gmsGoods);
        // 保存矩阵定价
        saveLevelPrices(gmsGoods.getId(), addDTO.getLevelPrices(), addDTO.getLevelCoupons());
        // 保存套餐明细
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

        // 自动切换上下架状态逻辑
        if (GoodsStatus.SOLD_OUT.name().equals(gmsGoods.getStatus()) && updateDTO.getStock() > 0) gmsGoods.setStatus(GoodsStatus.SALE.name());
        if (GoodsStatus.SALE.name().equals(gmsGoods.getStatus()) && updateDTO.getStock() <= 0) gmsGoods.setStatus(GoodsStatus.SOLD_OUT.name());

        this.updateById(gmsGoods);

        // 重置矩阵价格
        posSkuLevelPriceMapper.delete(new LambdaQueryWrapper<PosSkuLevelPrice>().eq(PosSkuLevelPrice::getSkuId, gmsGoods.getId()));
        saveLevelPrices(gmsGoods.getId(), updateDTO.getLevelPrices(), updateDTO.getLevelCoupons());

        // 重置套餐明细
        gmsGoodsComboMapper.delete(new LambdaQueryWrapper<GmsGoodsCombo>().eq(GmsGoodsCombo::getComboGoodsId, gmsGoods.getId()));
        saveComboDetails(gmsGoods.getId(), updateDTO.getIsCombo(), updateDTO.getSubGoodsList());
    }

    @Override
    public void delete(Set<Long> ids) {
        this.removeByIds(ids);
        posSkuLevelPriceMapper.delete(new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, ids));
        gmsGoodsComboMapper.delete(new LambdaQueryWrapper<GmsGoodsCombo>().in(GmsGoodsCombo::getComboGoodsId, ids));
    }

    @Override
    public void sell(Long goodsId, Integer qty) {
        // 预留接口：销售扣库存已在 PosServiceImpl 核心类中处理
    }

    // ==========================================
    // 🌟 核心修复：完善退货时的库存回滚逻辑，支持套餐拆解
    // ==========================================
    @Override
    public void updateStock(Long goodsId, Integer qty) {
        if (goodsId == null || qty == null || qty == 0) return;

        GmsGoods goods = this.getById(goodsId);
        if (goods == null) return;

        // 如果是组合套餐，退货时需要将里面的子商品库存加回去
        if (goods.getIsCombo() != null && goods.getIsCombo() == 1) {
            List<GmsGoodsCombo> combos = gmsGoodsComboMapper.selectList(
                    new LambdaQueryWrapper<GmsGoodsCombo>().eq(GmsGoodsCombo::getComboGoodsId, goodsId)
            );
            if (combos != null && !combos.isEmpty()) {
                for (GmsGoodsCombo combo : combos) {
                    GmsGoods subGoods = this.getById(combo.getSubGoodsId());
                    if (subGoods != null) {
                        int addQty = qty * (combo.getSubGoodsQty() != null ? combo.getSubGoodsQty() : 1);
                        long currentStock = subGoods.getStock() == null ? 0 : subGoods.getStock();
                        subGoods.setStock(currentStock + addQty);

                        // 退货后如果库存从 0 变正，自动恢复上架
                        if (subGoods.getStock() > 0 && GoodsStatus.SOLD_OUT.name().equals(subGoods.getStatus())) {
                            subGoods.setStatus(GoodsStatus.SALE.name());
                        }
                        this.updateById(subGoods);
                    }
                }
            }
        } else {
            // 普通单品，直接加回库存
            long currentStock = goods.getStock() == null ? 0 : goods.getStock();
            goods.setStock(currentStock + qty);

            // 退货后如果库存从 0 变正，自动恢复上架
            if (goods.getStock() > 0 && GoodsStatus.SOLD_OUT.name().equals(goods.getStatus())) {
                goods.setStatus(GoodsStatus.SALE.name());
            }
            this.updateById(goods);
        }
    }

    @Override
    public BigDecimal getCurrentStockValue() {
        return BigDecimal.ZERO;
    }

    private void saveLevelPrices(Long skuId, Map<String, BigDecimal> levelPrices, Map<String, BigDecimal> levelCoupons) {
        if (levelPrices != null && !levelPrices.isEmpty()) {
            levelPrices.forEach((levelDictValue, price) -> {
                if (price != null) {
                    PosSkuLevelPrice levelPrice = new PosSkuLevelPrice();
                    levelPrice.setSkuId(skuId);
                    levelPrice.setLevelId(levelDictValue);
                    levelPrice.setMemberPrice(price);

                    BigDecimal coupon = BigDecimal.ZERO;
                    if (levelCoupons != null && levelCoupons.containsKey(levelDictValue)) {
                        BigDecimal userCoupon = levelCoupons.get(levelDictValue);
                        coupon = userCoupon != null ? userCoupon : BigDecimal.ZERO;
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

                GmsGoods goods = this.lambdaQuery().eq(GmsGoods::getBarcode, dto.getBarcode()).one();
                boolean isNew = goods == null;
                if (isNew) goods = new GmsGoods();

                goods.setBarcode(dto.getBarcode());
                goods.setName(dto.getName());
                goods.setMnemonicCode(PinyinUtil.getFirstLetter(dto.getName()));
                goods.setPurchasePrice(dto.getPurchasePrice() != null ? dto.getPurchasePrice() : BigDecimal.ZERO);
                goods.setSalePrice(dto.getSalePrice() != null ? dto.getSalePrice() : BigDecimal.ZERO);
                goods.setStock(dto.getStock() != null ? dto.getStock().longValue() : 0L);
                goods.setIsCombo(0);

                if (isNew) this.save(goods); else this.updateById(goods);

                Map<String, BigDecimal> lp = new HashMap<>();
                if (dto.getGoldPrice() != null) lp.put("HJ_VIP", dto.getGoldPrice());
                if (dto.getPlatinumPrice() != null) lp.put("BJ_VIP", dto.getPlatinumPrice());

                posSkuLevelPriceMapper.delete(new LambdaQueryWrapper<PosSkuLevelPrice>().eq(PosSkuLevelPrice::getSkuId, goods.getId()));
                saveLevelPrices(goods.getId(), lp, null);
            }
            return true;
        } catch (Exception e) {
            log.error("Excel导入失败: ", e);
            throw new BaseException("导入失败，Excel 格式有误");
        }
    }
}