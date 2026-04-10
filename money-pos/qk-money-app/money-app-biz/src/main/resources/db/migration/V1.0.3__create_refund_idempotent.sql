-- ========================================================
-- V1.0.3: 创建退款防重放幂等表 (退款领域防线的物理底座)
-- ========================================================

CREATE TABLE IF NOT EXISTS `oms_refund_idempotent` (
                                                       `req_id` varchar(64) NOT NULL COMMENT '前端请求幂等键',
    `biz_type` varchar(32) NOT NULL COMMENT '业务类型 (如 FULL_REFUND, PARTIAL_REFUND)',
    `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '多租户ID (兼容底层租户插件)',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '拦截创建时间',
    PRIMARY KEY (`req_id`, `biz_type`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款防重放幂等表';