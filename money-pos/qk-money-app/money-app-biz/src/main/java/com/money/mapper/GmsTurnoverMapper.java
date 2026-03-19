package com.money.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.money.dto.GmsGoods.TurnoverDataVO.WarningItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface GmsTurnoverMapper {

    // 🌟 终极真理版：
    // 1. 过滤条件改为 g.status IN ('SALE', 'SOLD_OUT')，只管在售和刚卖光的，不管已下架的。
    // 2. 严格对应实体类：g.name (商品名), g.stock (库存)。
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT " +
            "  g.id AS goodsId, " +
            "  g.name AS goodsName, " +
            "  IFNULL(g.stock, 0) AS currentStock, " +
            "  IFNULL(SUM(CASE WHEN o.create_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) THEN od.quantity ELSE 0 END), 0) AS sales30Days, " +
            "  IFNULL(SUM(CASE WHEN o.create_time >= DATE_SUB(CURDATE(), INTERVAL 90 DAY) THEN od.quantity ELSE 0 END), 0) AS sales90Days, " +
            "  MAX(o.create_time) AS lastSaleTime " +
            "FROM gms_goods g " +
            "LEFT JOIN oms_order_detail od ON g.id = od.goods_id " +
            "LEFT JOIN oms_order o ON od.order_no = o.order_no AND o.status IN ('PAID', 'COMPLETED', 'PARTIAL_REFUNDED') " +
            "WHERE g.status IN ('SALE', 'SOLD_OUT') " +
            "GROUP BY g.id, g.name, g.stock")
    List<WarningItemVO> scanAllGoodsTurnover();
}