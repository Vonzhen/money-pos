package com.money.dto.UmsMember;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UmsMemberImportExcelDTO {

    @ExcelProperty("会员姓名")
    @ColumnWidth(15)
    private String name;

    @ExcelProperty("手机号(必填)")
    @ColumnWidth(20)
    private String phone;

    // 🌟 明确名称，消除歧义
    @ExcelProperty("初始本金(元)")
    @ColumnWidth(15)
    private BigDecimal balance;

    // 🌟 核心新增：直接在 Excel 导入会员券
    @ExcelProperty("初始会员券(元)")
    @ColumnWidth(15)
    private BigDecimal coupon;

    @ExcelProperty({"品牌专属特权(选填)", "格式为: 品牌名:等级代码,品牌名:等级代码。例如: 绿叶:VIP,宛伊:ANGEL。不需要特权则留空"})
    @ColumnWidth(40)
    private String brandPrivileges;

    @ExcelProperty("备注")
    @ColumnWidth(20)
    private String remark;
}