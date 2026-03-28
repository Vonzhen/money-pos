package com.money.service.impl;

import com.money.dto.GmsGoods.TurnoverDataVO.*;
import com.money.entity.SysStrategy;
import com.money.mapper.GmsTurnoverMapper;
import com.money.mapper.SysStrategyMapper; // 🌟 新增：导入大脑数据通道
import com.money.service.GmsTurnoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GmsTurnoverServiceImpl implements GmsTurnoverService {

    private final GmsTurnoverMapper gmsTurnoverMapper;
    private final SysStrategyMapper sysStrategyMapper; // 🌟 新增：注入大脑 Mapper

    @Override
    public TurnoverDashboardVO getTurnoverWarnings() {
        List<WarningItemVO> rawList = gmsTurnoverMapper.scanAllGoodsTurnover();

        List<WarningItemVO> replenishList = new ArrayList<>();
        List<WarningItemVO> deadStockList = new ArrayList<>();

        // 🌟 神经连通：向大脑请示最新的预警水位线！
        SysStrategy strategy = sysStrategyMapper.getGlobalStrategy();
        int leadTimeDays = 3;
        int targetStockDays = 14;
        int deadStockThreshold = 60;

        // 如果您在前端保存了新参数，立刻生效！
        if (strategy != null) {
            if (strategy.getTurnoverLeadTime() != null) leadTimeDays = strategy.getTurnoverLeadTime();
            if (strategy.getTurnoverTargetDays() != null) targetStockDays = strategy.getTurnoverTargetDays();
            if (strategy.getDeadStockDays() != null) deadStockThreshold = strategy.getDeadStockDays();
        }

        for (WarningItemVO item : rawList) {
            // 算法 1：积压库存判定引擎 (动态参数)
            if (item.getCurrentStock() > 0 && item.getSales90Days() == 0) {
                item.setWarningType("DEAD_STOCK");
                if (item.getLastSaleTime() != null) {
                    LocalDateTime lastSale = LocalDateTime.parse(item.getLastSaleTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    item.setDeadDays((int) ChronoUnit.DAYS.between(lastSale, LocalDateTime.now()));
                } else {
                    item.setDeadDays(999);
                }

                // 🌟 使用前端配好的积压天数
                if (item.getDeadDays() >= deadStockThreshold) {
                    deadStockList.add(item);
                }
                continue;
            }

            // 算法 2：Lead Time 智能补货预警引擎 (动态参数)
            if (item.getSales30Days() > 0) {
                double dailyVelocity = item.getSales30Days() / 30.0;

                // 🌟 使用前端配好的进货提前期
                double safetyStock = dailyVelocity * leadTimeDays;

                if (item.getCurrentStock() <= safetyStock) {
                    item.setWarningType("REPLENISH");
                    // 🌟 使用前端配好的期望备货天数
                    int suggested = (int) Math.ceil((dailyVelocity * targetStockDays) - item.getCurrentStock());
                    item.setSuggestedQty(Math.max(suggested, 1));
                    replenishList.add(item);
                }
            }
        }

        TurnoverDashboardVO dashboard = new TurnoverDashboardVO();
        replenishList.sort((a, b) -> Integer.compare(a.getCurrentStock(), b.getCurrentStock()));
        deadStockList.sort((a, b) -> Integer.compare(b.getDeadDays(), a.getDeadDays()));

        dashboard.setReplenishList(replenishList);
        dashboard.setDeadStockList(deadStockList);
        return dashboard;
    }
}