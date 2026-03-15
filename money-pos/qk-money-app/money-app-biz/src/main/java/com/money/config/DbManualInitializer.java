package com.money.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 🌟 数据库手动初始化工具 (大一统库存单据版)
 * 执行完重启后，请注释掉上面的 @Component 即可
 */
@Slf4j
// @Component // ⬅️ 执行完毕看到成功日志后，注释掉此行：// @Component
@RequiredArgsConstructor
public class DbManualInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("🚀 正在检查并执行【大一统库存单据】数据库脚本...");

        // 1. 清理之前的旧表（防止冲突）
        jdbcTemplate.execute("DROP TABLE IF EXISTS `gms_stock_in_order`;");
        jdbcTemplate.execute("DROP TABLE IF EXISTS `gms_stock_in_item`;");

        // 2. 创建【库存单据主表】
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `gms_inventory_doc` (" +
                "  `id` bigint NOT NULL AUTO_INCREMENT," +
                "  `doc_no` varchar(32) NOT NULL COMMENT '单据号(RK/PD/BS开头)'," +
                "  `doc_type` varchar(16) NOT NULL COMMENT '类型: INBOUND, CHECK, OUTBOUND'," +
                "  `total_qty` int NOT NULL DEFAULT 0 COMMENT '变动总数量(绝对值)'," +
                "  `total_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '总金额(进货额或盈亏额)'," +
                "  `operator` varchar(64) DEFAULT NULL COMMENT '经办人'," +
                "  `remark` varchar(255) DEFAULT NULL COMMENT '备注'," +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP," +
                "  `tenant_id` bigint DEFAULT NULL," +
                "  PRIMARY KEY (`id`)," +
                "  UNIQUE KEY `uk_doc_no` (`doc_no`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存单据主表';");

        // 3. 创建【库存单据明细快照表】
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `gms_inventory_doc_item` (" +
                "  `id` bigint NOT NULL AUTO_INCREMENT," +
                "  `doc_no` varchar(32) NOT NULL COMMENT '关联单据号'," +
                "  `goods_id` bigint NOT NULL COMMENT '商品ID'," +
                "  `goods_name` varchar(128) DEFAULT NULL COMMENT '商品名称快照'," +
                "  `barcode` varchar(64) DEFAULT NULL COMMENT '条码快照'," +
                "  `change_qty` int NOT NULL COMMENT '变动数量(带正负号)'," +
                "  `cost_price` decimal(10,2) NOT NULL COMMENT '快照单价(入库为录入价，盘亏为当时均价)'," +
                "  `pre_stock` bigint DEFAULT '0' COMMENT '变动前账面库存'," +
                "  `after_stock` bigint DEFAULT '0' COMMENT '变动后实际库存'," +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP," +
                "  `tenant_id` bigint DEFAULT NULL," +
                "  PRIMARY KEY (`id`)," +
                "  KEY `idx_doc_no` (`doc_no`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存单据明细快照表';");

        log.info("✅ 【大一统库存单据】架构搭建完毕！请停机并将 DbManualInitializer 的 @Component 注解注释掉。");
    }
}