package com.money.mapper;

import com.money.entity.UmsMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 会员表 Mapper 接口 (V3.0 财务口径升级版)
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
public interface UmsMemberMapper extends BaseMapper<UmsMember> {

    /**
     * 🌟 V3.0 会员偏好榜：全状态覆盖，精准扣减退货，数据库底层秒级直出
     */
    @Select("SELECT d.goods_name AS goodsName, " +
            "SUM(d.quantity - IFNULL(d.return_quantity, 0)) AS buyCount " +
            "FROM oms_order_detail d " +
            "JOIN oms_order o ON d.order_no = o.order_no " +
            "WHERE o.member_id = #{memberId} " +
            "  AND o.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " + // 🌟 状态红线
            "GROUP BY d.goods_id, d.goods_name " +
            "HAVING buyCount > 0 " +
            "ORDER BY buyCount DESC " +
            "LIMIT 20")

    List<Map<String, Object>> getTop20Goods(@Param("memberId") Long memberId);
    // 1. 土豪榜：累计消费 Top 50
    @org.apache.ibatis.annotations.Select("SELECT id, name, phone, consume_amount as amount FROM ums_member WHERE deleted = 0 ORDER BY consume_amount DESC LIMIT 50")
    List<com.money.dto.UmsMember.MemberRankVO> getTopConsumeMembers();

    // 2. 储值榜：当前余额 Top 50
    @org.apache.ibatis.annotations.Select("SELECT id, name, phone, balance as amount FROM ums_member WHERE deleted = 0 ORDER BY balance DESC LIMIT 50")
    List<com.money.dto.UmsMember.MemberRankVO> getTopBalanceMembers();

    // 3. 铁粉榜：到店频次 Top 50
    @org.apache.ibatis.annotations.Select("SELECT id, name, phone, consume_times as times FROM ums_member WHERE deleted = 0 ORDER BY consume_times DESC LIMIT 50")
    List<com.money.dto.UmsMember.MemberRankVO> getTopFrequencyMembers();
}