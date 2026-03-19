package com.money.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

@Component
public class DbStrategyInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        String createTableSql = "CREATE TABLE IF NOT EXISTS sys_strategy (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "traffic_order_threshold DECIMAL(10,2) DEFAULT 1.00, " +
                "traffic_value_threshold DECIMAL(10,2) DEFAULT 50.00, " +
                "turnover_lead_time INT DEFAULT 3, " +
                "turnover_target_days INT DEFAULT 14, " +
                "dead_stock_days INT DEFAULT 60, " +
                "weekly_analysis_days INT DEFAULT 90, " +
                "monthly_analysis_days INT DEFAULT 180, " +
                "tenant_id BIGINT DEFAULT 0" +
                ")";
        jdbcTemplate.execute(createTableSql);

        // 🌟 防呆补丁：如果表以前建好了，这里自动为您追加新字段，绝不报错！
        try { jdbcTemplate.execute("ALTER TABLE sys_strategy ADD COLUMN weekly_analysis_days INT DEFAULT 90"); } catch (Exception e) {}
        try { jdbcTemplate.execute("ALTER TABLE sys_strategy ADD COLUMN monthly_analysis_days INT DEFAULT 180"); } catch (Exception e) {}

        String countSql = "SELECT COUNT(*) FROM sys_strategy";
        Integer count = jdbcTemplate.queryForObject(countSql, Integer.class);
        if (count != null && count == 0) {
            String insertSql = "INSERT INTO sys_strategy (traffic_order_threshold, traffic_value_threshold, turnover_lead_time, turnover_target_days, dead_stock_days, weekly_analysis_days, monthly_analysis_days, tenant_id) " +
                    "VALUES (1.00, 50.00, 3, 14, 60, 90, 180, 0)";
            jdbcTemplate.execute(insertSql);
        }
    }
}