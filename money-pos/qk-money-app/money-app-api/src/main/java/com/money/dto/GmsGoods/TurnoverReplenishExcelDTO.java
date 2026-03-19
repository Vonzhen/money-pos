package com.money.dto.GmsGoods;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
public class TurnoverReplenishExcelDTO {

    @ExcelProperty("序号")
    @ColumnWidth(10)
    private Integer index;

    @ExcelProperty("商品名称")
    @ColumnWidth(30)
    private String goodsName;

    @ExcelProperty("当前库存")
    @ColumnWidth(15)
    private Integer currentStock;

    @ExcelProperty("建议补货量")
    @ColumnWidth(15)
    private Integer suggestedQty;

    @ExcelProperty("实际采购量(请填写)")
    @ColumnWidth(25) // 留宽一点方便手写
    private String actualQty;

    @ExcelProperty("备注")
    @ColumnWidth(20)
    private String remark;
}