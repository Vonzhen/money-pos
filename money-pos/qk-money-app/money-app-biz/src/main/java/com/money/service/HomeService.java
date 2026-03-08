package com.money.service;


import com.money.dto.Home.HomeCountVO;

/**
 * @author : money
 * @version : 1.0.0
 * @description : 主页服务
 * @createTime : 2022-04-28 21:38:43
 */
public interface HomeService {

    /**
     * 主页统计数
     *
     * @return {@link HomeCountVO}
     */
    HomeCountVO homeCount();
    // 返回我们定义好的强类型大包装对象
    com.money.dto.Home.HomeChartsVO getChartsData();
}
