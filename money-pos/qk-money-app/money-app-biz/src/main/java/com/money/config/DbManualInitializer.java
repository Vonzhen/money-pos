package com.money.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 🌟 数据库手动初始化工具 (修复审计字段版)
 */
@Slf4j
// @Component // ⬅️ 执行完毕看到成功日志后，注释掉此行：// @Component
@RequiredArgsConstructor
public class DbManualInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("🚀 正在修复【库存单据表】缺失的审计字段...");

        // 1. 删除之前的半成品表
        jdbcTemplate.execute("DROP TABLE IF EXISTS `gms_inventory_doc`;");
        jdbcTemplate.execute("DROP TABLE IF EXISTS `gms_inventory_doc_item`;");

        // 2. 重新创建【库存单据主表】（补全了审计字段）
        jdbcTemplate.execute("CREATE TABLE `gms_inventory_doc` (" +
                "  `id` bigint NOT NULL AUTO_INCREMENT," +
                "  `doc_no` varchar(32) NOT NULL COMMENT '单据号'," +
                "  `doc_type` varchar(16) NOT NULL COMMENT '类型: INBOUND, CHECK, OUTBOUND'," +
                "  `total_qty` int NOT NULL DEFAULT 0 COMMENT '总变动数量'," +
                "  `total_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '总金额'," +
                "  `operator` varchar(64) DEFAULT NULL COMMENT '经办人'," +
                "  `remark` varchar(255) DEFAULT NULL COMMENT '备注'," +
                "  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人(BaseEntity所需)'," +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  `update_by` varchar(64) DEFAULT NULL COMMENT '修改人(BaseEntity所需)'," +
                "  `update_time` datetime DEFAULT NULL COMMENT '修改时间'," +
                "  `tenant_id` bigint DEFAULT '0' COMMENT '租户ID'," +
                "  PRIMARY KEY (`id`)," +
                "  UNIQUE KEY `uk_doc_no` (`doc_no`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存单据主表';");

        // 3. 重新创建【库存单据明细表】（补全了审计字段）
        jdbcTemplate.execute("CREATE TABLE `gms_inventory_doc_item` (" +
                "  `id` bigint NOT NULL AUTO_INCREMENT," +
                "  `doc_no` varchar(32) NOT NULL COMMENT '关联单据号'," +
                "  `goods_id` bigint NOT NULL COMMENT '商品ID'," +
                "  `goods_name` varchar(128) DEFAULT NULL COMMENT '商品名称快照'," +
                "  `barcode` varchar(64) DEFAULT NULL COMMENT '条码快照'," +
                "  `change_qty` int NOT NULL COMMENT '变动数量'," +
                "  `cost_price` decimal(10,2) NOT NULL COMMENT '快照单价'," +
                "  `pre_stock` bigint DEFAULT '0' COMMENT '变动前库存'," +
                "  `after_stock` bigint DEFAULT '0' COMMENT '变动后库存'," +
                "  `create_by` varchar(64) DEFAULT NULL," +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP," +
                "  `update_by` varchar(64) DEFAULT NULL," +
                "  `update_time` datetime DEFAULT NULL," +
                "  `tenant_id` bigint DEFAULT '0'," +
                "  PRIMARY KEY (`id`)," +
                "  KEY `idx_doc_no` (`doc_no`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存单据明细表';");

        log.info("✅ 【库存单据表】审计字段修复完毕！请注释掉 @Component 后重新启动。");
    }
}