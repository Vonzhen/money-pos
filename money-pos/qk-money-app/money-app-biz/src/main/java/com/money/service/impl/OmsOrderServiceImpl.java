package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.constant.OrderStatusEnum;
import com.money.dto.OmsOrder.*;
import com.money.dto.OmsOrderDetail.OmsOrderDetailVO;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderDetail;
import com.money.entity.OmsOrderLog;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.GmsGoodsMapper;
import com.money.service.OmsOrderDetailService;
import com.money.service.OmsOrderLogService;
import com.money.service.OmsOrderService;
import com.money.service.UmsMemberService;
import com.money.util.PageUtil;
import com.money.web.exception.BaseException;
import com.money.web.vo.PageVO;
import com.money.web.util.BeanMapUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OmsOrderServiceImpl extends ServiceImpl<OmsOrderMapper, OmsOrder> implements OmsOrderService {

    private final OmsOrderDetailService omsOrderDetailService;
    private final OmsOrderLogService omsOrderLogService;
    private final UmsMemberService umsMemberService;
    private final GmsGoodsMapper gmsGoodsMapper;

    @Override
    public PageVO<OmsOrderVO> list(OmsOrderQueryDTO queryDTO) {
        Page<OmsOrder> page = this.lambdaQuery()
                .eq(queryDTO.getMember() != null, OmsOrder::getMember, queryDTO.getMember())
                .eq(queryDTO.getOrderNo() != null, OmsOrder::getOrderNo, queryDTO.getOrderNo())
                .eq(queryDTO.getStatus() != null, OmsOrder::getStatus, queryDTO.getStatus())
                .between(queryDTO.getStartTime() != null && queryDTO.getEndTime() != null,
                        OmsOrder::getPaymentTime, queryDTO.getStartTime(), queryDTO.getEndTime())
                .orderByDesc(OmsOrder::getPaymentTime)
                .page(PageUtil.toPage(queryDTO));
        return PageUtil.toPageVO(page, OmsOrderVO::new);
    }

    @Override
    public OrderCountVO countOrderAndSales(LocalDateTime startTime, LocalDateTime endTime) {
        List<OmsOrder> list = this.lambdaQuery()
                .between(startTime != null && endTime != null, OmsOrder::getPaymentTime, startTime, endTime)
                .in(OmsOrder::getStatus, Arrays.asList(OrderStatusEnum.PAID.name(), OrderStatusEnum.PARTIAL_REFUNDED.name()))
                .list();

        OrderCountVO vo = new OrderCountVO();
        vo.setOrderCount((long) list.size());

        // 1. 计算总销售额
        BigDecimal totalSales = list.stream().map(OmsOrder::getFinalSalesAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setTotalSales(totalSales);
        vo.setSaleCount(totalSales); // 🌟 同时塞给首页大盘用的字段

        // 2. 计算总成本
        BigDecimal totalCost = list.stream().map(o -> o.getCostAmount() != null ? o.getCostAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setCostCount(totalCost); // 🌟 同时塞给首页大盘用的字段

        // 3. 计算利润
        vo.setProfit(totalSales.subtract(totalCost));
        return vo;
    }

    @Override
    public OrderDetailVO getOrderDetail(Long id) {
        OmsOrder order = this.getById(id);
        if (order == null) throw new BaseException("订单不存在");

        OrderDetailVO vo = BeanMapUtil.to(order, OrderDetailVO::new);
        List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>().eq(OmsOrderDetail::getOrderNo, order.getOrderNo()));
        vo.setOrderDetails(BeanMapUtil.to(details, OmsOrderDetailVO::new)); // 🌟 对应新补齐的字段
        return vo;
    }

    @Override
    public PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO) {
        Page<OmsOrder> page = this.lambdaQuery()
                .between(queryDTO.getStartTime() != null && queryDTO.getEndTime() != null,
                        OmsOrder::getPaymentTime, queryDTO.getStartTime(), queryDTO.getEndTime())
                .orderByDesc(OmsOrder::getPaymentTime)
                .page(PageUtil.toPage(queryDTO));
        return PageUtil.toPageVO(page, ProfitAuditVO::new);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnOrder(String orderNo) {
        boolean lockSuccess = this.lambdaUpdate()
                .set(OmsOrder::getStatus, OrderStatusEnum.REFUNDED.name())
                .eq(OmsOrder::getOrderNo, orderNo)
                .in(OmsOrder::getStatus, Arrays.asList(OrderStatusEnum.PAID.name(), OrderStatusEnum.PARTIAL_REFUNDED.name()))
                .update();

        if (!lockSuccess) throw new BaseException("订单不可退或已处理");

        OmsOrder order = this.getOne(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getOrderNo, orderNo));
        List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>().eq(OmsOrderDetail::getOrderNo, order.getOrderNo()));

        for (OmsOrderDetail detail : details) {
            int canReturnQty = detail.getQuantity() - (detail.getReturnQuantity() != null ? detail.getReturnQuantity() : 0);
            if (canReturnQty > 0) {
                omsOrderDetailService.lambdaUpdate()
                        .set(OmsOrderDetail::getStatus, OrderStatusEnum.REFUNDED.name())
                        .setSql("return_quantity = return_quantity + " + canReturnQty)
                        .eq(OmsOrderDetail::getId, detail.getId())
                        .update();
                gmsGoodsMapper.addStockAtomically(detail.getGoodsId(), new BigDecimal(canReturnQty));
            }
        }
        if (order.getVip() != null && order.getVip()) {
            umsMemberService.processReturn(order.getMemberId(), order.getPayAmount(), order.getUseVoucherAmount(), true, orderNo);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnGoods(ReturnGoodsDTO dto) {
        boolean detailLock = omsOrderDetailService.lambdaUpdate()
                .setSql("return_quantity = return_quantity + " + dto.getReturnQty())
                .set(OmsOrderDetail::getStatus, OrderStatusEnum.PARTIAL_REFUNDED.name())
                .eq(OmsOrderDetail::getId, dto.getDetailId())
                .apply("quantity - IFNULL(return_quantity, 0) >= {0}", dto.getReturnQty())
                .update();

        if (!detailLock) throw new BaseException("退货数量非法");

        this.lambdaUpdate().set(OmsOrder::getStatus, OrderStatusEnum.PARTIAL_REFUNDED.name())
                .eq(OmsOrder::getOrderNo, dto.getOrderNo()).eq(OmsOrder::getStatus, OrderStatusEnum.PAID.name()).update();

        OmsOrderDetail detail = omsOrderDetailService.getById(dto.getDetailId());
        OmsOrder order = this.getOne(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getOrderNo, dto.getOrderNo()));
        gmsGoodsMapper.addStockAtomically(detail.getGoodsId(), new BigDecimal(dto.getReturnQty()));

        if (order.getVip() != null && order.getVip()) {
            umsMemberService.processReturn(order.getMemberId(), detail.getGoodsPrice().multiply(new BigDecimal(dto.getReturnQty())),
                    detail.getCoupon().multiply(new BigDecimal(dto.getReturnQty())), false, dto.getOrderNo());
        }
    }
}