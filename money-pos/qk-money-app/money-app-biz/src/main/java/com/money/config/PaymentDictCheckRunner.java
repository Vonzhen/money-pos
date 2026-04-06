package com.money.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.SysDictDetail;
import com.money.mapper.SysDictDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 🌟 支付字典核反应堆安全阀：系统启动自检
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentDictCheckRunner implements ApplicationRunner {

    private final SysDictDetailMapper sysDictDetailMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("🔍 [自检引擎] 正在校验支付核心字典完整性...");

        Long count = sysDictDetailMapper.selectCount(
                new LambdaQueryWrapper<SysDictDetail>()
                        .eq(SysDictDetail::getDict, "pos_payment_method")
        );

        if (count == null || count == 0) {
            log.error("=========================================================");
            log.error("🚨 致命错误：支付基础权威字典【pos_payment_method】缺失！");
            log.error("🚨 请检查数据库 sys_dict_detail 表是否已正确初始化。");
            log.error("🚨 为保证全系统资金账目命名一致性，系统将拒绝继续启动！");
            log.error("=========================================================");

            // 抛出异常阻断 Spring Boot 启动流程，防止带病运行
            throw new IllegalStateException("支付基础字典【pos_payment_method】未初始化，系统启动中止！");
        }

        log.info("✅ [自检引擎] 支付字典校验通过，资金命名权威防线已就绪。");
    }
}