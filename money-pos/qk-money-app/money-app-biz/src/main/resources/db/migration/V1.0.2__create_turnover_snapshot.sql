-- --------------------------------------------------------
-- V1.0.2 供应链周转预警每日快照表 (支持历史趋势追踪)
-- --------------------------------------------------------

CREATE TABLE `gms_turnover_warning_snapshot` (
                                                 `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                                 `snapshot_date` date NOT NULL COMMENT '快照归属日期 (YYYY-MM-DD)',

                                                 `replenish_count` int(11) NOT NULL DEFAULT '0' COMMENT '当日触发【紧急补货】的商品总数 (红线)',
                                                 `dead_stock_count` int(11) NOT NULL DEFAULT '0' COMMENT '当日触发【积压清仓】的商品总数 (蓝线)',

                                                 `top_replenish_goods_json` json DEFAULT NULL COMMENT '补货压力商品黑榜Top20 (存为JSON数组，含商品ID、名称、缺货量)',
                                                 `top_dead_stock_goods_json` json DEFAULT NULL COMMENT '积压死库存商品黑榜Top20 (存为JSON数组，含商品ID、名称、积压量)',

                                                 `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照生成时间',
                                                 `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',

                                                 PRIMARY KEY (`id`),
                                                 UNIQUE KEY `uk_snapshot_date` (`snapshot_date`) -- 保证每天只存一条快照，支持幂等更新
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存周转预警每日快照表';