const { app, BrowserWindow, screen } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const http = require('http');

// ==========================================
// 1. 核心路径动态寻址 (兼容开发与打包后的环境)
// ==========================================
const isPackaged = app.isPackaged;
// 如果是打包后，APP_ROOT 就是安装目录 (如 D:/MoneyPOS)
const APP_ROOT = isPackaged ? path.dirname(app.getPath('exe')) : path.join(__dirname, '..');
const RESOURCES_DIR = isPackaged ? process.resourcesPath : __dirname;

// 定位 Java 和 Jar 包
const JAVA_BIN = isPackaged ? path.join(APP_ROOT, 'jre', 'bin', 'java.exe') : 'java';
const JAR_PATH = isPackaged ? path.join(RESOURCES_DIR, 'backend', 'money-pos.jar') : path.join(__dirname, '../qk-money-app/money-app-biz/target/money-pos.jar');

let mainWindow;
let guestWindow;
let backendProcess;

// ==========================================
// 2. 双屏 UI 调度引擎
// ==========================================
function createWindows() {
    const displays = screen.getAllDisplays();

    // 创建主收银台 (主屏)
    mainWindow = new BrowserWindow({
        width: 1280, height: 800,
        title: "MoneyPOS 智能收银终端",
        webPreferences: { nodeIntegration: false, contextIsolation: true },
        autoHideMenuBar: true
    });

    const indexUrl = isPackaged ? `file://${path.join(__dirname, 'dist', 'index.html')}` : 'http://localhost:1520/money-pos';
    mainWindow.loadURL(indexUrl);

    // 探测客显副屏
    const externalDisplay = displays.find(d => d.bounds.x !== 0 || d.bounds.y !== 0);
    if (externalDisplay) {
        guestWindow = new BrowserWindow({
            x: externalDisplay.bounds.x, y: externalDisplay.bounds.y,
            fullscreen: true, frame: false,
            title: "MoneyPOS 客显屏",
            webPreferences: { nodeIntegration: false, contextIsolation: true }
        });
        guestWindow.loadURL(`${indexUrl}#/guest`); // 假设您的客显路由是 /guest
    }
}

// ==========================================
// 3. 后端拉起与应用层健康检查
// ==========================================
function startBackend() {
    console.log("🚀 [Electron总控] 正在拉起底层 Java 引擎...");

    // 启动 Java 进程，并将 APP_ROOT 注入给后端的 app.home
    backendProcess = spawn(JAVA_BIN, [
        '-jar', JAR_PATH,
        `--app.home=${APP_ROOT}`
    ], { cwd: APP_ROOT });

    backendProcess.stdout.on('data', (data) => console.log(`[Java] ${data.toString().trim()}`));
    backendProcess.stderr.on('data', (data) => console.error(`[Java Error] ${data.toString().trim()}`));

    // 循环探测后端的健康状态
    checkHealth();
}

function checkHealth() {
    console.log("⏳ [Electron总控] 正在探测后端健康状态...");
    const req = http.get('http://127.0.0.1:9101/money-pos/sys/backup/stream', (res) => {
        // 只要后端返回了 HTTP 状态码 (哪怕是 401 拦截)，都说明 Spring 上下文已经就绪！
        console.log(`✅ [Electron总控] 后端已就绪 (HTTP ${res.statusCode})，启动 UI 界面！`);
        createWindows();
    });

    req.on('error', (err) => {
        // 如果连接被拒绝，说明 Java 还没启动好，1.5 秒后重试
        setTimeout(checkHealth, 1500);
    });
    req.end();
}

// ==========================================
// 4. 生命周期管理
// ==========================================
app.whenReady().then(startBackend);

app.on('window-all-closed', () => {
    // 优雅停机：UI 关闭时，斩断后台 Java 进程
    if (backendProcess) backendProcess.kill('SIGINT');
    if (process.platform !== 'darwin') app.quit();
});