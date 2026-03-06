package com.money.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.web.exception.BaseException;
import com.money.web.util.BeanMapUtil;
import com.money.web.vo.PageVO;
import com.money.constant.OrderStatusEnum;
import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.dto.OmsOrder.OmsOrderVO;
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.dto.OmsOrder.OrderDetailVO;
import com.money.dto.OmsOrder.ReturnGoodsDTO;
import com.money.dto.OmsOrderDetail.OmsOrderDetailVO;
import com.money.dto.OmsOrderLog.OmsOrderLogVO;
import com.money.dto.UmsMember.UmsMemberVO;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderDetail;
import com.money.entity.OmsOrderLog;
import com.money.mapper.OmsOrderMapper;
import com.money.service.GmsGoodsService;
import com.money.service.OmsOrderDetailService;
import com.money.service.OmsOrderLogService;
import com.money.service.OmsOrderService;
import com.money.service.UmsMemberService;
import com.money.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class OmsOrderServiceImpl extends ServiceImpl<OmsOrderMapper, OmsOrder> implements OmsOrderService {

    private final UmsMemberService umsMemberService;
    private final GmsGoodsService gmsGoodsService;
    private final OmsOrderDetailService omsOrderDetailService;
    private final OmsOrderLogService omsOrderLogService;
    private final com.money.mapper.GmsStockLogMapper gmsStockLogMapper;

    @Override
    public PageVO<OmsOrderVO> list(OmsOrderQueryDTO queryDTO) {
        Page<OmsOrder> page = this.lambdaQuery().eq(StrUtil.isNotBlank(queryDTO.getStatus()), OmsOrder::getStatus, queryDTO.getStatus())
                .like(StrUtil.isNotBlank(queryDTO.getOrderNo()), OmsOrder::getOrderNo, queryDTO.getOrderNo())
                .like(StrUtil.isNotBlank(queryDTO.getMember()), OmsOrder::getMember, queryDTO.getMember())
                .ge(queryDTO.getStartTime() != null, OmsOrder::getCreateTime, queryDTO.getStartTime())
                .le(queryDTO.getEndTime() != null, OmsOrder::getCreateTime, queryDTO.getEndTime())
                .orderByDesc(StrUtil.isBlank(queryDTO.getOrderBy()), OmsOrder::getCreateTime)
                .last(StrUtil.isNotBlank(queryDTO.getOrderBy()), queryDTO.getOrderBySql())
                .page(PageUtil.toPage(queryDTO));
        return PageUtil.toPageVO(page, OmsOrderVO::new);
    }

    @Override
    public OrderCountVO countOrderAndSales(LocalDateTime startTime, LocalDateTime endTime) {
        List<OmsOrderDetail> omsOrderDetails = omsOrderDetailService.lambdaQuery()
                .select(OmsOrderDetail::getOrderNo, OmsOrderDetail::getQuantity, OmsOrderDetail::getReturnQuantity,
                        OmsOrderDetail::getGoodsPrice, OmsOrderDetail::getPurchasePrice)
                .eq(OmsOrderDetail::getStatus, OrderStatusEnum.PAID)
                .ge(startTime != null, OmsOrderDetail::getCreateTime, startTime)
                .le(endTime != null, OmsOrderDetail::getCreateTime, endTime)
                .list();
        long count = omsOrderDetails.stream().map(OmsOrderDetail::getOrderNo).distinct().count();
        BigDecimal saleCount = BigDecimal.ZERO;
        BigDecimal costCount = BigDecimal.ZERO;
        for (OmsOrderDetail omsOrderDetail : omsOrderDetails) {
            int quantity = omsOrderDetail.getQuantity() - omsOrderDetail.getReturnQuantity();
            saleCount = saleCount.add(omsOrderDetail.getGoodsPrice().multiply(new BigDecimal(quantity)));
            costCount = costCount.add(omsOrderDetail.getPurchasePrice().multiply(new BigDecimal(quantity)));
        }
        OrderCountVO vo = new OrderCountVO();
        vo.setOrderCount(count);
        vo.setSaleCount(saleCount);
        vo.setCostCount(costCount);
        vo.setProfit(saleCount.subtract(costCount));
        return vo;
    }

    // =========================================================================
    // 🌟 核心重构：所有订单详情的获取，必须经过这个“绝对计算中枢”！
    // =========================================================================
    @Override
    public OrderDetailVO getOrderDetail(Long id) {
        OrderDetailVO vo = new OrderDetailVO();
        OmsOrder order = this.getById(id);
        if (order == null) throw new BaseException("订单不存在");

        OmsOrderVO orderVO = BeanMapUtil.to(order, OmsOrderVO::new);

        // 🌟 贯彻解耦定律：在这里对所有的金额进行终极配平，确保前后端统一。
        BigDecimal total = orderVO.getTotalAmount() != null ? orderVO.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal pay = orderVO.getPayAmount() != null ? orderVO.getPayAmount() : BigDecimal.ZERO;
        BigDecimal coupon = orderVO.getCouponAmount() != null ? orderVO.getCouponAmount() : BigDecimal.ZERO;
        BigDecimal voucher = orderVO.getUseVoucherAmount() != null ? orderVO.getUseVoucherAmount() : BigDecimal.ZERO;

        // 公式兜底计算：解决历史老订单没有存“整单优惠”字段的问题
        // 整单优惠 = 应收总价 - 实付 - 会员券 - 满减券
        BigDecimal manual = total.subtract(pay).subtract(coupon).subtract(voucher);
        orderVO.setManualDiscountAmount(manual.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : manual);

        // 装入统一的数据包装盒
        vo.setOrder(orderVO);
        vo.setMember(BeanMapUtil.to(umsMemberService.getById(order.getMemberId()), UmsMemberVO::new));
        vo.setOrderDetail(BeanMapUtil.to(omsOrderDetailService.listByOrderNo(order.getOrderNo()), OmsOrderDetailVO::new));
        vo.setOrderLog(BeanMapUtil.to(omsOrderLogService.listByOrderId(id), OmsOrderLogVO::new));

        return vo;
    }

    @Override
    public void returnOrder(Set<Long> ids) {
        ids.stream().map(this::getById).forEach(order -> {
            List<OmsOrderDetail> orderDetails = omsOrderDetailService.listByOrderNo(order.getOrderNo());
            AtomicReference<BigDecimal> returnPrice = new AtomicReference<>(BigDecimal.ZERO);
            AtomicReference<BigDecimal> returnCoupon = new AtomicReference<>(BigDecimal.ZERO);
            orderDetails.forEach(orderDetail -> {
                int returnQty = orderDetail.getQuantity() - orderDetail.getReturnQuantity();
                returnPrice.set(returnPrice.get().add(orderDetail.getGoodsPrice().multiply(new BigDecimal(returnQty))));
                returnCoupon.set(returnCoupon.get().add(orderDetail.getCoupon().multiply(new BigDecimal(returnQty))));
                gmsGoodsService.updateStock(orderDetail.getGoodsId(), returnQty);

                com.money.entity.GmsGoods goods = gmsGoodsService.getById(orderDetail.getGoodsId());
                com.money.entity.GmsStockLog stockLog = new com.money.entity.GmsStockLog();
                stockLog.setGoodsId(goods.getId());
                stockLog.setGoodsName(goods.getName());
                stockLog.setGoodsBarcode(goods.getBarcode());
                stockLog.setType("RETURN");
                stockLog.setQuantity(returnQty);
                stockLog.setAfterQuantity(goods.getStock() == null ? 0 : goods.getStock().intValue());
                stockLog.setOrderNo(order.getOrderNo());
                stockLog.setRemark("整单退款，商品退回仓库");
                stockLog.setCreateTime(LocalDateTime.now());
                gmsStockLogMapper.insert(stockLog);
            });
            umsMemberService.rebate(order.getMemberId(), returnPrice.get(), returnCoupon.get(), true);
            order.setFinalSalesAmount(BigDecimal.ZERO);
            order.setStatus(OrderStatusEnum.RETURN.name());
            this.updateById(order);
            omsOrderDetailService.updateBatchById(orderDetails);
            OmsOrderLog log = new OmsOrderLog();
            log.setOrderId(order.getId());
            log.setDescription("<span style=\"color:red\">退单</span>");
            omsOrderLogService.save(log);
        });
    }

    @Override
    public void returnGoods(ReturnGoodsDTO returnGoodsDTO) {
        Integer returnQty = returnGoodsDTO.getQuantity();
        OmsOrderDetail orderDetail = omsOrderDetailService.getById(returnGoodsDTO.getId());
        orderDetail.setReturnQuantity(orderDetail.getReturnQuantity() + returnQty);
        if (orderDetail.getReturnQuantity().compareTo(orderDetail.getQuantity()) == 0) {
            orderDetail.setStatus(OrderStatusEnum.RETURN.name());
        } else if (orderDetail.getReturnQuantity().compareTo(orderDetail.getQuantity()) > 0) {
            throw new BaseException("退货数量不能超过商品数量");
        }
        omsOrderDetailService.updateById(orderDetail);

        OmsOrder order = this.lambdaQuery().eq(OmsOrder::getOrderNo, orderDetail.getOrderNo()).one();
        BigDecimal returnPrice = orderDetail.getGoodsPrice().multiply(new BigDecimal(returnQty));
        BigDecimal finalSalesAmount = order.getFinalSalesAmount().subtract(returnPrice);
        order.setFinalSalesAmount(finalSalesAmount);
        this.updateById(order);

        BigDecimal returnCoupon = orderDetail.getCoupon().multiply(new BigDecimal(returnQty));
        umsMemberService.rebate(order.getMemberId(), returnPrice, returnCoupon, false);
        gmsGoodsService.updateStock(orderDetail.getGoodsId(), returnQty);

        com.money.entity.GmsGoods goods = gmsGoodsService.getById(orderDetail.getGoodsId());
        com.money.entity.GmsStockLog stockLog = new com.money.entity.GmsStockLog();
        stockLog.setGoodsId(goods.getId());
        stockLog.setGoodsName(goods.getName());
        stockLog.setGoodsBarcode(goods.getBarcode());
        stockLog.setType("RETURN");
        stockLog.setQuantity(returnQty);
        stockLog.setAfterQuantity(goods.getStock() == null ? 0 : goods.getStock().intValue());
        stockLog.setOrderNo(order.getOrderNo());
        stockLog.setRemark("单品售后退货");
        stockLog.setCreateTime(LocalDateTime.now());
        gmsStockLogMapper.insert(stockLog);

        OmsOrderLog log = new OmsOrderLog();
        log.setOrderId(order.getId());
        log.setDescription("<span style=\"color:red\">退货</span>" + orderDetail.getGoodsName() + " X " + returnQty);
        omsOrderLogService.save(log);
    }
}