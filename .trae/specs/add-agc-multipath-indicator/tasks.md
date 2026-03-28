# Tasks

- [x] Task 1: 扩展 GnssSatellite 数据模型
  - [x] SubTask 1.1: 添加 `agcLevelDb: Double?` 字段
  - [x] SubTask 1.2: 添加 `multipathIndicator: MultipathIndicator?` 字段
  - [x] SubTask 1.3: 创建 `MultipathIndicator` 枚举类（UNKNOWN, DETECTED, NOT_DETECTED）

- [x] Task 2: 更新 GnssDataSourceImpl 数据采集
  - [x] SubTask 2.1: 在 MeasurementExtras 数据类中添加 agcLevelDb 和 multipathIndicator 字段
  - [x] SubTask 2.2: 从 GnssMeasurement 提取 AGC 数据（使用 hasAutomaticGainControlLevel 和 automaticGainControlLevel）
  - [x] SubTask 2.3: 从 GnssMeasurement 提取多路径指示数据（使用 multipathIndicator）
  - [x] SubTask 2.4: 将提取的数据合并到 GnssSatellite 对象

- [x] Task 3: 更新 UI 组件显示
  - [x] SubTask 3.1: 在 SatelliteCard 中添加多路径警告标记（当 multipathIndicator == DETECTED 时显示）
  - [x] SubTask 3.2: 添加字符串资源用于多路径显示

- [x] Task 4: 构建验证
  - [x] SubTask 4.1: 运行 `./gradlew assembleDebug` 确保编译通过

# Task Dependencies
- Task 2 依赖 Task 1（数据模型必须先更新）
- Task 3 依赖 Task 1 和 Task 2（UI 需要数据模型和数据源支持）
- Task 4 依赖所有前置任务
