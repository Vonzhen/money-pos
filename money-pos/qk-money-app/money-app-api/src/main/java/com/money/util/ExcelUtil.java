package com.money.util;

import com.alibaba.excel.EasyExcel;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

/**
 * 全站通用 Excel 导出引擎
 */
public class ExcelUtil {

    /**
     * 极简导出方法
     * @param response  HttpServletResponse流
     * @param fileName  导出的文件名 (不带.xlsx)
     * @param sheetName 工作表名称
     * @param clazz     映射的图纸(DTO)类
     * @param data      要导出的数据列表
     */
    public static <T> void export(HttpServletResponse response, String fileName, String sheetName, Class<T> clazz, List<T> data) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        // 处理中文文件名乱码
        String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + encodedFileName + ".xlsx");

        // 核心：调用阿里 EasyExcel 引擎一键写入
        EasyExcel.write(response.getOutputStream(), clazz)
                .sheet(sheetName)
                .doWrite(data);
    }
}