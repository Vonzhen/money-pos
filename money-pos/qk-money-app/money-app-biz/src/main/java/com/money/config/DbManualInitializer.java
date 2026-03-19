package com.money.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 🌟 数据库手动初始化工具 (小票动态配置表 V2 升级版 - 包含门店地址)
 */
@Slf4j
@Component // ⬅️ 保持放开状态，让它在启动时执行
@RequiredArgsConstructor
public class DbManualInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("🚀 [数据库工具] 正在为您升级【小票打印与硬件配置表 sys_print_config】...");

        // ==========================================
        // 升级：小票打印与硬件动态配置表 sys_print_config (增加门店地址)
        // ==========================================
        jdbcTemplate.execute("DROP TABLE IF EXISTS `sys_print_config`;");

        jdbcTemplate.execute("CREATE TABLE `sys_print_config` (" +
                "  `id` bigint NOT NULL DEFAULT '1' COMMENT '主键(固定为1)'," +
                "  `shop_name` varchar(64) DEFAULT NULL COMMENT '小票抬头店名'," +
                "  `shop_phone` varchar(32) DEFAULT NULL COMMENT '联系电话'," +
                "  `shop_address` varchar(128) DEFAULT NULL COMMENT '门店地址'," + // 🌟 新增字段
                "  `header_msg` varchar(128) DEFAULT NULL COMMENT '头部欢迎语'," +
                "  `footer_msg` varchar(255) DEFAULT NULL COMMENT '底部留言(退换货规则)'," +
                "  `auto_print` tinyint(1) NOT NULL DEFAULT '1' COMMENT '结账后自动打印小票(1:是, 0:否)'," +
                "  `open_drawer` tinyint(1) NOT NULL DEFAULT '1' COMMENT '结账后自动弹开钱箱(1:是, 0:否)'," +
                "  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人'," +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  `update_by` varchar(64) DEFAULT NULL COMMENT '修改人'," +
                "  `update_time` datetime DEFAULT NULL COMMENT '修改时间'," +
                "  `tenant_id` bigint DEFAULT '0' COMMENT '租户ID'," +
                "  PRIMARY KEY (`id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小票打印与硬件配置表';");

        // 初始化种子数据 (包含了门店地址)
        jdbcTemplate.execute("INSERT INTO `sys_print_config` " +
                "(`id`, `shop_name`, `shop_phone`, `shop_address`, `footer_msg`, `auto_print`, `open_drawer`, `create_by`) " +
                "VALUES (1, '麦尼收银(演示)', '138-8888-8888', '深圳市南山区科技园XXX号', '凭此小票七日内免费退换，谢谢惠顾！', 1, 1, 'System');");

        log.info("✅ [数据库工具] 小票配置表 V2 升级成功！");
        log.warn("⚠️ 警告：请立即去 DbManualInitializer.java 中注释掉 @Component，防止下次重启重复执行！");
    }
}