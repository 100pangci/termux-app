# SSH 主机管理与应用国际化（本分支）

本文档说明本分支相对 `termux/termux-app` 上游源码的新增功能、设计取舍、改动文件、构建测试结果和已知限制。

## 1. 范围与兼容性

本分支只做最小侵入式扩展，不修改以下上游机制：

- 终端模拟器、PTY、`TerminalSession`、`TermuxService` 会话管理。
- Bootstrap 安装流程和包管理器（bootstrap 内容需按新包名重新编译，见 1.1）。
- 插件 Intent / `RUN_COMMAND` API 的实现方式（包名变化会影响官方插件，见 1.1）。
- 原有 `TermuxActivity` 的终端主题和抽屉布局结构（仅新增一个 SSH 入口按钮）。

### 1.1 包名与运行时前缀

按用户明确要求，本分支的 Android `applicationId` 已改为 `com.termux.ywpc`，与官方 Termux 并存安装。Java 包名和 namespace 仍保持 `com.termux`，因此组件类名、`R`、`BuildConfig` 及绝大多数源码不需要重命名，便于继续合并上游。

由此带来的约束：

- 运行时 `$PREFIX` 为 `/data/data/com.termux.ywpc/files/usr`，`$HOME` 为 `/data/data/com.termux.ywpc/files/home`。
- Bootstrap 二进制和脚本中的前缀是编译期硬编码的，必须使用按新前缀编译的四个 `bootstrap-<arch>.zip`，放在 `app/src/main/cpp/`；也可用 `TERMUX_BOOTSTRAP_DIR` 指向存放这四个文件的目录，构建时会复制到该位置。自定义包名的构建不会再被 `./gradlew clean` 删除这些 zip。
- Gradle 在 native 编译前校验 bootstrap：`bin/login` 的 shebang、`bin/bash` 的 ELF 架构和 `$PREFIX`、`bin/termux-am-socket` 的套接字路径、`SYMLINKS.txt` 中的前缀和 `bin/sh` 链接，并扫描 `bin/` 下是否仍引用 `/data/data/com.termux/files/`。还会检查 `termux-open`、`termux-wake-lock`、`termux-wake-unlock`、`termux-reload-settings`、`termux-setup-storage`、`termux-reset` 生成的组件和 action：包名必须是 `com.termux.ywpc`，组件类名必须是 `com.termux.app.*`，`termux-reset` 的 termux-am 类必须是 `com.termux.termuxam.Am`。任一检查不通过时默认终止构建；仅设置 `TERMUX_ALLOW_UPSTREAM_BOOTSTRAP=1` 可降级为编译验证，此时 APK 的终端环境不可用。
- `Android.mk` 把四个 zip 声明为 `termux-bootstrap-zip.o` 的依赖（`.incbin` 不会自动记录到依赖文件），Gradle 也把 zip 声明为 native 任务输入，因此替换 zip 后会重新嵌入，不会复用旧的对象文件。
- 单元测试不读取 bootstrap，`./gradlew :app:testDebugUnitTest` 可在没有 zip 的全新检出上运行；`assemble` 以及直接调用 native 任务时仍需要 bootstrap。
- 新应用与官方 Termux 数据完全隔离，不能覆盖安装官方 Termux，也不能读取其数据。
- 官方 Termux:API、Termux:Boot 等插件以及外部按 `com.termux` 包名/权限编写 `RUN_COMMAND` 的客户端不兼容；需要针对新包名重新编译或修改。`TermuxConstants` 中插件包名（`TERMUX_API_PACKAGE_NAME` 等）使用独立的 `TERMUX_PLUGIN_PACKAGE_NAME_PREFIX = "com.termux"`，仍指向官方插件，避免应用内安装链接变成 `com.termux.ywpc.*` 的 404 页面；但官方插件无法控制本应用。
- `TermuxConstants` 中新增 `TERMUX_JAVA_PACKAGE_NAME`：类名相关常量使用它，包名/权限/路径相关常量使用 `TERMUX_PACKAGE_NAME`，避免 applicationId 与 Java 包名不一致时产生错误组件名。
- Bootstrap 中的 `termux-tools` 也需要同样的拆分。上游模板把 `@TERMUX_APP_PACKAGE@` 同时用于 Intent 包名和 Java 组件类名；仅以 `com.termux.ywpc` 构建会生成 `com.termux.ywpc.app.TermuxOpenReceiver`、`com.termux.ywpc.app.TermuxService` 和 `com.termux.ywpc.termuxam.Am`，这些类不存在。必须在 termux-packages/termux-tools 中为组件类名使用独立的 Java 包变量（当前为 `com.termux`），或在打包前把脚本中的上述三处类前缀改回 `com.termux.app.*` / `com.termux.termuxam.Am`；Gradle 会拒绝未修正的 bootstrap。

新增的 SSH 界面使用 Material 3（`Theme.TermuxSsh.DayNight.NoActionBar`），并且只在支持设备上应用动态取色；原有界面继续使用上游主题。

## 2. SSH 主机与会话流程

### 2.1 数据模型

`SshHost` 字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 应用内 UUID |
| `name` | 显示名称 |
| `hostname` | IPv4 / IPv6 / 域名 |
| `port` | 端口，`0` 表示不传 `-p`，由 OpenSSH 配置或默认值决定 |
| `username` | 用户名，可选 |
| `identityFile` | 私钥路径，可选；只保存路径 |
| `configAlias` | `~/.ssh/config` 中的 Host 别名，可选；设置后作为 ssh 目标 |
| `notes` | 备注 |

目标选择规则：有 `configAlias` 时目标为别名，否则为 `hostname`。因此别名可以完整复用 OpenSSH 的 `Include`、通配符、`Match` 和优先级解析；应用不重复实现配置解析器。

### 2.2 会话创建

点击主机后：

1. 主机列表 Activity 检查 `TermuxService` 是否已绑定。
2. 检查当前会话数是否达到上游 `MAX_SESSIONS` 限制。
3. 检查 `$PREFIX/bin/ssh` 和 `$PREFIX/bin/sh` 是否可执行。
4. 由 `SshCommandBuilder` 生成独立的 argv 数组。
5. 通过 `TermuxService.createTermuxSession(...)` 创建普通 Termux 会话。
6. 把新会话 handle 写入当前的会话偏好，再启动 `TermuxActivity`，由原界面显示终端。

不使用模拟键盘输入，也不把用户字段拼接成 shell 命令。

### 2.3 参数安全

用户字段全部作为独立 argv 元素传递：

```text
$PREFIX/bin/sh -l -c 'exec "$0" "$@"' \
  $PREFIX/bin/ssh [-l user] [-p port] [-i key] -- destination
```

- `exec "$0" "$@"` 是常量脚本，用户输入只通过位置参数进入 OpenSSH，不经过 shell 求值。
- 目的地址放在 `--` 之后，防止被解析成选项。
- 校验拒绝控制字符、空白、以 `-` 开头的目的地址，以及会混淆目标地址的 shell 元字符。OpenSSH 配置别名按单个 token 校验，允许非 ASCII 字符，但同样拒绝空白、控制字符、shell 分隔符和开头的 `-`。
- 登录 shell（`-l`）用于加载用户 profile，从而让 `SSH_AUTH_SOCK` 等环境变量在支持时可用。

## 3. 数据存储与 OpenSSH 配置

- 主机元数据保存在应用私有的 `SharedPreferences` 文件 `termux_ssh_hosts` 中（JSON，格式版本 1）。
- 不保存密码、口令、私钥内容或任何认证材料。
- `~/.ssh/config` 只读：导入功能通过 `SshConfigHosts` 扫描 `Host` 和 `Include` 中的具体别名，支持注释、引号、`Host=alias`/`Host = alias`、多个模式、`~` 和 Include 通配符；有循环与文件数量/大小限制。条件块（`Host foo`、`Match`）内的 `Include` 只在条件成立时才会被 OpenSSH 读取，因此导入时不会展开这些 Include；`Host *` 和 `Match all` 下的 Include 视为全局展开。
- 应用不会新增、修改或删除 `~/.ssh/config`。真实连接时仍由 OpenSSH 自己读取该文件。

## 4. 零自动连接约束

以下路径均不调用 `SshSessionLauncher.createSession(...)`：

- 应用启动、`TermuxActivity` 创建/恢复。
- 打开或关闭抽屉、进入主机列表、进入编辑页。
- `onResume`、语言切换导致的 Activity 重建。
- 网络状态变化（应用没有注册任何 SSH 网络监听）。

唯一的调用点是 `SshHostListActivity.connectToHost(...)`，由用户点击主机行触发。代码中没有重连、监控、后台探测或定时任务。

## 5. 国际化

- 语言资源：
  - `app/src/main/res/values/`（英语默认）
  - `app/src/main/res/values-zh-rCN/`
  - `app/src/main/res/values-zh-rTW/`
  - `termux-shared` 和 `terminal-view` 也补充了对应中文资源。
- 默认跟随系统语言。
- `Settings` 根页面新增“应用语言”，可选跟随系统、English、简体中文、繁體中文。
- 使用 AppCompat per-app locales（`AppCompatDelegate.setApplicationLocales`）。
- Android 13+ 使用 `android:localeConfig`；旧版本使用 `AppLocalesMetadataHolderService` 的 `autoStoreLocales` 持久化。
- 语言切换会重建 Activity，但终端会话由 `TermuxService` 持有，因此不会被结束。
- 设置界面的标题、分类、入口摘要、开关说明、日志级别名称和“默认”后缀均已资源化并翻译，包括 Termux 设置根页面和各级调试、终端 I/O、终端视图页面。
- 设置“关于”页面生成的 Markdown 技术报告为便于在 GitHub 提交，结构性标题（如 “Device Info”、“Important Links”）保持英文；设置界面本身已本地化。
- `TermuxService` 的常驻通知文本通过本地化 Context 读取，在 API 32 及以下也能应用应用内语言选择。
- 通知中的会话/任务数量使用 plurals；占位符和格式参数保持资源化。
- ISO 代码、路径、IP、域名和 shell 输出不属于翻译范围。

## 6. 改动文件清单

### 新增 Java

- `app/src/main/java/com/termux/app/AppLanguageUtils.java`
- `app/src/main/java/com/termux/app/ssh/SshHost.java`
- `app/src/main/java/com/termux/app/ssh/SshHostValidator.java`
- `app/src/main/java/com/termux/app/ssh/SshCommandBuilder.java`
- `app/src/main/java/com/termux/app/ssh/SshHostStore.java`
- `app/src/main/java/com/termux/app/ssh/SshConfigHosts.java`
- `app/src/main/java/com/termux/app/ssh/SshSessionLauncher.java`
- `app/src/main/java/com/termux/app/ssh/SshHostAdapter.java`
- `app/src/main/java/com/termux/app/ssh/SshHostListActivity.java`
- `app/src/main/java/com/termux/app/ssh/SshHostEditActivity.java`

### 新增资源

- `app/src/main/res/layout/activity_ssh_hosts.xml`
- `app/src/main/res/layout/activity_ssh_host_edit.xml`
- `app/src/main/res/layout/item_ssh_host.xml`
- `app/src/main/res/menu/menu_ssh_hosts.xml`
- `app/src/main/res/menu/menu_ssh_host_item.xml`
- `app/src/main/res/drawable/ic_ssh.xml`
- `app/src/main/res/drawable/ic_add.xml`
- `app/src/main/res/drawable/ic_search.xml`
- `app/src/main/res/drawable/ic_more_vert.xml`
- `app/src/main/res/drawable/ic_arrow_back.xml`
- `app/src/main/res/drawable/ic_import.xml`
- `app/src/main/res/drawable/bg_ssh_badge.xml`
- `app/src/main/res/values/strings_ssh.xml`
- `app/src/main/res/values/strings_language.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`
- `app/src/main/res/values-zh-rCN/strings_ssh.xml`
- `app/src/main/res/values-zh-rCN/strings_language.xml`
- `app/src/main/res/values-zh-rTW/strings.xml`
- `app/src/main/res/values-zh-rTW/strings_ssh.xml`
- `app/src/main/res/values-zh-rTW/strings_language.xml`
- `app/src/main/res/xml/locales_config.xml`
- `termux-shared/src/main/res/values-zh-rCN/strings.xml`
- `termux-shared/src/main/res/values-zh-rTW/strings.xml`
- `terminal-view/src/main/res/values-zh-rCN/strings.xml`
- `terminal-view/src/main/res/values-zh-rTW/strings.xml`

### 新增测试

- `app/src/test/java/com/termux/app/ssh/SshHostValidatorTest.java`
- `app/src/test/java/com/termux/app/ssh/SshCommandBuilderTest.java`
- `app/src/test/java/com/termux/app/ssh/SshConfigHostsTest.java`
- `app/src/test/java/com/termux/app/ssh/SshHostStoreTest.java`
- `app/src/test/java/com/termux/app/ssh/SshLocaleResourcesTest.java`
- `app/src/test/java/com/termux/app/TermuxPackageNameTest.java`

### 修改的上游文件

- `app/build.gradle`：`applicationId` 改为 `com.termux.ywpc`；Manifest 占位符同步；新增 bootstrap 完整性校验、`TERMUX_ALLOW_UPSTREAM_BOOTSTRAP` 编译验证开关、native 任务依赖/输入声明和 `clean` 保护。
- `app/src/main/cpp/Android.mk`：为 `termux-bootstrap-zip.o` 增加四个 `.incbin` zip 的显式依赖，替换 zip 后强制重新嵌入。
- `termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java`：`TERMUX_PACKAGE_NAME` 改为新包名；新增 `TERMUX_JAVA_PACKAGE_NAME`，类名常量改用它。
- `app/src/main/res/xml/shortcuts.xml`：`targetPackage` 与 failsafe extra 改为新包名，`targetClass` 保持 Java 包名。
- `app/src/main/java/com/termux/app/TermuxActivity.java`：抽屉 SSH 入口；非 `com.termux` 包名时隐藏不兼容的 Termux:Styling 入口。
- `app/src/main/java/com/termux/app/TermuxService.java`：`LocalBinder` 公开；通知文本资源化。
- `app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java`：`MAX_SESSIONS` 公开；退出提示资源化。
- `app/src/main/java/com/termux/app/terminal/TermuxSessionsListViewController.java`：会话空标题资源化。
- `app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java`：问题报告标题资源化。
- `app/src/main/java/com/termux/app/terminal/io/TermuxTerminalExtraKeys.java`：错误 Toast 资源化。
- `app/src/main/java/com/termux/app/activities/SettingsActivity.java`：应用语言选择；About 标题资源化。
- `app/src/main/java/com/termux/app/api/file/FileReceiverActivity.java`：文件接收错误提示资源化。
- `app/src/main/res/layout/activity_termux.xml`：新增 SSH 入口按钮。
- `app/src/main/res/values/themes.xml`：新增 SSH 界面的 Material 3 主题。
- `app/src/main/res/values/strings.xml`：新增 SSH 相关的通用会话/通知/File Receiver 字符串；`TERMUX_PACKAGE_NAME` 实体改为新包名。
- `app/src/main/res/values-zh-rCN/strings.xml`、`app/src/main/res/values-zh-rTW/strings.xml`：同步 `TERMUX_PACKAGE_NAME` 实体。
- `app/src/main/res/xml/root_preferences.xml`：新增“应用语言”偏好。
- `app/src/main/AndroidManifest.xml`：两个新 Activity、localeConfig、AppCompat 语言存储服务。
- `termux-shared/src/main/java/com/termux/shared/activities/TextIOActivity.java`：标题资源化。
- `termux-shared/src/main/java/com/termux/shared/activities/ReportActivity.java`：报告标题资源化。
- `termux-shared/src/main/java/com/termux/shared/settings/properties/SharedProperties.java`：属性文件错误提示资源化。
- `termux-shared/src/main/java/com/termux/shared/logger/Logger.java`：设置页日志级别的“默认”后缀资源化。
- `termux-shared/src/main/res/values/strings.xml`：新增上述共享字符串；`TERMUX_PACKAGE_NAME` 和 `TERMUX_PREFIX_DIR_PATH` 实体改为新包名/新前缀。
- `termux-shared/src/main/res/values-zh-rCN/strings.xml`、`termux-shared/src/main/res/values-zh-rTW/strings.xml`：同步上述两个实体。
- `terminal-view/src/main/res/values/strings.xml`：保持英文默认资源，新增中文目录。
- `README.md`：功能说明、包名说明与文档入口。

## 7. 构建与测试结果

构建环境：Linux、Java 21（Gradle 编译目标 Java 8）、Gradle 9.2.1、AGP 8.13.2、compileSdk 36、minSdk 21。

已执行（当前 `app/src/main/cpp/` 中仍是上游 bootstrap，因此显式开启编译验证开关；提供新前缀 bootstrap 后无需该变量）：

```bash
TERMUX_ALLOW_UPSTREAM_BOOTSTRAP=1 ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

同时验证了：`./gradlew :app:testDebugUnitTest` 在没有 bootstrap 的依赖图中也能运行（35 个测试）；无开关时 `:app:downloadBootstraps` 会以按架构列出的校验错误终止；`./gradlew clean` 不再删除自定义包名所需的四个 zip；替换任一 zip 后 `buildNdkBuildDebug[<abi>]` 会重新编译 `termux-bootstrap-zip.o` 并嵌入新内容。

结果：

- `:app:testDebugUnitTest`：`BUILD SUCCESSFUL`，共 35 个测试，0 失败
  - `SshHostValidatorTest`：7
  - `SshCommandBuilderTest`：6
  - `SshConfigHostsTest`：9（含 `Host = alias`、`Host = *`、`Match = all` 分隔符写法）
  - `SshHostStoreTest`：4（Robolectric）
  - `SshLocaleResourcesTest`：3（Robolectric，zh-CN / zh-TW / en）
  - `TermuxPackageNameTest`：4（Robolectric，applicationId/常量/源 Manifest 占位符/快捷方式一致性）
  - 上游已有测试：2
- `:app:assembleDebug`：`BUILD SUCCESSFUL`，APK 位于 `app/build/outputs/apk/debug/`
  - 由于当前嵌入的是上游 bootstrap，此 APK 仅用于编译验证，**安装后终端环境不可用**；替换为按 `com.termux.ywpc` 前缀编译的 bootstrap 后重新构建才会得到可运行 APK。
- `:app:lintDebug`：`BUILD SUCCESSFUL`，0 errors（剩余警告均为上游已有警告或非阻断性提示）

调试 APK 未在真实 Android 设备或模拟器上安装运行；在缺少新前缀 bootstrap 的情况下也未验证安装后的终端启动。

## 8. 已知限制与未验证项

### 未验证（缺少设备/模拟器）

- 使用按 `com.termux.ywpc` 前缀编译的 bootstrap 进行全新安装、首次启动和包管理器初始化。
- 与官方 Termux 并存安装时的快捷方式、文件接收/分享、`RUN_COMMAND` 和插件行为。
- SSH 实际连接、密码/口令交互、主机密钥确认和 `known_hosts` 写入。
- Ed25519 / RSA 密钥、`ssh-agent`（`SSH_AUTH_SOCK`）在真实 Termux 环境中的行为。
- IPv6、域名、非标准端口、自定义私钥路径的端到端连接。
- Material 3 界面在浅色/深色、动态取色、横屏、小屏和大字体下的显示效果。
- 语言切换时 Activity 重建、当前会话保持、系统语言变化后的资源选择。
- Android 13+ 系统“应用语言”页面与本应用内选择器的交互。

### 功能限制

- 官方 Termux:API、Termux:Boot、Termux:Float、Termux:Styling、Termux:Tasker、Termux:Widget 等插件按 `com.termux` 包名编写，不适用于本分支；外部按旧包名/权限提交 `RUN_COMMAND` 的客户端同样不兼容，需要改为 `com.termux.ywpc`。非 `com.termux` 包名构建会隐藏终端上下文菜单中的“样式”入口，避免引导安装无法控制本应用的 Termux:Styling。
- 新应用与官方 Termux 数据目录完全隔离，不能覆盖安装或迁移官方 Termux 的 `$PREFIX`/`$HOME`。
- 四个 bootstrap zip 必须通过 termux-packages 以 `TERMUX_APP__PACKAGE_NAME=com.termux.ywpc` 编译（`$PREFIX` 随之为 `/data/data/com.termux.ywpc/files/usr`），并且 `termux-tools` 的组件类名必须保持 `com.termux.app.*`、termux-am 类名必须保持 `com.termux.termuxam.Am`。上游 `termux-tools` 目前用同一个 `@TERMUX_APP_PACKAGE@` 生成 Intent 包名和类名，直接以新包名构建会生成不存在的 `com.termux.ywpc.app.*` 类；需要给 termux-tools 增加独立的 Java 包变量，或在打包前修正脚本中的类前缀。构建期会校验 shebang、`bin/bash` 的 ABI 和前缀、`bin/termux-am-socket` 的套接字路径、`SYMLINKS.txt`、组件/action 字符串，并扫描 `bin/` 是否残留 `/data/data/com.termux/files/`。
- 上游 bootstrap 中的 `termux-am`、`termux-am-socket`、`termux-open`、`termux-wake-lock`/`termux-wake-unlock`、`termux-reload-settings`、`termux-setup-storage` 等工具把 `com.termux` 编入套接字路径、Intent action/组件或脚本；即使用 `TERMUX_ALLOW_UPSTREAM_BOOTSTRAP=1` 生成编译验证 APK，这些命令和 `$PREFIX/bin/sh` 也不可用，终端无法正常启动。
- 自定义前缀 bootstrap 是本分支的构建输入，无法从上游下载；请在另外的目录保留一份原始 zip，或使用 `TERMUX_BOOTSTRAP_DIR` 指向它们。`clean` 已不会删除自定义包名的 zip，但仓库的 `.gitignore` 仍忽略 `*.zip`，丢失后只能重新编译。
- 导入只发现 `~/.ssh/config` 中可直接作为目标的 `Host` 别名；`Match`、`CanonicalizeHostname` 等复杂语义不做本地解析，连接时交给 OpenSSH。
- Include 支持 `~`、绝对路径和 `* ? []` 通配符；不支持 `~user` 和其他用户的 home 展开，也未实现 OpenSSH 新版 `%` 令牌展开。
- 应用不管理 `known_hosts`，不提供密码保存、私钥生成或 `ssh-agent` 启动；这些由 OpenSSH / Termux 环境负责。
- OpenSSH 未安装时只提示 `pkg install openssh`，不会自动安装。
- 主机列表超过 8 个会话时受上游 `MAX_SESSIONS` 限制，需先关闭已有终端。
- 应用私有存储中的主机元数据不含秘密；如果设备已被 root 或应用数据被备份，元数据本身仍可能被读取。
- 为保持上游可合并性，`TermuxService.LocalBinder` 和 `TermuxTerminalSessionActivityClient.MAX_SESSIONS` 仅作了可见性放宽，没有重构会话管理。

## 9. 上游同步建议

SSH 功能集中在 `com.termux.app.ssh` 包和独立资源文件中；上游文件改动尽量保持单行/局部改动。合并上游更新时优先保留上游实现，只重新应用：

1. 抽屉按钮和 Activity 入口。
2. 两个可见性调整（`LocalBinder`、`MAX_SESSIONS`）。
3. 已资源化的字符串调用。
4. Manifest 中的 locale 配置。
5. 包名配置：`app/build.gradle` 的 `applicationId`/占位符/bootstrap 校验与 native 任务依赖、`app/src/main/cpp/Android.mk` 的 `.incbin` 依赖规则、`TermuxConstants.TERMUX_PACKAGE_NAME`、`TERMUX_JAVA_PACKAGE_NAME` 与 `TERMUX_PLUGIN_PACKAGE_NAME_PREFIX`、`strings.xml` 中的 `TERMUX_PACKAGE_NAME`/`TERMUX_PREFIX_DIR_PATH` 实体、`shortcuts.xml` 的 `targetPackage` 和 failsafe extra。
