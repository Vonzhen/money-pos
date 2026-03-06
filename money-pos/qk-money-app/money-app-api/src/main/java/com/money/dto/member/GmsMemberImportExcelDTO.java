package com.money.dto.member;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class GmsMemberImportExcelDTO {

    @ExcelProperty("会员姓名")
    private String name;

    @ExcelProperty("手机号(必填)")
    private String phone;

    @ExcelProperty("初始余额")
    private BigDecimal balance;

    @ExcelProperty("会员等级(如: 黄金会员)")
    private String levelName;

    @ExcelProperty("备注")
    private String remark;
}