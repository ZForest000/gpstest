# Checklist

## 数据模型
- [x] GnssSatellite 包含 `agcLevelDb: Double?` 字段
- [x] GnssSatellite 包含 `multipathIndicator: MultipathIndicator?` 字段
- [x] MultipathIndicator 枚举定义了 UNKNOWN、DETECTED、NOT_DETECTED 三个值

## 数据采集
- [x] GnssDataSourceImpl 从 GnssMeasurement 提取 AGC 数据
- [x] GnssDataSourceImpl 从 GnssMeasurement 提取多路径指示数据
- [x] 数据正确合并到 GnssSatellite 对象

## UI 显示
- [x] SatelliteCard 在检测到多路径时显示警告标记
- [x] 多路径标记使用合适的颜色或图标区分

## 构建验证
- [x] `./gradlew assembleDebug` 编译成功，无错误
