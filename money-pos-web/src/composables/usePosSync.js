import { ref } from 'vue'

export function usePosSync() {
    const posState = ref('OFFLINE')
    const realCart = ref([])
    const realMember = ref(null)
    const realParticipatingAmount = ref(0)
    const realPayment = ref({ targetPay: 0, tendered: 0, aggregate: 0, change: 0 })

    let receiverWs = null
    let reconnectTimer = null
    let successResetTimer = null

    // 🌟 统一的数据防腐层 (防止 undefined 导致 NaN)
    const getPrice = (item) => Number(item.price ?? item.unitRealPrice ?? 0)
    const getOriginalPrice = (item) => Number(item.originalPrice ?? item.unitOriginalPrice ?? 0)
    const getSubtotal = (item) => Number(item.subtotal ?? item.subTotalRetail ?? item.subTotalMember ?? 0)
    const getQty = (item) => Number(item.qty ?? item.quantity ?? 1)

    const resetToStandby = () => {
        posState.value = 'STANDBY'
        realCart.value = []
        realMember.value = null
        realParticipatingAmount.value = 0
        realPayment.value = { targetPay: 0, tendered: 0, aggregate: 0, change: 0 }
    }

    const scheduleReconnect = () => {
        clearTimeout(reconnectTimer)
        reconnectTimer = setTimeout(() => { initReceiver() }, 5000)
    }

    const initReceiver = () => {
        if (receiverWs) {
            receiverWs.close()
            receiverWs = null
        }

        let wsHost = window.location.hostname || '127.0.0.1'
        let wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'

        if (window.location.protocol === 'file:') {
            wsHost = '127.0.0.1'
            wsProtocol = 'ws:'
        }

        const wsUrl = `${wsProtocol}//${wsHost}:9101/money-pos/ws/pos-sync`
        receiverWs = new WebSocket(wsUrl)

        receiverWs.onopen = () => { posState.value = 'STANDBY' }
        receiverWs.onerror = () => { posState.value = 'OFFLINE' }
        receiverWs.onclose = () => {
            posState.value = 'OFFLINE'
            scheduleReconnect()
        }

        receiverWs.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data || '{}')
                const { state, cart, pAmount, member, payment } = data

                if (!state) return

                if (Array.isArray(cart)) realCart.value = cart
                if (pAmount !== undefined) realParticipatingAmount.value = Number(pAmount) || 0
                if (member !== undefined) realMember.value = member
                if (payment !== undefined) {
                    realPayment.value = {
                        targetPay: Number(payment.targetPay) || 0,
                        tendered: Number(payment.tendered) || 0,
                        aggregate: Number(payment.aggregate) || 0,
                        change: Number(payment.change) || 0
                    }
                }

                if (state === 'IDLE') { resetToStandby(); return; }
                if (state === 'CASHIER_UPDATE' || state === 'CHECKOUT_OPEN') { posState.value = 'CASHIER'; return; }
                if (state === 'PAY_SUCCESS') {
                    posState.value = 'SUCCESS'
                    clearTimeout(successResetTimer)
                    successResetTimer = setTimeout(() => { resetToStandby() }, 3000)
                }
            } catch (e) {
                console.error('[PosSync] 解析同步消息失败', e)
            }
        }
    }

    const closeReceiver = () => {
        clearTimeout(reconnectTimer)
        clearTimeout(successResetTimer)
        if (receiverWs) {
            receiverWs.close()
            receiverWs = null
        }
    }

    return {
        posState, realCart, realMember, realParticipatingAmount, realPayment,
        initReceiver, closeReceiver, getPrice, getOriginalPrice, getSubtotal, getQty
    }
}