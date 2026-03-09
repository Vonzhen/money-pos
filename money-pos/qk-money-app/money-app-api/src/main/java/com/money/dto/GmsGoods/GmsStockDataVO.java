package com.money.dto.GmsGoods;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;
import java.math.BigDecimal;

public class GmsStockDataVO {

    @Data
    public static class StockAnalysisReportVO {
        @ExcelIgnore
        private Long goodsId;

        @ExcelProperty("商品名称")
        @ColumnWidth(25)
        private String goodsName;

        @ExcelProperty("国际条码")
        @ColumnWidth(20)
        private String goodsBarcode;

        @ExcelProperty("采购入库 (件)")
        private Integer inboundQty;

        @ExcelProperty("售后退回 (件)")
        private Integer returnQty;

        @ExcelProperty("销售卖出 (件)")
        private Integer saleQty;

        @ExcelProperty("报损销毁 (件)")
        private Integer scrapQty;

        @ExcelProperty("盘点盈亏 (件)")
        private Integer checkQty;

        @ExcelProperty("期间净变动 (件)")
        private Integer netChangeQty;

        @ExcelProperty("进货成本价 (元)")
        private BigDecimal purchasePrice;

        @ExcelProperty("资产流失总额 (元)")
        @ColumnWidth(18)
        private BigDecimal lossAmount;

        public StockAnalysisReportVO() {
            this.inboundQty = 0;
            this.returnQty = 0;
            this.saleQty = 0;
            this.scrapQty = 0;
            this.checkQty = 0;
            this.netChangeQty = 0;
            this.purchasePrice = BigDecimal.ZERO;
            this.lossAmount = BigDecimal.ZERO;
        }
    }
}