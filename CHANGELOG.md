# Changelog

本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 约定，版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

- 工程文档：`CONTRIBUTING.md`、`CHANGELOG.md`、`docs/ARCHITECTURE.md`

## [1.0.0] - 2026-07-18

首个对外文档化的功能基线（`versionName` 1.0）。功能摘要来自 README。

### Added

#### 卫星监测

- 实时 GNSS 卫星追踪，按「已定位 / 可见 / 搜索中」分组
- 多星座：GPS、GLONASS、Galileo、BeiDou、QZSS、SBAS
- 星座健康摘要、60 秒 C/N0 信号历史、卫星详情弹窗
- 星历/历书状态；高级测量（载波频率/周期、多普勒、AGC、多路径、基带信噪比等，视 API/硬件）

#### 天空图与精度

- 极坐标天空图（方位角/高度角）、星座着色、交互选星
- PDOP / HDOP / VDOP 与质量等级（Gauss-Jordan 矩阵求逆）
- TTFF 计时与质量分级、手动重置

#### 定位与时钟

- 实时位置（经纬高、精度、速度与方向）
- 气压计辅助海拔
- 接收机时钟偏差/漂移及不确定度

#### A-GPS

- Qualcomm XTRA 下载（OkHttp，多 URL 回退）、校验与注入
- WorkManager 定时更新与指数退避重试
- 状态/有效期展示；文件导入

#### 历史与帮助

- 约每 60 秒卫星状态快照（DataStore + JSON）
- 历史浏览
- 内置帮助页

#### 可选增强

- Shizuku 状态检测与 GNSS dumpsys 系统级数据（不可用时不影响主功能）

### Notes

- 技术栈：Kotlin 2.1、Jetpack Compose BOM 2024.10.01、Material 3、minSdk 24、targetSdk 35
- 架构：Clean MVVM + 手动 DI（详见 `docs/ARCHITECTURE.md`）

[Unreleased]: https://github.com/ZForest000/gpstest/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/ZForest000/gpstest/releases/tag/v1.0.0
