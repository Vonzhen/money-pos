package com.money.util;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.util.Map;

/**
 * Excel 智能下拉框注入引擎 (防呆设计)
 */
public class ExcelDropDownHandler implements SheetWriteHandler {

    // 存放需要加下拉框的列索引和对应的下拉选项
    private final Map<Integer, String[]> dropDownMap;

    public ExcelDropDownHandler(Map<Integer, String[]> dropDownMap) {
        this.dropDownMap = dropDownMap;
    }

    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        Sheet sheet = writeSheetHolder.getSheet();
        DataValidationHelper helper = sheet.getDataValidationHelper();

        // 遍历需要配置下拉框的列
        for (Map.Entry<Integer, String[]> entry : dropDownMap.entrySet()) {
            Integer colIndex = entry.getKey();
            String[] options = entry.getValue();

            // 设置下拉框的作用范围：从第 2 行开始 (索引为1)，到第 1000 行，作用于这一列
            CellRangeAddressList rangeList = new CellRangeAddressList(1, 1000, colIndex, colIndex);

            // 设置下拉数据源
            DataValidationConstraint constraint = helper.createExplicitListConstraint(options);
            DataValidation validation = helper.createValidation(constraint, rangeList);

            // 阻止输入非下拉框里的内容
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.setShowErrorBox(true);
            validation.setSuppressDropDownArrow(true);
            validation.createErrorBox("输入错误", "请从下拉列表中选择合法的系统等级，禁止手动输入其他字符！");

            sheet.addValidationData(validation);
        }
    }
}