package com.money.dto.Home;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "品牌会员等级分布图表数据")
public class MemberBarVO {
    @Schema(description = "品牌名称")
    private String brandName;
    @Schema(description = "会员等级字典Code")
    private String levelCode;
    @Schema(description = "该等级会员人数")
    private Integer count;
}