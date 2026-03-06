import { onMounted, onUnmounted } from 'vue';

export function useScanner({ onEnter, onEscape }) {
    const handleKeydown = (e) => {
        // 如果正在输入框里打字，绝对不要拦截
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

        if (e.key === 'Escape' && typeof onEscape === 'function') {
            onEscape();
        } else if (e.key === 'Enter' && typeof onEnter === 'function') {
            onEnter();
        }
    };

    onMounted(() => {
        window.addEventListener('keydown', handleKeydown);
    });

    onUnmounted(() => {
        window.removeEventListener('keydown', handleKeydown);
    });
}