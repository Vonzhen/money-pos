package com.money.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.money.entity.UmsMemberBrandLevel;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;
    private final com.money.mapper.OmsOrderPayMapper omsOrderPayMapper;

    @Override
    public PageVO<OmsOrderVO> list(OmsOrderQueryDTO queryDTO) {
        LambdaQueryWrapper<OmsOrder> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(StrUtil.isNotBlank(queryDTO.getStatus()), OmsOrder::getStatus, queryDTO.getStatus())
                .like(StrUtil.isNotBlank(queryDTO.getOrderNo()), OmsOrder::getOrderNo, queryDTO.getOrderNo())
                .ge(queryDTO.getStartTime() != null, OmsOrder::getCreateTime, queryDTO.getStartTime())
                .le(queryDTO.getEndTime() != null, OmsOrder::getCreateTime, queryDTO.getEndTime());

        if (StrUtil.isNotBlank(queryDTO.getMember())) {
            String keyword = queryDTO.getMember();
            if (keyword.matches("^1[3-9]\\d{9}$") || keyword.matches("^\\d+$")) {
                List<Long> memberIds = umsMemberService.lambdaQuery()
                        .like(com.money.entity.UmsMember::getPhone, keyword)
                        .list()
                        .stream()
                        .map(com.money.entity.UmsMember::getId)
                        .collect(java.util.stream.Collectors.toList());

                if (!memberIds.isEmpty()) {
                    wrapper.in(OmsOrder::getMemberId, memberIds);
                } else {
                    wrapper.eq(OmsOrder::getId, -1L);
                }
            } else {
                wrapper.like(OmsOrder::getMember, keyword);
            }
        }

        wrapper.orderByDesc(StrUtil.isBlank(queryDTO.getOrderBy()), OmsOrder::getCreateTime)
                .last(StrUtil.isNotBlank(queryDTO.getOrderBy()), queryDTO.getOrderBySql());

        Page<OmsOrder> page = this.page(PageUtil.toPage(queryDTO), wrapper);
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

    @Override
    public OrderDetailVO getOrderDetail(Long id) {
        OrderDetailVO vo = new OrderDetailVO();
        OmsOrder order = this.getById(id);
        if (order == null) throw new BaseException("订单不存在");

        OmsOrderVO orderVO = BeanMapUtil.to(order, OmsOrderVO::new);

        BigDecimal total = orderVO.getTotalAmount() != null ? orderVO.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal pay = orderVO.getPayAmount() != null ? orderVO.getPayAmount() : BigDecimal.ZERO;
        BigDecimal coupon = orderVO.getCouponAmount() != null ? orderVO.getCouponAmount() : BigDecimal.ZERO;
        BigDecimal voucher = orderVO.getUseVoucherAmount() != null ? orderVO.getUseVoucherAmount() : BigDecimal.ZERO;

        BigDecimal manual = total.subtract(pay).subtract(coupon).subtract(voucher);
        orderVO.setManualDiscountAmount(manual.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : manual);

        vo.setOrder(orderVO);

        UmsMemberVO memberVO = null;
        if (order.getMemberId() != null) {
            memberVO = BeanMapUtil.to(umsMemberService.getById(order.getMemberId()), UmsMemberVO::new);
            if (memberVO != null) {
                List<UmsMemberBrandLevel> levels = umsMemberBrandLevelMapper.selectList(
                        new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, memberVO.getId())
                );
                Map<String, String> levelMap = new HashMap<>();
                for (UmsMemberBrandLevel bl : levels) {
                    levelMap.put(bl.getBrand(), bl.getLevelCode());
                }
                memberVO.setBrandLevels(levelMap);
            }
        }
        vo.setMember(memberVO);

        vo.setOrderDetail(BeanMapUtil.to(omsOrderDetailService.listByOrderNo(order.getOrderNo()), OmsOrderDetailVO::new));
        vo.setOrderLog(BeanMapUtil.to(omsOrderLogService.listByOrderId(id), OmsOrderLogVO::new));

        List<com.money.entity.OmsOrderPay> payments = omsOrderPayMapper.selectList(
                new LambdaQueryWrapper<com.money.entity.OmsOrderPay>()
                        .eq(com.money.entity.OmsOrderPay::getOrderNo, order.getOrderNo())
        );
        vo.setPayments(payments);

        BigDecimal balanceAmount = BigDecimal.ZERO;
        BigDecimal scanAmount = BigDecimal.ZERO;
        BigDecimal cashAmount = BigDecimal.ZERO;

        if (payments != null) {
            for (com.money.entity.OmsOrderPay p : payments) {
                String code = p.getPayMethodCode() != null ? p.getPayMethodCode().toUpperCase() : "";
                BigDecimal amt = p.getPayAmount() != null ? p.getPayAmount() : BigDecimal.ZERO;

                if (code.contains("BALANCE")) {
                    balanceAmount = balanceAmount.add(amt);
                } else if (code.contains("CASH")) {
                    cashAmount = cashAmount.add(amt);
                } else {
                    scanAmount = scanAmount.add(amt);
                }
            }
        }

        vo.setBalanceAmount(balanceAmount);
        vo.setScanAmount(scanAmount);
        vo.setCashAmount(cashAmount);

        return vo;
    }

    @Override
    public void returnOrder(Set<Long> ids) {
        ids.stream().map(this::getById).forEach(order -> {
            List<OmsOrderDetail> orderDetails = omsOrderDetailService.listByOrderNo(order.getOrderNo());

            // 库存退回必须循环
            orderDetails.forEach(orderDetail -> {
                int returnQty = orderDetail.getQuantity() - orderDetail.getReturnQuantity();
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

            // 🌟 核心修复：整单退款时，扣除会员的历史消费额必须等于这单当时的最终实付款 (而不是通过明细的原价去算)，这样绝不扣超！
            BigDecimal realReturnPrice = order.getFinalSalesAmount() != null ? order.getFinalSalesAmount() : (order.getPayAmount() != null ? order.getPayAmount() : BigDecimal.ZERO);
            BigDecimal realReturnCoupon = order.getCouponAmount() != null ? order.getCouponAmount() : BigDecimal.ZERO;

            umsMemberService.processReturn(order.getMemberId(), realReturnPrice, realReturnCoupon, true, order.getOrderNo());

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

        // 🌟 核心修复防卫：部分退货时，保证扣减的金额不能超过整单的剩余实付金额，防止扣穿底线！
        BigDecimal theoryReturnPrice = orderDetail.getGoodsPrice().multiply(new BigDecimal(returnQty));
        BigDecimal theoryReturnCoupon = orderDetail.getCoupon().multiply(new BigDecimal(returnQty));

        BigDecimal actualReturnPrice = theoryReturnPrice;
        if (order.getFinalSalesAmount() != null && theoryReturnPrice.compareTo(order.getFinalSalesAmount()) > 0) {
            actualReturnPrice = order.getFinalSalesAmount(); // 最多只能退到0
        }

        BigDecimal finalSalesAmount = order.getFinalSalesAmount().subtract(actualReturnPrice);
        order.setFinalSalesAmount(finalSalesAmount);
        this.updateById(order);

        // 调用新的退回方法 processReturn
        umsMemberService.processReturn(order.getMemberId(), actualReturnPrice, theoryReturnCoupon, false, order.getOrderNo());

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

    @Override
    public PageVO<com.money.dto.OmsOrder.ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO) {
        Page<?> page = PageUtil.toPage(queryDTO);
        Boolean anomalyOnly = "ANOMALY".equals(queryDTO.getStatus());
        Page<com.money.dto.OmsOrder.ProfitAuditVO> result = baseMapper.getProfitAuditPage(page, queryDTO.getOrderNo(), anomalyOnly);
        return PageUtil.toPageVO(result, com.money.dto.OmsOrder.ProfitAuditVO::new);
    }
}