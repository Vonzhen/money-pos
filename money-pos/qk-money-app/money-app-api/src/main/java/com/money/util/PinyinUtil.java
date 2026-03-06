package com.money.utils;

import net.sourceforge.pinyin4j.PinyinHelper;

public class PinyinUtil {
    /**
     * 提取中文拼音首字母，英文数字保持不变，并全部转为大写
     */
    public static String getFirstLetter(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c > 128) { // 简单判断是否可能为中文
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        sb.append(pinyinArray[0].charAt(0));
                    } else {
                        sb.append(c);
                    }
                } catch (Exception e) {
                    sb.append(c);
                }
            } else {
                sb.append(c); // 英文或数字直接拼接
            }
        }
        return sb.toString().toUpperCase();
    }
}