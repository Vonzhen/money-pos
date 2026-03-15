package com.money.service.checkout;

import com.money.entity.GmsGoods;
import com.money.entity.GmsInventoryDoc;
import com.money.entity.GmsInventoryDocItem;
import com.money.entity.OmsOrderDetail;
import com.money.mapper.GmsInventoryDocItemMapper;
import com.money.mapper.GmsInventoryDocMapper; // 🌟 修正：直接引入主表的 Mapper
import com.money.service.impl.PosInventoryActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 🌟 结算流水线第四关：库管员 (升级版)
 * 职责：
 * 1. 委托底层扣减物理库存和记录库存流水。
 * 2. 自动生成正规的【销售出库单据(SALE_OUT)】，实现凡变动必有单！
 */
@Service
@RequiredArgsConstructor
public class CheckoutInventoryService {

    private final PosInventoryActionService inventoryActionService;

    // 🌟 修正：统统改用底层 Mapper 强制写库，绕过没有 save 方法的 Service
    private final GmsInventoryDocMapper inventoryDocMapper;
    private final GmsInventoryDocItemMapper inventoryDocItemMapper;

    public void deductStock(CheckoutContext context) {
        List<OmsOrderDetail> orderDetails = context.getOrderDetails();
        Map<Long, GmsGoods> goodsMap = context.getGoodsMap();
        String orderNo = context.getOrder().getOrderNo();

        // 1. 原有逻辑：执行物理库存扣减和基础流水记录
        inventoryActionService.deduct(orderDetails, goodsMap, orderNo);

        // 2. 🌟 财务闭环新增：自动生成库存出库单
        createSaleOutDocument(context);
    }

    /**
     * 内部动作：拼装并保存【销售出库单】
     */
    private void createSaleOutDocument(CheckoutContext context) {
        String orderNo = context.getOrder().getOrderNo();
        List<OmsOrderDetail> orderDetails = context.getOrderDetails();

        // ================= 1. 组装出库主单据 =================
        GmsInventoryDoc doc = new GmsInventoryDoc();
        // 加上 XS(销售) 前缀，方便在单据列表中和普通入库单区分
        doc.setDocNo("XS-" + orderNo);
        doc.setDocType("SALE_OUT"); // 明确类型：销售出库
        doc.setRemark("前台收银自动生成，关联单号：" + orderNo);
        doc.setCreateTime(LocalDateTime.now());

        // 计算这批货的总成本价值
        BigDecimal totalCostAmount = BigDecimal.ZERO;
        for (OmsOrderDetail detail : orderDetails) {
            BigDecimal qty = new BigDecimal(detail.getQuantity());
            BigDecimal cost = detail.getPurchasePrice() != null ? detail.getPurchasePrice() : BigDecimal.ZERO;
            totalCostAmount = totalCostAmount.add(cost.multiply(qty));
        }

        // 出库代表资产流失/减少，记为负数
        doc.setTotalAmount(totalCostAmount.negate());

        // 🌟 修正：使用 Mapper 插入主表
        inventoryDocMapper.insert(doc);

        // ================= 2. 组装出库明细并循环保存 =================
        for (OmsOrderDetail detail : orderDetails) {
            GmsInventoryDocItem item = new GmsInventoryDocItem();

            item.setDocNo(doc.getDocNo()); // 关联单号
            item.setGoodsId(detail.getGoodsId());
            item.setGoodsName(detail.getGoodsName());
            item.setBarcode(detail.getGoodsBarcode());

            // 销售出库，数量记为负数表示扣减
            item.setChangeQty(-detail.getQuantity());

            BigDecimal cost = detail.getPurchasePrice() != null ? detail.getPurchasePrice() : BigDecimal.ZERO;
            item.setCostPrice(cost);

            // 使用 Mapper 逐条插入明细
            inventoryDocItemMapper.insert(item);
        }
    }
}