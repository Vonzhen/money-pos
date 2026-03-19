import { watch, onUnmounted } from 'vue'
import { usePosStore } from './usePosStore'

/**
 * 🌟 架构规范：客显实时同步通讯引擎 (Hook) - 修复版
 */
export function useDisplaySync(checkoutVisible) {
    // 🌟 引入 totalCount 作为最稳健的雷达触发器
    const { cartList, currentMember, participatingAmount, paymentStats, getCartItemPrices, totalCount } = usePosStore();
    let displayWs = null;

    // 1. 组装商品快照
    const formatCartForDisplay = () => {
        return cartList.value.map(item => {
            // 🌟 使用新的双轨制价格函数
            const { unitOriginalPrice, unitRealPrice } = getCartItemPrices(item, currentMember.value);
            const qty = Number(item.qty) || 1;
            return {
                name: item.name,
                qty: qty,
                originalPrice: unitOriginalPrice,
                price: unitRealPrice,
                subtotal: Number((unitRealPrice * qty).toFixed(2))
            };
        });
    }

    // 2. 组装支付状态快照
    const getPaymentState = () => {
        const isCheckout = checkoutVisible.value;
        const stats = paymentStats.value;

        if (!isCheckout) {
            return { targetPay: stats.targetPay, tendered: 0, aggregate: 0, change: 0 };
        }

        return {
            targetPay: stats.targetPay,
            tendered: stats.tendered,
            aggregate: stats.aggregate,
            change: stats.change
        };
    }

    // 3. 初始化基站
    const initDisplaySync = () => {
        const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const hostname = window.location.hostname || 'localhost';
        const wsUrl = `${wsProtocol}//${hostname}:9101/money-pos/ws/pos-sync`;

        displayWs = new WebSocket(wsUrl);
        displayWs.onopen = () => console.log('🟢 [收银台-通讯连] 镜像投屏基站已就绪！');
        displayWs.onerror = () => console.error('❌ [收银台-通讯连] 无法连接基站。');
        displayWs.onclose = () => { setTimeout(initDisplaySync, 5000); };
    }

    // 4. 发射器
    const broadcastToDisplay = (state) => {
        if (displayWs && displayWs.readyState === WebSocket.OPEN) {
            if (state === 'IDLE' || state === 'PAY_SUCCESS') {
                displayWs.send(JSON.stringify({ state }));
                return;
            }
            displayWs.send(JSON.stringify({
                state,
                cart: formatCartForDisplay(),
                pAmount: participatingAmount.value,
                member: currentMember.value,
                payment: getPaymentState()
            }));
        }
    }

    // 5. 🌟 修复版：联合雷达阵列 (使用 totalCount 代替 cartList 监听，杜绝 Proxy 浅拷贝 Bug)
    watch([
        () => totalCount.value,
        () => currentMember.value,
        () => paymentStats.value
    ], ([newCount]) => {
        // 只要件数为 0 且不在结账页面，就是 IDLE (播广告)
        if (newCount === 0 && !checkoutVisible.value) {
            broadcastToDisplay('IDLE');
        } else {
            // 只要有件数，或者在结账页面，就切回明细
            broadcastToDisplay('CASHIER_UPDATE');
        }

    }, { deep: true });

    watch(checkoutVisible, (isVisible) => {
        if (isVisible) broadcastToDisplay('CHECKOUT_OPEN');
        else if (totalCount.value === 0) broadcastToDisplay('IDLE');
        else broadcastToDisplay('CASHIER_UPDATE');
    });

    initDisplaySync();
    onUnmounted(() => { if (displayWs) displayWs.close(); });

    return {
        notifyPaySuccess: () => broadcastToDisplay('PAY_SUCCESS'),
        notifyIdle: () => broadcastToDisplay('IDLE'),
        notifyMemberBind: () => broadcastToDisplay('CASHIER_UPDATE')
    }
}