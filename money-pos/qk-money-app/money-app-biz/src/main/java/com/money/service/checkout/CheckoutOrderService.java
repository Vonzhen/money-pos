package com.money.service.checkout;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.OrderStatusEnum;
import com.money.dto.pos.SettleTrialResVO;
import com.money.entity.GmsGoods;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderDetail;
import com.money.entity.OmsOrderLog;
import com.money.entity.UmsMember;
import com.money.entity.GmsGoodsCategory;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.service.OmsOrderDetailService;
import com.money.service.GmsGoodsCategoryService;
import com.money.service.OmsOrderLogService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 🌟 结算流水线第三关：档案员 (完整审计版)
 */
@Service
@RequiredArgsConstructor
public class CheckoutOrderService {

    private final OmsOrderDetailService omsOrderDetailService;
    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderDetailMapper omsOrderDetailMapper;
    private final GmsGoodsCategoryService gmsGoodsCategoryService;

    // 🌟 引入日志服务，找回开单日记本
    private final OmsOrderLogService omsOrderLogService;

    public boolean loadExistingOrder(CheckoutContext context) {
        String reqId = context.getRequest().getReqId();
        OmsOrder existingOrder = omsOrderMapper.selectOne(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getOrderNo, reqId));
        if (existingOrder == null) return false;

        List<OmsOrderDetail> existingDetails = omsOrderDetailMapper.selectList(new LambdaQueryWrapper<OmsOrderDetail>().eq(OmsOrderDetail::getOrderNo, reqId));
        context.setOrder(existingOrder);
        context.setOrderDetails(existingDetails);
        return true;
    }

    public void createOrder(CheckoutContext context) {
        SettleTrialResVO trialRes = context.getPricingResult();
        UmsMember verifiedMember = context.getMember();
        Map<Long, GmsGoods> goodsMap = context.getGoodsMap();
        String orderNo = context.getRequest().getReqId();

        OmsOrder order = new OmsOrder();
        order.setOrderNo(orderNo);
        order.setTotalAmount(trialRes.getTotalAmount());
        order.setCouponAmount(trialRes.getMemberCouponDeduct());
        order.setUseVoucherAmount(trialRes.getVoucherDeduct());
        order.setManualDiscountAmount(trialRes.getManualDeduct());
        order.setCostAmount(trialRes.getCostAmount());
        order.setPayAmount(trialRes.getFinalPayAmount());
        order.setFinalSalesAmount(trialRes.getFinalPayAmount());
        order.setStatus(OrderStatusEnum.PAID.name());
        order.setPaymentTime(LocalDateTime.now());

        if (verifiedMember != null) {
            order.setVip(true);
            order.setMemberId(verifiedMember.getId());
            order.setMember(verifiedMember.getName());
            order.setContact(verifiedMember.getPhone());
        } else {
            order.setVip(false);
        }

        try {
            omsOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            throw new BaseException("【订单处理异常】请刷新页面后重试！");
        }

        List<OmsOrderDetail> details = new ArrayList<>();
        for (SettleTrialResVO.ItemRes itemRes : trialRes.getItems()) {
            GmsGoods goods = goodsMap.get(itemRes.getGoodsId());
            OmsOrderDetail detail = new OmsOrderDetail();
            detail.setOrderNo(orderNo);
            detail.setStatus(OrderStatusEnum.PAID.name());
            detail.setGoodsId(goods.getId());
            detail.setBrandId(goods.getBrandId());
            detail.setGoodsBarcode(goods.getBarcode());
            detail.setGoodsName(goods.getName());
            detail.setSalePrice(itemRes.getOriginalPrice());
            detail.setPurchasePrice(itemRes.getCostPrice() != null ? itemRes.getCostPrice() : BigDecimal.ZERO);
            detail.setVipPrice(goods.getVipPrice());
            detail.setQuantity(itemRes.getQuantity());
            detail.setGoodsPrice(itemRes.getRealPrice());
            detail.setCoupon(itemRes.getCouponDeduct() != null ? itemRes.getCouponDeduct() : BigDecimal.ZERO);

            if (goods.getCategoryId() != null) {
                detail.setCategoryId(goods.getCategoryId());
                GmsGoodsCategory category = gmsGoodsCategoryService.getById(goods.getCategoryId());
                if (category != null) {
                    detail.setCategoryName(category.getName());
                }
            }
            details.add(detail);
        }
        omsOrderDetailService.saveBatch(details);

        // 🌟 核心：原汁原味地写回前端能够解析的 JSON 格式结算日志
        Map<String, Object> logInfo = new HashMap<>();
        logInfo.put("action", "SETTLE_SUCCESS");
        logInfo.put("totalPaid", order.getTotalAmount());
        logInfo.put("finalPay", order.getPayAmount());
        // 提取前端需要的支付组合字符串
        StringBuilder payMethodsStr = new StringBuilder();
        context.getRequest().getPayments().forEach(p -> {
            if ("AGGREGATE".equals(p.getPayMethodCode()) && p.getPayTag() != null) {
                payMethodsStr.append("AGGREGATE:").append(p.getPayTag()).append(",");
            } else {
                payMethodsStr.append(p.getPayMethodCode()).append(",");
            }
        });
        if (payMethodsStr.length() > 0) payMethodsStr.deleteCharAt(payMethodsStr.length() - 1);
        logInfo.put("payMethods", payMethodsStr.toString());

        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());
        orderLog.setDescription(JSON.toJSONString(logInfo));
        omsOrderLogService.save(orderLog);

        context.setOrder(order);
        context.setOrderDetails(details);
    }
}