package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.constant.BizErrorStatus;
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
        if (order == null) throw new BaseException(BizErrorStatus.POS_REFUND_NOT_FOUND).withData(orderNo);

        if (OrderStatusEnum.REFUNDED.name().equals(order.getStatus())) {
            throw new BaseException(BizErrorStatus.POS_REFUND_REPEAT).withData(orderNo);
        }

        boolean isLocked = omsOrderMapper.update(null, new LambdaUpdateWrapper<OmsOrder>()
                .eq(OmsOrder::getOrderNo, orderNo)
                .ne(OmsOrder::getStatus, OrderStatusEnum.REFUNDED.name())
                .set(OmsOrder::getUpdateTime, LocalDateTime.now())) > 0;

        if (!isLocked) {
            log.warn("拦截到重复的整单退款请求: {}", orderNo);
            throw new BaseException(BizErrorStatus.POS_REFUND_REPEAT).withData(orderNo);
        }

        omsOrderMapper.updateRefundStatusToFull(orderNo, OrderStatusEnum.REFUNDED.name());

        List<OmsOrderDetail> details = omsOrderDetailMapper.selectList(new LambdaQueryWrapper<OmsOrderDetail>().eq(OmsOrderDetail::getOrderNo, orderNo));

        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo("TH" + System.currentTimeMillis());
        doc.setDocType("RETURN");
        doc.setCreateTime(LocalDateTime.now());
        gmsInventoryDocMapper.insert(doc);

        BigDecimal totalCost = BigDecimal.ZERO;
        for (OmsOrderDetail d : details) {
            int canReturn = d.getQuantity() - Optional.ofNullable(d.getReturnQuantity()).orElse(0);
            if (canReturn > 0) {
                totalCost = totalCost.add(processInventoryAndLogs(orderNo, d, canReturn, doc));
            }
        }

        doc.setTotalAmount(totalCost);
        gmsInventoryDocMapper.updateById(doc);

        if (order.getMemberId() != null) {
            // 🌟 防护墙：因为传入的 order.getCouponAmount() 已经是完全干净的数据(免券=0)
            // 所以底层 processReturn 无需修改，它会自动根据 0 来跳过退券
            umsMemberAssetService.processReturn(order.getMemberId(), order.getFinalSalesAmount(), order.getCouponAmount(), true, orderNo);

            if (order.getUseVoucherAmount() != null && order.getUseVoucherAmount().compareTo(BigDecimal.ZERO) > 0) {
                Long voucherCount = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>().eq(PosMemberCoupon::getOrderNo, orderNo));
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
        if (detail == null || order == null) {
            throw new BaseException(BizErrorStatus.POS_REFUND_NOT_FOUND).withData(dto);
        }

        if (!detail.getOrderNo().equals(order.getOrderNo())) {
            log.error("触发越权/串单退货拦截! 请求单号:{}, 实际明细归属单号:{}", dto.getOrderNo(), detail.getOrderNo());
            throw new BaseException(BizErrorStatus.POS_REFUND_NOT_FOUND).withData(dto.getOrderNo());
        }

        BigDecimal unitSalesPrice = detail.getGoodsPrice() != null ? detail.getGoodsPrice() : BigDecimal.ZERO;
        BigDecimal unitCostPrice = detail.getPurchasePrice() != null ? detail.getPurchasePrice() : BigDecimal.ZERO;

        BigDecimal totalDetailCoupon = detail.getCoupon() != null ? detail.getCoupon() : BigDecimal.ZERO;

        // ==========================================
        // 🌟 核心修复 4：终极防火墙！
        // 如果整单本身根本没有发生扣券行为（免券单，或者全单都是单轨），
        // 强制抹除明细表的券均摊，绝对禁止无中生有乱退资产！
        // ==========================================
        if (order.getCouponAmount() == null || order.getCouponAmount().compareTo(BigDecimal.ZERO) <= 0) {
            totalDetailCoupon = BigDecimal.ZERO;
        }

        BigDecimal unitCoupon = BigDecimal.ZERO;
        if (detail.getQuantity() > 0 && totalDetailCoupon.compareTo(BigDecimal.ZERO) > 0) {
            // 这里现在极度安全，因为明细表的 coupon 已经被洗成了真实快照
            unitCoupon = totalDetailCoupon.divide(new BigDecimal(detail.getQuantity()), 4, RoundingMode.HALF_UP);
        }

        BigDecimal refundSales = unitSalesPrice.multiply(new BigDecimal(dto.getReturnQty()));
        BigDecimal refundCost = unitCostPrice.multiply(new BigDecimal(dto.getReturnQty()));
        BigDecimal refundMemberCoupon = unitCoupon.multiply(new BigDecimal(dto.getReturnQty()));

        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo("TH" + System.currentTimeMillis());
        doc.setDocType("RETURN");
        doc.setCreateTime(LocalDateTime.now());
        gmsInventoryDocMapper.insert(doc);

        BigDecimal cost = processInventoryAndLogs(dto.getOrderNo(), detail, dto.getReturnQty(), doc);

        doc.setTotalAmount(cost);
        gmsInventoryDocMapper.updateById(doc);

        omsOrderMapper.applyPartialRefund(dto.getOrderNo(), refundSales, refundCost, refundMemberCoupon, OrderStatusEnum.PARTIAL_REFUNDED.name());

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
        int updatedRows = omsOrderDetailMapper.refundGoodsAtomically(detail.getId(), returnQty);
        if (updatedRows != 1) {
            log.error("明细退货数量更新失败(可能超退或并发)。明细ID:{}, 请求退数量:{}", detail.getId(), returnQty);
            throw new BaseException(BizErrorStatus.POS_REFUND_QTY_INVALID).withData("请求退数量:" + returnQty);
        }

        BigDecimal impact = BigDecimal.ZERO;
        GmsGoods goods = gmsGoodsMapper.selectById(detail.getGoodsId());

        if (goods == null) {
            log.error("退货异常：商品档案不存在，商品ID: {}", detail.getGoodsId());
            throw new BaseException(BizErrorStatus.GOODS_NOT_FOUND).withData(detail.getGoodsId());
        }

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