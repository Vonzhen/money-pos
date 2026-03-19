package com.money.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 🌟 [阵地二] 资产驾驶舱数据底座初始化工具
 * 作用：一键推平并创建符合《8.1 白皮书》定义的资产分析表
 */
@Slf4j
//@Component // ⬅️ 执行时请放开，执行完请务必注释掉
@RequiredArgsConstructor
public class DbReportInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("🚀 [资产驾驶舱] 正在构建资产溯源与经营分析底座...");

        // 1. 清理旧表（生产环境慎用，改造期专用）
        jdbcTemplate.execute("DROP TABLE IF EXISTS `rpt_asset_daily_summary`;");
        jdbcTemplate.execute("DROP TABLE IF EXISTS `rpt_member_asset_source`;");

        // 2. 创建【每日资产经营汇总表】
        // 严格遵循白皮书：final_pay_amount(真钱), waived_coupon_amount(免收), actual_coupon_deduct(核销)
        jdbcTemplate.execute("CREATE TABLE `rpt_asset_daily_summary` (" +
                "  `id` bigint NOT NULL AUTO_INCREMENT," +
                "  `report_date` date NOT NULL COMMENT '统计日期'," +
                "  `total_real_cash` decimal(12,2) DEFAULT '0.00' COMMENT '今日真金白银流入 (final_pay_amount累加)'," +
                "  `total_waived_amount` decimal(12,2) DEFAULT '0.00' COMMENT '今日店铺免收成本 (waived_coupon_amount累加)'," +
                "  `total_asset_deduct` decimal(12,2) DEFAULT '0.00' COMMENT '今日会员资产核销 (actual_coupon_deduct累加)'," +
                "  `order_count` int DEFAULT '0' COMMENT '成交单数'," +
                "  `refund_amount` decimal(12,2) DEFAULT '0.00' COMMENT '今日退款支出'," +
                "  `net_income` decimal(12,2) DEFAULT '0.00' COMMENT '实际净营收 (final_sales_amount)'," +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)," +
                "  UNIQUE KEY `uk_date` (`report_date`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日资产经营汇总表';");

        // 3. 创建【会员资产成分分析表】
        // 核心痛点：解决 1:10 充值后，本金与赠送金的比例穿透
        jdbcTemplate.execute("CREATE TABLE `rpt_member_asset_source` (" +
                "  `id` bigint NOT NULL AUTO_INCREMENT," +
                "  `member_id` bigint NOT NULL COMMENT '会员ID'," +
                "  `total_balance` decimal(12,2) DEFAULT '0.00' COMMENT '当前总余额'," +
                "  `principal_amount` decimal(12,2) DEFAULT '0.00' COMMENT '真金白银本金部分'," +
                "  `gift_amount` decimal(12,2) DEFAULT '0.00' COMMENT '店铺赠送金额部分'," +
                "  `external_amount` decimal(12,2) DEFAULT '0.00' COMMENT '外部渠道转入金额'," +
                "  `last_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)," +
                "  KEY `idx_member` (`member_id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员资产成分溯源表';");

        log.info("✅ [资产驾驶舱] 数据库底座构建完毕！资产溯源逻辑已激活。");
        log.warn("⚠️ 警告：任务已完成，请立即注释掉 DbReportInitializer.java 中的 @Component，防止重启时数据重置！");
    }
}