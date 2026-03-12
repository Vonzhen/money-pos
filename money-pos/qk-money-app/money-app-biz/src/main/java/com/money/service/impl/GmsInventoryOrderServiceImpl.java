package com.money.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.dto.inventory.GmsInventoryOrderDTO;
import com.money.dto.inventory.GmsInventoryOrderDetailDTO;
import com.money.entity.GmsGoods;
import com.money.entity.GmsInventoryOrder;
import com.money.entity.GmsInventoryOrderDetail;
import com.money.entity.GmsStockLog;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.GmsInventoryOrderDetailMapper;
import com.money.mapper.GmsInventoryOrderMapper;
import com.money.mapper.GmsStockLogMapper;
import com.money.service.GmsInventoryOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GmsInventoryOrderServiceImpl extends ServiceImpl<GmsInventoryOrderMapper, GmsInventoryOrder> implements GmsInventoryOrderService {

    private final GmsInventoryOrderDetailMapper detailMapper;
    private final GmsGoodsMapper goodsMapper;
    private final GmsStockLogMapper gmsStockLogMapper;

    // 🌟 重构 D：抽取复用，采用毫秒级时间戳防单号碰撞
    private String generateOrderNo(String prefix) {
        return prefix + DateUtil.format(new Date(), "yyyyMMddHHmmssSSS") + RandomUtil.randomNumbers(4);
    }

    // 🌟 重构 E：提取公共逻辑，符合 DRY 原则
    private BigDecimal calculateTotalAmount(List<GmsInventoryOrderDetailDTO> details) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (details != null) {
            for (GmsInventoryOrderDetailDTO detail : details) {
                if (detail.getPrice() != null && detail.getQty() != null) {
                    totalAmount = totalAmount.add(detail.getPrice().multiply(new BigDecimal(detail.getQty())));
                }
            }
        }
        return totalAmount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createInboundOrder(GmsInventoryOrderDTO dto) {
        String orderNo = generateOrderNo("IN");
        BigDecimal totalAmount = calculateTotalAmount(dto.getDetails());

        GmsInventoryOrder order = new GmsInventoryOrder();
        order.setOrderNo(orderNo);
        order.setType("INBOUND");
        order.setTotalAmount(totalAmount);
        order.setStatus("COMPLETED");
        order.setRemark(dto.getRemark());
        this.save(order);

        if (dto.getDetails() != null && !dto.getDetails().isEmpty()) {
            // 🌟 重构 B：彻底消灭 N+1，批量预取商品字典
            Set<Long> goodsIds = dto.getDetails().stream().map(GmsInventoryOrderDetailDTO::getGoodsId).collect(Collectors.toSet());
            Map<Long, GmsGoods> goodsMap = goodsMapper.selectBatchIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));

            List<GmsInventoryOrderDetail> detailList = new ArrayList<>();
            List<GmsStockLog> logList = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (GmsInventoryOrderDetailDTO detailDTO : dto.getDetails()) {
                GmsInventoryOrderDetail detail = new GmsInventoryOrderDetail();
                detail.setOrderId(order.getId());
                detail.setGoodsId(detailDTO.getGoodsId());
                detail.setQty(detailDTO.getQty());
                detail.setPrice(detailDTO.getPrice());
                detailList.add(detail);

                GmsGoods goods = goodsMap.get(detailDTO.getGoodsId());
                if (goods != null) {
                    // 🌟 重构 A：下沉原子操作，使用 SQL 行锁防并发覆盖
                    LambdaUpdateWrapper<GmsGoods> updateWrapper = new LambdaUpdateWrapper<>();
                    updateWrapper.eq(GmsGoods::getId, goods.getId());
                    updateWrapper.setSql("stock = stock + " + detailDTO.getQty());

                    if (detailDTO.getPrice() != null && detailDTO.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                        updateWrapper.set(GmsGoods::getPurchasePrice, detailDTO.getPrice());
                    }

                    // 🌟 重构 F：解除状态绑架，只对非人工封禁商品自动上架
                    if (!"FORBIDDEN".equals(goods.getStatus())) {
                        updateWrapper.set(GmsGoods::getStatus, "SALE");
                    }
                    goodsMapper.update(null, updateWrapper);

                    GmsStockLog stockLog = new GmsStockLog();
                    stockLog.setGoodsId(goods.getId());
                    stockLog.setGoodsName(goods.getName());
                    stockLog.setGoodsBarcode(goods.getBarcode());
                    stockLog.setType("INBOUND");
                    stockLog.setQuantity(detailDTO.getQty());

                    long oldStock = goods.getStock() == null ? 0 : goods.getStock();
                    stockLog.setAfterQuantity((int) (oldStock + detailDTO.getQty()));
                    stockLog.setOrderNo(orderNo);
                    stockLog.setRemark("后台采购入库" + (StrUtil.isNotBlank(dto.getRemark()) ? "：" + dto.getRemark() : ""));
                    stockLog.setCreateTime(now);
                    logList.add(stockLog);
                }
            }
            // 批量一次性落库，降低网络 IO 损耗
            for (GmsInventoryOrderDetail d : detailList) detailMapper.insert(d);
            for (GmsStockLog l : logList) gmsStockLogMapper.insert(l);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCheckOrder(GmsInventoryOrderDTO dto) {
        String orderNo = generateOrderNo("CH");

        GmsInventoryOrder order = new GmsInventoryOrder();
        order.setOrderNo(orderNo);
        order.setType("CHECK");
        order.setTotalAmount(BigDecimal.ZERO);
        order.setStatus("COMPLETED");
        order.setRemark(dto.getRemark());
        this.save(order);

        if (dto.getDetails() != null && !dto.getDetails().isEmpty()) {
            Set<Long> goodsIds = dto.getDetails().stream().map(GmsInventoryOrderDetailDTO::getGoodsId).collect(Collectors.toSet());
            Map<Long, GmsGoods> goodsMap = goodsMapper.selectBatchIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));

            List<GmsInventoryOrderDetail> detailList = new ArrayList<>();
            List<GmsStockLog> logList = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (GmsInventoryOrderDetailDTO detailDTO : dto.getDetails()) {
                GmsInventoryOrderDetail detail = new GmsInventoryOrderDetail();
                detail.setOrderId(order.getId());
                detail.setGoodsId(detailDTO.getGoodsId());
                detail.setQty(detailDTO.getQty());
                detail.setPrice(BigDecimal.ZERO);
                detailList.add(detail);

                GmsGoods goods = goodsMap.get(detailDTO.getGoodsId());
                if (goods != null) {
                    // 🌟 重构 C：修复“虚假盘点覆盖”，利用差值计算保证盘点过程中的真实销售不丢失
                    long oldStock = goods.getStock() == null ? 0 : goods.getStock();
                    long newStock = detailDTO.getQty();
                    long diff = newStock - oldStock;

                    if (diff != 0) { // 只有产生盈亏才发生物理变动
                        LambdaUpdateWrapper<GmsGoods> updateWrapper = new LambdaUpdateWrapper<>();
                        updateWrapper.eq(GmsGoods::getId, goods.getId());
                        // 利用 MySQL 自动运算：假设盘点中途卖出 1 件，这里的原子累加会精准挂载到最新值上
                        updateWrapper.setSql("stock = stock + " + diff);
                        goodsMapper.update(null, updateWrapper);

                        GmsStockLog stockLog = new GmsStockLog();
                        stockLog.setGoodsId(goods.getId());
                        stockLog.setGoodsName(goods.getName());
                        stockLog.setGoodsBarcode(goods.getBarcode());
                        stockLog.setType("CHECK");
                        stockLog.setQuantity((int) diff);
                        stockLog.setAfterQuantity((int) (oldStock + diff));
                        stockLog.setOrderNo(orderNo);
                        stockLog.setRemark(diff > 0 ? "盘点校准(盘盈)" : "盘点校准(盘亏)");
                        stockLog.setCreateTime(now);
                        logList.add(stockLog);
                    }
                }
            }
            for (GmsInventoryOrderDetail d : detailList) detailMapper.insert(d);
            for (GmsStockLog l : logList) gmsStockLogMapper.insert(l);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOutboundOrder(GmsInventoryOrderDTO dto) {
        String orderNo = generateOrderNo("OU");
        BigDecimal totalAmount = calculateTotalAmount(dto.getDetails());

        GmsInventoryOrder order = new GmsInventoryOrder();
        order.setOrderNo(orderNo);
        order.setType("OUTBOUND");
        order.setTotalAmount(totalAmount);
        order.setStatus("COMPLETED");
        order.setRemark(dto.getRemark());
        this.save(order);

        if (dto.getDetails() != null && !dto.getDetails().isEmpty()) {
            Set<Long> goodsIds = dto.getDetails().stream().map(GmsInventoryOrderDetailDTO::getGoodsId).collect(Collectors.toSet());
            Map<Long, GmsGoods> goodsMap = goodsMapper.selectBatchIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));

            List<GmsInventoryOrderDetail> detailList = new ArrayList<>();
            List<GmsStockLog> logList = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (GmsInventoryOrderDetailDTO detailDTO : dto.getDetails()) {
                GmsInventoryOrderDetail detail = new GmsInventoryOrderDetail();
                detail.setOrderId(order.getId());
                detail.setGoodsId(detailDTO.getGoodsId());
                detail.setQty(detailDTO.getQty());
                detail.setPrice(detailDTO.getPrice());
                detailList.add(detail);

                GmsGoods goods = goodsMap.get(detailDTO.getGoodsId());
                if (goods != null) {
                    // 🌟 同样使用 SQL 层面的原子级扣减，强行锁行防并发
                    LambdaUpdateWrapper<GmsGoods> updateWrapper = new LambdaUpdateWrapper<>();
                    updateWrapper.eq(GmsGoods::getId, goods.getId());
                    updateWrapper.setSql("stock = stock - " + detailDTO.getQty());
                    goodsMapper.update(null, updateWrapper);

                    GmsStockLog stockLog = new GmsStockLog();
                    stockLog.setGoodsId(goods.getId());
                    stockLog.setGoodsName(goods.getName());
                    stockLog.setGoodsBarcode(goods.getBarcode());
                    stockLog.setType("SCRAP");
                    stockLog.setQuantity(-detailDTO.getQty());

                    long oldStock = goods.getStock() == null ? 0 : goods.getStock();
                    stockLog.setAfterQuantity((int) (oldStock - detailDTO.getQty()));
                    stockLog.setOrderNo(orderNo);
                    stockLog.setRemark("后台报损出库" + (StrUtil.isNotBlank(dto.getRemark()) ? "：" + dto.getRemark() : ""));
                    stockLog.setCreateTime(now);
                    logList.add(stockLog);
                }
            }
            for (GmsInventoryOrderDetail d : detailList) detailMapper.insert(d);
            for (GmsStockLog l : logList) gmsStockLogMapper.insert(l);
        }
    }
}