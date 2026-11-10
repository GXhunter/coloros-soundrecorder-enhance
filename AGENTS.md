# AGENTS.md — ColorOS录音助手

本文件适用于本项目及所有子目录。默认用中文沟通，遵循用户提供的全局开发约定；用户当前指令优先。面向使用者的行为、安装和验证状态写在 README，本文件记录维护约束。

## 项目边界

- 这是适配一加 13 / PJZ110、Android 16、ColorOS 录音 17.8.8（17008008）的现代 LSPosed API 102 模块。
- 模块包名：`io.github.hunter.recordingenhance`；目标 App：`com.coloros.soundrecorder`。
- 当前作用域为 **`system`**、**`com.coloros.soundrecorder`**、**`com.android.systemui`**。不要把现代 API 的系统作用域写成 `android`。
- 只使用 `io.github.libxposed.api`，不混用旧版 `de.robv.android.xposed`。
- 当前静态作用域、`autoHotReload=false`。不要未经需求增加 SystemUI 或其他 App 作用域。
- 配置热同步不等于代码热重载。日常配置不要求重启；新版本是否需要重启由运行目标的版本和状态提示。不要为实现热配置自动重启录音服务或系统。
- 保留录音前台服务、权限和冲突检查。1.4.0 按用户最新要求收窄绿点隐藏：仅原生录音 App 独占麦克风时允许隐藏，其他来源必须保留。用户授权“显示录音通知”开关，仅过滤 SystemUI 中的录音条目，不取消系统前台服务通知记录、不伪造 AppOps。
- 1.1.0 使用官方 libxposed service 远程偏好同步设置。系统侧只修改目标包通知 1024 的渠道副本，支持图标和锁屏显示双向切换，不永久改写系统渠道；禁用模块后原有“静默通知”设备设置仍保留。

## 修改前

1. 先说明具体改动点，读取受影响实现、调用者、测试和配置；只解决当前需求。
2. 有根目录 `.codegraph/` 时，先用 CodeGraph 理解代码；没有则不创建索引，使用 `rg`。
3. 优先复用原生录音 App 的现有流程，尤其是开始、停止、保存和废弃。不要自行实现音频采集或增加无关设置界面。
4. 实际修改生产依赖、执行数据库迁移或删除用户已有文件前，确认具体目标和影响；已有同范围授权继续有效。新工具先解释必要性、替代方案和影响并获得确认。
5. Java 使用现有 Gradle 命令。本项目不使用 Spring，不为注入样式引入 Lombok、DI 框架或额外运行时依赖。

## 代码约定

- 使用 Java 17 语法，UTF-8；成员字段使用清晰的 `mXxx` 命名，常量使用大写下划线。
- DTO、VO、Entity、配置和序列化 Bean 字段使用普通驼峰，不加 `m`。如需多个返回值，复用结果类型或最小结果类，不通过可变入参传出结果。
- 最小完整改动，不增加只包装一行代码的辅助层。注释只解释非显然的原因和约束，默认中文。
- 按键 Hook 必须保留 `chain.proceed()`，不能因模块失败阻断系统原有按键处理。
- 日志使用模块 `log(...)`，标签 `RecordingEnhance`，使启动诊断保留在 LSPosed 模块日志中。
- 不将转写内容、令牌、账号、录音全文或私人文件路径写入新日志。
- 修改 App 反射接口时，先核对目标 APK 的实际名称和签名。JADX 的 `f12345x` 等展示名可能是反编译重命名，不能直接当作运行时字段名。
- 新增非平凡逻辑要有小型有效自检。不要为纯文档或简单文本调整增加测试框架。

## 关键适配点

完整实现位于 `app/src/main/java/io/github/hunter/recordingenhance/RecordingModule.java`。

| 范围 | 当前依赖 |
| --- | --- |
| 系统按键 | `PhoneWindowManager.mContext`、`interceptKeyBeforeQueueing(KeyEvent, int)` |
| 手势 | `TriplePress.release`；配置为两次/三次音量上/下，1200ms 内，单按不超过 400ms，冷却 2000ms；切换配置重置计数 |
| 原生入口 | `oplus.multimedia.soundrecorder.slidebar.TransparentActivity` |
| 快捷请求 | 模块 `START_ONLY` action，经 `parseDataFromIntent` 转为 `oplus.intent.action.START_RECORD_FROM_CUBE_BUTTON` |
| 防重复启动 | `handleFromCubeOrLockScreenBefore()`、`com.soundrecorder.recorderservice.manager.s.c()` |
| 反馈 | 短震动60ms、长震动250ms、亮屏单选；本次选项随 Intent 固定，震动模式换为 PARTIAL_WAKE_LOCK，亮屏沿用原入口 |
| 普通通知 | `NotificationManager.canPostPromotedNotifications()`、`SeedlingSdkApi.isSupportFluidCloud/isSupportSystemSendIntent` |
| 旧红色胶囊 | `statusbar.h.g(Context, String)` 转调 `pp.a.m(Context, String, true)` |
| 锁屏隐藏 | `notification.common.d.k()` 后读取 `cj.c.e`，设置 SECRET，清空 publicVersion |
| 废弃按钮 | `notification.common.h.x()`；Context 为运行时字段 `cj.c.c` |
| 废弃接收 | `bj.e.e()` 扩展原生 IntentFilter，`manager.n.b(RecorderService, Intent, boolean)` 处理专用 action |
| 废弃状态 | `manager.s.a(int)`；1=录音，2=暂停；`manager.x.a()` 返回 ViewModel，`RecorderViewModel.M()` 判断当前可废弃 |
| 原生删除入口 | `rk.a` 的静态字段 **`a`** 和方法 `d(true)`，会走取消、删除和停服流程 |
| 配置通信 | `AssistantApplication` 通过 service 库读写 settings 组，Hook 使用 `getRemotePreferences`，不自行建开放广播/Provider 通道 |
| 当前录音热刷新 | 保留 SharedPreferences listener 强引用，对显示项变更合并延迟500ms，在主线程调用当前普通通知控制器 `cj.c.q(false)` 和旧胶囊更新；控制器使用 WeakReference |
| 隐藏桌面图标 | 只切换 `.Launcher` alias，保留 `.SettingsActivity` 的 MODULE_SETTINGS filter；使用 DONT_KILL_APP，读取实际组件状态，不把设置 Activity 一起禁用 |
| 录音菜单入口 | `RecorderMenuHook` 在 COUIToolbar.inflateMenu 的 recorder_list_option_browse 菜单及 RecorderActivity.onCreateOptionsMenu 加入“录音增强”；首页会覆盖点击监听，需在 BrowseFragment.onOptionsItemSelected 处理专用 ID |
| 录音隐私圆点 | 1.3.1 保留原生 shouldShowDot，Hook 两套控制器 access$processNextViewState 之后控制 privacy_dot 子视图；配置监听也刷新视图。必须用 View.post 回到所属线程，不能假定为主线程；不得改写旧/新状态比较结果 |
| 通知渠道副本 | `NotificationRecord` 构造及 `updateNotificationChannel`；仅目标包/ID1024/录音渠道；Parcel 深复制后设置 ColorOS showIcon/unimportant 与锁屏可见性 |

表中 `manager.*` 指 `com.soundrecorder.recorderservice.manager.*`，`notification.*` 指 `com.soundrecorder.notification.*`，`statusbar.*` 指上述 manager 下的 statusbar 包。

快捷启动目前检查录音 App versionCode，但 App 侧 Hook 没有统一版本门禁。适配新版本时必须逐项重新取证，不仅改版本号，也不要假定“反射未报错”就等于行为兼容。

1.4.0：`SystemEventCoordinator$privacyStateListener$1.onPrivacyItemsChanged` 提供隐私列表，缓存仅原生录音麦克风的判断。通知开关 Hook `RankingCoordinator.mSuspendedFilter.shouldFilterOut`，限定目标包和 ID1024；通过 `invalidateList` 热刷新。修改范围策略要跑 `visibilitySelfTest`，不可将“录音正在进行”当作隐藏所有来源提示的依据。

1.4.1 界面改名“隐藏录音通知”，开启表示隐藏。保留存储键 `show_notification` 及其正向显示语义，UI 读取/保存/旋转恢复时取反，避免升级改变用户设置或与已加载旧 Hook 不兼容。

## 录音与删除约束

- “废弃”已由用户确定为直接执行、不弹确认；不能自行改成保存或新增确认交互。
- 只允许删除按钮绑定的当前录音，不能按目录里最新文件、时间前缀或文件名推测目标。
- 保留随机一次性 session 标识、不可变 PendingIntent、包名限定和原生非导出接收器。
- 暂停/继续沿用同一 session；结束后失效；旧通知、重复点击、保存中的请求必须被拒绝。
- 原生取消入口负责数据库和媒体文件一致性，不用宿主文件删除、清库或强停代替。
- 更新、强停 App 或重启前，确认没有正在录音或待保存的数据。已有录音由用户先保存并结束。
- 真机删除测试只对用户授权的新建测试录音执行。记录测试前文件清单，验证测试文件消失、服务和麦克风停止、历史清单不变；不要默认获得了删除历史录音的授权。
- 代码中的成功请求日志不等于文件已删除；报告必须区分请求发出、原生处理及最终结果。

## 构建与测试

项目尚无 Gradle Wrapper。已验证：JDK 21、Gradle 9.5.1、AGP 9.2.1、SDK 37 / Build Tools 37.0.0；源/目标 Java 17。

```powershell
gradle --gradle-user-home .gradle-home :app:assembleDebug :app:gestureSelfTest :app:discardSelfTest :app:lintDebug
```

- 使用项目内 `.gradle-home`，不读取或修改用户全局 Gradle 配置，不输出其中的凭证。
- libxposed API 是 compileOnly；用户已授权 service:102.0.0 运行时配置通信库及 interface 传递依赖。保留 `kotlin.stdlib.default.dependency=false`，不要额外引入 UI 框架；第三方许可随源码和 APK 保存。
- APK：`app/build/outputs/apk/debug/app-debug.apk`；Lint：`app/build/reports/lint-results-debug.html`。
- 手势改动跑 `gestureSelfTest`；废弃/session 改动跑 `discardSelfTest`；交付代码更新前构建并跑 Lint。不要用跳过 Lint、全局关闭错误来处理失败。
- 有限局部 Lint 抑制必须解释真实运行环境，例如震动调用发生在 system_server，权限不由模块 APK 声明提供。
- 通知 dump 可运行 `python tests/verify_recording_notification.py work/notification-dump.txt`。它不检查按钮名称、按钮行为或文件删除，不能替代实机验收。
- 显示开关改动运行 `python tests/verify_display_settings.py <隐藏快照> <显示快照>`；验证渠道副本双向切换，不能仅验证隐藏成功。配置页使用 Android 原生控件，保存失败/连接失败必须有明确状态，不输出假成功。
- 桌面入口改动运行 `python tests/verify_launcher_entry.py`，并验证隐藏后仍能打开设置和恢复图标。热刷新实测应在同一段录音中切换，核对 PID、ServiceRecord 和麦克风持续运行，不可停止后重新开始冒充热生效。
- 增量 APK 异常膨胀或依赖变更后，检查 runtimeClasspath，必要时清理本任务构建产物后重建。不要无故反复清理。
- 修改交付行为时更新 versionCode/versionName 和 README；纯文档修改不发新版本、不安装手机。

## 手机操作与验证

- 使用 android-adb-debug 技能，APK 分析使用 apk-reverse。先 `adb devices -l`，确认唯一目标；所有命令显式指定 `-s <serial>`，不硬编码之前的设备序列号。
- 查询当前 Android 用户，操作当前任务的用户与包。分身用户出现未激活提示不代表机主用户失败，不顺带启用分身。
- LSPosed CLI 使用前读取设备上的帮助；已有可用开发者 CLI 时优先使用。配置后回读 enabled、实际 scope，不能只看 APK 的 declaredScope。
- 不修改 LSPosed 数据库、SELinux、全局安全设置，也不卸载或清除录音 App 数据来解决问题。
- 已授权的安装与同范围更新可继续，无需重复确认；重启/更新时仍需检查当前录音，避免丢失数据。
- 系统 Hook/作用域更新需要重启；只改 App Hook 可在保存结束后重启 App。禁用热重载时不能假定 install -r 就已替换现有进程的 Hook。
- UI 操作遵循先观察、再定位、执行后验证。未看到按钮时不要盲点旧坐标。
- read-only 日志和设备检查通常设置 15 秒超时；UI dump 可至 30 秒。保存证据到 `work/`，只报告目标应用相关片段。
- 不清空 logcat。开机日志丢失时读取 LSPosed 日志；“enabled=true”只证明配置，不证明注入。
- 原始日志、截图及 dumpsys 可能含其他 App 通知、录音转写或私人文件名；避免全量输出、提交或对外共享。

## Windows 执行约定

- Git Bash 中的 ADB 命令设置 `MSYS_NO_PATHCONV=1`，防止设备路径被转换为 Windows 路径。
- 用 `adb pull` 拉取 APK、DEX、JAR 等二进制；不要经 `adb shell cat` 文本输出保存二进制。
- 不跨 shell 组合删除或移动命令。只清理本次任务创建且路径已核对的临时文件。

## 文档与交付

- README 以当前行为为准，版本历史集中放一处，清理过期或互相矛盾的验证结论。
- 明确标注：代码/构建检查、系统属性检查、用户实机确认、尚未验证；不要把自检通过写成全部真机功能已验证。
- 已确认：锁屏三按录音、黑屏震动、红色胶囊隐藏、系统静默通知隐藏普通图标、下拉通知保留、录音中废弃删除。
- 未单独确认：暂停后真机废弃、保存/废弃竞态、首次解锁前、不同 ROM、分身/工作资料、所有锁屏视觉模式。
- 1.1.0 已实测配置保存和回读；ADB 注入两下音量下触发长震动并保持黑屏，三下音量下触发亮屏，显示开关两态通知属性正确。物理按键组合不能仅凭注入测试宣称全部验证。
- 1.2.0 已验证桌面 alias 隐藏/恢复和独立设置入口，旧代码/已就绪版本提示，同一段录音中双向切换显示且 PID、ServiceRecord 不变。
- `work/`、构建目录、Gradle 缓存、密钥及本地配置按 .gitignore 保持未跟踪，不提交目标 App 的 APK 或反编译源码。
- 完成后用中文说明改动、检查结果、未验证项和实际风险。不要因文档任务安装模块或改变手机设置。
- 隐私圆点改动必须回归录音中开关双向切换及停止后的状态清理；`verify_privacy_idle.py` 检查 Idle、persistent=false 及容器无残留。1.3.1 已在实机通过，旧故障快照会失败；原 verify_privacy_dot.py 仅适用于1.3.0的父容器隐藏实现。
