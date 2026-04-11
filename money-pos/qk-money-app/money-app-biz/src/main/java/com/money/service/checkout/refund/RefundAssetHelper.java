package com.money.service.checkout.refund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.constant.PayMethodEnum;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderPay;
import com.money.entity.PosMemberCoupon;
import com.money.mapper.OmsOrderPayMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.service.UmsMemberAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundAssetHelper {

    private final UmsMemberAssetService umsMemberAssetService;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final OmsOrderPayMapper omsOrderPayMapper;

    /**
     * 处理整单退货资产与资金回退
     */
    public void processFullOrderAsset(OmsOrder order, String orderNo) {
        if (order.getMemberId() == null) return;

        // 1. 回退会员券与累计消费
        umsMemberAssetService.processReturn(order.getMemberId(), order.getFinalSalesAmount(), order.getCouponAmount(), true, orderNo);

        // 2. 回退满减券状态
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

        // 3. 回退会员余额通道付款
        List<OmsOrderPay> pays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>().eq(OmsOrderPay::getOrderNo, orderNo));
        for (OmsOrderPay pay : pays) {
            if (PayMethodEnum.fromCode(pay.getPayMethodCode()) == PayMethodEnum.BALANCE) {
                umsMemberAssetService.addBalance(order.getMemberId(), pay.getPayAmount(), orderNo, "整单退款:返还余额");
            }
        }
    }

    /**
     * 处理部分退货资产回退 (仅退部分销售额与均摊会员券)
     */
    public void processPartialReturnAsset(OmsOrder order, BigDecimal refundSales, BigDecimal refundMemberCoupon, String orderNo) {
        if (order.getMemberId() != null) {
            umsMemberAssetService.processReturn(order.getMemberId(), refundSales, refundMemberCoupon, false, orderNo);
        }
    }
}