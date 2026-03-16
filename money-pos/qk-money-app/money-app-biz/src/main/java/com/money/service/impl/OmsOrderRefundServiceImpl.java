package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.OrderStatusEnum;
import com.money.constant.PayMethodEnum;
import com.money.dto.OmsOrder.ReturnGoodsDTO;
import com.money.entity.*;
import com.money.mapper.*;
import com.money.service.GmsStockLogService;
import com.money.service.OmsOrderLogService; // 🌟 引入日志服务
import com.money.service.OmsOrderRefundService;
import com.money.service.UmsMemberService;
import com.money.util.MoneyUtil;
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
    private final UmsMemberService umsMemberService;

    // 引入审计流水线
    private final GmsStockLogService gmsStockLogService;
    private final GmsInventoryDocMapper gmsInventoryDocMapper;
    private final GmsInventoryDocItemMapper gmsInventoryDocItemMapper;

    // 🌟 引入订单操作日志服务
    private final OmsOrderLogService omsOrderLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnOrder(String orderNo) {
        OmsOrder order = omsOrderMapper.selectOne(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getOrderNo, orderNo));
        if (order == null || OrderStatusEnum.REFUNDED.name().equals(order.getStatus())) {
            throw new BaseException("订单不存在或已全额退款");
        }

        BigDecimal currentNetSales = order.getFinalSalesAmount() != null ? order.getFinalSalesAmount() : order.getPayAmount();
        BigDecimal totalCouponToReturn = MoneyUtil.add(
                order.getCouponAmount() != null ? order.getCouponAmount() : BigDecimal.ZERO,
                order.getUseVoucherAmount() != null ? order.getUseVoucherAmount() : BigDecimal.ZERO
        );

        // 1. 物理对冲主表
        int updated = omsOrderMapper.updateRefundStatusToFull(orderNo, OrderStatusEnum.REFUNDED.name());
        if (updated == 0) throw new BaseException("状态流转失败");

        // 2. 库存回滚 + 生成审计单据
        List<OmsOrderDetail> details = omsOrderDetailMapper.selectList(new LambdaQueryWrapper<OmsOrderDetail>().eq(OmsOrderDetail::getOrderNo, orderNo));

        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo("TH" + System.currentTimeMillis());
        doc.setDocType("RETURN");
        doc.setRemark("整单退款回滚，关联单号：" + orderNo);
        doc.setCreateTime(LocalDateTime.now());

        BigDecimal totalCostAmount = BigDecimal.ZERO;
        boolean hasReturnItem = false;

        for (OmsOrderDetail d : details) {
            int canReturn = d.getQuantity() - Optional.ofNullable(d.getReturnQuantity()).orElse(0);
            if (canReturn > 0) {
                hasReturnItem = true;

                GmsGoods goods = gmsGoodsMapper.selectById(d.getGoodsId());
                long preStock = goods != null && goods.getStock() != null ? goods.getStock() : 0L;
                long afterStock = preStock + canReturn;

                omsOrderDetailMapper.refundGoodsAtomically(d.getId(), canReturn);
                gmsGoodsMapper.addStockAtomically(d.getGoodsId(), new BigDecimal(canReturn));

                BigDecimal cost = d.getPurchasePrice() != null ? d.getPurchasePrice() : BigDecimal.ZERO;
                BigDecimal impactAmount = cost.multiply(new BigDecimal(canReturn));
                totalCostAmount = totalCostAmount.add(impactAmount);

                // 记台账流水
                GmsStockLog log = new GmsStockLog();
                log.setGoodsId(d.getGoodsId());
                log.setGoodsName(d.getGoodsName());
                log.setGoodsBarcode(d.getGoodsBarcode());
                log.setType("RETURN");
                log.setQuantity(canReturn);
                log.setAfterQuantity((int) afterStock);
                log.setOrderNo(orderNo);
                log.setCostPriceSnapshot(cost);
                log.setImpactAmount(impactAmount);
                log.setRemark("整单退款回滚");
                log.setCreateTime(LocalDateTime.now());
                gmsStockLogService.save(log);

                // 记单据明细
                GmsInventoryDocItem item = new GmsInventoryDocItem();
                item.setDocNo(doc.getDocNo());
                item.setGoodsId(d.getGoodsId());
                item.setGoodsName(d.getGoodsName());
                item.setBarcode(d.getGoodsBarcode());
                item.setChangeQty(canReturn);
                item.setCostPrice(cost);
                item.setPreStock(preStock);
                item.setAfterStock(afterStock);
                gmsInventoryDocItemMapper.insert(item);
            }
        }

        if (hasReturnItem) {
            doc.setTotalAmount(totalCostAmount);
            gmsInventoryDocMapper.insert(doc);
        }

        // 3. 会员资产精准回滚
        if (order.getMemberId() != null) {
            umsMemberService.processReturn(order.getMemberId(), currentNetSales, totalCouponToReturn, true, orderNo);

            List<OmsOrderPay> pays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>().eq(OmsOrderPay::getOrderNo, orderNo));
            for (OmsOrderPay pay : pays) {
                if (PayMethodEnum.fromCode(pay.getPayMethodCode()) == PayMethodEnum.BALANCE) {
                    umsMemberService.deductBalance(order.getMemberId(), pay.getPayAmount().negate(), orderNo, "整单退款退回余额");
                }
            }
        }

        // 🌟 4. 补齐操作记录：记入订单日志
        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());
        orderLog.setDescription("执行整单退款操作");
        omsOrderLogService.save(orderLog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnGoods(ReturnGoodsDTO dto) {
        OmsOrderDetail detail = omsOrderDetailMapper.selectById(dto.getDetailId());
        OmsOrder order = omsOrderMapper.selectOne(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getOrderNo, dto.getOrderNo()));
        if (detail == null || order == null) throw new BaseException("单据数据异常");

        int alreadyReturned = Optional.ofNullable(detail.getReturnQuantity()).orElse(0);
        if (dto.getReturnQty() <= 0 || dto.getReturnQty() > (detail.getQuantity() - alreadyReturned)) {
            throw new BaseException("退货数量超出该明细可退上限");
        }

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
        BigDecimal impactAmount = refundCost;

        // 1. 执行主表原子对冲
        omsOrderMapper.applyPartialRefund(dto.getOrderNo(), refundSales, refundCost, refundMemberCoupon, OrderStatusEnum.PARTIAL_REFUNDED.name());

        // 2. 扣减库存 + 生成审计单据
        GmsGoods goods = gmsGoodsMapper.selectById(detail.getGoodsId());
        long preStock = goods != null && goods.getStock() != null ? goods.getStock() : 0L;
        long afterStock = preStock + dto.getReturnQty();

        omsOrderDetailMapper.refundGoodsAtomically(dto.getDetailId(), dto.getReturnQty());
        gmsGoodsMapper.addStockAtomically(detail.getGoodsId(), new BigDecimal(dto.getReturnQty()));

        // 记台账流水
        GmsStockLog log = new GmsStockLog();
        log.setGoodsId(detail.getGoodsId());
        log.setGoodsName(detail.getGoodsName());
        log.setGoodsBarcode(detail.getGoodsBarcode());
        log.setType("RETURN");
        log.setQuantity(dto.getReturnQty());
        log.setAfterQuantity((int) afterStock);
        log.setOrderNo(dto.getOrderNo());
        log.setCostPriceSnapshot(unitCostPrice);
        log.setImpactAmount(impactAmount);
        log.setRemark("部分退货回滚");
        log.setCreateTime(LocalDateTime.now());
        gmsStockLogService.save(log);

        // 记出入库单据
        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo("TH" + System.currentTimeMillis());
        doc.setDocType("RETURN");
        doc.setRemark("部分退货回滚，关联单号：" + dto.getOrderNo());
        doc.setCreateTime(LocalDateTime.now());
        doc.setTotalAmount(impactAmount);
        gmsInventoryDocMapper.insert(doc);

        GmsInventoryDocItem item = new GmsInventoryDocItem();
        item.setDocNo(doc.getDocNo());
        item.setGoodsId(detail.getGoodsId());
        item.setGoodsName(detail.getGoodsName());
        item.setBarcode(detail.getGoodsBarcode());
        item.setChangeQty(dto.getReturnQty());
        item.setCostPrice(unitCostPrice);
        item.setPreStock(preStock);
        item.setAfterStock(afterStock);
        gmsInventoryDocItemMapper.insert(item);

        // 3. 会员资产精准回滚
        if (order.getMemberId() != null) {
            umsMemberService.processReturn(order.getMemberId(), refundSales, refundMemberCoupon, false, dto.getOrderNo());
        }

        // 4. 状态自动机检查
        omsOrderMapper.checkAndUpgradeToFullRefund(dto.getOrderNo());

        // 🌟 5. 补齐操作记录：记入订单日志
        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());
        orderLog.setDescription("执行部分退货: [" + detail.getGoodsName() + "] x" + dto.getReturnQty());
        omsOrderLogService.save(orderLog);
    }
}