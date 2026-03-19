package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.entity.OmsDailySummary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 每日经营快照 Mapper
 * 职责：专职负责 oms_daily_summary 表的基础读写
 */
@Mapper
public interface OmsDailySummaryMapper extends BaseMapper<OmsDailySummary> {

    // 继承 BaseMapper 后，自带 insert, update, selectList 等能力，无需手写基础 SQL。
    // 后续如果需要复杂的跨表聚合查询，可以补充在这里。
}