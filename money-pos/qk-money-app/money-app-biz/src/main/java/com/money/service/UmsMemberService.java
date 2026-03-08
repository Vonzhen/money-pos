package com.money.service;

import com.money.entity.UmsMember;
import com.baomidou.mybatisplus.extension.service.IService;
import com.money.web.vo.PageVO;
import com.money.dto.UmsMember.UmsMemberDTO;
import com.money.dto.UmsMember.UmsMemberQueryDTO;
import com.money.dto.UmsMember.UmsMemberVO;

import java.math.BigDecimal;
import java.util.Set;

/**
 * <p>
 * 会员表 服务类
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
public interface UmsMemberService extends IService<UmsMember> {

    PageVO<UmsMemberVO> list(UmsMemberQueryDTO queryDTO);

    void add(UmsMemberDTO addDTO);

    void update(UmsMemberDTO updateDTO);

    void delete(Set<Long> ids);

    /**
     * 消费
     *
     * @param id     id
     * @param amount 消费金额
     * @param coupon 优惠券
     */
    void consume(Long id, BigDecimal amount, BigDecimal coupon);

    /**
     * 退款
     *
     * @param id                  id
     * @param amount              消费金额
     * @param coupon              优惠券
     * @param increaseCancelTimes 增加退单次数
     */
    void rebate(Long id, BigDecimal amount, BigDecimal coupon, boolean increaseCancelTimes, String orderNo);

    void recharge(com.money.dto.Ums.RechargeDTO dto);

    /**
     * 老会员 Excel 批量导入
     */
    void importMembers(org.springframework.web.multipart.MultipartFile file);

    // ==========================================
    // 🌟 核心新增：获取画像所需的 Top 10 商品接口
    // ==========================================
    /**
     * 获取会员最爱购买的 Top 10 商品
     */
    java.util.List<java.util.Map<String, Object>> getTop10Goods(Long memberId);

    /**
     * 沉睡雷达：按天数筛选流失会员（按消费总额降序，优先挽回大客户）
     */
    java.util.List<UmsMemberVO> getDormantMembers(Integer days);

    /**
     * 导弹发射：批量为指定会员派发满减券，并记入流水
     */
    void batchIssueVoucher(java.util.List<Long> memberIds, Long ruleId, Integer quantity);
}