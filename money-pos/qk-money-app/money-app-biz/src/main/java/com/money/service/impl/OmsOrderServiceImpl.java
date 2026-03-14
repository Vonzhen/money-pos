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
        // 🌟 万源归宗：彻底废弃 Java 内存循环统计！
        // 直接调用大盘专属的 Mapper 接口获取原子数据，确保列表顶部统计与报表大屏一分钱都不差。
        List<AnalysisAtomicDataDTO> stats = this.getBaseMapper().getPeriodAtomicStats(startTime, endTime, "DAILY");

        OrderCountVO vo = new OrderCountVO();
        long totalOrder = 0;
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        // 这里的 stats 通常只有 1 条（如果是查当天），或者几天的数据。
        for (AnalysisAtomicDataDTO stat : stats) {
            totalOrder += stat.getOrderCount();
            totalSales = MoneyUtil.add(totalSales, stat.getNetSalesAmount());
            totalCost = MoneyUtil.add(totalCost, stat.getCostAmount());
        }

        vo.setOrderCount(totalOrder);
        vo.setTotalSales(totalSales);
        vo.setSaleCount(totalSales);
        vo.setCostCount(totalCost);
        // 使用 MoneyUtil 扣减，规避精度丢失
        vo.setProfit(MoneyUtil.subtract(totalSales, totalCost));

        return vo;
    }

    @Override
    public OrderDetailVO getOrderDetail(Long id) {
        OmsOrder order = this.getById(id);
        if (order == null) throw new BaseException(BizErrorStatus.POS_SETTLE_REQ_EMPTY, "未找到对应的订单记录");
        return assembleOrderDetail(order);
    }

    @Override
    public OrderDetailVO getOrderDetailByNo(String orderNo) {
        if (StrUtil.isBlank(orderNo)) throw new BaseException("订单编号不能为空");

        OmsOrder order = this.lambdaQuery().eq(OmsOrder::getOrderNo, orderNo).one();
        if (order == null) throw new BaseException(BizErrorStatus.POS_SETTLE_REQ_EMPTY, "未找到对应的订单记录：" + orderNo);

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

        if (!lockSuccess) throw new BaseException("订单当前状态不可退款，请勿重复操作！");

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

        // 🌟 新增：整单退款审计日志
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

        // 1. 兵工厂原子更新明细
        int affectedRows = ((OmsOrderDetailMapper)omsOrderDetailService.getBaseMapper())
                .refundGoodsAtomically(dto.getDetailId(), returnQty);

        if (affectedRows == 0) {
            throw new BaseException("操作失败，该商品剩余可退数量不足或并发冲突！");
        }

        // 主表状态设为 'PARTIAL' (部分退款)
        this.lambdaUpdate()
                .set(OmsOrder::getStatus, "PARTIAL")
                .eq(OmsOrder::getOrderNo, dto.getOrderNo())
                .eq(OmsOrder::getStatus, OrderStatusEnum.PAID.name())
                .update();

        OmsOrderDetail detail = omsOrderDetailService.getById(dto.getDetailId());
        OmsOrder order = this.lambdaQuery().eq(OmsOrder::getOrderNo, dto.getOrderNo()).one();

        // 2. 写入审计日志
        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());
        orderLog.setDescription(String.format("🔄 进行了【单品退货】商品：%s，数量：%d", detail.getGoodsName(), returnQty));
        omsOrderLogService.save(orderLog);

        // 3. 归还库存
        gmsGoodsMapper.addStockAtomically(detail.getGoodsId(), new BigDecimal(returnQty));

        // 4. 资产返还
        if (order.getVip() != null && order.getVip()) {
            BigDecimal goodsPrice = Optional.ofNullable(detail.getGoodsPrice()).orElse(BigDecimal.ZERO);
            BigDecimal coupon = Optional.ofNullable(detail.getCoupon()).orElse(BigDecimal.ZERO);

            BigDecimal refundCash = MoneyUtil.multiply(goodsPrice, returnQty);
            BigDecimal refundCoupon = MoneyUtil.multiply(coupon, returnQty);

            umsMemberService.processReturn(order.getMemberId(), refundCash, refundCoupon, false, dto.getOrderNo());
        }
    }
}