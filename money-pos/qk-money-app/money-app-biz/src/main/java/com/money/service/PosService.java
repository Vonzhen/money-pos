package com.money.service;

import com.money.dto.OmsOrder.OmsOrderVO;
import com.money.dto.Pos.PosGoodsVO;
import com.money.dto.Pos.PosMemberVO;
import com.money.dto.pos.SettleAccountsDTO; // 🌟 确保小写
import com.money.entity.PosCouponRule;
import java.util.List;

public interface PosService {
    List<PosGoodsVO> listGoods(String barcode);
    List<PosMemberVO> listMember(String member);
    List<PosCouponRule> getValidCouponRules();
    OmsOrderVO settleAccounts(SettleAccountsDTO dto);
}