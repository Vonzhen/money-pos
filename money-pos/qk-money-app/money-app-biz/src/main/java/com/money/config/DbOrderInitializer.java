package com.money.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 🌟 数据库手动初始化工具 (增加订单双轨计价审计字段)
 */
@Slf4j
//@Component // ⬅️ 启动执行一次后，请务必注释掉！
@RequiredArgsConstructor
public class DbOrderInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("🚀 [数据库升级工具] 正在为【订单主表】补充双轨制计价审计字段...");

        try {
            // 使用 ALTER TABLE ADD COLUMN 补充字段 (避免删表丢失数据)
            String alterSql = "ALTER TABLE `oms_order` " +
                    "ADD COLUMN `retail_amount` decimal(10,2) DEFAULT '0.00' COMMENT '零售价总额(吊牌价)', " +
                    "ADD COLUMN `member_amount` decimal(10,2) DEFAULT '0.00' COMMENT '会员价基准总额(底价)', " +
                    "ADD COLUMN `privilege_amount` decimal(10,2) DEFAULT '0.00' COMMENT '会员特权原值(零售价-会员价)', " +
                    "ADD COLUMN `actual_coupon_deduct` decimal(10,2) DEFAULT '0.00' COMMENT '真实会员券核销额(免收为0)', " +
                    "ADD COLUMN `waived_coupon_amount` decimal(10,2) DEFAULT '0.00' COMMENT '店铺承担免收额(免收为特权原值)';";

            jdbcTemplate.execute(alterSql);
            log.info("✅ [数据库升级工具] 字段补充成功！");
        } catch (Exception e) {
            log.warn("⚠️ [数据库升级工具] 字段可能已存在，跳过添加。异常信息: {}", e.getMessage());
        }

        log.warn("⚠️ 警告：请立即去 DbOrderInitializer.java 中注释掉 @Component，然后重新启动系统！");
    }
}