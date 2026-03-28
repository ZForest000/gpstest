# AGC 与多路径指示功能 Spec

## Why
AGC (自动增益控制) 和多路径指示是评估 GNSS 信号质量和定位精度的重要指标。AGC 反映信号干扰环境，多路径指示检测信号是否经反射到达，这些数据对专业用户调试和优化定位精度非常有价值。

## What Changes
- 在 `GnssSatellite` 数据模型中添加 `agcLevelDb` (AGC 电平) 和 `multipathIndicator` (多路径指示) 字段
- 在 `GnssDataSourceImpl` 中从 `GnssMeasurement` 提取 AGC 和多路径数据
- 在 `SatelliteCard` UI 组件中显示多路径指示标记
- 在卫星详情中显示 AGC 数值（如可用）

## Impact
- Affected specs: 卫星数据显示能力
- Affected code:
  - `domain/model/GnssSatellite.kt`
  - `data/source/GnssDataSourceImpl.kt`
  - `ui/components/SatelliteCard.kt`
  - `ui/screens/satellite/SatelliteDetailScreen.kt` (如存在)

## ADDED Requirements

### Requirement: AGC 数据采集
系统应从 `GnssMeasurement.automaticGainControlLevel` 采集 AGC 电平数据。

#### Scenario: AGC 数据可用
- **WHEN** 设备支持 AGC 数据且 `GnssMeasurement.hasAutomaticGainControlLevel()` 返回 true
- **THEN** 系统应将 `automaticGainControlLevel` 值存储到对应卫星的 `agcLevelDb` 字段

#### Scenario: AGC 数据不可用
- **WHEN** 设备不支持 AGC 数据或 `hasAutomaticGainControlLevel()` 返回 false
- **THEN** 系统应将 `agcLevelDb` 设为 null

### Requirement: 多路径指示数据采集
系统应从 `GnssMeasurement.multipathIndicator` 采集多路径指示数据。

#### Scenario: 多路径指示可用
- **WHEN** 设备提供多路径指示数据
- **THEN** 系统应将 `multipathIndicator` 值（枚举：UNKNOWN、DETECTED、NOT_DETECTED）存储到对应卫星

#### Scenario: 多路径指示不可用
- **WHEN** 设备不提供多路径指示数据
- **THEN** 系统应将 `multipathIndicator` 设为 null

### Requirement: 多路径状态 UI 展示
系统应在卫星卡片上显示多路径指示标记。

#### Scenario: 检测到多路径
- **WHEN** 卫星的 `multipathIndicator` 为 DETECTED
- **THEN** 系统应在卫星卡片上显示多路径警告标记（如 "⚠️ MP" 或类似图标）

#### Scenario: 未检测到多路径
- **WHEN** 卫星的 `multipathIndicator` 为 NOT_DETECTED
- **THEN** 系统可显示正常状态标记或不显示

#### Scenario: 多路径状态未知
- **WHEN** 卫星的 `multipathIndicator` 为 UNKNOWN 或 null
- **THEN** 系统不应显示多路径相关标记

### Requirement: AGC 数值展示
系统应在卫星详情中显示 AGC 数值（如适用）。

#### Scenario: AGC 数据存在
- **WHEN** 卫星有有效的 AGC 数据
- **THEN** 系统应在卫星详情中显示 AGC 电平值（单位 dB）

#### Scenario: AGC 数据不存在
- **WHEN** 卫星无 AGC 数据
- **THEN** 系统应显示 "N/A" 或隐藏该字段
