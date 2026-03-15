package com.money.service.checkout;

import com.money.constant.OrderStatusEnum;
import com.money.dto.pos.SettleTrialResVO;
import com.money.entity.GmsGoods;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderDetail;
import com.money.entity.UmsMember;
import com.money.service.OmsOrderDetailService;
import com.money.service.OmsOrderService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 🌟 结算流水线第三关：档案员
 * 纯写库操作。负责生成订单主表和明细表，并拦截重复提交。
 */
@Service
@RequiredArgsConstructor
public class CheckoutOrderService {

    private final OmsOrderService omsOrderService;
    private final OmsOrderDetailService omsOrderDetailService;

    public void createOrder(CheckoutContext context) {
        // 从公文包里拿出前面工人准备好的材料
        SettleTrialResVO trialRes = context.getPricingResult();
        UmsMember verifiedMember = context.getMember();
        Map<Long, GmsGoods> goodsMap = context.getGoodsMap();
        String orderNo = context.getRequest().getReqId();

        // ================= 1. 组装并保存【订单主表】 =================
        OmsOrder order = new OmsOrder();
        order.setOrderNo(orderNo);
        order.setTotalAmount(trialRes.getTotalAmount());
        order.setCouponAmount(trialRes.getMemberCouponDeduct());
        order.setUseVoucherAmount(trialRes.getVoucherDeduct());
        order.setManualDiscountAmount(trialRes.getManualDeduct());

        // 拿精算师算好的总成本
        order.setCostAmount(trialRes.getCostAmount());
        order.setPayAmount(trialRes.getFinalPayAmount());
        order.setFinalSalesAmount(trialRes.getFinalPayAmount());

        order.setStatus(OrderStatusEnum.PAID.name());
        order.setPaymentTime(LocalDateTime.now());

        // 如果是会员，打上 VIP 标签
        if (verifiedMember != null) {
            order.setVip(true);
            order.setMemberId(verifiedMember.getId());
            order.setMember(verifiedMember.getName());
            order.setContact(verifiedMember.getPhone());
        } else {
            order.setVip(false);
        }

        // 防重落库：如果单号在数据库里已经存在，直接抛出异常拦截
        try {
            omsOrderService.save(order);
        } catch (DuplicateKeyException e) {
            throw new BaseException("【订单已处理】请勿重复点击提交！");
        }

        // ================= 2. 组装并保存【订单明细表】 =================
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

            // 🌟 财务闭环核心：使用精算师存在 itemRes 里的成本快照，绝不重新查库！
            detail.setPurchasePrice(itemRes.getCostPrice() != null ? itemRes.getCostPrice() : BigDecimal.ZERO);

            detail.setVipPrice(goods.getVipPrice());
            detail.setQuantity(itemRes.getQuantity());
            detail.setGoodsPrice(itemRes.getRealPrice());
            detail.setCoupon(itemRes.getCouponDeduct() != null ? itemRes.getCouponDeduct() : BigDecimal.ZERO);
            details.add(detail);
        }
        // 批量保存明细
        omsOrderDetailService.saveBatch(details);

        // 👉 将建好的订单主表和明细表，塞回公文包，传给下一关！
        context.setOrder(order);
        context.setOrderDetails(details);
    }
}