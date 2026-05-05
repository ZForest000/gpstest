---
name: gen-test
description: 为指定的 Kotlin 源文件生成 JUnit 4 单元测试
disable-model-invocation: true
---

# Generate Unit Tests

为目标 Kotlin 源文件生成对应的 JUnit 4 单元测试。

## 输入

- `$ARGUMENTS` — 源文件路径（必需），例如 `app/src/main/java/com/example/gpstest/domain/util/DopCalculator.kt`

## 执行步骤

1. 读取目标源文件，分析其中的类、方法和逻辑

2. 确定测试文件位置：
   - 将 `src/main/java` 替换为 `src/test/java`
   - 文件名添加 `Test` 后缀（如 `DopCalculator.kt` → `DopculatorTest.kt`）

3. 生成测试代码，遵循项目现有测试风格：
   - 使用 JUnit 4（`org.junit.Assert.*`）
   - 测试类命名：`<ClassName>Test`
   - 测试方法命名：使用反引号描述性名称（如 ``@Test fun `add valid satellite returns success`()``）
   - 覆盖正常路径、边界条件和异常情况

4. 写入测试文件到对应位置

5. 运行生成的测试验证其通过：
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21" && cd D:/project/gpstest && ./gradlew testDebugUnitTest
```

## 注意事项

- 优先测试 domain 层纯逻辑代码（无 Android SDK 依赖）
- 如果目标文件依赖 Android SDK，提示用户先添加 Mockito/Robolectric 测试依赖
