const { app, BrowserWindow, screen, Menu } = require('electron'); // 🌟 引入 Menu
const path = require('path');
const { spawn, exec } = require('child_process');
const http = require('http');

const isPackaged = app.isPackaged;
const APP_ROOT = isPackaged ? path.dirname(app.getPath('exe')) : path.join(__dirname, '..');
const RESOURCES_DIR = isPackaged ? process.resourcesPath : __dirname;

// 🌟 品牌重塑：打包后使用 vana-java 和 vana-pos.jar
const JAVA_BIN = isPackaged ? path.join(APP_ROOT, 'jre', 'bin', 'vana-java.exe') : 'java';
const JAR_PATH = isPackaged ? path.join(RESOURCES_DIR, 'backend', 'vana-pos.jar') : path.join(__dirname, '../qk-money-app/money-app-biz/target/money-pos.jar');

let mainWindow;
let guestWindow;
let backendProcess;

function createWindows() {
    const displays = screen.getAllDisplays();

    mainWindow = new BrowserWindow({
        width: 1280, height: 800,
        title: "万象收银系统", show: false,
        webPreferences: { nodeIntegration: false, contextIsolation: true, webSecurity: false },
        autoHideMenuBar: true
    });

    const indexPath = isPackaged ? `file://${path.join(__dirname, 'dist', 'index.html')}` : 'http://localhost:1520/money-pos';

    mainWindow.loadURL(`${indexPath}#/pos`);
    mainWindow.once('ready-to-show', () => { mainWindow.show(); mainWindow.maximize(); });

    const externalDisplay = displays.find(d => d.bounds.x !== 0 || d.bounds.y !== 0);
    if (externalDisplay) {
        guestWindow = new BrowserWindow({
            // 1. 物理坐标定位
            x: externalDisplay.bounds.x, y: externalDisplay.bounds.y,
            // 2. 基础无边框全屏
            fullscreen: true, frame: false, show: false,

            // ==========================================
            // 🌟 核心改造：客显屏专属“隐身装甲”配置
            // ==========================================
            skipTaskbar: true,          // 绝对不在底部的 Windows 任务栏显示图标
            type: 'toolbar',            // 🌟 杀手锏：在 Windows 中设为 toolbar，彻底在 Alt+Tab 中隐身！
            alwaysOnTop: true,          // 始终置顶，防止被其他弹窗遮挡
            minimizable: false,         // 禁止最小化
            maximizable: false,         // 禁止最大化
            resizable: false,           // 禁止缩放
            kiosk: true,                // 开启 POS/广告机专属的霸道全屏模式

            title: "万象收银系统-客显屏",
            webPreferences: { nodeIntegration: false, contextIsolation: true, webSecurity: false }
        });

        // 强行拔除副屏的菜单栏
        guestWindow.setMenu(null);

        guestWindow.loadURL(`${indexPath}#/guest`);
        guestWindow.once('ready-to-show', () => { guestWindow.show(); });
    }
}

function startBackend() {
    console.log("🚀 [VanaPOS总控] 正在拉起专属 Java 引擎...");
    backendProcess = spawn(JAVA_BIN, ['-jar', JAR_PATH, `--app.home=${APP_ROOT}`], { cwd: APP_ROOT });
    backendProcess.stdout.on('data', (data) => console.log(`[VanaPOS] ${data.toString().trim()}`));
    backendProcess.stderr.on('data', (data) => console.error(`[VanaPOS Error] ${data.toString().trim()}`));
    checkHealth();
}

function checkHealth() {
    const req = http.get('http://127.0.0.1:9101/money-pos/actuator/health', (res) => {
        if (res.statusCode === 200) {
            createWindows();
        } else {
            setTimeout(checkHealth, 1500);
        }
    });
    req.on('error', () => setTimeout(checkHealth, 1500));
    req.end();
}

app.whenReady().then(() => {
    // 🌟 终极拔除：全局清空原生菜单栏，界面更清爽、更商业化
    Menu.setApplicationMenu(null);
    startBackend();
});

app.on('window-all-closed', () => {
    if (backendProcess) {
        console.log("🛑 [VanaPOS总控] 正在执行优雅停机序列...");
        backendProcess.kill('SIGINT');

        if (process.platform === 'win32') {
            setTimeout(() => {
                exec(`taskkill /pid ${backendProcess.pid} /t /f`, (err) => {});
            }, 3000);
        }
    }
    if (process.platform !== 'darwin') app.quit();
});