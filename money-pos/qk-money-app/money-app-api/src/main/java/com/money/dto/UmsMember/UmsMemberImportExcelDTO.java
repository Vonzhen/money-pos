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

    // 🌟 统一命名：三大资产之一
    @ExcelProperty("初始会员余额(元)")
    @ColumnWidth(20)
    private BigDecimal balance;

    // 🌟 统一命名：三大资产之二
    @ExcelProperty("初始会员券(元)")
    @ColumnWidth(18)
    private BigDecimal coupon;

    // 🌟 优化指南：授人以渔，指引员工去字典查看代码
    @ExcelProperty({"品牌专属特权(选填)", "格式: 品牌名:等级代码(多个用半角逗号隔开)。等级代码请前往系统【字典管理-memberType】查看。例如: 绿叶:VIP代码"})
    @ColumnWidth(45)
    private String brandPrivileges;

    @ExcelProperty("备注")
    @ColumnWidth(20)
    private String remark;
}