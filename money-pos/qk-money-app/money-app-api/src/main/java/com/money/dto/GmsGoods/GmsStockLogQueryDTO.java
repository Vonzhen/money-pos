package com.money.dto.GmsGoods;

import com.money.web.dto.PageQueryRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "进销存台账查询参数")
public class GmsStockLogQueryDTO extends PageQueryRequest {

    @Schema(description = "商品名称")
    private String goodsName;

    @Schema(description = "变动类型(SALE销售, RETURN退货, INBOUND入库, SCRAP报损, CHECK盘点)")
    private String type;

    @Schema(description = "关联单号")
    private String orderNo;

}