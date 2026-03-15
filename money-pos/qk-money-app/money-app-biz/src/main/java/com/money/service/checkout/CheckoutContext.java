package com.money.service.checkout;

import com.money.dto.pos.NormalizedPaymentResult;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.dto.pos.SettleResultVO;
import com.money.dto.pos.SettleTrialResVO;
import com.money.entity.GmsGoods;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderDetail;
import com.money.entity.UmsMember;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 🌟 结算流水线公文包 (Context)
 * 用于在各个处理环节中传递数据，避免重复查库和超长参数传递
 */
@Data
public class CheckoutContext {

    // 1. 顾客的原始请求（从前端传过来的）
    private SettleAccountsDTO request;

    // 2. 安检员核实后的数据
    private UmsMember member; // 核实后的会员信息（如果是散客则为null）
    private Map<Long, GmsGoods> goodsMap; // 核实后的商品档案库（包含移动均价）

    // 3. 精算师和出纳员算好的数据
    private SettleTrialResVO pricingResult; // 价格计算结果
    private NormalizedPaymentResult paymentResult; // 支付清洗结果

    // 4. 档案员建好的实体（准备落库的）
    private OmsOrder order; // 订单主表
    private List<OmsOrderDetail> orderDetails; // 订单明细表

    // 5. 最终要返回给前端的小票结果
    private SettleResultVO finalResult;
}