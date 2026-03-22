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
        show: false, // 先隐藏，等页面加载好再显示，防止白屏闪烁
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true,
            webSecurity: false // 🌟 允许本地 file:// 协议跨域请求 127.0.0.1
        },
        autoHideMenuBar: true
    });

    // 🌟 核心修改：彻底切换为“本地加载模式(Model B)”
    // 打包后直接去读本地 dist 文件夹里的 HTML，彻底抛弃让 Java 吐页面的幻想！
    const indexPath = isPackaged
        ? `file://${path.join(__dirname, 'dist', 'index.html')}`
        : 'http://localhost:1520/money-pos';

    mainWindow.loadURL(indexPath);

    // 页面准备好后再显示，体验更丝滑
    mainWindow.once('ready-to-show', () => {
        mainWindow.show();
        mainWindow.maximize(); // 默认最大化

        // 🌟 强烈建议：本次打包强行开启控制台！
        // 如果界面出不来，看右侧控制台的红字，一秒钟破案！正常运行后可以再注释掉。
        mainWindow.webContents.openDevTools();
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
            webPreferences: { nodeIntegration: false, contextIsolation: true, webSecurity: false }
        });

        // 客显屏同样访问本地文件
        guestWindow.loadURL(`${indexPath}#/guest`);
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

    // 🌟 换成最标准、最准确的 actuator 接口，彻底避免安全框架误伤
    const req = http.get('http://127.0.0.1:9101/money-pos/actuator/health', (res) => {
        if (res.statusCode === 200) {
            console.log(`✅ [万象总控] 后端已就绪 (HTTP 200)，正在唤起本地 UI 界面...`);
            createWindows();
        } else {
            console.log(`⚠️ [万象总控] 收到响应但非200 (${res.statusCode})，继续等待...`);
            setTimeout(checkHealth, 1500);
        }
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