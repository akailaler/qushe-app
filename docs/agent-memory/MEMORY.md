# 项目长期记忆（小工具工作区）

> 每日进展写 `YYYY-MM-DD.md`；这里只放长期有效的东西，一条一行。**本文件有注入上限，要加内容先压旧的。**

## 本机环境

- Win11/AMD64。GitHub release 下载被代理拦（502）→ 前缀 `https://ghfast.top/`，镜像必须用官方 sha256 校验。
- **系统原先没装 Git**（只有工具链里的 PortableGit，用户终端用不了）。已把 PortableGit 2.55 复制到 `C:\Users\<用户名>\Git`（含 GCM + bash）并加进**用户 PATH** —— 免管理员，`winget install Git.Git` 会卡在 github 下载。
- **网络实测**：`github.com` 直连被代理拒（CONNECT 502），`api.github.com` 200，`ghfast.top` 只做只读加速。
- **★ 推送方案**（跑通）：用户装了 **GitHub Desktop**，凭据在 Windows Credential Manager，GCM 直接能用。`remote.origin` 拆两条：
  - fetch `https://ghfast.top/https://github.com/akailaler/qushe-app.git`（镜像只读）
  - push `https://github.com:443/akailaler/qushe-app.git`（**带端口**不匹配全局 `insteadOf` 前缀 `https://github.com/`，所以不会被改写；GCM 按 hostname 认 github.com）
  Bash 工具环境自带 `https_proxy=127.0.0.1:64311`（会拒 github.com），push 要 `env -u https_proxy -u HTTPS_PROXY -u http_proxy -u HTTP_PROXY git push`。本地分支已改名 `main`，upstream = `origin/main`。
- **直连 github.com 会间歇性断**（`Connection reset` / 21s 超时，但 api.github.com 正常）。兜底：`git push https://x-access-token:<token>@ghfast.top/https://github.com/<owner>/<repo>.git main`（token 用 `git credential fill` 取，只走命令行、不写 remote、输出前替换成 `***`）。
- Bash 工具 PATH 缺 coreutils：先 `export PATH="/c/Users/<用户名>/.workbuddy/binaries/PortableGit/versions/1.3.0/usr/bin:$PATH"`。
- **Git 身份** = `40493403+akailaler@users.noreply.github.com`（GitHub 新式 noreply，带 id 才关联账号）。2026-10-22 用 `filter-branch` 重写过全部历史清掉旧 QQ 邮箱（89 提交约 12 分钟，元数据 + 文件内容各一遍，之后 `push --force` main/tags/backup）。**`git add -A` 会把仓库根的 `.git-rewrite/` 吞进提交**（已 gitignore）。
- Git Bash 会把 python 正则 `\d` 转成 `/d`（静默失配）→ 写 `[0-9]`；`/tmp` 映射 Windows Temp → 用 `cygpath -w`；脚本用 `python - << 'PYEOF'`。
- PowerShell 捕获 Python 子进程 stdout 会丢内容 → 重定向到文件再读。
- managed Python 3.13 无 tkinter（GUI 用系统 3.12）；隔离环境 `binaries/python/envs/default`。
- adb 真机（小米 2304FPN6DC / HyperOS 3.0.308.0）：`cmd power wakeup` + `wm dismiss-keyguard` 唤醒；`input` 注入有效；`input motionevent` 支持多指。
- 分析录屏：ffmpeg。先 `fps=1,scale=200:-1,tile=6x4` 拼网格，再 `-ss T -frames:v 1` 抽原始帧看细节。

## 「取舍」Android（主线）

工程 `qushe-android/`，JDK17 + Gradle 8.9 + SDK 35，无 Android Studio，命令行构建 + 小米真机验证。当前 **1.4.0**（versionCode 148）。

```bash
export JAVA_HOME="C:\Users\<用户名>\.workbuddy\binaries\jdk17\jdk-17.0.20.1+1"
"C:/Users/<用户名>/.workbuddy/binaries/gradle-8.9/bin/gradle" assembleDebug lintDebug
```

- **改完先跑 `python verify.py`**（退出码 0/1）：编码自证 / 版本号四处一致 / 收藏同步逐条比对 / 回收站记账 / **顶层函数重名** / 历史残留 / 构建产物。
- **换机器**：`git clone` → `python setup.py`（恢复记忆 + 找 JDK/SDK + 写 `local.properties`）。离开前 commit+push，回来先 pull。
- 备份 `D:/backup/qushe-android.git`（remote `backup`，`post-commit` 自动 push）；记忆经 `pre-commit` 镜像到 `docs/agent-memory/`。异地远端未配。
- 稳定 tag **`v1.3.54`**（用户评价"比较完美"）。构建后 `app-debug.apk` 复制为 `qushe-vX.Y-debug.apk`；改版本号别漏 `SettingsScreen.kt` 硬编码字符串。
- **包体积先看构成**：debug 32.6MB 里 dex 占 99% ⇒ 删 ABI / 压资源全是无效功，**只有 R8 有效**（`isMinifyEnabled + isShrinkResources` + debug 签名 ⇒ 3.7MB）。release 不可 `run-as`，收藏核对用 debug 包。
- **UI 副标题只写"影响什么"，不解释"为什么"**（为什么写注释）。
- **颜色一律走 `QusheColors.palette`**：`val Ink: Color get() = QusheColors.palette.ink`，
  **不要再加 `val X = Color(...)` 这种常量**——浅色主题下它不会变。
  两套调色板在 `ui/theme/Theme.kt`，默认 `THEME_DARK`，设置在「外观」。
  颜色会在 `Canvas`/`drawBehind` 里被读，所以不能用 `@Composable get()`。大范围编辑前 `git status` 确认干净；改坏 `git checkout -- <file>`。
- **README 只写当前状态** + 最近 ~30 个版本的一句话（表格）；原理和踩坑留在代码注释与 `docs/agent-memory/`。1.3.87 把 3254 行 / 115 个版本的更新日志压成表格（3706 → 390 行），**别再往 README 堆历史**。
- **发版**：改版本号 → `gradle assembleRelease` → **先 commit** → `python release.py` —— 自动打 tag、建 GitHub Release、传 APK、把 SHA-256 写进说明（幂等）。**顺序不能反**：tag 打在 HEAD 上，先发版后提交会让 tag 指向上一版代码（踩过一次）；脚本已加 `assert_releasable` 拦这个。仓库 `github.com/akailaler/qushe-app`，下载页 `/releases/latest`；凭据用 `git credential fill` 取 GitHub Desktop 那份。**1.3.89 起是正式签名**：`keystore/` + `keystore.properties` 不进仓库，备份在 `D:\backup\qushe-signing\`，丢了就再也发不了覆盖升级；没有它构建会退回 debug 签名（能出包、装不到老用户）。**不用 Actions**：要发真机验过的包，本机构建 40 秒。

## 收藏同步到系统相册（已定论）

**同步 100% 正确，小米相册也读这一列，只是刷新极慢。**

- 实测取舍收藏夹 286 = 系统 `is_favorite=1` 286（逐条 0 差异）；小米相册 256→270→273→**重启相册后正常**。
- **"看不到" ≠ "没同步"**：必须重启手机或重开相册。提示文案已写"系统相册可能需要重启或等一会儿"。
- 小米相册**双向读写** `is_favorite`（手动收藏视频 → 系统 +1）⇒ 「同步系统收藏到这里」是有效功能。
- **唯一缺口**：在取舍里取消收藏，系统不跟着取消（同步只做加法）。用户要求"完全对齐"但先搁置，等他确认。
- 这台小米上 `createFavoriteRequest` **静默授权、不弹框**；`FAVORITE_BATCH = 20` 保留（别的 ROM 会弹）。
- **★ 不是所有 ROM 都支持**：红米 K20 Pro / MIUI 12.5.6（Android 11）的相册**不写** MediaStore 的 `is_favorite` —— 反向读到 0 条、正向写进去相册也不显示（但读回校验能过，UI 会显示"已同步"）。HyperOS 3.0 那台正常。验证方法：相册里手动收藏一张 → 点「同步系统收藏到这里」，提示"没有读到任何收藏"即证实。
- 教训：① **核对脚本先自证**（storeKey 解码写反 ⇒ 假结论"0 条对上"，白折腾五轮）；② **不同写入路径不等价**（`content update` ≠ `createFavoriteRequest`），平台能力先 `content query` 实测；③ **★★★ 别用"平台应该怎样"推断"用户看到了什么"**（犯过两次）；④ **结论没坐实不给方案**。

## Compose：绘制 / 层级

- **★ `background` / `clip` 只影响它之后的那截链**：背景跟 alpha 要写在 `graphicsLayer{alpha}` 之后；跟圆角要写在 `clip` 之后。写前面 = 背景在 alpha 层外 / 圆角外留方角。
- **★ 多层垫底时变换必须挂在"包住全部层"的那层**，否则其他层不跟着动（缩放露底 / 拖动留残影）。
- **"alpha=0" ≠ 看不见**：占着节点就会露。要真消失让节点不进组合（`if(cond)`，cond 用 `derivedStateOf`）。
- **"内容为空" ≠ 不画**：带 `background`/`padding` 的容器留空壳。该藏就 `return`/`if`。
- **"闪一下"分两种**：闪黑 = 那帧没内容（占位没接上）；闪糊 = 占位太糊、清晰度跳变。用户报哪个词查哪层。
- **不透明 ≠ 挡触摸**：全屏覆盖层要 `pointerInput` consume；深色半透明底压黑界面等于不存在（要可见用浅色圆 + 淡边）。
- **★ 装饰性效果不要动布局基准**：为圆角缩 `aspectRatio` 会改变整条链尺寸基准 ⇒ "图片自动放大"。正解 = 在图之上、变换层之内画四条角路径（角上方块 − 四分之一圆，填页面底色），只画四角。
- **"调好的数字"提出来共用**（`rememberContentCorner()`、`rememberPageGap()`），但**共用前分清场景**。圆角全 App 唯一：格子 `clip`，大图用四角遮罩；首页卡片/胶片条不参与。
- **★ "存在的函数说未解析"几乎总是括号问题**：少一个收尾括号会把后面所有顶层定义算进函数体。先看顶层函数列表，再看编译错误。
- **重排大文件用"显式行段"**，别按缩进猜顶层语句。先干跑打印每行、确认边界落在空行。
- **`Column + verticalScroll` 一次性组合全部内容**；重页面用 `LazyColumn`。`LazyColumn` 各 `item` 不共享作用域，块内局部 `val` 要提到外面。
- **★ 同一件事有两个"真源"，只改一个 = 半生效**：底栏读 `tab`、内容读 `pager`，`BackHandler` 只改前者 ⇒ 内容没回。有 `goToX()` 这类函数就统一走它。
- **★ 优化手段本身可能成为新的卡顿源**：`beyondViewportPageCount` 把成本挪到空闲，但启动同时组合多页各自查 MediaStore ⇒ 把省下的吃回去。**正解是把重页面做轻。**
- **信息胶囊 vs 详情面板**（用户定，全 App 统一）：胶囊只有一行 ⇒ `日期 · 文件夹 · 大小`；分辨率/时长/地点/EXIF 进详情面板。三处共用 `InfoPill` + `PhotoDetailContent`。信息入口居中，操作按钮靠边。
- **拍摄时间 ≠ 添加时间**：截图/下载文件的 EXIF 与 `DATE_ADDED` 可能差几年。`dateAddedMillis` 源单位是秒，进模型转毫秒；两者相同只显示一行。
- **时间格式两个独立开关**（相对/具体），四种组合都合法（都不显示时 `InfoPill.primary` 传 null）。`infoPillPrimary/Secondary` 是唯一来源。
- **"相对时间"函数只输出相对时间**（`relativeDayLabel` 曾拼上日期，那是调用方的事）。
- **★ 垫底"下一张"别用小阈值淡入**：alpha 绑翻页判定线 18%（72dp）⇒ 手指走十几像素下一张就糊上来。正解 = 停在旁边一整屏 + 同速移动（`translationX = dx - dir*屏宽`）。
- **★ 增量式 `snapTo` 会丢帧**：`snapTo(current+delta)` 读挂起写出的值、每帧还 launch，后一次取消前一次就丢增量 ⇒ "拖一点点顿住"。**手势内本地累加、每次写绝对值**，起点用当前 Animatable 值。
- **轴锁定顺带简化判定**：按主方向定死轴 ⇒ 不会"斜着划同时命中翻页和删除"。
- **★★★★ `drawnSizePx` 的 Fit 曾把宽高写反**（容器 1080×2400、图 828×30656 算成 2400×64.8）。`panLimit`/`snapScale` 全靠它。修好后**长图必须传 `contentRatio`** —— 旧结论"长图不传"是建立在 bug 上的。
- **`effectiveMax = max(coverScale, maxScale)` 不能 min**：min 等于把"铺满"当上限（828 宽要 16.6 倍，被 8 倍截断 ⇒ 看不清）。`snapScale` = 铺满所需的最小倍率（回弹下限）。
- **放大态下限是"铺满"**：scale < cover 时某轴露底 ⇒ 松手弹到 cover，offset 按 `target/scale` 等比放大。
- **★ 一个参数往往同时管好几件事**：`contentRatio` 同时管 `snapScale` 和 `decodeSize`；为放开上限删掉它 ⇒ 解码退化成容器尺寸 ⇒ 放大必糊。正解：拆开、另加 `sourceSize`。**改"看起来只管一件事"的参数前先 grep 谁在读。**
- **角标优先级：截屏 > 长图**（用户定）。
- **同一组件多个调用点传参不一致时，先看哪个带注释解释**（1.3.77 瀑布流漏了 `contentRatio = null`）。
- **单击+双击共存交给 `detectTapsWithoutConsuming`**；但单击切 chrome 别走 `onSingleTap`（延后 280ms 手感钝），放父层"没拖过"分支。
- **状态机不能用"投影"当指针**：三态里①③在某字段上取值相同 ⇒ 永远回不到①。必须自己记 `stage`。
- **两个 `weight(1f)` 不等于居中**：两侧宽度不等就偏。要真居中用整屏 `Box` + `align(Center)`。
- **"随心所欲/不像 pager"多半是 rotation**（`rotationZ=(offsetX/80)`）⇒ 不按轨迹。
- **★ 淡出和位移绑同层 = 退场卡一下**：alpha 按 `offset/阈值` 算，退场要把 offset 涨到屏高×1.15 ⇒ 前 20% 就淡没。**拆层**：黑底只淡出 / 内容只位移 / chrome 只淡出，且 chrome 画在内容之后。
- **★ 组件已内聚的行为，调用方原样传参**：`markedKeys→removedFlags→宽度收 0` 已实现"飞出去 + 左移"，调用方再 `filterNot` 就是换机制 ⇒ 整排重排。
- **★ 同一个动作有两条路 ⇒ 净效果为零**（chrome 显隐既有外层 clickable 又有 onSingleTap ⇒ 切两次抵消）。**先数这条动作有几条路径。**
- **★ 父节点抢手势，子节点"放大后拖动"就废了**：放大态父节点一个事件都不消费。
- **★★ 一个 state 多写者时，每个写者只写自己"拥有的那次变化"**：`chromeVisible` 既有 toggle 又有赋值 ⇒ 切掉→写回→再切，"只收不回来"。判 `if (isZoomed != wasZoomed)` 才写。
- **状态变化要报"跨越的那一刻"**，不是"手势结束"；只在跨越时报。toggle 还是强制赋值看意图（缩回 1x 用强制显示）。
- **★★★★ SharedPreferences 读不是 Compose 状态**：直接读 `prefs.xxx` ⇒ 设置改完不重组。**UI 要跟随的设置由 `AppRoot` 持有 state 往下传**（仓库数据同理：toggle 后自增 tick）。
- **★★★★ `LaunchedEffect` 的 effect 体里绝不修改自己的 key**：改 x 那下键就变 ⇒ 协程取消重启 ⇒ `launch()` 来不及执行，表现"点了没反应"。要改内容另加只当 key 的 `tick`。
- **★★★★ 动手前先 grep 有没有现成实现**：`allSystemFavoriteKeys()` 本就在，我又写 `allFavoriteKeys()` 且奇偶写反 ⇒ "同步完全没用"。**近名重复函数是本工程反复出问题的根源**（`SegChip` 也是）。`verify.py` 查顶层重名。
- **★★★ 同步/记账类别自己记"已同步"，去问对方**：每次读对方当前状态算差集。记账字段必须区分「谁写的」「什么时候写的」。
- **★★ 单向同步绝不做自动"删除对方"**：只做加法，减法只由用户触发。
- **★ Box 子节点默认堆在左上角**：`Row{A;B}` 改 `Box` 时要自己套 Row。
- **★ 局部 lambda 要用 `align` 就得把签名改成 Scope**（`BoxScope.(MediaItem)->Unit`），外面包 Box 没用。
- **★★★ 同一病根（犯三次）：替用户做决定 + 不告诉他**：下线设置只删 UI 留旧值 / 同步失败偷偷关开关。**任何"失败就自动改用户设置"都去掉**。**下线设置项 = 删 UI + 停读 +（可选）清存量。**
- **手动入口必须和自动入口同链路**；同一交互在不同入口必须同一套语义（连"何时提交"也复用）。
- **★★ `Dialog` 是独立窗口：外面盖的东西一律被压住**。正解：暴露 `overlayContent` 槽位（`BoxScope.() -> Unit`），塞进同一窗口排最后。跨窗口 z 序看谁窗口在上。**已踩两次。**
- **★ Dialog 内容框不与屏幕重合**（顶边在状态栏下、底边越屏底）：`fillMaxSize` 填的是窗口。overlay 要显式给 `屏幕高 − 窗口顶边`；`Dialog + enableEdgeToEdge()` 要 `decorFitsSystemWindows=false`。
- **脚本改完必须 grep 关键符号确认落地**：`str.replace` 锚点没匹配会静默跳过。
- **★ 覆盖层起不来时"什么都不发生"远好于"被踢回去"**：切全屏视图别拆下层，让新层盖上。`remember` 住传下去的 list（新实例会重启 effect）。
- **要干净的黑缝：缩放只作用在"图"，底色满屏**；缝越翻越窄。
- **★ "比 pager 突兀" = 出场用 tween 而不是 spring**：轻扫时松手瞬间速度跳 5 倍。**翻页接手指用 spring；删除扔掉用 tween。**
- **★ "动画跑完才 commit" = 动画时长就是感知延迟**（翻页在出场后才换 index ⇒ 滑一张要等）。
- **会骗人的设置项直接删**（选项存在但无效比没有更糟）。跨页复用组件起点必须实测（`boundsInWindow()`）。
- **滑动判据必须是"距离 或 速度"**：只判距离时快速一甩会被判没到阈值。
- **★★ `pointerInput` 闭包只在键变化时重启**：读 `var x` 永远拿到首次组合的值（`rememberUpdatedState` 转发）。把值放进键也救不了（键不变不重启）。
- **常驻垫底层会从黑边露脸**：按 `max(|dx|,|dy|,收走进度)` 淡入。
- **`mutableStateOf(0)` 用 `mutableIntStateOf`**。

## Compose：手势

- **"位置不对"先查容器 `contentAlignment`**：`BoxWithConstraints(fillMaxSize)` 默认贴左上；改 `Center` 会把指针先水平居中。正解 `CenterStart` + 补偿指针半宽。
- **滑条参考系不能动**：刻度静止、指针动；刻度层不带底色、指针独立层；滚动状态读放 `graphicsLayer{}`/draw。
- **拖拽增益与松手惯性是两份参数**（1:1.25 + 松手×1.6）。用户说"幅度小"先问哪一段 —— 给拖拽加增益会毁掉跟手。
- **惯性 = 指数衰减滑行**：每帧 `withFrameNanos` 取真实间隔、`v *= 0.998^dt`，直接 `dispatchRawDelta`（不吃 Mutex）；新手势先 cancel 上次 job。
- **父子手势分配**：子优先认领自己方向并 consume；父只在 `Final` 兜底；父要抢方向才在 `Initial` 判方向 + consume。阈值用 `touchSlop`。
- **全量 consume 会吃子组件手势**：按主方向判别。**消费了就必须让它真能动**（又归零又 consume = 一进来吸死）。
- **手势"难触发"先查层级竞争**：`detectDragGestures` 锁第一根手指 ⇒ 捏合被平移吃掉。自己写 `awaitEachGesture`，第二根手指落下就放弃拖拽。
- **"逐帧累加 + 反向扣减"不可靠**（真实手势不单调）⇒ 用单调量 `1 - cur/base`（Compose 1.7.5 无 `calculateCentroidSize`，用两点距离）。
- **渐进阻尼 ≠ 硬边界**：iOS 橡皮筋 `c*(1-1/(over/c+1))`，松手再动画吸回。
- **状态机切换时检查"为旧状态算的约束"**：捏合触发后 `lowerBound` 从 0.72 变 1，`coerceIn` 把 0.85 硬抬回 1 ⇒ 触发那帧猛弹大。
- **横滑手势与横滑翻页只能存其一**。可逆动作当场生效，不可逆留到退出时结算。
- **`RailAction` 的 label 恒定，状态只交给图标**（"颜色变了就够了"）。
- **★ `awaitEachGesture` 是受限协程作用域**：不能调成员挂起函数（`Animatable.snapTo`）⇒ `scope.launch { ... }`。`detectDragGestures` 的 `onDrag` 同样。
- **门控动画要配保险丝**：绑"内容就绪"要加 `delay(TIMEOUT); ready = true`，否则回调没来就永久透明。

## Compose：性能 / 组合

- **组合期读 state 是最隐蔽的卡顿根因**：① 局部读在 `graphicsLayer{}` 对，调用点 `Overlay(offsetX = offsetX.value)` 就把整棵子树登记成观察者（**改完局部回看调用点**）；② "结果只有两种"也要 `derivedStateOf`；③ 每帧变的量不能当 `LaunchedEffect` 键；④ 热路径 `RoundedCornerShape` 提顶层单例。
- **可跳过性**：`MediaItem`（带 Uri）和捕获容器的 lambda 不稳定 ⇒ 格子整片不可跳过。抽 `@Immutable` 小模型 + `@Stable` 回调包装类。
- **下游 remember 的键里"调用点现造的 lambda"换 `rememberUpdatedState`**（`DayCellActions` 一次拖 ~50 格）。
- **别停在最像的根因**：不确定就把所有可计算成本一次清掉（虚拟化 + key + 只给需要的格子建动画）。

## Compose：布局 / 自绘虚拟化

- **虚拟化**：一排全量 `Row` + `AsyncImage` 会卡死；按滚动偏移算可见区间，区间外放等宽 Spacer，占位与真格子用**同一计算函数**。单节点宽上限 262143px，超宽空档拆多个 ≤200000px。
- **间距属于容器**：`spacedBy` 的 gap 不随子项消失；要"子项连间距一起消"改成子项尾 padding。**但 `width(w).padding(g)` 总宽仍是 w** —— 要让步进含间距必须外层 `width(w+g)`，否则模型与布局差 `k×g`。
- **模型必须与布局同取整**：累计偏移一律 `roundToPx`（`toPx` 留小数会累积几十像素漂移）。
- **`clip` 形状共享实例**（每格现算会重建所有裁剪节点）。**多排共用 `ScrollState`**：靠短排尾部垫片让 `maxValue` 恒等；`scroll.maxValue` 不能当刻度分母，用各排上报总长取 max；拖动定位别用 `animateScrollTo`。
- **★ 为排版插的 `Spacer` 必须算进"内容总宽"**：半格错位的 `rowOffsetPx` 漏算 ⇒ 两排 `maxValue` 差半格 ⇒ 共用一个 `ScrollState` 到尽头一边夹一边放，手指继续划就**原地抖/像闪**。全工程"内容宽"只有一个数，漏一处三处全歪。
- **★ 尽头橡皮筋**：`dispatchRawDelta` 的**返回值**才是真实消费量，差值 = 拉出量（1px 死区，因为 `ScrollState` 内部 `roundToInt`）；阻尼 `limit*raw/(limit+|raw|)`；位移画在 `graphicsLayer{translationX}` 且必须在 **`horizontalScroll` 之前**（之后被滚动容器 clip 掉，看不见）；反向拖先收皮筋再滚内容。
- **`ScrollState` 外部只能 `scrollTo()` 且被 `maxValue` 夹** ⇒ 每帧检查、没对上再推（带上限）。
- **★ 组件里写死 `widthIn(max = 固定值)` = 定时炸弹**（它不知道旁边有按钮）⇒ 压住按钮、点击被吃。宽度上限交给父容器约束：`padding(horizontal=52.dp)` 收缩的是**约束**（真居中且不重叠），`weight(1f)` 在剩余空间居中（天然偏右）。
- **「居中」要对齐格子中心**（`cumPx[k] + w/2`）。**属性互斥时改分派别硬凑**（焦点图必在上排）。补偿性 padding 治不了观感。

## 图片加载（Coil / MediaStore）

- **Coil 不给 size 就按原图解码**（4000×3000 ≈ 48MB）⇒ `ImageRequest.size(显示尺寸)`。但**动图不能给 size**（`ImageDecoderDecoder` 翻成 `setTargetSize()` 会闪退）。
- **放大后糊 = 解码尺寸按 1x 算**：放大是 `graphicsLayer`，解码器不知道 ⇒ 显式 `.size(容器 × 放大上限)`，用 `sourceSize` 封顶。
- **★ 超长图不要整张解码**：828×30656 = 25MP ≈ 101MB，`size()` 只影响 `inSampleSize`（2 的幂），长图会按宽算出极小的 sample ⇒ 缩略图成细条、还会 OOM 闪退。正解 = **`BitmapRegionDecoder` 只解可见/居中 4:3 区域**。
- **★ 跨页复用 Bitmap 只能靠"请求尺寸完全相同"**（memoryCacheKey 含 size）。让来源页把 `decodeSize ?: container` 回调出去。
- **★ 占位必须是"已拿在手上的对象"**，走 Coil 的占位都要 IO + 一帧回调 = 闪。正解：调用方提前读成 `ImageBitmap` 交进来。
- **★ "避免空档"比"尽快填满"更稳**：让旧内容留在原地直到新内容可画。
- **自制翻页/列表要自己预加载**，请求尺寸必须与主请求一致。
- **缩略图墙用 `ContentResolver.loadThumbnail`**（靠 `ImageRequest` parameter 打标记），系统那份最长边 512，**短边 < 目标一半就丢弃**。
- **Coil 2.7 `Parameters` 的键是 String**：`setParameter` / `options.parameters.value<Boolean>(key)`。
- **★ Content Uri 里没有文件名** ⇒ 任何"从 Uri 猜扩展名"都是坏的，类型由调用方传。
- **MediaStore 的 WIDTH/HEIGHT 不带 rotation**。**★ `DATE_ADDED` ≠ 拍摄时间**。`LazyVerticalGrid.itemsIndexed` 要单独 import。

## 跨页飞入 / 共享元素

- 三道坎：① 目标层瞬间不透明会"啪"地盖住 ⇒ 从透明淡入；② size 不同 cache 必 miss ⇒ 系统缩略图垫底；③ 起点按"触发那一刻"的形态算。
- **让目标元素自己从起点插值**（别另画一张会飞的图）。终点用 `boundsInWindow()`（不受 graphicsLayer 影响）；终点没测到前 alpha=0。
- 有飞入时目标页只淡入、不缩放不位移。统一比例取几何平均 `sqrt(sx*sy)`。异步内容 + 立即进场动画 = 亮度跳变 ⇒ 绑"内容就绪"。

## 根因与流程

- **"偶发"先找那条只在一半情况下成立的路径**，别急着怀疑阈值或性能。
- **"被挡住/太显眼"先确认哪个页面哪个控件**；"最近改过的"不是"用户看到的"；"两排/一列"先确认行还是列；参考截图先分辨常态界面还是引导页。看不准就问。
- **测平移必须用互相关，不能用像素差**（曾据此误判"两排独立滚动"、白改一版架构）。**用单一可疑证据改架构前，先换一种测量方法复核。**
- **改架构要回头清掉依赖旧不变量的分支**。**注释和实现不符当场对齐**（bug 就长在这）。
- **按缩进批量删代码后必须回看调用点**、grep 周边函数名。**一个动作只留一个入口。**
- **"改成和 X 一样"先确认适用范围**（"喜欢页按钮和视频页一样"指的是视频条目）。红色在视频页 = "已标记待删"，别照抄成删除。
- **清零 state 要挑"状态真正切换"的时机**（`LaunchedEffect(index){p=0}` 救不了从子页返回）。
- **别相信"以前 lint 过了"**：增量分析会漏，只有完整 `lintDebug` 扫得出。局部 fun 后紧跟同级语句踩 SuspiciousIndentation → 改 `val f = {}`。
- **Kotlin 字符串模板中文是合法标识符**：`"$var中文"` 会解析成变量，必须 `"${var}中文"`。
- **首帧门控**：常驻 + alpha 显隐的播放视图，TextureView 会留上一段最后一帧 ⇒ `playKey`/`readyKey` 双钥匙。
- **预览页动作回调传"当前条目"不传索引**；量窗口用 `onGloballyPositioned`；先 grep 标准件再调数值。
- **`getOrElse(-1)` 会抛下标异常**：`indexOfFirst{}` 的结果用前一律 `idx in arr.indices` 守卫。

## 微博收藏备份（已定论）

用 **WeiBack-rs v0.3.1**（程序 `%LOCALAPPDATA%\weiback-rs\`，数据 `%APPDATA%\weiback\`，手机号+短信登录，产出 HTML）。自建 downloader 已废弃：别让用户手动复制 Cookie；接口参数别靠搜索推测；强风控站点用浏览器会话或模拟正常节奏。

改造版源码 `小工具/weiback-rs-src/`，构建目录 `C:/wb-build/`（必须英文路径）。重装要点：VS Build Tools + Windows SDK + Rust；`vcvars64.bat` 后手工补 `LIB`/`INCLUDE`（否则 LNK1181）；`.bat` 必须纯 ASCII；cargo 的 git 依赖要 PortableGit 在 PATH + `ghfast.top` insteadOf。已实现：每页条数可调、本地作者列表、批量删除、删除按钮改「⋮」菜单。
