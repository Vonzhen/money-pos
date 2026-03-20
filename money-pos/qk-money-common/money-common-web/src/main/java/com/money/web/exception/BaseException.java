package com.money.web.exception;

import com.money.web.i18n.I18nSupport;
import com.money.web.response.IStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 基本异常 (🌟 大一统升级版：采用无歧义的链式调用注入数据)
 *
 * @author : money
 * @since : 1.0.0
 */
@Getter
@NoArgsConstructor
public class BaseException extends RuntimeException {

    private static final long serialVersionUID = 3620837280475323035L;

    /**
     * 错误代码
     */
    private int errorCode;

    /**
     * 业务数据负荷（前端可直接用于弹窗计算或UI渲染）
     */
    private Object data;

    public BaseException(String message, Object... args) {
        super(I18nSupport.get(message, args));
    }

    public BaseException(IStatus status) {
        super(status.getMessage());
        this.errorCode = status.getCode();
    }

    public BaseException(IStatus status, String message, Object... args) {
        super(I18nSupport.get(message, args));
        this.errorCode = status.getCode();
    }

    // ==========================================
    // 🌟 战舰级新增：链式注入数据负荷 (彻底消灭编译器重载歧义)
    // ==========================================
    public BaseException withData(Object data) {
        this.data = data;
        return this; // 返回当前异常实例，支持 throw new BaseException(...).withData(...)
    }
}