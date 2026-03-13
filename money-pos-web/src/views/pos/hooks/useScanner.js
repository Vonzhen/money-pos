import { onMounted, onUnmounted } from 'vue';

export function useScanner({ onEnter, onEscape }) {
    // 🌟 核心防抖缓冲区
    let buffer = '';
    let lastKeyTime = Date.now();

    const handleKeydown = (e) => {
        // 如果焦点在输入框，绝对不拦截，交由组件自带的逻辑处理
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

        const currentTime = Date.now();
        const timeDiff = currentTime - lastKeyTime;
        lastKeyTime = currentTime;

        // 如果两次按键间隔 > 50ms，说明是人类手动按键，清空扫码枪机器 buffer
        if (timeDiff > 50) {
            buffer = '';
        }

        // 1. ESC 拦截防线
        if (e.key === 'Escape' && typeof onEscape === 'function') {
            e.preventDefault();
            onEscape();
            return;
        }

        // 2. Enter 拦截与分流
        if (e.key === 'Enter') {
            e.preventDefault();

            // 如果 buffer 里有多个字符，说明是扫码枪刚以极其狂暴的速度扫完一串条码触发的 Enter！
            // 此时绝不能触发“结算”热键，直接丢弃（或未来触发特定 onScan）
            if (buffer.length > 3) {
                buffer = '';
                return;
            }

            // 如果 buffer 为空，说明是人类纯按了一个回车，执行结算命令
            if (typeof onEnter === 'function') {
                onEnter();
            }
            buffer = '';
            return;
        }

        // 记录可见字符到缓冲区
        if (e.key.length === 1) {
            buffer += e.key;
        }
    };

    onMounted(() => {
        window.addEventListener('keydown', handleKeydown);
    });

    onUnmounted(() => {
        window.removeEventListener('keydown', handleKeydown);
    });
}