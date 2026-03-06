package com.money.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
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
import java.util.Date;

@Service
@RequiredArgsConstructor
public class GmsInventoryOrderServiceImpl extends ServiceImpl<GmsInventoryOrderMapper, GmsInventoryOrder> implements GmsInventoryOrderService {

    private final GmsInventoryOrderDetailMapper detailMapper;
    private final GmsGoodsMapper goodsMapper;
    // 🌟 引入库存流水台账 Mapper
    private final GmsStockLogMapper gmsStockLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createInboundOrder(GmsInventoryOrderDTO dto) {
        // 1. 自动生成单号
        String orderNo = "IN" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(4);

        // 2. 计算总金额
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (dto.getDetails() != null) {
            for (GmsInventoryOrderDetailDTO detail : dto.getDetails()) {
                if (detail.getPrice() != null && detail.getQty() != null) {
                    totalAmount = totalAmount.add(detail.getPrice().multiply(new BigDecimal(detail.getQty())));
                }
            }
        }

        // 3. 保存主单
        GmsInventoryOrder order = new GmsInventoryOrder();
        order.setOrderNo(orderNo);
        order.setType("INBOUND");
        order.setTotalAmount(totalAmount);
        order.setStatus("COMPLETED");
        order.setRemark(dto.getRemark());
        this.save(order);

        // 4. 保存明细 & 真实更新商品库存
        if (dto.getDetails() != null) {
            for (GmsInventoryOrderDetailDTO detailDTO : dto.getDetails()) {
                GmsInventoryOrderDetail detail = new GmsInventoryOrderDetail();
                detail.setOrderId(order.getId());
                detail.setGoodsId(detailDTO.getGoodsId());
                detail.setQty(detailDTO.getQty());
                detail.setPrice(detailDTO.getPrice());
                detailMapper.insert(detail);

                GmsGoods goods = goodsMapper.selectById(detailDTO.getGoodsId());
                if (goods != null) {
                    goods.setStock(goods.getStock() + detailDTO.getQty().longValue());

                    if (detailDTO.getPrice() != null && detailDTO.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                        goods.setPurchasePrice(detailDTO.getPrice());
                    }

                    goods.setStatus("SALE");
                    goodsMapper.updateById(goods);

                    // 🌟 探头 3：记录【采购入库】流水台账
                    GmsStockLog stockLog = new GmsStockLog();
                    stockLog.setGoodsId(goods.getId());
                    stockLog.setGoodsName(goods.getName());
                    stockLog.setGoodsBarcode(goods.getBarcode());
                    stockLog.setType("INBOUND");
                    stockLog.setQuantity(detailDTO.getQty()); // 进货为正数
                    stockLog.setAfterQuantity(goods.getStock() == null ? 0 : goods.getStock().intValue());
                    stockLog.setOrderNo(orderNo);
                    stockLog.setRemark("后台采购入库" + (StrUtil.isNotBlank(dto.getRemark()) ? "：" + dto.getRemark() : ""));
                    stockLog.setCreateTime(LocalDateTime.now());
                    gmsStockLogMapper.insert(stockLog);
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCheckOrder(GmsInventoryOrderDTO dto) {
        String orderNo = "CH" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(4);

        GmsInventoryOrder order = new GmsInventoryOrder();
        order.setOrderNo(orderNo);
        order.setType("CHECK");
        order.setTotalAmount(BigDecimal.ZERO);
        order.setStatus("COMPLETED");
        order.setRemark(dto.getRemark());
        this.save(order);

        if (dto.getDetails() != null) {
            for (GmsInventoryOrderDetailDTO detailDTO : dto.getDetails()) {
                GmsInventoryOrderDetail detail = new GmsInventoryOrderDetail();
                detail.setOrderId(order.getId());
                detail.setGoodsId(detailDTO.getGoodsId());
                detail.setQty(detailDTO.getQty());
                detail.setPrice(BigDecimal.ZERO);
                detailMapper.insert(detail);

                GmsGoods goods = goodsMapper.selectById(detailDTO.getGoodsId());
                if (goods != null) {
                    // 🌟 探头 4：计算盘点差额并记录流水台账
                    int oldStock = goods.getStock() == null ? 0 : goods.getStock().intValue();
                    int newStock = detailDTO.getQty();
                    int diff = newStock - oldStock;

                    if (diff != 0) { // 数量有变化才记流水
                        GmsStockLog stockLog = new GmsStockLog();
                        stockLog.setGoodsId(goods.getId());
                        stockLog.setGoodsName(goods.getName());
                        stockLog.setGoodsBarcode(goods.getBarcode());
                        stockLog.setType("CHECK");
                        stockLog.setQuantity(diff);
                        stockLog.setAfterQuantity(newStock);
                        stockLog.setOrderNo(orderNo);
                        stockLog.setRemark(diff > 0 ? "盘点校准(盘盈)" : "盘点校准(盘亏)");
                        stockLog.setCreateTime(LocalDateTime.now());
                        gmsStockLogMapper.insert(stockLog);
                    }

                    // 覆盖物理库存
                    goods.setStock(detailDTO.getQty().longValue());
                    goodsMapper.updateById(goods);
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOutboundOrder(GmsInventoryOrderDTO dto) {
        String orderNo = "OU" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(4);

        BigDecimal totalAmount = BigDecimal.ZERO;
        if (dto.getDetails() != null) {
            for (GmsInventoryOrderDetailDTO detail : dto.getDetails()) {
                if (detail.getPrice() != null && detail.getQty() != null) {
                    totalAmount = totalAmount.add(detail.getPrice().multiply(new BigDecimal(detail.getQty())));
                }
            }
        }

        GmsInventoryOrder order = new GmsInventoryOrder();
        order.setOrderNo(orderNo);
        order.setType("OUTBOUND");
        order.setTotalAmount(totalAmount);
        order.setStatus("COMPLETED");
        order.setRemark(dto.getRemark());
        this.save(order);

        if (dto.getDetails() != null) {
            for (GmsInventoryOrderDetailDTO detailDTO : dto.getDetails()) {
                GmsInventoryOrderDetail detail = new GmsInventoryOrderDetail();
                detail.setOrderId(order.getId());
                detail.setGoodsId(detailDTO.getGoodsId());
                detail.setQty(detailDTO.getQty());
                detail.setPrice(detailDTO.getPrice());
                detailMapper.insert(detail);

                GmsGoods goods = goodsMapper.selectById(detailDTO.getGoodsId());
                if (goods != null) {
                    goods.setStock(goods.getStock() - detailDTO.getQty().longValue());
                    goodsMapper.updateById(goods);

                    // 🌟 探头 5：记录【报损出库】流水台账
                    GmsStockLog stockLog = new GmsStockLog();
                    stockLog.setGoodsId(goods.getId());
                    stockLog.setGoodsName(goods.getName());
                    stockLog.setGoodsBarcode(goods.getBarcode());
                    stockLog.setType("SCRAP"); // 使用 SCRAP 代表报损
                    stockLog.setQuantity(-detailDTO.getQty()); // 报损记为负数
                    stockLog.setAfterQuantity(goods.getStock() == null ? 0 : goods.getStock().intValue());
                    stockLog.setOrderNo(orderNo);
                    stockLog.setRemark("后台报损出库" + (StrUtil.isNotBlank(dto.getRemark()) ? "：" + dto.getRemark() : ""));
                    stockLog.setCreateTime(LocalDateTime.now());
                    gmsStockLogMapper.insert(stockLog);
                }
            }
        }
    }
}