const { app, BrowserWindow, screen } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const http = require('http');

// ==========================================
// 1. 核心路径动态寻址 (兼容开发与打包后的环境)
// ==========================================
const isPackaged = app.isPackaged;
// 如果是打包后，APP_ROOT 就是安装目录 (如 D:/WanXiangPOS)
const APP_ROOT = isPackaged ? path.dirname(app.getPath('exe')) : path.join(__dirname, '..');
const RESOURCES_DIR = isPackaged ? process.resourcesPath : __dirname;

// 定位 Java 运行环境和后端 Jar 包
const JAVA_BIN = isPackaged ? path.join(APP_ROOT, 'jre', 'bin', 'java.exe') : 'java';
const JAR_PATH = isPackaged ? path.join(RESOURCES_DIR, 'backend', 'money-pos.jar') : path.join(__dirname, '../qk-money-app/money-app-biz/target/money-pos.jar');

let mainWindow;
let guestWindow;
let backendProcess;

// ==========================================
// 2. 双屏 UI 调度引擎 (万象收银系统专属)
// ==========================================
function createWindows() {
    const displays = screen.getAllDisplays();

    // --- 创建主收银台 (主屏) ---
    mainWindow = new BrowserWindow({
        width: 1280,
        height: 800,
        title: "万象收银系统",
        show: false, // 🌟 先隐藏，等页面加载好再显示，防止白屏闪烁
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true
        },
        autoHideMenuBar: true
    });

    // 🌟 核心修改：打包后不再读本地 file://，直接读取 Java 提供的 Web 服务
    // 这样做能保证 CSS/JS 路径 100% 正确
    const indexUrl = isPackaged
        ? 'http://127.0.0.1:9101/money-pos/'
        : 'http://localhost:1520/money-pos';

    mainWindow.loadURL(indexUrl);

    // 页面准备好后再显示，体验更丝滑
    mainWindow.once('ready-to-show', () => {
        mainWindow.show();
        mainWindow.maximize(); // 默认最大化
        // 如果需要调试，可以取消下面这行的注释
        // mainWindow.webContents.openDevTools();
    });

    // --- 探测并创建客显副屏 ---
    const externalDisplay = displays.find(d => d.bounds.x !== 0 || d.bounds.y !== 0);
    if (externalDisplay) {
        guestWindow = new BrowserWindow({
            x: externalDisplay.bounds.x,
            y: externalDisplay.bounds.y,
            fullscreen: true,
            frame: false,
            show: false,
            title: "万象收银系统-客显屏",
            webPreferences: { nodeIntegration: false, contextIsolation: true }
        });

        // 客显屏同样访问 Java 提供的路由
        guestWindow.loadURL(`${indexUrl}#/guest`);
        guestWindow.once('ready-to-show', () => guestWindow.show());
    }
}

// ==========================================
// 3. 后端拉起与健康检查逻辑
// ==========================================
function startBackend() {
    console.log("🚀 [万象总控] 正在拉起底层 Java 引擎...");

    // 启动 Java 进程
    backendProcess = spawn(JAVA_BIN, [
        '-jar', JAR_PATH,
        `--app.home=${APP_ROOT}`
    ], { cwd: APP_ROOT });

    // 日志输出
    backendProcess.stdout.on('data', (data) => console.log(`[Java] ${data.toString().trim()}`));
    backendProcess.stderr.on('data', (data) => console.error(`[Java Error] ${data.toString().trim()}`));

    // 开始循环探测后端是否启动成功
    checkHealth();
}

function checkHealth() {
    console.log("⏳ [万象总控] 正在探测后端健康状态...");

    // 探测后端一个特定的接口
    const req = http.get('http://127.0.0.1:9101/money-pos/sys/backup/stream', (res) => {
        // 只要后端给了响应（哪怕是 401），说明 Tomcat 已经把服务跑起来了
        console.log(`✅ [万象总控] 后端已就绪 (HTTP ${res.statusCode})，正在唤起 UI 界面...`);
        createWindows();
    });

    req.on('error', (err) => {
        // 如果还没启动好，1.5 秒后继续探测
        setTimeout(checkHealth, 1500);
    });
    req.end();
}

// ==========================================
// 4. 应用生命周期控制
// ==========================================
app.whenReady().then(() => {
    // 注册双屏探测
    startBackend();
});

app.on('window-all-closed', () => {
    // 优雅停机：UI 关闭时，斩断后台 Java 进程，防止进程残留
    if (backendProcess) {
        console.log("🛑 [万象总控] 正在关闭底层引擎...");
        backendProcess.kill('SIGINT');
    }
    if (process.platform !== 'darwin') app.quit();
});