package com.money.util;

/**
 * 🌟 打印机底层硬件控制指令集 (ESC/POS)
 */
public class EscPosUtil {

    // 初始化打印机 (清除缓存)
    public static final byte[] INIT = {0x1B, 0x40};

    // 换行
    public static final byte[] NEW_LINE = {0x0A};

    // 对齐方式
    public static final byte[] ALIGN_LEFT = {0x1B, 0x61, 0x00};

    // 对齐方式
    public static final byte[] ALIGN_CENTER = {0x1B, 0x61, 0x01};

    // 对齐方式
    public static final byte[] ALIGN_RIGHT = {0x1B, 0x61, 0x02};

    // 字体加粗开启/关闭
    public static final byte[] BOLD_ON = {0x1B, 0x45, 0x01};
    public static final byte[] BOLD_OFF = {0x1B, 0x45, 0x00};

    // 字体大小 (正常 / 宽高各放大一倍)
    public static final byte[] TEXT_NORMAL = {0x1D, 0x21, 0x00};
    public static final byte[] TEXT_LARGE = {0x1D, 0x21, 0x11};

    // 🌟 核心：弹开钱箱指令 (向钱箱脉冲引脚发送高电平)
    public static final byte[] OPEN_CASH_DRAWER = {0x1B, 0x70, 0x00, 0x3C, (byte) 0xFF};
}