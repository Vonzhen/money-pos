package com.money.service;

import com.money.dto.OmsOrder.ReturnGoodsDTO;

public interface OmsOrderRefundService {
    /**
     * 整单退款
     */
    void returnOrder(String orderNo);

    /**
     * 部分退货
     */
    void returnGoods(ReturnGoodsDTO dto);
}