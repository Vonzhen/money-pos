package com.money.controller;

import com.money.entity.SysStrategy;
import com.money.mapper.SysStrategyMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "SysStrategy", description = "策略中心控制台")
@RestController
@RequestMapping("/sys/strategy")
@RequiredArgsConstructor
public class SysStrategyController {

    private final SysStrategyMapper sysStrategyMapper;

    @Operation(summary = "获取全局策略")
    @GetMapping("/get")
    public SysStrategy getStrategy() {
        SysStrategy strategy = sysStrategyMapper.getGlobalStrategy();
        if (strategy == null) {
            strategy = new SysStrategy(); // 兜底防空指针
        }
        return strategy;
    }

    @Operation(summary = "保存/更新全局策略")
    @PostMapping("/save")
    public String saveStrategy(@RequestBody SysStrategy strategy) {
        SysStrategy exist = sysStrategyMapper.getGlobalStrategy();
        if (exist != null) {
            strategy.setId(exist.getId());
            sysStrategyMapper.updateById(strategy);
        } else {
            strategy.setTenantId(0L);
            sysStrategyMapper.insert(strategy);
        }
        return "success";
    }
}