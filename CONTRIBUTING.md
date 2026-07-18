# 贡献指南

感谢参与 GPS Debug Tool 的开发！本文说明如何提交 Issue / PR、提交信息规范，以及本地开发与检查要求。

## 开发环境

| 项          | 要求                                                                  |
| ----------- | --------------------------------------------------------------------- |
| JDK         | **构建 JDK 21**（`JAVA_HOME` 指向 JDK 21）；源码/目标兼容 **Java 17** |
| Android SDK | compileSdk / targetSdk **35**，minSdk **24**                          |
| 工具        | Android Studio 或命令行 + Gradle Wrapper                              |

本地示例（Git Bash / WSL）：

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export PATH="$PATH:/d/android_sdk/platform-tools"
```

Windows 也可在 `gradle.properties` 中设置本地 `org.gradle.java.home`（**勿提交**机器路径到共享默认配置）。

## 常用命令

```bash
./gradlew assembleDebug     # Debug APK
./gradlew test              # 单元测试
./gradlew testDebugUnitTest # 显式 Debug 单元测试
./gradlew ktlintCheck       # ktlint 1.5.0（android 模式）
./gradlew installDebug      # 安装到设备
./gradlew clean
```

提交前请至少通过：

1. `./gradlew ktlintCheck`
2. `./gradlew test`

CI（push/PR 到 `master`）会跑测试与 Debug/Release 构建；风格检查以本地 `ktlintCheck` 为准。

## 分支与 PR 流程

1. Fork 仓库（或在有写权限的分支上工作）
2. 从最新 `master` 创建功能分支，例如：
    ```bash
    git checkout -b feat/short-description
    ```
3. 按小步提交（见下方约定式提交）
4. 推送分支并创建 Pull Request
5. PR 描述写清：动机、改动摘要、如何验证（命令与结果）
6. 通过 review / CI 后再合并

### PR 检查清单

- [ ] 变更范围清晰，无无关重构
- [ ] 有用户可见文案时使用 string 资源（中文面向用户）
- [ ] 新增领域逻辑时补充单元测试（`domain/` 与可测 ViewModel 路径）
- [ ] `./gradlew ktlintCheck` 与 `./gradlew test` 通过
- [ ] 未提交密钥、本机路径（如私有 `JAVA_HOME`）、未跟踪的计划草稿

## 约定式提交（Conventional Commits + emoji）

格式：

```text
<type>: <emoji> <subject>
```

示例：`feat: ✨ 添加天空图缩放`、`docs: 📝 更新贡献指南`

| type       | emoji | 用途                    |
| ---------- | ----- | ----------------------- |
| `feat`     | ✨    | 新功能                  |
| `fix`      | 🐛    | 缺陷修复                |
| `docs`     | 📝    | 文档                    |
| `style`    | 💄    | 格式/风格（不影响逻辑） |
| `refactor` | ♻️    | 重构                    |
| `perf`     | ⚡    | 性能                    |
| `test`     | ✅    | 测试                    |
| `chore`    | 🔧    | 构建/工具/杂项          |
| `revert`   | ⏪    | 回滚                    |

Subject 简短说明「做了什么」；中英文均可，与仓库近期提交风格一致即可。

## 代码风格与架构约定

- **语言**: Kotlin；Kotlin 代码风格 `official`；ktlint android 模式
- **架构**: Clean MVVM，单向数据流；接口 + `Impl`；Repository 在 `domain/repository/`
- **DI**: 手动 DI（`ViewModelProvider.Factory`），无 Hilt/Dagger/Koin
- **UI**: Jetpack Compose + Material 3；无状态组件，状态提升到 Screen / ViewModel
- **异步**: 协程 + Flow；I/O 使用 `Dispatchers.IO`
- **命名**: 类 PascalCase；方法/变量 camelCase；私有后备字段 `_` 前缀；常量 `SCREAMING_SNAKE_CASE`
- **注释/文档**: 领域与面向用户说明优先中文

更完整的目录与数据流说明见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 与根目录 [AGENTS.md](AGENTS.md)。

## 测试约定

- 框架：JUnit 4；测试类命名 `<源类名>Test.kt`，包路径与源码镜像
- 优先覆盖 **domain** 纯逻辑与 **ViewModel** 可测路径；Android 平台强耦合代码可不强制单测
- 方法名可用反引号描述性行为，例如 `` `quality is EXCELLENT when pdop less than 1` ``

## 行为准则

- 保持讨论与 review 建设性、具体
- 不提交恶意代码或故意破坏设备 GNSS 行为的改动
- 本应用面向调试；文档与 PR 中勿夸大定位精度保证

有问题可先开 Issue 讨论设计，再提交大范围 PR。
