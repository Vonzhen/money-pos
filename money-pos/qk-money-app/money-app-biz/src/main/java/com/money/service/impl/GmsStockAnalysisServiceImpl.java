package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.GmsGoods.GmsStockDataVO.StockAnalysisReportVO;
import com.money.entity.GmsGoods;
import com.money.entity.GmsStockLog;
import com.money.mapper.GmsStockLogMapper;
import com.money.service.GmsGoodsService;
import com.money.service.GmsStockAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GmsStockAnalysisServiceImpl implements GmsStockAnalysisService {

    private final GmsStockLogMapper gmsStockLogMapper;
    private final GmsGoodsService gmsGoodsService;

    @Override
    public List<StockAnalysisReportVO> getStockAnalysisReport(String startDate, String endDate, String keyword) {
        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(29);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDateTime startTime = LocalDateTime.of(start, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        // 1. 构建查询条件并捞取指定时间段内的所有库存流水
        LambdaQueryWrapper<GmsStockLog> queryWrapper = new LambdaQueryWrapper<GmsStockLog>()
                .ge(GmsStockLog::getCreateTime, startTime)
                .le(GmsStockLog::getCreateTime, endTime);

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(w -> w.like(GmsStockLog::getGoodsName, keyword).or().like(GmsStockLog::getGoodsBarcode, keyword));
        }

        List<GmsStockLog> allLogs = gmsStockLogMapper.selectList(queryWrapper);

        if (allLogs.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 按商品ID进行核心分组 (Group By)
        Map<Long, List<GmsStockLog>> groupedLogs = allLogs.stream()
                .filter(log -> log.getGoodsId() != null)
                .collect(Collectors.groupingBy(GmsStockLog::getGoodsId));

        // 3. 批量查询关联的商品主表信息，获取最新的进货价 (解决 N+1 性能问题)
        List<Long> goodsIds = new ArrayList<>(groupedLogs.keySet());
        Map<Long, GmsGoods> goodsMap = new HashMap<>();
        if (!goodsIds.isEmpty()) {
            List<GmsGoods> goodsList = gmsGoodsService.listByIds(goodsIds);
            for (GmsGoods g : goodsList) {
                goodsMap.put(g.getId(), g);
            }
        }

        List<StockAnalysisReportVO> resultList = new ArrayList<>();

        // 4. 遍历每个商品，进行数学聚合与资产流失推演
        for (Map.Entry<Long, List<GmsStockLog>> entry : groupedLogs.entrySet()) {
            Long goodsId = entry.getKey();
            List<GmsStockLog> logs = entry.getValue();

            StockAnalysisReportVO vo = new StockAnalysisReportVO();
            vo.setGoodsId(goodsId);

            // 取第一条日志获取基础信息防空
            GmsStockLog firstLog = logs.get(0);
            vo.setGoodsName(firstLog.getGoodsName());
            vo.setGoodsBarcode(firstLog.getGoodsBarcode());

            // 获取商品主表的进货价
            GmsGoods goods = goodsMap.get(goodsId);
            if (goods != null && goods.getPurchasePrice() != null) {
                vo.setPurchasePrice(goods.getPurchasePrice());
            }

            int netChange = 0;

            // 循环该商品的所有日志进行累加
            for (GmsStockLog log : logs) {
                int qty = log.getQuantity() != null ? log.getQuantity() : 0;
                String type = log.getType() != null ? log.getType().toUpperCase() : "";

                switch (type) {
                    case "INBOUND":
                        vo.setInboundQty(vo.getInboundQty() + Math.abs(qty));
                        break;
                    case "RETURN":
                        vo.setReturnQty(vo.getReturnQty() + Math.abs(qty));
                        break;
                    case "SALE":
                        vo.setSaleQty(vo.getSaleQty() + Math.abs(qty));
                        break;
                    case "SCRAP":
                        vo.setScrapQty(vo.getScrapQty() + Math.abs(qty));
                        break;
                    case "CHECK":
                        // 盘点可能是正（盘盈）也可能是负（盘亏）
                        vo.setCheckQty(vo.getCheckQty() + qty);
                        break;
                }
                netChange += qty;
            }
            vo.setNetChangeQty(netChange);

            // 🌟 财务核算风控底线：计算资产流失总金额
            // 流失数量 = 报损出库(正数) + 盘点亏损(绝对值)
            int totalLossQty = vo.getScrapQty();
            if (vo.getCheckQty() < 0) {
                totalLossQty += Math.abs(vo.getCheckQty());
            }

            BigDecimal lossAmount = vo.getPurchasePrice().multiply(new BigDecimal(totalLossQty));
            vo.setLossAmount(lossAmount);

            resultList.add(vo);
        }

        // 默认按期间净变化数量降序排列
        resultList.sort((a, b) -> b.getNetChangeQty().compareTo(a.getNetChangeQty()));

        return resultList;
    }
}