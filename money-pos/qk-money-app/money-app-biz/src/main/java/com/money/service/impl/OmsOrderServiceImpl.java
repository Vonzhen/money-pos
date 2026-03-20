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
import com.money.util.MoneyUtil;
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

@Service // 🌟 挂牌，纳入 Spring 容器管理
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
        // 提取前端可能传过来的字符串关键字（防呆处理：兼容 String 或 Long 类型）
        String memberKeyword = queryDTO.getMember() != null ? String.valueOf(queryDTO.getMember()) : null;

        Page<OmsOrder> page = omsOrderMapper.selectPage(PageUtil.toPage(queryDTO), new LambdaQueryWrapper<OmsOrder>()
                // 🌟 修复 1：会员智能搜索。如果输入了手机号或姓名，进行双字段模糊匹配
                .and(StrUtil.isNotBlank(memberKeyword), w -> w
                        .like(OmsOrder::getMember, memberKeyword)
                        .or()
                        .like(OmsOrder::getContact, memberKeyword))

                // 🌟 修复 2：订单号智能搜索。必须用 like 模糊查询，不能用 eq！
                .like(StrUtil.isNotBlank(queryDTO.getOrderNo()), OmsOrder::getOrderNo, queryDTO.getOrderNo())

                // 状态和时间精确匹配保持不变
                .eq(StrUtil.isNotBlank(queryDTO.getStatus()), OmsOrder::getStatus, queryDTO.getStatus())
                .between(queryDTO.getStartTime() != null && queryDTO.getEndTime() != null,
                        OmsOrder::getCreateTime, queryDTO.getStartTime(), queryDTO.getEndTime())
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

        // 1. 组装明细
        List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>().eq(OmsOrderDetail::getOrderNo, order.getOrderNo()));
        vo.setOrderDetails(BeanMapUtil.to(details, OmsOrderDetailVO::new));

        // 2. 组装会员及多轨身份
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

        // 3. 组装操作日志
        List<OmsOrderLog> logs = omsOrderLogService.list(new LambdaQueryWrapper<OmsOrderLog>().eq(OmsOrderLog::getOrderId, order.getId()).orderByAsc(OmsOrderLog::getCreateTime));
        vo.setOrderLog(BeanMapUtil.to(logs, OrderDetailVO.OrderLogVO::new));

        // 4. 组装支付流与找零计算
        List<OmsOrderPay> pays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>().eq(OmsOrderPay::getOrderNo, order.getOrderNo()));
        vo.setPayments(BeanMapUtil.to(pays, OrderDetailVO.OrderPayVO::new));

        // 执行找零逻辑
        calculateChange(vo, pays, order.getPayAmount());

        return vo;
    }

    // 🌟 修复版找零计算：严格执行支付降维建模
    private void calculateChange(OrderDetailVO vo, List<OmsOrderPay> pays, BigDecimal orderReceivableAmount) {
        BigDecimal balanceAmount = BigDecimal.ZERO;
        BigDecimal scanAmount = BigDecimal.ZERO;
        BigDecimal cashAmount = BigDecimal.ZERO;
        BigDecimal changeAmount = BigDecimal.ZERO;

        for (OmsOrderPay pay : pays) {
            PayMethodEnum method = PayMethodEnum.fromCode(pay.getPayMethodCode());
            if (method == null) method = PayMethodEnum.AGGREGATE; // 防御未知支付方式

            if (method == PayMethodEnum.BALANCE) {
                balanceAmount = MoneyUtil.add(balanceAmount, pay.getPayAmount());
            } else if (method == PayMethodEnum.AGGREGATE) {
                scanAmount = MoneyUtil.add(scanAmount, pay.getPayAmount());
            } else if (method == PayMethodEnum.CASH) {
                cashAmount = MoneyUtil.add(cashAmount, pay.getPayAmount());
            }
        }

        BigDecimal totalIn = MoneyUtil.add(MoneyUtil.add(balanceAmount, scanAmount), cashAmount);
        changeAmount = MoneyUtil.subtract(totalIn, orderReceivableAmount);

        if(changeAmount.compareTo(BigDecimal.ZERO) < 0) {
            changeAmount = BigDecimal.ZERO;
        } else {
            // 找零只能从现金池里扣
            cashAmount = MoneyUtil.subtract(cashAmount, changeAmount);
        }

        vo.setBalanceAmount(balanceAmount);
        vo.setScanAmount(scanAmount);
        vo.setCashAmount(cashAmount);
        vo.setChangeAmount(changeAmount);
    }
}