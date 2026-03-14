package com.money.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.money.web.vo.PageVO;
import com.money.dto.OmsOrder.*;
import com.money.entity.OmsOrder;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * <p>
 * 订单表 服务类 (2.0 重构版)
 * </p>
 */
public interface OmsOrderService extends IService<OmsOrder> {

    /**
     * 订单列表查询
     */
    PageVO<OmsOrderVO> list(OmsOrderQueryDTO queryDTO);

    /**
     * 订单统计 (看板使用)
     */
    OrderCountVO countOrderAndSales(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取订单详情
     */
    OrderDetailVO getOrderDetail(Long id);

    /**
     * 🌟 契约重塑：整单退款 (按单号执行，支持幂等)
     * 注意：原 Set<Long> ids 已被弃用，改为 String orderNo
     */
    void returnOrder(String orderNo);

    /**
     * 🌟 契约重塑：部分退货 (使用 DTO 传参)
     */
    void returnGoods(ReturnGoodsDTO returnGoodsDTO);

    /**
     * 真实损益毛利审计分页
     */
    PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO);

}