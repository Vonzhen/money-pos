package com.money.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("pos_member_level")
public class PosMemberLevel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String levelName;
    private String tenantId;
}