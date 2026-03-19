package com.money.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("sys_strategy")
@Schema(description = "全局经营策略参数表")
public class SysStrategy {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "客流极低判定-单量阈值(默认1单)")
    private BigDecimal trafficOrderThreshold;

    @Schema(description = "客流极低判定-产出阈值(默认50元)")
    private BigDecimal trafficValueThreshold;

    @Schema(description = "补货提前期-LeadTime(默认3天)")
    private Integer turnoverLeadTime;

    @Schema(description = "期望备货天数(默认14天)")
    private Integer turnoverTargetDays;

    @Schema(description = "死库存判定天数(默认60天)")
    private Integer deadStockDays;

    // ==========================================
    // 🌟 新增：宏观潮汐大盘采样参数
    // ==========================================
    @Schema(description = "按周分析取样天数(默认90天)")
    private Integer weeklyAnalysisDays;

    @Schema(description = "按月分析取样天数(默认180天)")
    private Integer monthlyAnalysisDays;

    @Schema(description = "多租户隔离")
    private Long tenantId;
}