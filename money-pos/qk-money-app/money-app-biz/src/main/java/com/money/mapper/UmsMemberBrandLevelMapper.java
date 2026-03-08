package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.entity.UmsMemberBrandLevel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UmsMemberBrandLevelMapper extends BaseMapper<UmsMemberBrandLevel> {
    // 3. 品牌高等级会员分布矩阵
    @org.apache.ibatis.annotations.Select("SELECT IFNULL(gb.name, umbl.brand) AS brandName, " +
            "umbl.level_code AS levelCode, " +
            "COUNT(umbl.member_id) AS count " +
            "FROM ums_member_brand_level umbl " +
            "LEFT JOIN gms_brand gb ON umbl.brand = gb.id " +
            "GROUP BY umbl.brand, gb.name, umbl.level_code")
    java.util.List<com.money.dto.Home.MemberBarVO> getMemberBarData();
}