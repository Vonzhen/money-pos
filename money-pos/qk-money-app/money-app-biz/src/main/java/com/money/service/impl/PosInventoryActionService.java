package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.*;
import com.money.mapper.*;
import com.money.service.GmsGoodsService;
import com.money.service.GmsStockLogService; // 🌟 引入 Service 层以支持批量插入
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PosInventoryActionService {
    private static final String STOCK_TYPE_SALE = "SALE";

    private final GmsGoodsMapper gmsGoodsMapper;
    private final GmsGoodsComboMapper gmsGoodsComboMapper;
    private final GmsGoodsService gmsGoodsService;
    // 🌟 任务 8：换用 Service 层来实现高性能的 saveBatch
    private final GmsStockLogService gmsStockLogService;

    public void deduct(List<OmsOrderDetail> orderDetails, Map<Long, GmsGoods> goodsMap, String orderNo) {
        LocalDateTime now = LocalDateTime.now();

        // 🌟 任务 9：增加 distinct()，防止订单中有多个相同套餐导致 ID 重复，放大查询范围
        List<Long> comboGoodsIds = goodsMap.values().stream()
                .filter(g -> g.getIsCombo() != null && g.getIsCombo() == 1)
                .map(GmsGoods::getId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, List<GmsGoodsCombo>> comboMap = new HashMap<>();
        Map<Long, GmsGoods> subGoodsMap = new HashMap<>();

        if (!comboGoodsIds.isEmpty()) {
            List<GmsGoodsCombo> allCombos = gmsGoodsComboMapper.selectList(new LambdaQueryWrapper<GmsGoodsCombo>().in(GmsGoodsCombo::getComboGoodsId, comboGoodsIds));
            comboMap = allCombos.stream().collect(Collectors.groupingBy(GmsGoodsCombo::getComboGoodsId));
            List<Long> allSubGoodsIds = allCombos.stream().map(GmsGoodsCombo::getSubGoodsId).distinct().collect(Collectors.toList());
            if (!allSubGoodsIds.isEmpty()) {
                subGoodsMap = gmsGoodsService.listByIds(allSubGoodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));
            }
        }

        List<com.money.entity.GmsStockLog> pendingLogs = new ArrayList<>();

        for (OmsOrderDetail detail : orderDetails) {
            GmsGoods goods = goodsMap.get(detail.getGoodsId());
            if (goods == null) throw new BaseException("【库存拦截】商品数据丢失，ID: " + detail.getGoodsId());

            if (goods.getIsCombo() != null && goods.getIsCombo() == 1) {
                List<GmsGoodsCombo> combos = comboMap.get(goods.getId());
                if (combos == null || combos.isEmpty()) throw new BaseException("【库存拦截】套餐商品未配置子明细: " + goods.getName());

                for (GmsGoodsCombo combo : combos) {
                    GmsGoods subGoods = subGoodsMap.get(combo.getSubGoodsId());
                    if (subGoods == null) throw new BaseException("【数据异常】套餐子商品不存在: " + combo.getSubGoodsId());

                    // 🌟 任务 7：Math.multiplyExact 严格防御大订单 int 乘法溢出变负数的致命漏洞
                    int deductQty;
                    try {
                        deductQty = Math.multiplyExact(detail.getQuantity(), combo.getSubGoodsQty());
                    } catch (ArithmeticException e) {
                        throw new BaseException(String.format("【库存拦截】套餐子商品「%s」扣减总数超出系统安全上限", subGoods.getName()));
                    }

                    int rows = gmsGoodsMapper.deductStockAtomically(subGoods.getId(), new BigDecimal(deductQty));
                    if (rows == 0) throw new BaseException("【库存不足】套餐子商品「" + subGoods.getName() + "」抢购失败");
                    pendingLogs.add(buildLog(subGoods, -deductQty, orderNo, "售出联动扣除", now));
                }
            } else {
                int rows = gmsGoodsMapper.deductStockAtomically(goods.getId(), new BigDecimal(detail.getQuantity()));
                if (rows == 0) throw new BaseException("【库存不足】商品「" + goods.getName() + "」抢购失败");
                pendingLogs.add(buildLog(goods, -detail.getQuantity(), orderNo, "收银台售出", now));
            }
        }

        if (!pendingLogs.isEmpty()) {
            List<Long> logGoodsIds = pendingLogs.stream().map(com.money.entity.GmsStockLog::getGoodsId).distinct().collect(Collectors.toList());
            Map<Long, GmsGoods> latestGoodsMap = gmsGoodsService.listByIds(logGoodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));
            for (com.money.entity.GmsStockLog log : pendingLogs) {
                GmsGoods latest = latestGoodsMap.get(log.getGoodsId());
                log.setAfterQuantity(latest != null && latest.getStock() != null ? latest.getStock().intValue() : 0);
            }

            // 🌟 任务 8：一次数据库交互完成批量插入，拒绝 N 次连接开销
            gmsStockLogService.saveBatch(pendingLogs);
        }
    }

    private com.money.entity.GmsStockLog buildLog(GmsGoods goods, int qty, String orderNo, String remark, LocalDateTime now) {
        com.money.entity.GmsStockLog log = new com.money.entity.GmsStockLog();
        log.setGoodsId(goods.getId());
        log.setGoodsName(goods.getName());
        log.setGoodsBarcode(goods.getBarcode());
        log.setType(STOCK_TYPE_SALE);
        log.setQuantity(qty);
        log.setOrderNo(orderNo);
        log.setRemark(remark);
        log.setCreateTime(now);
        return log;
    }
}