package com.money.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.constant.BizErrorStatus;
import com.money.constant.OrderStatusEnum;
import com.money.dto.OmsOrder.*;
import com.money.dto.OmsOrderDetail.OmsOrderDetailVO;
import com.money.dto.UmsMember.UmsMemberVO;
import com.money.entity.*;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderPayMapper;
import com.money.service.OmsOrderDetailService;
import com.money.service.OmsOrderLogService;
import com.money.service.OmsOrderService;
import com.money.service.UmsMemberService;
import com.money.util.MoneyUtil;
import com.money.util.PageUtil;
import com.money.web.exception.BaseException;
import com.money.web.util.BeanMapUtil;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OmsOrderServiceImpl extends ServiceImpl<OmsOrderMapper, OmsOrder> implements OmsOrderService {

    private final OmsOrderDetailService omsOrderDetailService;
    private final OmsOrderLogService omsOrderLogService;
    private final UmsMemberService umsMemberService;
    private final GmsGoodsMapper gmsGoodsMapper;
    private final OmsOrderPayMapper omsOrderPayMapper;

    @Override
    public PageVO<OmsOrderVO> list(OmsOrderQueryDTO queryDTO) {
        Page<OmsOrder> page = this.lambdaQuery()
                .eq(queryDTO.getMember() != null, OmsOrder::getMember, queryDTO.getMember())
                .eq(StrUtil.isNotBlank(queryDTO.getOrderNo()), OmsOrder::getOrderNo, queryDTO.getOrderNo())
                .eq(StrUtil.isNotBlank(queryDTO.getStatus()), OmsOrder::getStatus, queryDTO.getStatus())
                .between(queryDTO.getStartTime() != null && queryDTO.getEndTime() != null,
                        OmsOrder::getPaymentTime, queryDTO.getStartTime(), queryDTO.getEndTime())
                .orderByDesc(OmsOrder::getPaymentTime)
                .page(PageUtil.toPage(queryDTO));
        return PageUtil.toPageVO(page, OmsOrderVO::new);
    }

    @Override
    public OrderCountVO countOrderAndSales(LocalDateTime startTime, LocalDateTime endTime) {
        List<AnalysisAtomicDataDTO> stats = this.getBaseMapper().getPeriodAtomicStats(startTime, endTime, "DAILY");

        OrderCountVO vo = new OrderCountVO();
        long totalOrder = 0;
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (AnalysisAtomicDataDTO stat : stats) {
            totalOrder += stat.getOrderCount();
            totalSales = MoneyUtil.add(totalSales, stat.getNetSalesAmount());
            totalCost = MoneyUtil.add(totalCost, stat.getCostAmount());
        }

        vo.setOrderCount(totalOrder);
        vo.setTotalSales(totalSales);
        vo.setSaleCount(totalSales);
        vo.setCostCount(totalCost);
        vo.setProfit(MoneyUtil.subtract(totalSales, totalCost));

        return vo;
    }

    @Override
    public OrderDetailVO getOrderDetail(Long id) {
        OmsOrder order = this.getById(id);
        // 🌟 异常升维：精准定位
        if (order == null) throw new BaseException(BizErrorStatus.POS_SETTLE_REQ_EMPTY, "查询失败：未找到系统ID为【{}】的订单记录", id);
        return assembleOrderDetail(order);
    }

    @Override
    public OrderDetailVO getOrderDetailByNo(String orderNo) {
        if (StrUtil.isBlank(orderNo)) throw new BaseException("查询失败：订单编号不能为空");

        OmsOrder order = this.lambdaQuery().eq(OmsOrder::getOrderNo, orderNo).one();
        // 🌟 异常升维：带入业务参数
        if (order == null) throw new BaseException(BizErrorStatus.POS_SETTLE_REQ_EMPTY, "查询失败：未找到单号为【{}】的订单记录", orderNo);

        return assembleOrderDetail(order);
    }

    private OrderDetailVO assembleOrderDetail(OmsOrder order) {
        OrderDetailVO vo = BeanMapUtil.to(order, OrderDetailVO::new);

        List<OmsOrderDetail> details = omsOrderDetailService.list(
                new LambdaQueryWrapper<OmsOrderDetail>().eq(OmsOrderDetail::getOrderNo, order.getOrderNo())
        );
        vo.setOrderDetails(BeanMapUtil.to(details, OmsOrderDetailVO::new));

        if (order.getMemberId() != null) {
            UmsMember member = umsMemberService.getById(order.getMemberId());
            if (member != null) {
                vo.setMemberInfo(BeanMapUtil.to(member, UmsMemberVO::new));
            }
        }

        List<OmsOrderLog> logs = omsOrderLogService.list(
                new LambdaQueryWrapper<OmsOrderLog>()
                        .eq(OmsOrderLog::getOrderId, order.getId())
                        .orderByAsc(OmsOrderLog::getCreateTime)
        );
        vo.setOrderLog(BeanMapUtil.to(logs, OrderDetailVO.OrderLogVO::new));

        List<OmsOrderPay> pays = omsOrderPayMapper.selectList(
                new LambdaQueryWrapper<OmsOrderPay>().eq(OmsOrderPay::getOrderNo, order.getOrderNo())
        );
        vo.setPayments(BeanMapUtil.to(pays, OrderDetailVO.OrderPayVO::new));

        BigDecimal balanceAmount = BigDecimal.ZERO;
        BigDecimal scanAmount = BigDecimal.ZERO;
        BigDecimal cashAmount = BigDecimal.ZERO;
        BigDecimal changeAmount = BigDecimal.ZERO;

        for (OmsOrderPay pay : pays) {
            if (pay.getPayMethodCode() != null) {
                if (pay.getPayMethodCode().contains("BALANCE")) {
                    balanceAmount = MoneyUtil.add(balanceAmount, pay.getPayAmount());
                } else if (pay.getPayMethodCode().contains("AGGREGATE")) {
                    scanAmount = MoneyUtil.add(scanAmount, pay.getPayAmount());
                } else if (pay.getPayMethodCode().contains("CASH")) {
                    cashAmount = MoneyUtil.add(cashAmount, pay.getPayAmount());
                }
            }
        }

        BigDecimal totalIn = MoneyUtil.add(MoneyUtil.add(balanceAmount, scanAmount), cashAmount);
        changeAmount = MoneyUtil.subtract(totalIn, order.getPayAmount());
        if(changeAmount.compareTo(BigDecimal.ZERO) < 0) {
            changeAmount = BigDecimal.ZERO;
        } else {
            cashAmount = MoneyUtil.subtract(cashAmount, changeAmount);
        }

        vo.setBalanceAmount(balanceAmount);
        vo.setScanAmount(scanAmount);
        vo.setCashAmount(cashAmount);
        vo.setChangeAmount(changeAmount);

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
        log.info("【资金逆向操作】收到整单退款请求，单号: {}", orderNo);

        boolean lockSuccess = this.lambdaUpdate()
                .set(OmsOrder::getStatus, OrderStatusEnum.REFUNDED.name())
                .eq(OmsOrder::getOrderNo, orderNo)
                .in(OmsOrder::getStatus, Arrays.asList(OrderStatusEnum.PAID.name(), "PARTIAL", "REFUNDED"))
                .update();

        // 🌟 异常升维：防重复提交大白话
        if (!lockSuccess) throw new BaseException(BizErrorStatus.POS_ORDER_DUPLICATED, "单号【{}】当前状态不可退款，可能已完成退款，请刷新列表确认！", orderNo);

        OmsOrder order = this.lambdaQuery().eq(OmsOrder::getOrderNo, orderNo).one();
        List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>().eq(OmsOrderDetail::getOrderNo, order.getOrderNo()));

        for (OmsOrderDetail detail : details) {
            int alreadyReturned = detail.getReturnQuantity() != null ? detail.getReturnQuantity() : 0;
            int canReturnQty = detail.getQuantity() - alreadyReturned;

            if (canReturnQty > 0) {
                ((OmsOrderDetailMapper)omsOrderDetailService.getBaseMapper())
                        .refundGoodsAtomically(detail.getId(), canReturnQty);

                gmsGoodsMapper.addStockAtomically(detail.getGoodsId(), new BigDecimal(canReturnQty));
            }
        }

        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());
        orderLog.setDescription("⚠️ 进行了【整单退款】操作，全额退回资金与库存");
        omsOrderLogService.save(orderLog);

        if (order.getVip() != null && order.getVip()) {
            umsMemberService.processReturn(order.getMemberId(), order.getPayAmount(), order.getUseVoucherAmount(), true, orderNo);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnGoods(ReturnGoodsDTO dto) {
        log.info("【资金逆向操作】收到部分退货请求，单号: {}, 明细ID: {}, 数量: {}", dto.getOrderNo(), dto.getDetailId(), dto.getReturnQty());

        if (dto.getReturnQty() <= 0) throw new BaseException("退货数量必须大于0");

        int returnQty = dto.getReturnQty();
        int affectedRows = ((OmsOrderDetailMapper)omsOrderDetailService.getBaseMapper())
                .refundGoodsAtomically(dto.getDetailId(), returnQty);

        // 🌟 异常升维：精准并发拦截反馈
        if (affectedRows == 0) {
            throw new BaseException(BizErrorStatus.STOCK_CALC_OVERFLOW, "操作失败：该商品剩余可退数量不足，或已被其他收银台处理，请刷新订单！");
        }

        this.lambdaUpdate()
                .set(OmsOrder::getStatus, "PARTIAL")
                .eq(OmsOrder::getOrderNo, dto.getOrderNo())
                .eq(OmsOrder::getStatus, OrderStatusEnum.PAID.name())
                .update();

        OmsOrderDetail detail = omsOrderDetailService.getById(dto.getDetailId());
        OmsOrder order = this.lambdaQuery().eq(OmsOrder::getOrderNo, dto.getOrderNo()).one();

        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());
        orderLog.setDescription(String.format("🔄 进行了【单品退货】商品：%s，数量：%d", detail.getGoodsName(), returnQty));
        omsOrderLogService.save(orderLog);

        gmsGoodsMapper.addStockAtomically(detail.getGoodsId(), new BigDecimal(returnQty));

        if (order.getVip() != null && order.getVip()) {
            BigDecimal goodsPrice = Optional.ofNullable(detail.getGoodsPrice()).orElse(BigDecimal.ZERO);
            BigDecimal coupon = Optional.ofNullable(detail.getCoupon()).orElse(BigDecimal.ZERO);

            BigDecimal refundCash = MoneyUtil.multiply(goodsPrice, returnQty);
            BigDecimal refundCoupon = MoneyUtil.multiply(coupon, returnQty);

            umsMemberService.processReturn(order.getMemberId(), refundCash, refundCoupon, false, dto.getOrderNo());
        }
    }
}