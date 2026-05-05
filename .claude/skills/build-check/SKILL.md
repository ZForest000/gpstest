---
name: build-check
description: 构建 debug APK 并运行单元测试，报告构建状态和测试结果
---

# Build & Test

构建项目并运行所有单元测试，验证代码没有编译错误或测试失败。

## 执行步骤

1. 设置 JAVA_HOME 并执行构建：
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21" && cd D:/project/gpstest && ./gradlew assembleDebug
```

2. 运行单元测试：
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21" && cd D:/project/gpstest && ./gradlew testDebugUnitTest
```

3. 汇总报告：
   - 构建状态（成功/失败）
   - 测试数量及通过/失败情况
   - 如有失败，列出失败的测试及错误信息

## 参数

- `$ARGUMENTS` — 可选：指定额外的 Gradle 参数（如 `--info`、`--rerun-tasks`）
