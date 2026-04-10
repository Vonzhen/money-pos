package com.money.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.constant.BizErrorStatus;
import com.money.constant.PayMethodEnum;
import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.dto.OmsOrder.OmsOrderVO;
import com.money.dto.OmsOrder.OrderDetailVO;
import com.money.dto.OmsOrderDetail.OmsOrderDetailVO;
import com.money.dto.UmsMember.UmsMemberVO;
import com.money.entity.*;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderPayMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.service.OmsOrderDetailService;
import com.money.service.OmsOrderLogService;
import com.money.service.OmsOrderService;
import com.money.service.UmsMemberService;
import com.money.util.PageUtil;
import com.money.web.exception.BaseException;
import com.money.web.util.BeanMapUtil;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OmsOrderServiceImpl extends ServiceImpl<OmsOrderMapper, OmsOrder> implements OmsOrderService {

    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderDetailService omsOrderDetailService;
    private final OmsOrderLogService omsOrderLogService;
    private final UmsMemberService umsMemberService;
    private final OmsOrderPayMapper omsOrderPayMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;

    @Override
    public PageVO<OmsOrderVO> list(OmsOrderQueryDTO queryDTO) {
        // ... (保持原 list 方法完全不变) ...
        String memberKeyword = queryDTO.getMember() != null ? String.valueOf(queryDTO.getMember()) : null;
        Page<OmsOrder> page = omsOrderMapper.selectPage(PageUtil.toPage(queryDTO), new LambdaQueryWrapper<OmsOrder>()
                .and(StrUtil.isNotBlank(memberKeyword), w -> w.like(OmsOrder::getMember, memberKeyword).or().like(OmsOrder::getContact, memberKeyword))
                .like(StrUtil.isNotBlank(queryDTO.getOrderNo()), OmsOrder::getOrderNo, queryDTO.getOrderNo())
                .eq(StrUtil.isNotBlank(queryDTO.getStatus()), OmsOrder::getStatus, queryDTO.getStatus())
                .between(queryDTO.getStartTime() != null && queryDTO.getEndTime() != null, OmsOrder::getCreateTime, queryDTO.getStartTime(), queryDTO.getEndTime())
                .orderByDesc(OmsOrder::getCreateTime));
        return PageUtil.toPageVO(page, OmsOrderVO::new);
    }

    @Override
    public OrderDetailVO getOrderDetail(Long id) {
        OmsOrder order = omsOrderMapper.selectById(id);
        if (order == null) throw new BaseException(BizErrorStatus.POS_SETTLE_REQ_EMPTY, "订单不存在");
        return assembleOrderDetail(order);
    }

    @Override
    public OrderDetailVO getOrderDetailByNo(String orderNo) {
        OmsOrder order = omsOrderMapper.selectOne(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getOrderNo, orderNo));
        if (order == null) throw new BaseException(BizErrorStatus.POS_SETTLE_REQ_EMPTY, "订单不存在");
        return assembleOrderDetail(order);
    }

    private OrderDetailVO assembleOrderDetail(OmsOrder order) {
        OrderDetailVO vo = BeanMapUtil.to(order, OrderDetailVO::new);

        List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>().eq(OmsOrderDetail::getOrderNo, order.getOrderNo()));
        vo.setOrderDetails(BeanMapUtil.to(details, OmsOrderDetailVO::new));

        if (order.getMemberId() != null) {
            UmsMember member = umsMemberService.getById(order.getMemberId());
            if (member != null) {
                UmsMemberVO memberVO = BeanMapUtil.to(member, UmsMemberVO::new);
                List<UmsMemberBrandLevel> levels = umsMemberBrandLevelMapper.selectList(new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, member.getId()));
                if (levels != null && !levels.isEmpty()) {
                    Map<String, String> levelMap = new HashMap<>();
                    for (UmsMemberBrandLevel bl : levels) levelMap.put(bl.getBrand(), bl.getLevelCode());
                    memberVO.setBrandLevels(levelMap);
                }
                vo.setMemberInfo(memberVO);
            }
        }

        List<OmsOrderLog> logs = omsOrderLogService.list(new LambdaQueryWrapper<OmsOrderLog>().eq(OmsOrderLog::getOrderId, order.getId()).orderByAsc(OmsOrderLog::getCreateTime));
        vo.setOrderLog(BeanMapUtil.to(logs, OrderDetailVO.OrderLogVO::new));

        List<OmsOrderPay> pays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>().eq(OmsOrderPay::getOrderNo, order.getOrderNo()));
        vo.setPayments(BeanMapUtil.to(pays, OrderDetailVO.OrderPayVO::new));

        // 🌟 核心重构：废弃 calculateChange()。直接从 OmsOrderPay 快照中读取展示！
        BigDecimal balanceAmount = BigDecimal.ZERO;
        BigDecimal scanAmount = BigDecimal.ZERO;
        BigDecimal cashAmount = BigDecimal.ZERO;
        BigDecimal changeAmount = BigDecimal.ZERO;

        for (OmsOrderPay pay : pays) {
            PayMethodEnum method = PayMethodEnum.fromCode(pay.getPayMethodCode());
            if (method == null) method = PayMethodEnum.AGGREGATE;

            // 读取原始实付 (兼容老数据)
            BigDecimal orig = pay.getOriginalAmount() != null ? pay.getOriginalAmount() : pay.getPayAmount();

            if (method == PayMethodEnum.BALANCE) balanceAmount = balanceAmount.add(orig);
            else if (method == PayMethodEnum.AGGREGATE) scanAmount = scanAmount.add(orig);
            else if (method == PayMethodEnum.CASH) cashAmount = cashAmount.add(orig);

            if (pay.getChangeAllocated() != null) {
                changeAmount = changeAmount.add(pay.getChangeAllocated());
            }
        }

        vo.setBalanceAmount(balanceAmount);
        vo.setScanAmount(scanAmount);
        vo.setCashAmount(cashAmount);
        vo.setChangeAmount(changeAmount);

        return vo;
    }
}