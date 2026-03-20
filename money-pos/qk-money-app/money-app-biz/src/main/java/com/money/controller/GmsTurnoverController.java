package com.money.controller;

import com.money.dto.GmsGoods.TurnoverDataVO.TurnoverDashboardVO;
import com.money.dto.GmsGoods.TurnoverDataVO.WarningItemVO;
import com.money.dto.GmsGoods.TurnoverReplenishExcelDTO;
import com.money.dto.GmsGoods.TurnoverDeadStockExcelDTO;
import com.money.service.GmsTurnoverService;
import com.money.util.ExcelUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "GmsTurnover", description = "周转预警中心")
@RestController
@RequestMapping("/gms/analysis")
@RequiredArgsConstructor
public class GmsTurnoverController {

    private final GmsTurnoverService gmsTurnoverService;

    @Operation(summary = "获取全盘补货与死库存预警")
    @GetMapping("/turnover-warnings")
    public TurnoverDashboardVO getTurnoverWarnings() {
        return gmsTurnoverService.getTurnoverWarnings();
    }

    @Operation(summary = "一键导出：智能采购建议单")
    @GetMapping("/export-replenish")
    public void exportReplenish(HttpServletResponse response) throws IOException {
        // 1. 获取大脑算出的实时预警数据
        TurnoverDashboardVO dashboard = gmsTurnoverService.getTurnoverWarnings();

        // 2. 将数据转换为 Excel 图纸格式
        List<TurnoverReplenishExcelDTO> list = new ArrayList<>();
        int i = 1;
        if (dashboard != null && dashboard.getReplenishList() != null) {
            for (WarningItemVO item : dashboard.getReplenishList()) {
                TurnoverReplenishExcelDTO dto = new TurnoverReplenishExcelDTO();
                dto.setIndex(i++);
                dto.setGoodsName(item.getGoodsName());
                dto.setCurrentStock(item.getCurrentStock());
                dto.setSuggestedQty(item.getSuggestedQty());
                dto.setActualQty(""); // 留白给人工填
                dto.setRemark("");
                list.add(dto);
            }
        }

        // 3. 调用通用引擎，一键下载！
        ExcelUtil.export(response, "智能采购建议单", "采购明细", TurnoverReplenishExcelDTO.class, list);
    }

    @Operation(summary = "一键导出：僵尸库存清仓单")
    @GetMapping("/export-deadstock")
    public void exportDeadStock(HttpServletResponse response) throws IOException {
        // 1. 获取大脑算出的实时预警数据
        TurnoverDashboardVO dashboard = gmsTurnoverService.getTurnoverWarnings();

        // 2. 将数据转换为 Excel 图纸格式
        List<TurnoverDeadStockExcelDTO> list = new ArrayList<>();
        int i = 1;
        if (dashboard != null && dashboard.getDeadStockList() != null) {
            for (WarningItemVO item : dashboard.getDeadStockList()) {
                TurnoverDeadStockExcelDTO dto = new TurnoverDeadStockExcelDTO();
                dto.setIndex(i++);
                dto.setGoodsName(item.getGoodsName());
                dto.setCurrentStock(item.getCurrentStock());
                dto.setDeadDays(item.getDeadDays());
                dto.setActionPlan(""); // 留白给人工填
                list.add(dto);
            }
        }

        // 3. 调用通用引擎，一键下载！
        ExcelUtil.export(response, "僵尸库存清仓单", "清仓明细", TurnoverDeadStockExcelDTO.class, list);
    }
}