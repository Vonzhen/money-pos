import { watch, onUnmounted } from 'vue'
import { usePosStore } from './usePosStore'

/**
 * 🌟 架构规范：客显实时同步通讯引擎 (Hook)
 */
export function useDisplaySync(checkoutVisible) {
    // 🌟 核心升级：增加提取 paymentList(支付列表) 和 finalPayAmount(最终应付)
    const { cartList, currentMember, totalAmount, paymentList, finalPayAmount } = usePosStore();
    let displayWs = null;

    const getLevelCode = (brandId, member) => {
        if (!member?.id || !brandId) return null;
        return member.brandLevels?.[String(brandId)] || null;
    }

    const getMemberPrice = (row, member) => {
        const code = getLevelCode(row.brandId, member);
        if (code && row.levelPrices && row.levelPrices[code] != null) return row.levelPrices[code];
        return null;
    }

    const formatCartForDisplay = (cart, member) => {
        return cart.map(item => {
            const memberPrice = getMemberPrice(item, member);
            const activePrice = memberPrice !== null ? memberPrice : (item.salePrice || 0);
            return {
                name: item.name,
                qty: Number(item.qty) || 1,
                originalPrice: item.salePrice || 0,
                price: activePrice,
            };
        });
    }

    const getParticipatingAmount = (cart, member) => {
        return cart.reduce((sum, item) => {
            if (item.isDiscountParticipable === 1) {
                const memberPrice = getMemberPrice(item, member);
                const activePrice = memberPrice !== null ? memberPrice : (item.salePrice || 0);
                return sum + (activePrice * (Number(item.qty) || 1));
            }
            return sum;
        }, 0);
    }

    const initDisplaySync = () => {
        const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const hostname = window.location.hostname;
        const wsUrl = `${wsProtocol}//${hostname}:9101/money-pos/ws/pos-sync`;

        displayWs = new WebSocket(wsUrl);
        displayWs.onopen = () => console.log('🟢 [收银台-通讯连] 已成功穿透并连接客显同步基站！');
        displayWs.onerror = () => console.error('❌ [收银台-通讯连] 无法连接基站。');
        displayWs.onclose = () => { setTimeout(initDisplaySync, 5000); };
    }

    const broadcastToDisplay = (state, payload = {}) => {
        if (displayWs && displayWs.readyState === WebSocket.OPEN) {
            displayWs.send(JSON.stringify({ state, ...payload }));
        }
    }

    // 🌟 核心升级：把 paymentList 和 finalPayAmount 加入雷达监控范围！
    watch([
        () => cartList.value,
        () => currentMember.value,
        () => totalAmount.value,
        () => paymentList?.value, // 监听支付金额输入变化
        () => finalPayAmount?.value
    ], ([newCart, newMember, newTotal, newPayments, newFinalPay]) => {
        if (newCart.length === 0 && !checkoutVisible.value) {
            broadcastToDisplay('IDLE');
        } else {
            broadcastToDisplay('CASHIER_UPDATE', {
                cart: formatCartForDisplay(newCart, newMember),
                total: newTotal,
                pAmount: getParticipatingAmount(newCart, newMember),
                member: newMember,
                payments: newPayments,     // 发送支付方式列表
                finalPay: newFinalPay || newTotal // 发送最终应付(扣除优惠券后)
            });
        }
    }, { deep: true });

    watch(checkoutVisible, (isVisible) => {
        if (isVisible) {
            broadcastToDisplay('CHECKOUT_OPEN', {
                cart: formatCartForDisplay(cartList.value, currentMember.value),
                total: totalAmount.value,
                pAmount: getParticipatingAmount(cartList.value, currentMember.value),
                member: currentMember.value,
                payments: paymentList?.value,
                finalPay: finalPayAmount?.value || totalAmount.value
            });
        } else if (cartList.value.length === 0) {
            broadcastToDisplay('IDLE');
        } else {
            broadcastToDisplay('CASHIER_UPDATE', {
                cart: formatCartForDisplay(cartList.value, currentMember.value),
                total: totalAmount.value,
                pAmount: getParticipatingAmount(cartList.value, currentMember.value),
                member: currentMember.value,
                payments: paymentList?.value,
                finalPay: finalPayAmount?.value || totalAmount.value
            });
        }
    });

    initDisplaySync();
    onUnmounted(() => { if (displayWs) displayWs.close(); });

    return {
        notifyPaySuccess: (amount) => broadcastToDisplay('PAY_SUCCESS', { amount }),
        notifyIdle: () => broadcastToDisplay('IDLE'),
        notifyMemberBind: (member) => {
            broadcastToDisplay('CASHIER_UPDATE', {
                cart: formatCartForDisplay(cartList.value, member),
                total: totalAmount.value,
                pAmount: getParticipatingAmount(cartList.value, member),
                member,
                payments: paymentList?.value,
                finalPay: finalPayAmount?.value || totalAmount.value
            });
        }
    }
}