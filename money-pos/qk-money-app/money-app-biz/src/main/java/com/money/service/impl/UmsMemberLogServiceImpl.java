package com.money.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.dto.UmsMember.UmsMemberLogQueryDTO;
import com.money.dto.UmsMember.UmsMemberLogVO;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.service.UmsMemberLogService;
import com.money.util.PageUtil;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UmsMemberLogServiceImpl extends ServiceImpl<UmsMemberLogMapper, UmsMemberLog> implements UmsMemberLogService {

    private final UmsMemberMapper umsMemberMapper;

    @Override
    public PageVO<UmsMemberLogVO> list(UmsMemberLogQueryDTO queryDTO) {
        // 1. 分页查询流水表
        Page<UmsMemberLog> page = this.lambdaQuery()
                .eq(queryDTO.getMemberId() != null, UmsMemberLog::getMemberId, queryDTO.getMemberId())
                .eq(StrUtil.isNotBlank(queryDTO.getType()), UmsMemberLog::getType, queryDTO.getType())
                .eq(StrUtil.isNotBlank(queryDTO.getOperateType()), UmsMemberLog::getOperateType, queryDTO.getOperateType())
                .orderByDesc(UmsMemberLog::getCreateTime)
                .page(PageUtil.toPage(queryDTO));

        // 2. 转换并关联查询会员名称
        PageVO<UmsMemberLogVO> pageVO = PageUtil.toPageVO(page, UmsMemberLogVO::new);

        if (!pageVO.getRecords().isEmpty()) {
            List<Long> memberIds = pageVO.getRecords().stream().map(log -> this.getById(log.getId()).getMemberId()).collect(Collectors.toList());
            // 为了简单直接，我们在这里循环补全一下名字（实际大规模数据建议用 Join 或 Map）
            for (UmsMemberLogVO vo : pageVO.getRecords()) {
                UmsMemberLog rawLog = this.getById(vo.getId());
                UmsMember m = umsMemberMapper.selectById(rawLog.getMemberId());
                if (m != null) {
                    vo.setMemberName(m.getName());
                    vo.setMemberPhone(m.getPhone());
                }
            }
        }
        return pageVO;
    }
}