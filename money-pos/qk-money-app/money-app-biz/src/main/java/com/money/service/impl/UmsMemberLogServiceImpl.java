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

        // 🌟 修复手机号搜索
        Long searchMemberId = queryDTO.getMemberId();
        if (StrUtil.isNotBlank(queryDTO.getPhone())) {
            UmsMember member = umsMemberMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UmsMember>()
                            .eq(UmsMember::getPhone, queryDTO.getPhone())
            );
            if (member != null) {
                searchMemberId = member.getId();
            } else {
                // 🌟 修复编译报错：如果查不到人，赋一个绝对不存在的 ID (-1L)
                // 让底下的标准分页查询去查，自然会完美返回 0 条记录的标准 PageVO 格式
                searchMemberId = -1L;
            }
        }

        // 1. 分页查询流水表
        Page<UmsMemberLog> page = this.lambdaQuery()
                .eq(searchMemberId != null, UmsMemberLog::getMemberId, searchMemberId)
                .eq(StrUtil.isNotBlank(queryDTO.getType()), UmsMemberLog::getType, queryDTO.getType())
                .eq(StrUtil.isNotBlank(queryDTO.getOperateType()), UmsMemberLog::getOperateType, queryDTO.getOperateType())
                .orderByDesc(UmsMemberLog::getCreateTime)
                .page(PageUtil.toPage(queryDTO));

        // 2. 转换 VO
        PageVO<UmsMemberLogVO> pageVO = PageUtil.toPageVO(page, UmsMemberLogVO::new);

        if (page.getRecords() != null && !page.getRecords().isEmpty()) {
            java.util.Set<Long> memberIds = page.getRecords().stream()
                    .map(UmsMemberLog::getMemberId)
                    .collect(java.util.stream.Collectors.toSet());

            java.util.Map<Long, UmsMember> memberMap = new java.util.HashMap<>();
            if (!memberIds.isEmpty()) {
                java.util.List<UmsMember> members = umsMemberMapper.selectBatchIds(memberIds);
                memberMap = members.stream().collect(java.util.stream.Collectors.toMap(UmsMember::getId, m -> m));
            }

            for (int i = 0; i < page.getRecords().size(); i++) {
                UmsMemberLog rawLog = page.getRecords().get(i);
                UmsMemberLogVO vo = pageVO.getRecords().get(i);

                UmsMember m = memberMap.get(rawLog.getMemberId());
                if (m != null) {
                    vo.setMemberName(m.getName());
                    vo.setMemberPhone(m.getPhone());
                }
            }
        }
        return pageVO;
    }
}