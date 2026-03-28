# GNSS 功能待办

## 高价值

- [*] **卫星天空图 (Sky View)** — 方位角+仰角极坐标图，直观展示卫星空间分布，参与定位的卫星用不同颜色标记
- [ ] **信号历史曲线** — 已有60秒 SignalHistory 数据，用折线图展示每颗卫星的信号强度变化
- [ ] **多路径指示 (Multipath)** — 来自 `GnssMeasurement.multipathIndicator`，检测信号是否经反射到达
- [ ] **自动增益控制 (AGC)** — 来自 `GnssMeasurement.automaticGainControlLevel`，反映信号干扰环境
- [ ] **HDOP/VDOP/PDOP** — 精度因子，衡量卫星几何分布质量
- [ ] **TTFF (首次定位时间)** — 从启动到获得首次定位的时间
- [ ] **信噪比柱状图** — 按卫星分组的信号强度柱状图

## 中等价值（专业/调试用）

- [ ] **伪距变化率** — `GnssMeasurement.pseudorangeRateMetersPerSecond`，原始多普勒数据
- [ ] **卫星时间不确定度** — `GnssMeasurement.svTimeUncertaintyNanos`，测量精度指标
- [ ] **载波相位完整周期** — `GnssMeasurement.fullCarrierPhaseCycleCount`，RTK/高精度定位核心数据
- [ ] **基带 C/N0** — `GnssStatus.basebandCn0DbHz`，与信噪比互补的信号质量指标
- [ ] **时钟偏差/漂移** — `GnssClock` 接收机时钟状态

## 展示形式增强

- [ ] **DOP 实时曲线** — 精度因子随时间变化
- [ ] **星座健康状态汇总** — 各星座可用卫星数/总数比例可视化
