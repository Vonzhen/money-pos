import { watch, onUnmounted } from 'vue'
import { usePosStore } from './usePosStore'

/**
 * 🌟 架构规范：客显实时同步通讯引擎 (Hook) - 极致瘦身版 (纯搬运工)
 */
export function useDisplaySync(checkoutVisible) {
    // 🌟 直接从中央引擎拿取成品数据！
    const { cartList, currentMember, participatingAmount, paymentStats, getCartItemPrices } = usePosStore();
    let displayWs = null;

    // 1. 组装商品快照
    const formatCartForDisplay = () => {
        return cartList.value.map(item => {
            const { unitPrice } = getCartItemPrices(item, currentMember.value);
            const qty = Number(item.qty) || 1;
            return {
                name: item.name,
                qty: qty,
                originalPrice: item.salePrice || 0,
                price: unitPrice,
                subtotal: Number((unitPrice * qty).toFixed(2))
            };
        });
    }

    // 2. 组装支付状态快照 (自带防状态遗留闸门)
    const getPaymentState = () => {
        const isCheckout = checkoutVisible.value;
        const stats = paymentStats.value; // 直接读取 Store 算好的四大金刚

        if (!isCheckout) {
            // 🌟 终极闸门：不在结算界面时，清空一切已收和扫码金额，只传目标应收
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
            // 发射标准化 Payload
            displayWs.send(JSON.stringify({
                state,
                cart: formatCartForDisplay(),
                pAmount: participatingAmount.value, // 直接用
                member: currentMember.value,
                payment: getPaymentState()          // 直接用
            }));
        }
    }

    // 5. 联合雷达阵列
    watch([
        () => cartList.value,
        () => currentMember.value,
        () => paymentStats.value // 🌟 监听 Store 里聚合出的支付状态变更
    ], ([newCart]) => {
        if (newCart.length === 0 && !checkoutVisible.value) broadcastToDisplay('IDLE');
        else broadcastToDisplay('CASHIER_UPDATE');
    }, { deep: true });

    watch(checkoutVisible, (isVisible) => {
        if (isVisible) broadcastToDisplay('CHECKOUT_OPEN');
        else if (cartList.value.length === 0) broadcastToDisplay('IDLE');
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