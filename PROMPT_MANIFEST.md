# 钱柜 (MoneyPOS) 项目架构大脑 (V1.0)
> 更新时间：随当前对话持续更新
> 架构核心原则：离线优先、计算下沉、高精度计算、物理路径隔离

## 1. 系统架构状态
- **运行模式**：Windows 单机离线运行，附带 Alpine 虚拟机异步备份。
- **存储架构**：彻底剥离 MySQL 与 Redis。使用 H2 (File Mode) + Hutool 本地缓存。
- **端口归一**：后端与 Vue 前端静态资源统一运行于 `9101` 端口。

## 2. 核心模块与代码映射
- `AppWorkspace` (money-app-biz)：负责系统启动时的“非 C 盘领地探测”与目录初始化。动态向 Spring 注入 H2 路径与 `local.bucket` 路径。
- `StorageWebConfig` (规划中)：负责将 `local.bucket` 的外部绝对路径映射到 `/assets/**`。

## 3. 前后端开发红线 (V1.0)
- **前端**：禁止原生浮点运算，必须使用 `decimal.js`；金额展示必须调用 `formatMoney`；关键操作必须防抖/节流。
- **后端**：禁止在 Java 内存中做 `SUM/COUNT`；金额必须使用 `BigDecimal` 与 `DECIMAL(10,2)`；原子扣减必须在 Mapper 层依靠 SQL 完成。

## 4. 当前开发进度
- [x] 第一阶段：H2 数据库接入与路径自适应改造（正在实施 `AppWorkspace`）。
- [ ] 第二阶段：CMS 素材库开发与静态映射。
- [ ] 第三阶段：单机 WebSocket 客显联动。
- [ ] 第四阶段：Windows USB 驱动小票打印 (Graphics2D)。

钱柜 (MoneyPOS) 项目架构大脑 (V2.0 - MariaDB 重装版)

    更新时间：2026-03-12 01:45
    架构核心原则：离线优先、原生 SQL 引擎、数据隔离、连锁化预留

1. 系统架构状态

   运行模式：Windows 单机离线运行，支持未来的 VPS 云端异步同步。

   存储架构：

        核心引擎：MariaDB Portable (侧挂运行模式)，杜绝 H2 方言不兼容问题。

        物理隔离：数据库引擎、数据文件、素材资源全部锁定在 D:\MoneyPOS_Data\。

        缓存方案：Hutool 本地内存缓存，跳过 Redis 安装依赖。

   端口归一：后端与 Vue 前端静态资源统一运行于 9101 端口；数据库私有端口 9102。

2. 核心模块与代码映射

   AppWorkspace (money-app-biz)：系统级守护者。

        启动阶段：探测 D 盘 -> 检查/静默拉起 mysqld.exe 进程 -> 确保 9102 端口可用。

        注入阶段：动态向 Spring 环境变量写入 sa 账号信息与 local.bucket 映射路径。

   StorageWebConfig：虚拟隧道。将物理路径 D:\MoneyPOS_Data\assets\ 映射为 /assets/** 供前端调用。

   StoreTags (规划中)：在 sys_config 中埋入 store_id 与 tenant_id，为连锁同步做唯一性标识。

3. 前后端开发红线 (V2.0)

   数据库：禁止使用 H2 特有语法；保持标准的 MySQL 5.7/8.0 SQL 编写习惯。

   计算精度：前端必须使用 decimal.js；后端 BigDecimal 严禁丢失精度。

   同步意识：所有涉及业务流水（订单、库存变动）的表必须具备自增长/雪花 ID，以便未来多店合并时 ID 不冲突。

4. 当前开发进度

   [x] 架构大转向：放弃 H2 嵌入方案，确认 MariaDB 捆绑方案。

   [x] 环境清扫：移除所有 H2 残留代码，完成 YML 配置“脱水”。

   [ ] 下一阶段 (实施中)：

        下载并精简 MariaDB Portable。

        重写 AppWorkspace 以 Java 进程方式静默管理 MariaDB 开启/关闭。

        恢复 CMS 素材上传与展示逻辑。