package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.constant.OrderStatusEnum;
import com.money.constant.PayMethodEnum;
import com.money.dto.OmsOrder.ReturnGoodsDTO;
import com.money.entity.*;
import com.money.mapper.*;
import com.money.service.GmsStockLogService;
import com.money.service.OmsOrderLogService;
import com.money.service.OmsOrderRefundService;
import com.money.service.UmsMemberAssetService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OmsOrderRefundServiceImpl implements OmsOrderRefundService {

    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderDetailMapper omsOrderDetailMapper;
    private final OmsOrderPayMapper omsOrderPayMapper;
    private final GmsGoodsMapper gmsGoodsMapper;
    private final UmsMemberAssetService umsMemberAssetService;
    private final GmsStockLogService gmsStockLogService;
    private final GmsInventoryDocMapper gmsInventoryDocMapper;
    private final GmsInventoryDocItemMapper gmsInventoryDocItemMapper;
    private final OmsOrderLogService omsOrderLogService;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final GmsGoodsComboMapper gmsGoodsComboMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnOrder(String orderNo) {
        OmsOrder order = omsOrderMapper.selectOne(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getOrderNo, orderNo));
        if (order == null || OrderStatusEnum.REFUNDED.name().equals(order.getStatus())) {
            throw new BaseException("订单不存在或已全额退款");
        }

        omsOrderMapper.updateRefundStatusToFull(orderNo, OrderStatusEnum.REFUNDED.name());

        List<OmsOrderDetail> details = omsOrderDetailMapper.selectList(new LambdaQueryWrapper<OmsOrderDetail>().eq(OmsOrderDetail::getOrderNo, orderNo));
        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo("TH" + System.currentTimeMillis());
        doc.setDocType("RETURN");
        doc.setCreateTime(LocalDateTime.now());

        BigDecimal totalCost = BigDecimal.ZERO;
        for (OmsOrderDetail d : details) {
            int canReturn = d.getQuantity() - Optional.ofNullable(d.getReturnQuantity()).orElse(0);
            if (canReturn > 0) {
                totalCost = totalCost.add(processInventoryAndLogs(orderNo, d, canReturn, doc));
            }
        }
        doc.setTotalAmount(totalCost);
        gmsInventoryDocMapper.insert(doc);

        if (order.getMemberId() != null) {
            umsMemberAssetService.processReturn(order.getMemberId(), order.getFinalSalesAmount(), order.getCouponAmount(), true, orderNo);

            if (order.getUseVoucherAmount() != null && order.getUseVoucherAmount().compareTo(BigDecimal.ZERO) > 0) {

                Long voucherCount = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>()
                        .eq(PosMemberCoupon::getOrderNo, orderNo));

                if (voucherCount != null && voucherCount > 0) {
                    posMemberCouponMapper.update(null, new LambdaUpdateWrapper<PosMemberCoupon>()
                            .eq(PosMemberCoupon::getOrderNo, orderNo)
                            .set(PosMemberCoupon::getStatus, "UNUSED")
                            .set(PosMemberCoupon::getUseTime, null)
                            .set(PosMemberCoupon::getOrderNo, null));

                    Long totalUnusedVouchers = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>()
                            .eq(PosMemberCoupon::getMemberId, order.getMemberId())
                            .eq(PosMemberCoupon::getStatus, "UNUSED"));

                    umsMemberAssetService.logVoucherRefund(order.getMemberId(), new BigDecimal(voucherCount), new BigDecimal(totalUnusedVouchers), orderNo);
                }
            }

            List<OmsOrderPay> pays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>().eq(OmsOrderPay::getOrderNo, orderNo));
            for (OmsOrderPay pay : pays) {
                if (PayMethodEnum.fromCode(pay.getPayMethodCode()) == PayMethodEnum.BALANCE) {
                    umsMemberAssetService.addBalance(order.getMemberId(), pay.getPayAmount(), orderNo, "整单退款:返还余额");
                }
            }
        }

        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());
        orderLog.setDescription("执行整单退款操作，资产与满减券已原路回退");
        omsOrderLogService.save(orderLog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnGoods(ReturnGoodsDTO dto) {
        OmsOrderDetail detail = omsOrderDetailMapper.selectById(dto.getDetailId());
        OmsOrder order = omsOrderMapper.selectOne(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getOrderNo, dto.getOrderNo()));
        if (detail == null || order == null) throw new BaseException("数据异常");

        BigDecimal unitSalesPrice = detail.getGoodsPrice() != null ? detail.getGoodsPrice() : BigDecimal.ZERO;
        BigDecimal unitCostPrice = detail.getPurchasePrice() != null ? detail.getPurchasePrice() : BigDecimal.ZERO;

        BigDecimal totalDetailCoupon = detail.getCoupon() != null ? detail.getCoupon() : BigDecimal.ZERO;
        BigDecimal unitCoupon = BigDecimal.ZERO;
        if (detail.getQuantity() > 0 && totalDetailCoupon.compareTo(BigDecimal.ZERO) > 0) {
            unitCoupon = totalDetailCoupon.divide(new BigDecimal(detail.getQuantity()), 4, RoundingMode.HALF_UP);
        }

        BigDecimal refundSales = unitSalesPrice.multiply(new BigDecimal(dto.getReturnQty()));
        BigDecimal refundCost = unitCostPrice.multiply(new BigDecimal(dto.getReturnQty()));
        BigDecimal refundMemberCoupon = unitCoupon.multiply(new BigDecimal(dto.getReturnQty()));

        omsOrderMapper.applyPartialRefund(dto.getOrderNo(), refundSales, refundCost, refundMemberCoupon, OrderStatusEnum.PARTIAL_REFUNDED.name());

        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo("TH" + System.currentTimeMillis());
        doc.setDocType("RETURN");
        doc.setCreateTime(LocalDateTime.now());
        doc.setTotalAmount(processInventoryAndLogs(dto.getOrderNo(), detail, dto.getReturnQty(), doc));
        gmsInventoryDocMapper.insert(doc);

        if (order.getMemberId() != null) {
            umsMemberAssetService.processReturn(order.getMemberId(), refundSales, refundMemberCoupon, false, dto.getOrderNo());
        }

        omsOrderMapper.checkAndUpgradeToFullRefund(dto.getOrderNo());

        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());
        orderLog.setDescription("执行部分退货: [" + detail.getGoodsName() + "] x" + dto.getReturnQty() + "，按商品成交价直退");
        omsOrderLogService.save(orderLog);
    }

    private BigDecimal processInventoryAndLogs(String orderNo, OmsOrderDetail detail, int returnQty, GmsInventoryDoc doc) {
        BigDecimal impact = BigDecimal.ZERO;
        GmsGoods goods = gmsGoodsMapper.selectById(detail.getGoodsId());
        if (goods == null) return impact;

        if (goods.getIsCombo() != null && goods.getIsCombo() == 1) {
            List<GmsGoodsCombo> combos = gmsGoodsComboMapper.selectList(new LambdaQueryWrapper<GmsGoodsCombo>().eq(GmsGoodsCombo::getComboGoodsId, goods.getId()));
            for (GmsGoodsCombo c : combos) {
                GmsGoods sub = gmsGoodsMapper.selectById(c.getSubGoodsId());
                if (sub != null) {
                    int qty = Math.multiplyExact(returnQty, c.getSubGoodsQty());

                    // 1. 先原子加库存
                    gmsGoodsMapper.addStockAtomically(sub.getId(), new BigDecimal(qty));

                    // 2. 查出最新库存
                    GmsGoods updatedSub = gmsGoodsMapper.selectById(sub.getId());
                    int latestStock = (updatedSub != null && updatedSub.getStock() != null) ? updatedSub.getStock().intValue() : 0;

                    BigDecimal cost = sub.getAvgCostPrice() != null ? sub.getAvgCostPrice() :
                            (sub.getPurchasePrice() != null ? sub.getPurchasePrice() : BigDecimal.ZERO);
                    impact = impact.add(cost.multiply(new BigDecimal(qty)));

                    // 3. 记录流水，传入最新结余
                    recordStockLog(sub, qty, latestStock, orderNo, cost, "套餐回补");
                }
            }
        } else {
            // 1. 先原子加库存
            gmsGoodsMapper.addStockAtomically(goods.getId(), new BigDecimal(returnQty));

            // 2. 查出最新库存
            GmsGoods updatedGoods = gmsGoodsMapper.selectById(goods.getId());
            int latestStock = (updatedGoods != null && updatedGoods.getStock() != null) ? updatedGoods.getStock().intValue() : 0;

            BigDecimal cost = detail.getPurchasePrice() != null ? detail.getPurchasePrice() : BigDecimal.ZERO;
            impact = cost.multiply(new BigDecimal(returnQty));

            // 3. 记录流水，传入最新结余
            recordStockLog(goods, returnQty, latestStock, orderNo, cost, "单品回补");
        }
        omsOrderDetailMapper.refundGoodsAtomically(detail.getId(), returnQty);
        return impact;
    }

    // 🌟 核心修复：接收 latestStock 参数，并赋值给 afterQuantity
    private void recordStockLog(GmsGoods g, int qty, int latestStock, String orderNo, BigDecimal cost, String remark) {
        GmsStockLog log = new GmsStockLog();
        log.setGoodsId(g.getId());
        log.setGoodsName(g.getName());
        log.setGoodsBarcode(g.getBarcode());
        log.setType("RETURN");
        log.setQuantity(qty);

        // 🌟 赋值结余库存
        log.setAfterQuantity(latestStock);

        log.setOrderNo(orderNo);
        log.setCostPriceSnapshot(cost);
        log.setImpactAmount(cost.multiply(new BigDecimal(qty)));
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());

        gmsStockLogService.save(log);
    }
}