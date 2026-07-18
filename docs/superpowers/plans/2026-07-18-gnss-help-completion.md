# GNSS 调试帮助页补全实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将帮助页补全为面向 GNSS 调试用户的离线诊断手册，并以单元测试保护章节完整性。

**架构：** 新增纯 Kotlin 的 `HelpGuide` 目录，定义必须存在的帮助章节标识。JUnit 测试先验证目录，再由 `HelpScreen` 使用该目录渲染快速诊断、页面指南、指标参考和高级能力内容。所有用户可见内容均位于中英文资源文件。

**技术栈：** Kotlin、Jetpack Compose Material 3、Android 资源、JUnit 4、ktlint、Gradle。

---

## 文件结构

- 创建：`app/src/main/java/com/example/gpstest/ui/screens/help/HelpGuide.kt`，帮助章节标识的纯 Kotlin 目录。
- 创建：`app/src/test/java/com/example/gpstest/ui/screens/help/HelpGuideTest.kt`，验证调试手册的必备章节和稳定顺序。
- 修改：`app/src/main/java/com/example/gpstest/ui/screens/help/HelpScreen.kt`，渲染新增章节，修正既有术语说明，并使卡片圆角为 8 dp。
- 修改：`app/src/main/res/values/strings.xml`，加入中文帮助文本。
- 修改：`app/src/main/res/values-en/strings.xml`，加入与中文键一一对应的英文帮助文本。

### 任务 1：建立可测试的帮助章节目录

**文件：**
- 创建：`app/src/test/java/com/example/gpstest/ui/screens/help/HelpGuideTest.kt`
- 创建：`app/src/main/java/com/example/gpstest/ui/screens/help/HelpGuide.kt`

- [ ] **步骤 1：编写失败的测试**

```kotlin
class HelpGuideTest {
    @Test
    fun `guide contains every GNSS diagnostic section in reading order`() {
        assertEquals(
            listOf(
                HelpGuideSection.QUICK_DIAGNOSTICS,
                HelpGuideSection.SCREEN_GUIDE,
                HelpGuideSection.METRICS_REFERENCE,
                HelpGuideSection.AGPS_AND_ADVANCED,
            ),
            HelpGuideSection.readingOrder,
        )
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew testDebugUnitTest --tests com.example.gpstest.ui.screens.help.HelpGuideTest`

预期：FAIL，编译错误提示 `HelpGuideSection` 未定义。

- [ ] **步骤 3：编写最少实现代码**

```kotlin
enum class HelpGuideSection {
    QUICK_DIAGNOSTICS,
    SCREEN_GUIDE,
    METRICS_REFERENCE,
    AGPS_AND_ADVANCED,
    ;

    companion object {
        val readingOrder = entries
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew testDebugUnitTest --tests com.example.gpstest.ui.screens.help.HelpGuideTest`

预期：PASS，`HelpGuideTest` 的 1 个测试通过。

### 任务 2：补全中文与英文资源

**文件：**
- 修改：`app/src/main/res/values/strings.xml`
- 修改：`app/src/main/res/values-en/strings.xml`

- [ ] **步骤 1：补全章节目录并加入资源键**

将 `readingOrder` 改为与任务 1 测试预期完全一致的显式 `listOf(...)`，并在两个资源文件中以相同键加入下列文字组：

```xml
<string name="help_quick_diagnostics_title">快速诊断</string>
<string name="help_screen_guide_title">页面与操作指南</string>
<string name="help_metrics_reference_title">指标解读边界</string>
<string name="help_advanced_title">A-GPS 与高级能力</string>
```

每个文字组包含：权限与环境、无数据排查、总览至设置的页面说明、DOP/CN0/位置精度限制、A-GPS 操作与限制、Shizuku/root 与 RINEX 的前提。英文文件保留 `GNSS`、`TTFF`、`DOP`、`C/N0`、`NMEA`、`RINEX` 和 `Shizuku` 术语。

- [ ] **步骤 2：运行目录测试验证通过**

运行：`./gradlew testDebugUnitTest --tests com.example.gpstest.ui.screens.help.HelpGuideTest`

预期：PASS，任务 1 的目录测试通过。

### 任务 3：渲染完整诊断手册

**文件：**
- 修改：`app/src/main/java/com/example/gpstest/ui/screens/help/HelpScreen.kt`

- [ ] **步骤 1：实现最少的 UI 渲染**

在 `HelpScreen` 中的既有术语卡片之前或之后按 `HelpGuideSection.readingOrder` 添加 4 个 `HelpSection`：

```kotlin
when (section) {
    HelpGuideSection.QUICK_DIAGNOSTICS -> QuickDiagnosticsSection()
    HelpGuideSection.SCREEN_GUIDE -> ScreenGuideSection()
    HelpGuideSection.METRICS_REFERENCE -> MetricsReferenceSection()
    HelpGuideSection.AGPS_AND_ADVANCED -> AdvancedCapabilitiesSection()
}
```

每个私有 Composable 仅组合 `HelpText` 和 `HelpSubItem`。在既有位置精度、DOP、C/N0、A-GPS 文案附近补充经验阈值、估计精度、注入和芯片兼容性限制。将 `RoundedCornerShape(12.dp)` 改为 `RoundedCornerShape(8.dp)`。

- [ ] **步骤 2：运行目录测试验证通过**

运行：`./gradlew testDebugUnitTest --tests com.example.gpstest.ui.screens.help.HelpGuideTest`

预期：PASS，任务 1 的目录测试仍通过。

### 任务 4：完整验证

**文件：**
- 验证：上述 5 个修改/创建文件。

- [ ] **步骤 1：运行单元测试**

运行：`./gradlew testDebugUnitTest`

预期：PASS，所有 Debug 单元测试通过。

- [ ] **步骤 2：运行格式检查**

运行：`./gradlew ktlintCheck`

预期：PASS，`HelpGuide.kt` 与 `HelpScreen.kt` 无格式违规。

- [ ] **步骤 3：构建 Debug APK**

运行：`./gradlew assembleDebug`

预期：BUILD SUCCESSFUL，并生成 Debug APK。

- [ ] **步骤 4：检查变更范围**

运行：`git diff --check && git status --short`

预期：无空白错误；仅帮助页、资源、目录测试和本计划相关变更。
