package com.money.service.printer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.OmsOrder.OrderDetailVO;
import com.money.dto.OmsOrderDetail.OmsOrderDetailVO;
import com.money.entity.PosMemberCoupon;
import com.money.entity.SysDictDetail;
import com.money.entity.SysPrintConfig;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.SysDictDetailMapper;
import com.money.mapper.SysPrintConfigMapper;
import com.money.util.EscPosUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.print.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosPrinterService {

    private final SysPrintConfigMapper sysPrintConfigMapper;
    private final SysDictDetailMapper sysDictDetailMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;

    public void printReceiptAndOpenDrawer(OrderDetailVO orderVO) {
        try {
            SysPrintConfig config = sysPrintConfigMapper.selectById(1L);
            if (config == null) return;

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(EscPosUtil.INIT);

            if (config.getOpenDrawer() != null && config.getOpenDrawer()) {
                bos.write(EscPosUtil.OPEN_CASH_DRAWER);
            }
            if (config.getAutoPrint() == null || !config.getAutoPrint()) {
                executeHardwareCommand(bos.toByteArray());
                return;
            }

            boolean isVip = orderVO.getVip() != null && orderVO.getVip();

            // ================= 1. 头部区域 =================
            bos.write(EscPosUtil.ALIGN_CENTER);
            if (StringUtils.hasText(config.getShopName())) {
                bos.write(EscPosUtil.BOLD_ON);
                bos.write(EscPosUtil.TEXT_LARGE);
                writeText(bos, config.getShopName() + "\n\n");
                bos.write(EscPosUtil.TEXT_NORMAL);
                bos.write(EscPosUtil.BOLD_OFF);
            }
            if (StringUtils.hasText(config.getHeaderMsg())) {
                writeText(bos, config.getHeaderMsg() + "\n");
            }
            writeText(bos, "\n");

            // ================= 2. 基础信息 =================
            bos.write(EscPosUtil.ALIGN_LEFT);
            writeText(bos, "单号: " + orderVO.getOrderNo() + "\n");
            writeText(bos, "时间: " + orderVO.getCreateTime() + "\n");
            writeText(bos, "--------------------------------\n");

            // ================= 3. 商品明细 =================
            writeText(bos, formatItemLine("原价", "现价", "数量", "优惠", "小计") + "\n");

            int totalQty = 0;
            for (OmsOrderDetailVO item : orderVO.getOrderDetails()) {
                totalQty += item.getQuantity();
                writeText(bos, item.getGoodsName() + "\n");

                String orig = fmt(item.getSalePrice());
                String curr = fmt(item.getGoodsPrice());
                String qty = "x" + item.getQuantity();
                BigDecimal discount = item.getSalePrice().subtract(item.getGoodsPrice()).multiply(new BigDecimal(item.getQuantity()));
                String disc = discount.compareTo(BigDecimal.ZERO) > 0 ? fmt(discount) : "0.00";
                String subtotal = fmt(item.getGoodsPrice().multiply(new BigDecimal(item.getQuantity())));

                writeText(bos, formatItemLine(orig, curr, qty, disc, subtotal) + "\n");
            }
            writeText(bos, "--------------------------------\n");

            // ================= 4. 汇总信息 =================
            BigDecimal totalDiscount = orderVO.getTotalAmount().subtract(orderVO.getPayAmount());
            BigDecimal waived = orderVO.getWaivedCouponAmount() != null ? orderVO.getWaivedCouponAmount() : BigDecimal.ZERO;

            writeText(bos, "总价: " + padRight(fmt(orderVO.getTotalAmount()), 11) + "件数: " + totalQty + "\n");
            writeText(bos, "优惠: " + padRight(fmt(totalDiscount), 11) + "抹零: " + fmt(waived) + "\n");

            bos.write(EscPosUtil.BOLD_ON);
            writeText(bos, "应收: " + fmt(orderVO.getPayAmount()) + "\n");
            bos.write(EscPosUtil.BOLD_OFF);
            writeText(bos, "--------------------------------\n");

            // ================= 5. 支付与会员相关信息 =================
            // 🌟 修复1：完全动态的支付列表遍历
            if (orderVO.getPayments() != null) {
                for (OrderDetailVO.OrderPayVO pay : orderVO.getPayments()) {
                    writeText(bos, resolvePayName(pay) + ": " + fmt(pay.getPayAmount()) + "\n");
                }
            }

            // 🌟 修复2：独立的找零打印逻辑
            if (orderVO.getChangeAmount() != null && orderVO.getChangeAmount().compareTo(BigDecimal.ZERO) > 0) {
                writeText(bos, "找零: " + fmt(orderVO.getChangeAmount()) + "\n");
            }

            if (isVip) {
                if (orderVO.getCouponAmount() != null && orderVO.getCouponAmount().compareTo(BigDecimal.ZERO) > 0) {
                    writeText(bos, "会员扣券: " + fmt(orderVO.getCouponAmount()) + "\n");
                }
                if (orderVO.getUseVoucherAmount() != null && orderVO.getUseVoucherAmount().compareTo(BigDecimal.ZERO) > 0) {
                    writeText(bos, "满减抵扣: " + fmt(orderVO.getUseVoucherAmount()) + "\n");
                }

                writeText(bos, "--------------------------------\n");
                if (orderVO.getMemberInfo() != null) {
                    writeText(bos, "会员姓名: " + orderVO.getMemberInfo().getName() + "\n");
                    if (orderVO.getMemberInfo().getCoupon() != null) {
                        writeText(bos, "会员券余额: " + fmt(orderVO.getMemberInfo().getCoupon()) + "\n");
                    }
                    if (orderVO.getMemberId() != null) {
                        Long vCount = posMemberCouponMapper.selectCount(
                                new LambdaQueryWrapper<PosMemberCoupon>()
                                        .eq(PosMemberCoupon::getMemberId, orderVO.getMemberId())
                                        .eq(PosMemberCoupon::getStatus, "UNUSED")
                        );
                        if (vCount != null && vCount > 0) {
                            writeText(bos, "满减券余量: " + vCount + " 张\n");
                        }
                    }
                }
            }

            // ================= 6. 尾部信息 =================
            // 🌟 修复3：将分割线移出 isVip 判定，所有人都有分割线
            writeText(bos, "--------------------------------\n");

            bos.write(EscPosUtil.ALIGN_LEFT);
            if (StringUtils.hasText(config.getShopPhone())) {
                writeText(bos, "门店热线: " + config.getShopPhone() + "\n");
            }
            if (StringUtils.hasText(config.getShopAddress())) {
                writeText(bos, "门店地址: " + config.getShopAddress() + "\n");
            }

            bos.write(EscPosUtil.ALIGN_CENTER);
            if (StringUtils.hasText(config.getFooterMsg())) {
                writeText(bos, "\n" + config.getFooterMsg() + "\n");
            }

            writeText(bos, "\n\n\n\n\n");

            executeHardwareCommand(bos.toByteArray());

        } catch (Exception e) {
            log.error("❌ 硬件打印指令执行失败: ", e);
        }
    }

    // ==========================================
    // 🌟 全自动字典驱动翻译引擎
    // ==========================================
    private String resolvePayName(OrderDetailVO.OrderPayVO pay) {
        // 第一优先级：解析子标签 (如把聚合支付进一步细化为 微信、支付宝)
        if (StringUtils.hasText(pay.getPayTag())) {
            SysDictDetail subDetail = sysDictDetailMapper.selectOne(
                    new LambdaQueryWrapper<SysDictDetail>()
                            .eq(SysDictDetail::getDict, "paySubTag")
                            .eq(SysDictDetail::getValue, pay.getPayTag())
                            .last("LIMIT 1")
            );
            if (subDetail != null && StringUtils.hasText(subDetail.getCnDesc())) {
                return subDetail.getCnDesc();
            }
        }

        // 第二优先级：解析主支付方式 (读取 pos_payment_method 字典)
        if (StringUtils.hasText(pay.getPayMethodCode())) {
            SysDictDetail mainDetail = sysDictDetailMapper.selectOne(
                    new LambdaQueryWrapper<SysDictDetail>()
                            .eq(SysDictDetail::getDict, "pos_payment_method")
                            .eq(SysDictDetail::getValue, pay.getPayMethodCode())
                            .last("LIMIT 1")
            );
            if (mainDetail != null && StringUtils.hasText(mainDetail.getCnDesc())) {
                return mainDetail.getCnDesc();
            }
        }

        // 第三优先级 (终极兜底)：脱敏清洗
        String fallbackName = pay.getPayMethodName();
        if (StringUtils.hasText(fallbackName)) {
            // 剔除脏数据前缀，如将 "其他渠道(CASH)" 变成 "CASH"
            return fallbackName.replace("其他渠道", "").replace("(", "").replace(")", "").trim();
        }

        return "扫码支付";
    }

    // ==========================================
    // 基础工具方法 (保持原样)
    // ==========================================
    private String fmt(BigDecimal amt) {
        if (amt == null) return "0.00";
        return amt.setScale(2, RoundingMode.HALF_UP).toString();
    }

    private void writeText(ByteArrayOutputStream bos, String text) throws IOException {
        bos.write(text.getBytes("GBK"));
    }

    private void executeHardwareCommand(byte[] printData) throws PrintException {
        PrintService printService = PrintServiceLookup.lookupDefaultPrintService();
        if (printService == null) return;
        DocPrintJob job = printService.createPrintJob();
        Doc doc = new SimpleDoc(printData, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        job.print(doc, null);
    }

    private String formatItemLine(String orig, String curr, String qty, String disc, String subtotal) {
        return padRight(orig, 6) + padRight(curr, 6) + padRight(qty, 5) + padRight(disc, 6) + padLeft(subtotal, 9);
    }

    private String padRight(String str, int length) {
        int strLen = getStringLength(str);
        if (strLen >= length) return str;
        StringBuilder sb = new StringBuilder(str);
        for (int i = 0; i < length - strLen; i++) sb.append(" ");
        return sb.toString();
    }

    private String padLeft(String str, int length) {
        int strLen = getStringLength(str);
        if (strLen >= length) return str;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length - strLen; i++) sb.append(" ");
        sb.append(str);
        return sb.toString();
    }

    private int getStringLength(String str) {
        if (str == null) return 0;
        int len = 0;
        for (char c : str.toCharArray()) {
            len += (c > 255) ? 2 : 1;
        }
        return len;
    }
}