package com.money.service.checkout;

import com.money.entity.OmsOrder;
import com.money.entity.UmsMember;
import com.money.service.impl.PosAssetActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 🌟 结算流水线第五关：资产管家
 * 职责：负责会员余额的扣减和优惠券的核销。
 * 联动说明：本单是否扣减会员券由 Pricing 环节的 waiveCoupon 开关决定。
 */
@Service
@RequiredArgsConstructor
public class CheckoutMemberAssetService {

    private final PosAssetActionService assetActionService;

    public void handleAsset(CheckoutContext context) {
        OmsOrder order = context.getOrder();
        UmsMember verifiedMember = context.getMember();

        // 1. 如果不是会员单，直接跳过资产处理
        if (!order.getVip() || verifiedMember == null) {
            return;
        }

        // 2. 执行资产动作
        // assetActionService 内部会根据 pricingResult.getMemberCouponDeduct() 的金额来判断是否需要写扣款日志。
        // 如果开启了“免收”，该金额为 0，则只会处理余额支付部分，不触动会员券资产。
        assetActionService.consume(
                verifiedMember.getId(),
                context.getRequest(),
                context.getPricingResult(),
                context.getPaymentResult(),
                order.getOrderNo()
        );
    }
}