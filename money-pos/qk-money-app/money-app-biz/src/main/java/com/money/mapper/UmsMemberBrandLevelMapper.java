package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.entity.UmsMemberBrandLevel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UmsMemberBrandLevelMapper extends BaseMapper<UmsMemberBrandLevel> {

    // 3. 品牌高等级会员分布矩阵
    // 🌟 核心修复：
    // 1. 增加 INNER JOIN ums_member m，自动过滤掉物理删除后遗留的孤儿数据
    // 2. 增加 WHERE m.deleted = 0，严格过滤掉被逻辑删除的幽灵会员
    @org.apache.ibatis.annotations.Select("SELECT IFNULL(gb.name, umbl.brand) AS brandName, " +
            "umbl.level_code AS levelCode, " +
            "COUNT(umbl.member_id) AS count " +
            "FROM ums_member_brand_level umbl " +
            "INNER JOIN ums_member m ON umbl.member_id = m.id " +
            "LEFT JOIN gms_brand gb ON umbl.brand = gb.id " +
            "WHERE m.deleted = 0 " +
            "GROUP BY umbl.brand, gb.name, umbl.level_code")
    java.util.List<com.money.dto.Home.MemberBarVO> getMemberBarData();
}