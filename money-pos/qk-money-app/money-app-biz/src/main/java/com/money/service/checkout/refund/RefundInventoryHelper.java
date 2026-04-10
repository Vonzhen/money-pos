package com.money.service.checkout.refund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.BizErrorStatus;
import com.money.entity.*;
import com.money.mapper.*;
import com.money.service.GmsStockLogService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundInventoryHelper {

    private final OmsOrderDetailMapper omsOrderDetailMapper;
    private final GmsGoodsMapper gmsGoodsMapper;
    private final GmsGoodsComboMapper gmsGoodsComboMapper;
    private final GmsStockLogService gmsStockLogService;
    private final GmsInventoryDocMapper gmsInventoryDocMapper;
    private final GmsInventoryDocItemMapper gmsInventoryDocItemMapper;

    /**
     * 处理整单退货库存
     */
    public void processFullOrderInventory(String orderNo, List<OmsOrderDetail> details) {
        GmsInventoryDoc doc = createReturnDoc();
        BigDecimal totalCost = BigDecimal.ZERO;

        for (OmsOrderDetail d : details) {
            int canReturn = d.getQuantity() - Optional.ofNullable(d.getReturnQuantity()).orElse(0);
            if (canReturn > 0) {
                totalCost = totalCost.add(processInventoryAndLogs(orderNo, d, canReturn, doc));
            }
        }
        doc.setTotalAmount(totalCost);
        gmsInventoryDocMapper.updateById(doc);
    }

    /**
     * 处理部分退货库存
     */
    public BigDecimal processPartialReturnInventory(String orderNo, OmsOrderDetail detail, int returnQty) {
        GmsInventoryDoc doc = createReturnDoc();
        BigDecimal cost = processInventoryAndLogs(orderNo, detail, returnQty, doc);
        doc.setTotalAmount(cost);
        gmsInventoryDocMapper.updateById(doc);
        return cost;
    }

    private GmsInventoryDoc createReturnDoc() {
        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo("TH" + System.currentTimeMillis());
        doc.setDocType("RETURN");
        doc.setCreateTime(LocalDateTime.now());
        gmsInventoryDocMapper.insert(doc);
        return doc;
    }

    private BigDecimal processInventoryAndLogs(String orderNo, OmsOrderDetail detail, int returnQty, GmsInventoryDoc doc) {
        int updatedRows = omsOrderDetailMapper.refundGoodsAtomically(detail.getId(), returnQty);
        if (updatedRows != 1) {
            throw new BaseException(BizErrorStatus.POS_REFUND_QTY_INVALID).withData("请求退数量:" + returnQty);
        }

        BigDecimal impact = BigDecimal.ZERO;
        GmsGoods goods = gmsGoodsMapper.selectById(detail.getGoodsId());
        if (goods == null) throw new BaseException(BizErrorStatus.GOODS_NOT_FOUND).withData(detail.getGoodsId());

        if (goods.getIsCombo() != null && goods.getIsCombo() == 1) {
            gmsGoodsMapper.addStockAtomically(goods.getId(), new BigDecimal(returnQty));
            GmsGoods updatedCombo = gmsGoodsMapper.selectById(goods.getId());
            int latestComboStock = (updatedCombo != null && updatedCombo.getStock() != null) ? updatedCombo.getStock().intValue() : 0;
            recordStockLog(goods, returnQty, latestComboStock, orderNo, BigDecimal.ZERO, "退款回补套餐配额");
            saveDocItem(doc.getDocNo(), goods, returnQty, BigDecimal.ZERO, latestComboStock);

            List<GmsGoodsCombo> combos = gmsGoodsComboMapper.selectList(new LambdaQueryWrapper<GmsGoodsCombo>().eq(GmsGoodsCombo::getComboGoodsId, goods.getId()));
            for (GmsGoodsCombo c : combos) {
                GmsGoods sub = gmsGoodsMapper.selectById(c.getSubGoodsId());
                if (sub != null) {
                    int qty = Math.multiplyExact(returnQty, c.getSubGoodsQty());
                    gmsGoodsMapper.addStockAtomically(sub.getId(), new BigDecimal(qty));
                    GmsGoods updatedSub = gmsGoodsMapper.selectById(sub.getId());
                    int latestStock = (updatedSub != null && updatedSub.getStock() != null) ? updatedSub.getStock().intValue() : 0;
                    BigDecimal cost = sub.getAvgCostPrice() != null ? sub.getAvgCostPrice() : (sub.getPurchasePrice() != null ? sub.getPurchasePrice() : BigDecimal.ZERO);

                    impact = impact.add(cost.multiply(new BigDecimal(qty)));
                    recordStockLog(sub, qty, latestStock, orderNo, cost, "套餐退款联动回补实物");
                    saveDocItem(doc.getDocNo(), sub, qty, cost, latestStock);
                }
            }
        } else {
            gmsGoodsMapper.addStockAtomically(goods.getId(), new BigDecimal(returnQty));
            GmsGoods updatedGoods = gmsGoodsMapper.selectById(goods.getId());
            int latestStock = (updatedGoods != null && updatedGoods.getStock() != null) ? updatedGoods.getStock().intValue() : 0;
            BigDecimal cost = detail.getPurchasePrice() != null ? detail.getPurchasePrice() : BigDecimal.ZERO;

            impact = cost.multiply(new BigDecimal(returnQty));
            recordStockLog(goods, returnQty, latestStock, orderNo, cost, "单品退货回补");
            saveDocItem(doc.getDocNo(), goods, returnQty, cost, latestStock);
        }
        return impact;
    }

    private void saveDocItem(String docNo, GmsGoods g, int qty, BigDecimal cost, int latestStock) {
        GmsInventoryDocItem item = new GmsInventoryDocItem();
        item.setDocNo(docNo);
        item.setGoodsId(g.getId());
        item.setGoodsName(g.getName());
        item.setBarcode(g.getBarcode());
        item.setChangeQty(qty);
        item.setCostPrice(cost);
        item.setPreStock((long) (latestStock - qty));
        item.setAfterStock((long) latestStock);
        gmsInventoryDocItemMapper.insert(item);
    }

    private void recordStockLog(GmsGoods g, int qty, int latestStock, String orderNo, BigDecimal cost, String remark) {
        GmsStockLog log = new GmsStockLog();
        log.setGoodsId(g.getId());
        log.setGoodsName(g.getName());
        log.setGoodsBarcode(g.getBarcode());
        log.setType("RETURN");
        log.setQuantity(qty);
        log.setAfterQuantity(latestStock);
        log.setOrderNo(orderNo);
        log.setCostPriceSnapshot(cost);
        log.setImpactAmount(cost.multiply(new BigDecimal(qty)));
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());
        gmsStockLogService.save(log);
    }
}