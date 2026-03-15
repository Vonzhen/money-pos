package com.money.service.checkout;

import com.money.entity.OmsOrder;
import com.money.entity.UmsMember;
// 🌟 重点注意：根据您之前的排查，这里直接引入 impl 包下的具体类
import com.money.service.impl.PosAssetActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 🌟 结算流水线第五关：资产管家
 * 负责会员余额的扣减和优惠券的核销。
 */
@Service
@RequiredArgsConstructor
public class CheckoutMemberAssetService {

    private final PosAssetActionService assetActionService;

    public void handleAsset(CheckoutContext context) {
        OmsOrder order = context.getOrder();
        UmsMember verifiedMember = context.getMember();

        // 如果这单是会员买的，且真实查到了会员信息，就执行资产扣减
        if (order.getVip() && verifiedMember != null) {
            assetActionService.consume(
                    verifiedMember.getId(),
                    context.getRequest(),
                    context.getPricingResult(),
                    context.getPaymentResult(),
                    order.getOrderNo()
            );
        }
    }
}