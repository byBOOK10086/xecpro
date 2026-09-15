# 模块页改版：回到上游结构 + 霓虹皮肤 + 长按弹出操作面板

日期：2026-09-15
状态：待评审（评审通过前不写任何实现代码）

## 1. 目标

把管理器「模块」页改成设计稿 `52546940-2756-44d7-acee-c9cb3a4c0500_1.html` 的样子：

1. 丢掉 XEC 自研的「左滑露出圆形操作按钮」那套交互，卡片结构回到上游 KernelSU 原版；
2. 卡片换成设计稿的霓虹紫/青暗色玻璃风；
3. **长按卡片 1 秒**弹出底部操作面板，面板里放 `Action` / `WebUI` / `Update` / `Uninstall` 四枚按钮。

## 2. 现状

| 来源 | 结构 |
| --- | --- |
| 上游 KernelSU（`ModuleMaterial.kt`，已在本仓库 `f6a0f14` 删除） | 卡片：名称/版本/作者 + 右侧启用开关 → 描述（最多 4 行可展开）→ META 徽标 → 分隔线 → 底部一排内联按钮（操作 / 打开 / 更新 / 卸载）。卡片整体在模块含 WebUI 时可点，点击打开 WebUI。长按「操作」「打开」按钮可创建桌面快捷方式。 |
| 当前工作区（`ModuleMiuix.kt`） | 卡片：名称 + META + 版本 + 作者 + 描述 + 更新药丸。**操作按钮全部藏在左滑露出的右侧竖排圆形按钮里**（`SlideActionButton`），`detectHorizontalDragGestures` 驱动 `offsetX`。无长按能力。 |
| 设计稿 | 霓虹卡片，**长按 1 秒**底部进度条走满 → 弹出操作按钮。卡片本体不含任何按钮。 |

关键结论：设计稿的「卡片本体不承载操作按钮」与上游「按钮内联在卡片里」是互斥的，本方案选择设计稿的形态，把上游的按钮行整体搬进长按面板。

## 3. 已确认的决策

| # | 决策 | 结论 |
| --- | --- | --- |
| D1 | 长按 1 秒后四枚按钮的出现形式 | **底部弹出操作面板**（不跳详情页） |
| D2 | 「恢复到原版 KSU」的口径 | **回到上游卡片式结构**（名称/版本/作者/开关/描述/META），按钮行从卡片内移入长按面板 |
| D3 | 霓虹紫青配色范围 | **仅模块页**；其他页面继续用现有 XEC Fluid Glass（墨绿灰 `#0B0F10` + teal `#12B886`） |
| D4 | 现有「左滑露出操作栏」手势 | **直接移除** |

## 4. 待你确认的默认取值

下列三点在提问时被跳过，我按「最贴合设计稿 + 零功能损失」取了默认值，实现前你可以否决任何一条：

| # | 项 | 默认取值 | 理由 |
| --- | --- | --- | --- |
| A1 | 卡片短按 | 模块含 WebUI 时打开 WebUI，否则无响应 | 与上游 `ModuleMaterial.kt` 完全一致；不新增第二条到 WebUI 的路径语义。 |
| A2 | 上游「长按按钮 → 创建桌面快捷方式」 | 保留：面板里 Action/WebUI 按钮长按即创建快捷方式 | 零功能损失，`ModuleShortcutState` 与 `ModuleShortcutDialog` 原样复用。 |
| A3 | 面板内容 | 只放「模块名 + 版本号」标题与四枚按钮，严格对齐设计稿 | 避免面板膨胀；模块介绍在卡片上已可展开阅读。 |

## 5. 视觉规格

### 5.1 霓虹色板

新增 `ui/design/token/XcNeon.kt`，导出 `XcNeon`。**不进 `XcTheme` 全局下发**，模块页内自行按 `isInDarkTheme()` 取值，保证只有本页变色。

| 语义 | 暗色档 | 浅色档 |
| --- | --- | --- |
| `cardBg` | `#14142D` @ 0.60 | `#FFFFFF` @ 0.92 |
| `cardBorder` | `#8B5CF6` @ 0.20 | `#6D28D9` @ 0.22 |
| `cardBorderPressed` | `#8B5CF6` @ 0.65 | `#6D28D9` @ 0.65 |
| `accentPurple` | `#8B5CF6` | `#6D28D9` |
| `accentCyan` | `#06B6D4` | `#0E7490` |
| `danger` | `#EF4444` | `#DC2626` |
| `textMain` | `#E0E7FF` | `#1E1B4B` |
| `textSub` | `#94A3B8` | `#5B6478` |
| `gradient` | `linear(#8B5CF6 → #06B6D4)` | `linear(#6D28D9 → #0E7490)` |
| `glow` | `#8B5CF6` @ 0.35 | `#6D28D9` @ 0.28 |

浅色档不是可选项：设计稿只有暗色稿，而管理器支持浅色主题，纯霓虹紫压在近白底上对比度不足，因此浅色档统一把紫/青各压深一档。

### 5.2 页面光晕

设计稿页面底有两团 radial-gradient（左上紫、右下青）。**不改全局背景**（`BgEffectBackground` 与用户背景图是共享的），改为在模块页列表容器上叠一层本页专属的 `drawBehind`：

- 左上：`radialGradient(center = (0.05w, 0.05h), radius = 0.6w)`，紫 @ 0.10 → 透明
- 右下：`radialGradient(center = (1.0w, 0.95h), radius = 0.6w)`，青 @ 0.08 → 透明

### 5.3 卡片

| 项 | 取值 |
| --- | --- |
| 圆角 | 12dp（`Xc.shapes.sm`） |
| 外边距 | 水平 16dp、底部 20dp（原 16dp） |
| 内边距 | 16dp |
| 底 / 描边 | `cardBg` / 1dp `cardBorder` |
| 模块名 | 17sp SemiBold `textMain` |
| 版本、作者 | 12sp Medium `textSub` |
| 描述 | 14sp `textSub`，默认最多 4 行，点击整段展开/收起（沿用上游 `onTextLayout` + `hasVisualOverflow` 判定） |
| META 徽标 | 底 `#8B5CF6` @ 0.18、字 `#C4B5FD`、圆角 8dp，位置与上游一致 |
| 开关 | 沿用 Miuix `Switch`，位置在卡片右上 |
| 待移除态 | 名称/版本/作者/描述加删除线，与上游一致 |

### 5.4 长按进度条

- 高 3dp，贴在卡片底部内沿，下两角与卡片同圆角
- 填充 `linear(#8B5CF6 → #06B6D4)`
- 宽度 0 → 100%，`tween(1000ms, LinearEasing)`；取消时 `tween(120ms)` 回 0
- 仅在按压中出现，非按压态完全不绘制

### 5.5 按压态

按压期间同时叠加：

1. 缩放 0.98
2. 描边转 `cardBorderPressed`
3. 外辉光：Compose 没有 `box-shadow`，用 `drawBehind` 叠 3 层递减 alpha（0.16 / 0.10 / 0.05）向外扩 6 / 12 / 18dp 的圆角描边近似。只在按压的约 1 秒内绘制，开销可接受。

### 5.6 底部操作面板

新增 `ui/screen/module/ModuleActionSheet.kt`，**不复用共享 `XGlassDialog`**（它是居中弹窗，动画与定位都不匹配底部面板）。面板自己实现：

| 项 | 取值 |
| --- | --- |
| 遮罩 | `Xc.colors.backdropScrim`，点击关闭 |
| 定位 | 底部对齐，左右各留 12dp，底部避开手势条（`WindowInsets.safeDrawing`） |
| 容器 | `XGlassSurface(backdrop, tint = 霓虹面板色, rimColor = 紫描边, shape = Xc.shapes.lg)`，内部再叠一层深层紫半透明，保证无模糊设备上回退时仍是霓虹色而不是默认墨绿 |
| 入场 / 退场 | `slideInVertically(initialOffsetY = { it }) + fadeIn` / `slideOutVertically + fadeOut`，180ms |
| 返回键 | `NavigationBackHandler` 接管，返回 = 关闭面板 |
| 拖柄 | 40dp × 4dp，居中，`textSub` @ 0.4 |
| 标题 | 模块名 17sp SemiBold `textMain`；版本号 12sp `textSub` |
| 按钮排布 | `Row`，`spacedBy(12.dp)`，每枚 `weight(1f)` |
| 按钮外观 | 圆角 12dp、底 `cardBg`、1dp `cardBorder`、纵向内边距 14dp、图标 20dp、文字 11sp、图标在上文字在下 |
| 图标着色 | Action / WebUI / Update 用 `accentPurple`；Uninstall 用 `danger` |
| 置灰 | `Modifier.alpha(0.35f)` + 图标与文字色向灰降饱和（`Color.lerp(c, Color.Gray, 0.6f)`），并且不注册点击 |

**图标选型**（实现时需确认存在于当前依赖的 icon 集合，缺失则回退）。`androidx-compose-material-icons-extended` 已在 `manager/gradle/libs.versions.toml` 声明并随 compose-bom 2026.08.00 解析，但本机 `~/.gradle/caches` 为空，无法离线核实具体图标名，故下表保持「首选 + 回退」双列，实现时以编译器报错为准：

| 按钮 | 首选 | 回退 |
| --- | --- | --- |
| Action | `Icons.Rounded.Terminal` | `Icons.Rounded.PlayArrow`（现用） |
| WebUI | `Icons.Rounded.Language` | `Icons.Rounded.Code`（现用） |
| Update | `MiuixIcons.UploadCloud`（现用） | — |
| Uninstall | `MiuixIcons.Delete` / `MiuixIcons.Undo`（现用） | — |

## 6. 交互规格

### 6.1 手势状态机

短按与长按必须写在**同一个 `pointerInput`** 里，否则两个手势识别器会互相竞争。

```
awaitEachGesture {
    down = awaitFirstDown(requireUnconsumed = false)   // 不立刻消费，列表照常能滚
    progress.snapTo(0f)
    launch { progress.animateTo(1f, tween(1000, LinearEasing)) }
    loop {
        event = awaitPointerEvent()
        if (位移 > 10.dp) → 取消
        if (!pressed)     → 抬起：若已跑满 1000ms 则视为长按，否则视为短按
    }
}
```

- **取消**（位移超 10dp / 提前抬起）：`progress.animateTo(0f, tween(120))`，不触发任何动作，纵向滚动不受影响。
- **长按触发**：进度跑满 1000ms 且未取消 → `HapticFeedbackType.LongPress`（等价于设计稿的 `navigator.vibrate(30)`）+ 打开面板 + 消费该次 up 事件，避免紧接着误触发短按。
- **短按**：1000ms 内抬起且位移 < 10dp → 按 A1 处理（含 WebUI 则打开 WebUI）。

震动反馈在本工程属于**首次引入**：全 manager 目录此前没有任何 `performHapticFeedback` / `HapticFeedbackType` 调用。本次采用 Compose 侧的 `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`（对应设计稿的 `navigator.vibrate(30)`），不用 `LocalView` + `HapticFeedbackConstants`，便于在无震动硬件的设备上静默降级。

### 6.2 按钮可用性

| 按钮 | 可用条件 | 置灰表现 |
| --- | --- | --- |
| Action | `module.hasActionScript && module.enabled && !module.remove` | 置灰 |
| WebUI | `module.hasWebUi && module.enabled && !module.remove` | 置灰 |
| Update | `updateUrl.isNotEmpty() && !module.remove` | 置灰 |
| Uninstall | 恒可用 | 不置灰；`module.remove` 为真时文案与图标切换为「撤销」 |

Action / WebUI 带上 `module.enabled` 这一条是沿用上游 `actionButtonsEnabled` 的语义：模块被禁用时 action.sh 与 WebUI 本身就跑不起来。

### 6.3 动作接线

面板不新增任何业务逻辑，全部转发到既有 `ModuleActions`：

| 按钮 | 回调 |
| --- | --- |
| Action | `actions.onExecuteModuleAction(module)` |
| WebUI | `actions.onOpenWebUi(module)` |
| Update | `actions.onRequestUpdateConfirmation(module, updateInfo)` |
| Uninstall | `module.remove` ? `actions.onUndoUninstallModule(module)` : `actions.onRequestUninstallConfirmation(module)` |

点击后**先关闭面板再执行动作**，避免确认框叠在面板上。

### 6.4 面板状态存放

面板只持有一个 `moduleId: String?`（`rememberSaveable`），显示时从 `uiState.moduleList` 反查 `Module`。不持有 `Module` 对象本身，避免刷新后拿到过期数据。

## 7. 改动清单

| 文件 | 改动 |
| --- | --- |
| `ui/design/token/XcNeon.kt` | **新增**。霓虹色板，仅模块页取用。 |
| `ui/screen/module/ModuleActionSheet.kt` | **新增**。底部操作面板（含遮罩、滑入动画、返回键接管、四枚按钮）。 |
| `ui/screen/module/ModuleMiuix.kt` | **重写** `ModuleList`(698-765) 与 `ModuleItem`(767-962)；**删除** `SlideActionButton`(964-995) 与 `detectHorizontalDragGestures` 相关逻辑；`ModulePagerMiuix` 增加 `pressedModuleId` 状态、页面光晕、面板挂载点。 |
| `ui/screen/module/ModuleUiState.kt` | 不改。 |
| `ui/screen/module/ModuleScreen.kt` | 不改。 |
| `ui/viewmodel/ModuleViewModel.kt` | 不改。 |
| `res/values/strings.xml` 及 15 个语言档 | 不改。按钮文案复用既有 `R.string.action` / `open` / `module_update` / `uninstall` / `undo`。 |

按钮文案有一处小分歧：设计稿写的是 `WebUi`，项目既有字符串是 `Open`。默认复用 `Open` 以保住 15 个语言档的既有翻译；若要求严格显示 `WebUi`，需要新增一条字符串并补 15 份翻译。

`ModuleMiuix.kt` 重写后需一并清理的 import（删掉左滑逻辑与卡片内联按钮行后，这些符号不再被引用，留着会触发未使用告警）：`animateContentSize`、`animate`、`detectHorizontalDragGestures`、`CircleShape`、`SubcomposeLayout`、`Constraints`、`IntOffset`、`Icons.Rounded.PowerSettingsNew`、`Icons.Rounded.PlayArrow`、`Icons.Rounded.Code`；`Modifier.xWaterDropClick` 为**疑似**失效项——它现在只服务于更新药丸与 `SlideActionButton`，两处都会删掉，但若卡片按压态决定复用 `xDropletPressScale` 则会保留。实现时以编译器告警逐条确认，不做超前删除。

## 8. 明确不做

- 不改任何全局设计令牌（`XcColors` / `XcShapes` / `XcTheme`），其他页面视觉零变化。
- 不改页面底色、背景图、全局光效（`BgEffectBackground`）。
- 不改底部导航栏、搜索栏（`SearchBarFake` / `SearchBox` / `SearchPager` 是共享组件）。
- 不改顶栏与安装 FAB 的既有 XEC 样式。模块页内会并存「霓虹卡片」与「墨绿顶栏/FAB」，这是 D3「仅模块页换肤」的必然结果；若你要顶栏标题也走紫青渐变、FAB 也换霓虹渐变，告诉我，这两处都在 `ModuleMiuix.kt` 内，改动很小。
- 不做模块详情页（设计稿的 `#page-module-detail`）——D1 已选底部面板方案。
- 不改 `ModuleRepo`（模块仓库）相关页面。
- 不改模块仓库页、不改 `ModuleShortcutState` / `ModuleShortcutDialog`（A2 复用）。

## 9. 风险与验证

| 风险 | 处理 |
| --- | --- |
| 长按与 `LazyColumn` 纵向滚动抢手势 | 位移阈值 10dp 内不消费事件；超过阈值立即取消长按，滚动交由列表处理。 |
| 短按与长按两个识别器竞争 | 强制写在同一个 `pointerInput` 内，不叠加 `detectTapGestures`。 |
| 长按跑满后抬起事件继续下传，误触发短按 | 触发长按时消费该次 up，并置一个已触发标记让短按分支跳过。 |
| 无模糊设备上面板回退成墨绿 | 面板内部叠一层霓虹半透明底层，保证回退时仍是紫调。 |
| 外辉光 3 层描边的绘制开销 | 仅在按压的约 1 秒内绘制；若低端机掉帧，降为 2 层或去掉最外层。 |
| 浅色主题下霓虹不可读 | 提供浅色档色板（见 5.1），不直接复用暗色档取值。 |

验证方式：

1. `./gradlew :manager:app:assembleDebug`（或仓库等价的 manager 构建任务）编译通过；
2. 装机后逐项走查：长按 1s 弹面板 + 震动、中途松手进度条回退、纵向滑动列表仍可滚、面板四按钮置灰规则、点击后先关面板再出确认框、模块禁用时 Action/WebUI 置灰、待移除模块 Uninstall 变「撤销」、浅色/深色/AMOLED 三档观感；
3. 切到其他页面确认视觉零变化。
