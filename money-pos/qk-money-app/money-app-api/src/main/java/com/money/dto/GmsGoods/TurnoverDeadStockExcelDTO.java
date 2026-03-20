package com.money.dto.GmsGoods;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
public class TurnoverDeadStockExcelDTO {

    @ExcelProperty("序号")
    @ColumnWidth(10)
    private Integer index;

    @ExcelProperty("僵尸商品名称")
    @ColumnWidth(30)
    private String goodsName;

    @ExcelProperty("积压库存")
    @ColumnWidth(15)
    private Integer currentStock;

    @ExcelProperty("滞销天数")
    @ColumnWidth(15)
    private Integer deadDays;

    @ExcelProperty("处理方案(打折/退供/核销)")
    @ColumnWidth(30)
    private String actionPlan;
}