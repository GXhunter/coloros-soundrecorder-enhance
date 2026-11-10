# ColorOS录音助手

面向一加 / ColorOS 原生录音 App 的 **LSPosed 现代 API 102 模块**。支持配置锁屏音量快捷键、触发反馈、状态栏与锁屏通知显示，录音控制保留在下拉通知栏。

当前版本：**1.5.0（versionCode 12）**。包名保留 `io.github.hunter.recordingenhance`，可覆盖旧版安装。录音 App 右上角菜单 →“录音增强”、桌面或 LSPosed 模块设置入口可打开配置页；模块不自行采集或存储音频，录音由原生 App 完成。

## 功能

| 功能 | 当前行为 |
| --- | --- |
| 锁屏快捷录音 | 两下/三下音量上或音量下；1.2 秒内完成，单次按住不超过 400 毫秒；默认三下音量上 |
| 触发反馈 | 单选短震动 60 毫秒、长震动 250 毫秒或亮屏；默认短震动，震动模式不主动亮屏 |
| 重复触发 | 识别后冷却 2 秒；已有录音或暂停中的录音不会被快捷键结束 |
| 通知控制 | 废弃、暂停/继续、保存；“标记”仍可在录音 App 内使用 |
| 状态栏隐藏 | 开启时隐藏胶囊和普通通知图标；关闭时允许原生旧版胶囊和图标；默认开启 |
| 锁屏隐藏 | 开启时设置为 `VISIBILITY_SECRET`；关闭时允许锁屏显示，仍受系统全局限制；默认开启 |
| 录音隐私绿点 | 仅原生录音 App 单独使用麦克风时隐藏；其他 App、相机、来源未知时保留提示，默认关闭 |
| 隐藏录音通知 | 默认关闭；开启后仅隐藏 SystemUI 中的录音条目，前台服务仍运行；需从录音 App 控制录音 |
| 隐藏桌面图标 | 可关闭独立 Launcher 入口，LSPosed 模块设置入口仍可访问和恢复图标 |

音量键仍执行原有音量调节，因此三连按可能提高音量。长按、其他按键、超时或不满足锁屏条件会打断识别。震动只表示启动请求已发出，不保证录音已成功；权限、空间不足或录音冲突仍由原生 App 处理。

**“废弃”不弹确认框，会直接停止并删除当前这段录音。需要保留时请点“保存”。** 删除复用 App 原生取消流程，不按文件名猜测目标，也不遍历删除历史文件。

## 兼容范围

以下是已适配及实测的环境，不代表其他设备兼容：

| 项目 | 版本 / 标识 |
| --- | --- |
| 手机 | 一加 13，型号 PJZ110 |
| 系统 | Android 16 / API 36，ColorOS |
| LSPosed | 2.2.1-it（7921），API 102 |
| 录音 App | `com.coloros.soundrecorder`，17.8.8（17008008） |
| 模块最低系统 | Android 16 / API 36 |
| Xposed API | minApiVersion = targetApiVersion = 102 |

本项目使用 App 内部类和方法，升级系统或录音 App 后需要重新适配。快捷启动会检查录音 App 的 versionCode；**App 侧其他 Hook 没有统一版本门禁**，不能将快捷键的版本检查理解成所有功能都受保护。目标版本变化时建议先停用模块。

## 录音 App 内的配置入口

打开录音 App，点击右上角三个点 → **录音增强**，即可进入现有配置页。模块桌面图标隐藏时仍可使用此入口；保留原有“编辑 / 听筒播放 / 设置”菜单项。菜单在首次安装或更新模块后，需先结束当前录音，再重新打开录音 App 以加载；日常使用无需重启系统。

1.5.0 已实测首页菜单唯一显示“录音增强”，点击可进入模块 SettingsActivity；重复打开未重复添加。录音进行页也已接入同名入口，尚未单独进行录音中的跳转测试。可用 `python tests/verify_recorder_menu.py <实机菜单标题JSON数组>` 检查首页菜单保留情况。

## 安装与使用

1. 安装apk。
2. 在 LSPosed 启用“ColorOS录音助手”，作用域只选择：
   - **系统框架：`system`**
   - **录音：`com.coloros.soundrecorder`**
   - **系统界面：`com.android.systemui`**（1.3.0 新增）
3. 重启手机，使系统侧按键 Hook 生效。
4. 解锁后打开录音 App，完成原有协议和权限授权，允许麦克风与通知。
5. 打开“ColorOS录音助手”，等待显示已连接 LSPosed，选择快捷键、反馈及显示开关，点 **保存设置**。
6. 锁屏后按配置的音量快捷键开始录音；解锁后下拉通知栏控制录音。

设置经官方 libxposed service 远程偏好同步。点击“保存并立即应用”后，快捷键和反馈用于下一次触发；状态栏与锁屏显示会主动刷新当前录音通知，通常约半秒，录音不中断。桌面图标随桌面刷新。日常修改不需要重启录音 App 或手机，连接不可用时不允许保存。

配置页顶部会查询 LSPosed 的运行版本：系统仍加载旧代码时提示重启手机一次，仅录音 App 为旧版时提示先结束录音再重新打开 App；版本已就绪时明确提示日常配置无需重启。无法查询时显示未知状态，不把“服务已连接”误报成“新代码已加载”。配置热生效与模块代码热重载是两回事，当前仍保持 `autoHotReload=false`。

“隐藏桌面图标”只禁用 `.Launcher` alias，不禁用 `.SettingsActivity`。隐藏后可从 **LSPosed → ColorOS录音助手 → 模块设置** 进入，关闭此开关并保存即可恢复；不卸载 App、不清除设置。这个开关是当前 Android 用户的本地组件状态，不需要写入系统 Hook 配置。

1.4.0 将“隐藏录音隐私绿点”限定为原生录音 App：只在隐私使用列表全部为该 App 的麦克风使用记录时隐藏。监听列表变化并缓存结果，无轮询；遇到其他 App、相机、空列表或来源读取失败时保留提示。仍保留 1.3.1 的系统状态和清理流程，不修改 shouldShowDot。其他模块的全局隐藏规则不受本模块控制。

“隐藏录音通知”仅过滤 SystemUI 中 `com.coloros.soundrecorder` 的通知 ID 1024，不取消系统前台服务通知记录。开启后下拉栏不再显示录音操作按钮，需从录音 App 暂停或结束录音；关闭后立即恢复通知列表条目，不重启服务。1.4.1 保留原有 show_notification 配置的存储语义，界面读写取反；升级不会改变当前通知是否显示，也兼容进程中已经加载的旧版 Hook。

1.0.x 隐藏普通图标依赖手动开启“静默通知”；1.1.0 对通知 1024 的渠道副本设置图标、静默分类和锁屏可见性，使配置开关能够双向生效，不永久改写系统渠道。保留“允许通知”开启。停用模块后仍恢复为原有系统渠道设置。

### 更新与恢复

- 当前 `autoHotReload=false`。修改系统侧代码或作用域后需要重启手机。
- 只修改录音 App 侧 Hook 时，先保存并结束录音，再更新 APK、结束并重新打开录音 App 即可。
- 不要在录音中强行结束 App 或重启手机。
- 停用模块并重启可移除模块 Hook；曾手动开启“静默通知”的设备，如需恢复图标还需在系统设置中关闭它。
- APK 使用本机 Android 调试签名。换机器构建时签名可能变化；签名不兼容时不要通过卸载录音 App 或清除其数据解决。

## 构建

已使用以下工具链构建：

| 工具 | 版本 |
| --- | --- |
| JDK | 21 |
| Gradle | 9.5.1 |
| Android Gradle Plugin | 9.2.1 |
| Android SDK / Build Tools | 37 / 37.0.0 |
| Java 源码及字节码级别 | 17 |
| compileSdk / minSdk / targetSdk | 37 / 36 / 36 |

设置 `JAVA_HOME`、`ANDROID_HOME`，确保 `gradle` 在 PATH 中。本项目尚未提供 Gradle Wrapper，命令在项目根目录执行：

```powershell
gradle --gradle-user-home .gradle-home :app:assembleDebug :app:gestureSelfTest :app:discardSelfTest :app:lintDebug
```

使用项目内独立 Gradle 用户目录，避免用户全局 init 脚本改写仓库配置。首次构建需要下载构建组件。libxposed API 以 `compileOnly 'io.github.libxposed:api:102.0.0'` 引入，由 LSPosed 提供；配置页新增官方运行时库 `io.github.libxposed:service:102.0.0` 及其 interface 传递依赖。已关闭 Kotlin 标准库自动引入。第三方许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

产物：`app/build/outputs/apk/debug/app-debug.apk`。Lint 报告：`app/build/reports/lint-results-debug.html`。

## 实现要点

- **按键入口**：在 `onSystemServerStarting` Hook `PhoneWindowManager.interceptKeyBeforeQueueing(KeyEvent, int)`，观察松键事件，不吞掉系统原处理。
- **启动录音**：向当前用户的 `TransparentActivity` 发送模块专用 action，App 侧启动保护安装后转换为原生 Cube Button 请求。已有录音时不执行原生快捷入口的保存行为。
- **反馈**：触发时固定本次反馈选项并传给原生入口；震动模式使用有 60 秒超时的 CPU WakeLock，亮屏模式沿用原生亮屏流程。
- **通知及胶囊**：始终保留已有普通通知分支，以保留废弃按钮；旧版胶囊是否显示由开关决定。system_server 仅对目标包通知 1024 的渠道副本设置 ColorOS 图标/静默分类和锁屏可见性，不影响其他通知。
- **配置页**：原生控件组成圆角分组卡片、单选弹窗、固定底部保存栏，支持浅色/深色配色。`AssistantApplication` 连接 libxposed service，`RecordingModule` 读取远程 SharedPreferences；显示变化监听会合并请求，在主线程调用原生通知刷新和胶囊更新，不重启录音服务。
- **废弃**：用“废弃”替换普通通知的标记按钮，复用原有非导出广播接收器。不可变 PendingIntent 携带本次录音的随机一次性标识；暂停时不更换，结束后失效。验证标识和 `RecorderViewModel.M()` 后调用 `rk.a.d(true)`，由 App 删除当前录音及记录、释放资源并停止服务。

API 参考：[libxposed 官方文档](https://libxposed.github.io/api/io/github/libxposed/api/package-summary.html)。完整适配点和维护约束见 `AGENTS.md`。

## 验证与已知限制

截至 2026-11-10：

1.4.0 已实测：原生录音开始/停止时隐私归属缓存正确变化；录音同时打开相机预览时识别为非录音独占，相机 CAMERA AppOp 持续运行。通知开关在同一段录音中隐藏/恢复条目和三个按钮，前台 ServiceRecord 不变、麦克风不中断。测试录音已保存结束，未拍照。`visibilitySelfTest` 覆盖多 App、相机、未知来源和其他通知隔离。性能未做基准测试，不能声称零开销。

1.3.1 已复现并修复常驻圆点：旧现场为系统 Idle、persistent dot=false，但右上角容器仍可见；`verify_privacy_idle.py` 对旧现场失败，对修复后快照通过。新版实机完成录音中开关双向切换、保存停止后的回归，系统 Idle 且无可见圆点容器，没有新的视图线程异常。测试录音均已保存结束。1.3.0 的容器可见性测试只适用于旧实现；新版直接控制子视图，不能用父容器可见与否判断隐藏开关效果。

回归命令：`python tests/verify_privacy_idle.py <停止录音后SystemUI快照>`。应等待系统正常隐私提示保留时间结束后采集。

1.3.0 已安装，SystemUI 日志确认两个圆点 Hook 加载。实机录音期间，开启开关时右上角圆点容器不可见；关闭开关并锁屏再亮屏后恢复可见，麦克风保持运行。测试后恢复开关开启并由用户保存结束测试录音。相机及其他 App 未单独实测；此实现无包名过滤，作用范围为全局。可用 `python tests/verify_privacy_dot.py <开启时SystemUI快照> <关闭时SystemUI快照>` 验证容器状态。

1.2.0 已实测桌面 alias 隐藏/恢复，隐藏后独立设置 Activity 仍可打开；已验证升级前“旧代码需要重启”提示和重启后“运行版本已就绪”提示。对同一段录音连续切换显示开关，通知属性双向更新，录音 PID、ServiceRecord 保持不变，麦克风持续运行；测试完成后已保存结束。桌面入口分离可运行 `python tests/verify_launcher_entry.py` 自检。

1.1.0 配置页已在实机连接 LSPosed，验证保存、重新打开后回读及跨进程同步。
通过 ADB 注入锁屏音量键实测“两下音量下 + 长震动”：录音启动、250ms 震动记录、屏幕保持熄屏；
实测“三下音量下 + 亮屏”：两下不启动，三下启动并亮屏。
隐藏开关开启/关闭的通知快照分别确认为 SECRET/PUBLIC、showIcon=false/true、unimportant=true/false，三个按钮保留；关闭状态栏隐藏时看到原生红色胶囊恢复。
测试后已保存并结束测试录音，将页面恢复为三下音量上、短震动、两个隐藏开启。
四种组合都有算法自检；两下音量上的实际物理按键、所有锁屏视觉模式尚未逐项实测。

可用 `python tests/verify_display_settings.py work/config-hidden.txt work/config-visible.txt` 复核两份通知快照。

| 检查 | 结果 |
| --- | --- |
| 构建、签名、模块入口 | 已通过 |
| 按键自检 | 四种快捷键、配置切换重置、非法选择回退、超时、长按、条件不满足和冷却已覆盖 |
| 废弃标识自检 | 旧通知、重复点击、暂停/恢复、新录音隔离、不可废弃状态已覆盖 |
| 锁屏快捷录音 | 用户确认正常录音、保持黑屏、短震动 |
| 红色胶囊与普通图标 | 用户确认消失，下拉通知正常 |
| 锁屏通知 | 系统 dump 确认为 SECRET；未单独完成所有锁屏模式的视觉验收 |
| 废弃实测 | 点击新建测试录音的“废弃”后，麦克风停止、服务退出、文件删除；测试前后录音目录清单一致 |
| 暂停后废弃、保存与废弃同时点击 | 标识逻辑已自检，尚未单独进行实机竞态测试 |
| 首次解锁前、其他 ROM、分身/工作资料 | 未验证 |
| Lint | 0 错误；有目标 API、内部反射 API、备份规则相关提示 |

通知属性检查：

```powershell
python tests/verify_recording_notification.py work/notification-dump.txt
```

输入需是**录音进行中**保存的 `adb shell dumpsys notification` 输出。脚本只确认通知 1024 存在、SECRET、前台服务标志和至少两个按钮，不验证按钮文字、点击效果或文件删除。

## 排查

1. 确认设备、录音 App 版本、模块启用用户及三个作用域正确。
2. 查 LSPosed 模块日志，标签为 `RecordingEnhance`。重点确认系统进程加载、按键 Hook、App 启动保护和对应功能 Hook 的加载记录。
3. “已请求原生录音入口”仅证明启动请求发出；需结合录音服务、麦克风使用和文件验证是否成功。
4. “已请求原生废弃当前录音并停止服务”仅证明调用原生取消入口；删除成功应以文件和服务状态核实。
5. 开机 logcat 可能已被覆盖，不能凭没有 logcat 输出断言模块未加载。优先读取框架日志，采集限定范围，勿清空原日志。

在授权的测试中，保存并结束已有录音后再重启 App；删除验证只使用新建测试录音，并比对测试前后的文件清单。不要上传含录音转写、私人文件名或其他应用信息的原始 dump。

## 许可证

本项目采用 [MIT License](LICENSE)，版权署名为 GXhunter。

许可证适用于本项目代码，不授予原厂录音 App、系统组件或其反编译材料的分发权；这些本地分析材料不包含在仓库中。
