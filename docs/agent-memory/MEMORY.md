# 项目长期记忆（小工具工作区）

> 每日进展写 `YYYY-MM-DD.md`；这里只放长期有效的东西，**一条一行**。
> 本文件注入有上限，超了会被截断（等于我看不到后半部分）—— 要加内容先压旧的。

## 本机环境

- Win11/AMD64。**GitHub release 下载被代理拦**（502）→ 加 `https://ghfast.top/` 前缀，**镜像务必用官方 sha256 校验**；`api.github.com` 正常。
- **Bash 工具 PATH 缺 coreutils**：先 `export PATH="/c/Users/22120/.workbuddy/binaries/PortableGit/versions/1.3.0/usr/bin:$PATH"`。
- Git Bash 会把 python 正则 `\d` 转成 `/d`（静默失配）→ bash 里正则写 `[0-9]`；`/tmp` 映射 Windows Temp、python 看不到 → 用 `cygpath -w`；脚本一律 `python - << 'PYEOF'` heredoc。
- PowerShell 捕获 Python 子进程 stdout 会丢内容 → 重定向到文件再读。
- managed Python 3.13 无 tkinter（GUI 用系统 3.12）；隔离环境 `binaries/python/envs/default`。
- adb 真机（小米 2304FPN6DC / HyperOS 3.0.308.0）：`cmd power wakeup` + `wm dismiss-keyguard` 可唤醒；`input` 注入 KEYCODE_WAKEUP 实证有效（"被 HyperOS 拦"已过时）；`input motionevent` 支持多指。
- 分析录屏：ffmpeg（`imageio_ffmpeg` 自带）。先 `fps=1,scale=200:-1,tile=6x4` 拼网格看流程，再 `-ss T -frames:v 1` 抽原始尺寸帧看细节。

## 装第三方软件

- **先确认目标版本有对应平台的构建产物**（WeiBack-rs Windows 最高 v0.3.1）。
- Tauri NSIS 支持 `/S` 静默装（当前用户模式免管理员）；装后验证程序路径/体积、注册表卸载项、开始菜单、启动日志无 ERROR。

## 「收藏同步到系统相册」—— 已定论，别再折腾

**同步 100% 正确，小米相册也读这一列，只是刷新极慢。**

- 实测：取舍收藏夹 286 = 系统 `is_favorite=1` 286（逐条对上、0 差异）；小米相册 256 → 270 → 273 → **重启相册后正常**（用户确认，此事终结）。
- **「看不到」≠「没同步」**：必须重启手机或重开相册才刷新。app 提示已改成"已写入 N 项。系统相册可能需要重启或等一会儿才会显示出来。"
- **小米相册双向读写 `is_favorite`**（用户手动收藏一个视频 → 系统 +1，实证）⇒ 「同步系统收藏到这里」是有效功能，不该删。
- **唯一真实缺口**：在取舍里取消收藏，系统标记不跟着取消（同步只做加法）。用户倾向"完全对齐"，但要求先解决相册显示，**暂缓、等他确认**。

**三个教训：**
1. **核对脚本自己写错**：`storeKey` 解码写反（正确：**偶数=图片 `id*2`，奇数=视频 `id*2+1`**）⇒ 算出"0 条对上"⇒ 假结论"同步没用"，白折腾五轮。**核对脚本也要先自证**（拿已知答案的样本验一遍）。
2. **不同写入路径不等价**：`content update` 单条写入 ≠ `createFavoriteRequest`（系统弹窗 + Provider 特权路径）。**平台能力先实测**：`content query` 比读代码可靠得多。
3. **结论没坐实就给方案建议**：据此列了三个"替代方案"，其中一个要占双份空间。**先钉死事实，再谈方案。**

## 微博收藏备份（已定论）

用 **WeiBack-rs v0.3.1**（程序 `%LOCALAPPDATA%\weiback-rs\`，数据 `%APPDATA%\weiback\`，手机号+短信登录，产出 HTML 归档）。自建 downloader 已废弃：①别让用户手动复制 Cookie；②接口参数别靠搜索推测；③**强风控站点用浏览器会话或模拟正常节奏，别用独立 HTTP 客户端硬刚**。

## 「取舍」Android（主线）

当前 **1.3.74**（versionCode 122）。工程 `qushe-android/`，JDK17 + Gradle 8.9 + SDK 35，无 Android Studio，命令行构建 + 小米真机验证。

```bash
export JAVA_HOME="C:\Users\22120\.workbuddy\binaries\jdk17\jdk-17.0.20.1+1"
"C:/Users/22120/.workbuddy/binaries/gradle-8.9/bin/gradle" assembleDebug lintDebug
```

- **改完先跑 `python verify.py`**（退出码 0/1）：编码自证 / 版本号四处一致 / 收藏同步逐条比对 / 回收站记账 / **顶层函数重名** / 历史残留 / 构建产物。
- **换机器**：`git clone` → `python setup.py`（恢复记忆 + 找 JDK/SDK + 写 `local.properties`）。**离开前 commit+push，回来先 pull。**
- 备份 `D:/backup/qushe-android.git`（remote `backup`，`post-commit` 自动 push）；记忆经 `pre-commit` 镜像到 `docs/agent-memory/` 跟着仓库走。**异地远端仍未配。**
- 稳定 tag **`v1.3.54`**（用户评价"比较完美"，回归基准）。
- 构建后 `app-debug.apk` 复制为 `qushe-vX.Y-debug.apk`；改版本号别漏 `SettingsScreen.kt` 里硬编码的字符串。
- **★ 包体积先看构成再动手**：debug 32.6MB 里 **dex 占 99%**（native 44KB、资源 761KB）⇒ 删 ABI / 压资源全是无效功，**只有 R8 有效**。`isMinifyEnabled + isShrinkResources` + debug 签名 ⇒ **3.7MB（-89%）**。**release 不可 `run-as`**，收藏核对要用 debug 包。
- **UI 副标题只写"影响什么"，不解释"为什么"**（为什么写代码注释里）。用户明确要求过文案简洁。
- 大范围编辑前 `git status` 确认干净；改坏了 `git checkout -- <file>` 一键回退。

---

## Compose：绘制 / 层级（最高频踩坑）

- **★ `background` / `clip` 只影响它之后的那截链**：要让背景跟着 alpha，`background` 必须写在 `graphicsLayer{alpha}` **之后**；要让底色跟着圆角，必须写在 `clip(圆角)` **之后**。写前面 = 背景在 alpha 层外（永远不透明）/ 圆角外留一圈方角底色。
- **★ 多层垫底时变换必须挂在"包住全部层"的那层**，否则其他层不跟着动（缩放露底层 / 拖动留残影）。
- **"alpha=0" 不等于"看不见"**：只要占着节点，上层未出图就整片露出来。要真消失得让节点**不进组合**（`if(cond)`，cond 用 `derivedStateOf` 包）。
- **"内容为空" 不等于"不画"**：带 `background`/`padding` 的容器哪怕没内容也留**空壳**。该藏就 `return`/`if`，别只清文字。
- **"闪一下"分两种**：**闪黑** = 那帧根本没内容（占位链路没接上）；**闪糊** = 占位太糊、清晰度跳变。用户报哪个词就查哪层。
- **不透明 ≠ 挡触摸**：全屏覆盖层要 `pointerInput` consume；深色半透明底压黑界面等于不存在（要可见用浅色半透明圆+淡边）。
- **★ 装饰性效果不要动布局基准**：为让圆角落在图片四角而缩 `aspectRatio`，会改变整条链尺寸基准 ⇒ "图片自动放大"。正解 = **在图之上、变换层之内画四条角路径**（角上方块 − 四分之一圆，填页面底色）：圆角准、跟着缩放平移、布局零改动。只画四个角，整片盖会把黑边也填上。
- **"调好的数字"提出来共用**：圆角 `rememberContentCorner()`、间隙 `rememberPageGap()`。但**共用前分清场景**：格子 4.dp 是密集小图间距，大图接缝不是一回事。
- **圆角值全 App 唯一**：格子用 `clip(rememberContentShape())`；大图用**四角遮罩**（Fit 画在整屏盒里，clip 整屏只裁黑边）。首页卡片/胶片条不参与统一。
- **★ "存在的函数说未解析"几乎总是括号问题**：少一个收尾括号会把后面所有顶层定义算进函数体，报错却指向那些函数。**先看顶层函数列表，再看编译错误。**
- **重排大文件用"显式行段"**，别按缩进猜顶层语句（启发式会把多行调用的收尾 `)` 当新语句）。先干跑打印每行、确认边界落在空行，再按 `(起,止)` 切。
- **`Column + verticalScroll` 一次性组合全部内容**；重页面用 `LazyColumn`。但 `LazyColumn` 各 `item` **不共享作用域**，块内局部 `val` 要提到外面。
- **★ 同一件事有两个"真源"，只改一个 = 半生效**：底栏读 `tab`、内容读 `pager`，`BackHandler` 只改前者 ⇒ "底栏回首页、内容没回"。有 `goToX()` 这类"一起做两件事"的函数，回调必须走它；`BackHandler(enabled=…)` 判据也要跟真正决定内容的那份状态一致。
- **★ 优化手段本身可能成为新的卡顿源**：`beyondViewportPageCount=1` 把组合成本挪到"空闲"，但启动时同时组合多页各自查 MediaStore ⇒ 后台持续解码，把省下的预算吃回去。**把成本挪去空闲只在那个空闲真空闲时成立；正解是把重页面做轻。**
- **"信息胶囊 vs 详情面板"（用户定，全 App 统一）**：胶囊只有一行 ⇒ 只放 `日期 · 文件夹 · 大小`；分辨率/时长/地点/EXIF 一律进详情面板（硬塞会被省略号截断）。三处共用 `InfoPill` + `PhotoDetailContent` —— **出现多次就要有唯一构造函数**。位置也分工：信息入口居中，操作按钮靠边。
- **"拍摄时间"和"添加时间"是两回事**：截图/下载/微信保存的文件，EXIF 与 `DATE_ADDED` 可能差好几年。`dateAddedMillis` 源单位是**秒**、进模型统一成毫秒；两者相同则只显示一行。
- **时间格式是两个独立开关**（相对/具体），四种组合都合法（都不显示时 `InfoPill.primary` 传 null）。都显示时具体日期挪到第二行。`infoPillPrimary/Secondary(item, relative, absolute)` 是唯一来源。
- **"相对时间"函数只输出相对时间**：`relativeDayLabel` 曾在一年前拼上 `" · 2024/5/3"` —— 那是调用方另起一行的事。
- **★ 垫底"下一张"别用小阈值淡入**：alpha 绑在"翻页判定线"的 18%（只有 72dp）⇒ 手指走十几像素下一张就整个糊上来。正解 = **停在旁边一整屏 + 同速移动**（`translationX = dx - dir*屏宽`）。出场目标必须**正好一屏**。
- **★ 增量式 `snapTo` 会丢帧**：`snapTo(current+delta)` 读的是挂起写出的值、每帧还 `launch`，后一次取消前一次就**丢了那段增量** ⇒ "拖一点点顿住"。正解：**手势内本地变量累加、每次写绝对值**，且起点必须是当前 Animatable 值（否则"跳一下"）。
- **轴锁定顺带简化判定**：越过 touchSlop 按主方向定死轴 ⇒ 位移恒有一分量为 0，不会"斜着划同时命中翻页和删除"。
- **★ 精确的边界估计可能把功能锁死**：长图传 `contentRatio` 会让 `snapScale` 只有 1~1.25、横向 `panLimit=0` ⇒ "放大后拖不动"、解码也按容器 ⇒ **糊**。**长图不传它**（整理页、瀑布流都一样），上限回 `maxScale`，另传 `sourceSize`（原始像素）给解码封顶。
- **角标优先级：截屏 > 长图**（用户定）。截屏说的是**来源**，比"要放大读"更具体。
- **同一组件的两个调用点传参不一致时，先看哪个带注释解释**（1.3.77 瀑布流漏了整理页的 `contentRatio = null`，症状复发一遍）。
- **★ 一个参数往往同时管好几件事**：`contentRatio` 同时管 `snapScale` 和 `decodeSize`；为放开上限删掉它 ⇒ 解码退化成容器尺寸 ⇒ 长图放大必然糊。正解：拆开、另加 `sourceSize`。**改"看起来只管一件事"的参数前先 grep 谁在读它。**
- **单击+双击共存交给 `detectTapsWithoutConsuming`**（内置延后 280ms 等第二击）。但**单击切 chrome 别走 `onSingleTap`** —— 会延后 280ms、手感钝；放父层"没拖过"分支里（松手当帧），双击调两次净效果为零。
- **状态机不能用"投影"当指针**：三态里状态①和③在 `filmstripVisible` 上取值相同 ⇒ 永远回不到①。必须自己记 `stage`。
- **两个 `weight(1f)` 不等于居中**：只保证"扣掉两侧后的剩余空间"居中，两侧宽度不等就偏。要真居中用整屏 `Box` + `align(Center)`。
- **"随心所欲/不像 pager"多半是 rotation**：`rotationZ=(offsetX/80).coerceIn(-8,8)` 这类"跟着歪"就是"不按轨迹"的来源。pager 严格一比一平移。
- **★ 淡出和位移绑同层 = 退场"卡一下"**：alpha 按 `offset/阈值` 算，而退场要把 offset 涨到屏高×1.15 ⇒ 前 20% 就淡没了、剩下 100+ms 全空。**拆层**：黑底只淡出 / 内容只位移 / chrome 只淡出，且 **chrome 必须画在内容之后**（否则被盖住点不到）。
- **★ 组件已内聚的行为，调用方原样传参**：`DayViewScreen` 用 `markedKeys→removedFlags→CellSlot 宽度收 0` 实现"格子飞出去、同排左移"；调用方再 `filterNot{in marked}` 就是**换了一套机制** ⇒ 整排重排、偶发残留。
- **★ 同一个动作有两条路 ⇒ 净效果为零**：chrome 显隐既有外层 `clickable` 又有 `onSingleTap` ⇒ 切两次互相抵消，"按了没反应"。**先数这条动作有几条路径。**
- **★ 父节点抢手势，子节点"放大后拖动看细节"就废了**：父在 Main 阶段先拿到单指并 `consume()`。**放大态父节点一个事件都不消费**，整条让给子节点（或判 `change.isConsumed` 放手）。
- **★★ 一个 state 多写者时，每个写者只写自己"拥有的那次变化"**：`chromeVisible` 既有 toggle 又有 `onZoomedChange` 的赋值 ⇒ 切掉→写回→再切，**看起来"只收不回来"**。解法：判 `if (isZoomed != wasZoomed)` 才写。
- **状态变化要报"跨越的那一刻"**，不是"手势结束"（否则图都铺满了 chrome 还杵着）；且只在跨越时报。
- **toggle 还是强制赋值看意图**：chrome 缩回 1x 用**强制显示**而非 toggle —— 用户刚松手的是"看图"动作。
- **★★★★ SharedPreferences 读不是 Compose 状态**：各页面直接读 `prefs.xxx` ⇒ 设置改完不重组 ⇒ "要切换几下才跟上"。**UI 要跟随的设置必须由 `AppRoot` 持有 state 往下传。**（仓库数据同理：toggle 后要自增 tick）
- **★★★★ `LaunchedEffect` 的 effect 体里绝不修改自己的 key**：`LaunchedEffect(x){ x=emptyList(); launcher.launch(...) }` —— 改 x 那下键就变了 ⇒ 协程取消重启 ⇒ `launch()` 来不及执行。表现"点了什么都没发生"。**要改内容就另加一个只当 key 的 `tick`。**
- **★★★★ 动手前先 grep 有没有现成实现**（最不该犯的错）：`allSystemFavoriteKeys()` 本就在且编码正确，我又加了个 `allFavoriteKeys()`、把奇偶写反 ⇒ 差额永远算错 ⇒ "同步完全没用"。**同名/近名重复函数是本工程反复出问题的根源**（`SegChip` 也是）。`verify.py` 现在查顶层重名。
- **★★★ 同步/记账类：别自己记"已同步"，去问对方**。自维护的集合必然与事实脱节。**每次同步读对方当前状态、算差集**；且**记账字段必须区分「谁写的」和「什么时候写的」**（导入时标成"已同步" ⇒ 只推 1 条）。
- **★★ 单向同步绝不做自动"删除对方"**：另一侧数据可能来自别处，我们无法区分谁写的。**只做加法，减法只由用户主动触发。**
- **★ Box 的子节点默认堆叠在左上角**：`Row{A;B}` 改成 `Box` 时，A/B 不自己套一层 Row 就会**叠在一起**。
- **★ 局部 lambda 里要用 `align` 就得把签名改成 Scope**：`(@Composable (MediaItem)->Unit)` 写 `align` 编译不过，**外面包 Box 没用**，必须 `BoxScope.(MediaItem)->Unit`。
- **★★★ 同一病根（已犯三次）：替用户做决定 + 不告诉他**：下线设置项只删 UI 却留着旧值 / 同步失败就偷偷关掉用户的开关。**任何"失败就自动改用户设置"都要去掉**，只如实报事实。**下线设置项 = 删 UI + 停读 +（可选）清存量，三件事一起做。**
- **手动入口必须和自动入口同链路**；**同一交互在不同入口必须同一套语义** —— 连"什么时候真正提交"也一起复用。
- **★★ `Dialog` 是独立窗口：调用方在外面盖的东西一律被压住**（"代码明明打开了，什么都看不到"）。**正解：暴露 `overlayContent` 槽位（`BoxScope.() -> Unit`，否则调用方拿不到 `align`）**，内容塞进同一窗口、排在其所有内容之后。跨窗口 z 序看谁窗口在上，不是"后写的在上"。**已踩两次。**
- **★ Dialog 的内容框不与屏幕重合**（顶边在状态栏下、底边越过屏底）—— `fillMaxSize` 填的是**窗口**。overlay 要显式给高度 `屏幕高 − 窗口顶边`。`Dialog + enableEdgeToEdge()` = 半个 edge-to-edge → `DialogProperties(decorFitsSystemWindows=false)`。
- **脚本改完必须 grep 关键符号确认落地**：`str.replace` 锚点没匹配会**静默跳过**，表现为"状态会切但什么都不显示"，很容易误判成遮挡/层级。
- **★ 覆盖层起不来时，"什么都不发生"远好于"被踢回去"**：切全屏视图**别把下层拆掉**，让新层盖在上面。另：`remember` 住传下去的 list，每次重组换新实例会重启 effect。
- **要干净的黑缝：缩放只作用在"图"，底色保持满屏**；缝还要**越翻越窄**（到位那刻它就是下一张，不收窄最后一帧会"长出"黑边）。
- **★ "比 pager 突兀" = 出场用了 tween 而不是 spring**：`tween(200ms 走一屏)` 是定时的，而判定线只有 72dp ⇒ 轻扫时手指 1000px/s、**松手瞬间速度跳 5 倍**。换 `spring(StiffnessMediumLow)`（`animateTo` 把当前速度当弹簧初速度）⇒ 速度连续。**翻页"接住手指"用 spring；删除"扔掉"用 tween。**
- **★ "动画跑完才 commit"的设计里，动画时长 = 用户的感知延迟**：翻页 `onDecision` 在出场动画后才换 `index` ⇒ 连续滑动变成"滑一张要等一下"。
- **会骗人的设置项直接删**：某选项分支从没调 `onDecision` ⇒ 选中就失效。**选项存在但无效比没有更糟。**
- **跨页复用组件时起点必须实测**：被 pager 平移过的容器用尺寸估会偏 ⇒ 用 `boundsInWindow()`。
- **滑动判据必须是"距离 _或_ 速度"**：只判距离时快速一甩会被当成"没到阈值" ⇒ 每次都弹回去。用 `VelocityTracker`。
- **★★ `pointerInput` 的闭包只在键变化时重启**：里面读 `var x` 永远拿到**首次组合**那个值（要 `rememberUpdatedState` 转发）。**把值放进键也救不了** —— 键不变就不重启（1.3.59 的 `zoomed` 就这么废掉的）。
- **常驻垫底层会从黑边露脸**：顶层 Fit 画图、竖图上下留宽黑边 ⇒ 垫在下面的全屏图会露出来。按 `max(|dx|,|dy|,收走进度)` 淡入。
- **`mutableStateOf(0)` 用 `mutableIntStateOf`**（否则每次写都装箱，lint 会提示）。

## Compose：手势

- **"位置不对"先查容器 `contentAlignment`**：`BoxWithConstraints(fillMaxSize)` 默认贴左上；改 `Center` 会把指针先水平居中。正解 `CenterStart` + 补偿指针半宽。
- **滑条参考系不能动**：刻度静止、指针动（否则像"背景在滑"）；刻度层不带底色、指针画独立层；滚动状态读放 `graphicsLayer{}`/draw，别在组合期读。
- **拖拽增益与松手惯性是两份参数**（`DRAG_GAIN` 1:1.25 + 松手速度×1.6）。用户说"幅度小"先问是哪一段 —— 给拖拽加增益会毁掉"跟手"。
- **惯性 = 指数衰减滑行**：每帧 `withFrameNanos` 取真实间隔、`v *= 0.998^dt`，位移直接 `dispatchRawDelta`（不吃 Mutex、随时能接住）；新手势按下先 cancel 上次 job。只做"投影+动画"等于没有滑行。
- **父子手势分配**：子优先认领自己方向并 consume；父只在 `Final` 兜底 `if(!isConsumed) consume()`；父要抢某方向才在 `Initial` 判方向 + consume。阈值用 `viewConfiguration.touchSlop`。
- **全量 consume 会吃子组件手势**：按本帧位移主方向判别（竖直才消费、水平放行，3px 防吞点击）。**消费了就必须让它真能动** —— 又"归零"又"消费"会变成"一进来就吸死、全动不了"。
- **手势"难触发"先查层级竞争**：`detectDragGestures` 锁定第一根手指拖到底、第二根落下也不松手 ⇒ 捏合量被平移吃掉。自己写 `awaitEachGesture`：**第二根手指一落下就放弃拖拽 + 回弹，且不消费事件**。
- **"逐帧累加 + 反向扣减"判定不可靠**：真实手势**不单调**（调整姿势时反向帧把量扣回去）⇒ 用户要做远超阈值的动作。必须换**单调量**：记起手基准算 `1 - cur/base`。（Compose 1.7.5 没有 `calculateCentroidSize`，用 `(p0.position-p1.position).getDistance()`）
- **渐进阻尼 ≠ 硬边界**：硬 `coerceIn` 是"1px 跟手、到边戛然而止"。要 iOS 橡皮筋 `c*(1-1/(over/c+1))`，松手再动画吸回 —— 两套边界并存。
- **状态机切换时检查"为旧状态算的约束"**：捏合触发后 `lowerBound` 从 0.72 变回 1f，`coerceIn` 把已缩到 0.85 的照片**硬抬回 1.0** ⇒ "触发那帧猛地弹大"。
- **横滑手势与横滑翻页只能存其一**，边界写进注释。
- **可逆动作当场生效，不可逆的留到退出时结算**（删除攒 `pendingDelete` 退出时一次提交）。
- **`RailAction` 的 label 要恒定，状态只交给图标**："已标记"这类文案用户不接受 —— "颜色变了就够了"。
- **★ `awaitEachGesture` 是受限协程作用域**：不能调成员挂起函数（`Animatable.snapTo`/`stop` 编译不过）。解法 `scope.launch { animatable.snapTo(...) }` —— `launch` 不是挂起函数。`detectDragGestures` 的 `onDrag` 是非挂起 lambda，同样调不了。
- **门控动画要配保险丝**：把淡入绑到"内容就绪"，一旦回调没来就是**永久透明** ⇒ 另加 `LaunchedEffect(Unit){ delay(TIMEOUT); ready = true }`。

## Compose：性能 / 组合

- **组合期读 state 是最隐蔽的卡顿根因**：① **调用点会把局部做对的抵消掉** —— 局部读在 `graphicsLayer{}` 里对，但调用点 `Overlay(offsetX = offsetX.value)` 就把整棵子树登记成观察者（**改完局部必须回看调用点**）；② "结果只有两种"不等于开销小，高频 state 的派生标量一律 `derivedStateOf`；③ 每帧变的量不能当 `LaunchedEffect` 的键；④ 热路径上的 `RoundedCornerShape` 提顶层单例（equals 比值不重建裁剪路径，但每次 new 是白付的 GC）。
- **可跳过性**：`MediaItem`（带 Uri）和捕获容器的 lambda 是不稳定参数 ⇒ 格子整片不可跳过。抽 `@Immutable` 小模型 + `@Stable` 回调包装类。
- **下游 remember 的键里"调用点现造的 lambda"换 `rememberUpdatedState`**：否则每次交互都换新实例，拖着大片子树重组（`DayCellActions` 一次拖 ~50 格）。
- **别停在最像的根因**：不确定就把所有可计算的成本中心一次清掉（虚拟化 + key + 只给需要的格子建动画）。

## Compose：布局 / 自绘虚拟化

- **虚拟化**：一排全量 `Row` 组合几百个 `AsyncImage` 会卡死；按滚动偏移算可见区间，区间外放等宽 Spacer —— 占位与真格子用**同一计算函数**。单节点宽上限 262143px，超宽空档拆多个 ≤200000px。
- **间距属于容器**：`spacedBy` 的 gap 不随子项消失；要"消除子项连同间距"改成子项尾 padding。**但 `width(w).padding(g)` 总宽仍是 w** —— 想让步进含间距必须让**总宽**真加上（外层 `width(w+g)`），否则模型与布局差 `k×g`、误差随可见起点跳变 ⇒ 定位偏 + 横滑卡顿 + 多排错位一起出现。
- **模型必须与布局同取整**：累计偏移一律 `roundToPx`（`toPx` 留小数、几百格累积成几十像素漂移），视口/错位/单格宽/累计数组全链统一。
- **`clip` 形状共享实例**：每格现算会让所有格子全量重建裁剪节点 ⇒ 横滑"图片卡一下"。
- **多排共用 `ScrollState`**：录屏用**互相关**实测上下排位移 100% 相同、两排锁死。代价是两个 `maxValue` 不同 ⇒ 短排先到顶，靠**短排尾部垫片**（`padToPx`，不进 `cumPx`）让 maxValue 恒等，且垫片要小（按宽度贪心均衡把差压到一格内）。`scroll.maxValue` 不能当刻度分母（会在两排两值间跳），改用各排 `SideEffect` 上报总长、外层取 max。拖动定位别用 `animateScrollTo`（松手那帧指针猛跳）。
- **`ScrollState` 外部只能 `scrollTo()` 且会被 `maxValue` 夹**（`value` 的 setter 是 private）⇒ 要精确定位别猜时机，"每帧检查、没对上再推一次（带次数上限）"最稳。
- **「居中」要对齐格子中心**：`cumPx[k]` 是左边缘，要 `+ w/2`。
- **属性互斥时改分派别硬凑**：如「焦点图在上排」×「分排固定 index%2」互斥 ⇒ 让**哪组上排由焦点图决定**，别去改滚动算式。
- **补偿性 padding 治不了观感**：垫片本身就是用户看到的空白 —— 治本要动分排本身。

## 图片加载（Coil / MediaStore）

- **Coil 不给 size 就按原图解码**（4000×3000 ≈ 48MB/张）⇒ `ImageRequest.size(显示尺寸)`。但**动图不能给 size**（`ImageDecoderDecoder` 会翻成 `setTargetSize()`，动画源会闪退）。
- **放大后糊 = 解码尺寸按 1x 算**：`AsyncImage(model=uri)` 自动挂 `ConstraintsSizeResolver`（请求尺寸=容器），而放大是 `graphicsLayer` 缩放、解码器不知道 ⇒ 显式 `.size(容器 × 放大上限)`。推不出上限时宁可不做。
- **★ 跨页复用一个 Bitmap 的唯一办法是"请求尺寸完全相同"**：memoryCacheKey 含 size。让来源页把 `decodeSize ?: container` **回调出去**、目标页照用。垫低清缩略图只是把"空白几十毫秒"换成"清晰度跳变"，都是闪。
- **★ 占位必须是"已拿在手上的对象"**，不能是"一个会去取它的请求"：走 Coil 的占位都要 IO + 一帧回调，纯黑上那一两帧就是"闪一下"。正解：调用方提前读成 `ImageBitmap` 交进来、直接 `Image(bitmap=...)`。
- **★ "避免空档"比"尽快填满"更稳**：黑底 + 异步内容时，往空档塞东西都在赌"来得及"；正解是让**旧内容留在原地**直到新内容可画。
- **自制翻页/列表要自己实现预加载**（`beyondBoundsPageCount` 帮不了）。预加载的请求尺寸必须和主请求一致才可能命中缓存。
- **缩略图墙用 `ContentResolver.loadThumbnail`**（图片视频都适用，比 Coil 快一个量级），但只给"明确要一格小图"的请求用（靠 `ImageRequest` parameter 打标记）；系统那份最长边只有 512，**短边 < 目标一半就丢弃**。
- **Coil 2.7 的 `Parameters` 没有 `Key<T>`**：键就是 `String`；写 `setParameter`、读 `options.parameters.value<Boolean>(key)`。
- **★ Content Uri 里没有文件名**（`.../media/12345`）⇒ **任何"从 Uri 猜扩展名/类型"的代码都是坏的**，要类型就由调用方传进来。
- **MediaStore 的 WIDTH/HEIGHT 不带 rotation**，横竖以播放器 `videoSize` 为准。
- **★ `DATE_ADDED` ≠ 拍摄时间**（它是"被扫描进库的时间"）⇒ 按时间用 `DATE_TAKEN`，`DATE_ADDED` 只当 tie-breaker。
- **`LazyVerticalGrid` 的 `itemsIndexed` 要单独 import**（grid 包）。

## 跨页飞入 / 共享元素动效

- **三道坎**：① 目标层若瞬间完全不透明，底下那张就"啪"地被盖住 ⇒ 从透明淡入；② size 不同 ⇒ cache 必然 miss，重新解码的几十毫秒里格子是空的 ⇒ 用系统缩略图当 placeholder；③ **起点必须按"触发那一刻"的形态算** ⇒ 把当时的 scale 当回调参数带出去，别回调去"读当前值"。
- **让目标元素自己从起点插值**，别"另画一张会飞的图"（后者要再解一次码，慢于动画就"目标已到位、飞行图才出现"）。起点用 `onGloballyPositioned` + `ContentScale.Fit` 算，终点用目标自己的 `boundsInWindow()`（**不受 graphicsLayer 影响**）。**终点没测到之前元素必须 alpha=0**。
- 有飞入时目标页**只淡入、不缩放不位移**（两层变换叠加算不准起点）。
- **统一比例取几何平均** `sqrt(sx*sy)`：分别按宽高算会**拉变形**。
- **异步内容 + 立即启动的进场动画 = 亮度跳变**（"照片被压暗 → 转圈消失、图突然亮回来"）：进场动画**绑到"内容就绪"**，或提前预加载。

## 根因与流程

- **"偶发"先找那条只在一半情况下成立的路径**，别急着怀疑阈值或性能。
- **"被挡住/太显眼"先确认哪个页面哪个控件** —— "最近改过的"不是"用户看到的"；**说"两排/一列"先确认行还是列**；**参考截图先分辨常态界面还是引导/教程页**。看不准就问。
- **测平移必须用互相关，不能用像素差**：相邻帧变化量在内容变化时会给出假位移（曾据此误判"两排独立滚动"、白改一版架构）。**用单一可疑证据改架构之前，先换一种测量方法复核。**
- **改架构要回头清掉依赖旧不变量的分支**（分排从 `index%2` 改成贪心均衡后，残留的 `focusIndex/2` 让两排都去定位、互相覆盖）。
- **注释和实现不符当场对齐**（`DayFilmstrip` 头写"下排传 null"、代码却传 `focusItem` —— bug 就长在这）。
- **按缩进批量删代码后必须回看调用点**（删提示块时把 `DecisionOverlay(...)` 调用点一起删了，编译器不报"少了调用"，提示两个版本不显示）。**批量删完 grep 周边函数名。**
- **一个动作只留一个入口**：同时保留新旧两套触发方式，迟早"这里点了没反应"。
- **"改成和 X 一样"先确认适用范围**（"喜欢页和收藏页的按钮改成和视频页一样"指的是**视频条目**，图片条目本该原样）。
- **红色在视频页 = "已标记待删"，不是"删除"**：照搬到"点了立即删"的页面时别把 tint 也抄成红。
- **清零 state 要挑"状态真正切换"的时机**，不是挑一个看起来会变的 key：`LaunchedEffect(index){p=0f}` 救不了"从子页面返回"（index 没变），写在"触发进入子页面"那个函数里才可靠。
- **别相信"以前 lint 过了"**：增量分析会连续几轮漏掉同一处，只有完整 `lintDebug` 才扫得出。
- **局部 fun 后紧跟同级语句踩 lint SuspiciousIndentation** → 改 `val f = {}` lambda。
- **Kotlin 字符串模板：中文是合法标识符**，`"$var中文"` 会解析成变量，必须 `"${var}中文"`。
- **首帧门控**：常驻 + alpha 显隐的播放视图，TextureView 会留上一段最后一帧 ⇒ `playKey`/`readyKey` 双钥匙；同类问题全仓扫。
- **预览页动作回调传"当前条目"不传索引**；量窗口位置用 `onGloballyPositioned`（`onSizeChanged` 刚挂载返回 0）；既有工程先 grep 标准件再调数值。
- **`getOrElse(-1)` 会抛下标异常**：`indexOfFirst{}` 的结果用前一律 `idx in arr.indices` 守卫。

## 改造 WeiBack-rs（微博备份客户端）

源码 `小工具/weiback-rs-src/`，构建目录 `C:/wb-build/`（**必须英文路径**）。已实现并验证：每页条数可调（12~192，默认 48）、本地作者列表、批量删除（复选框 + 深/浅层）、删除按钮改「⋮」菜单。

**重装环境要点**
1. 三件套：**VS Build Tools**（MSVC）+ **Windows SDK x64** + **Rust**
   `winget install Microsoft.VisualStudio.2022.BuildTools --override "--quiet --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"`
2. `vcvars64.bat` 后**手工补 `LIB`/`INCLUDE`**：纯 Build Tools 不会自动指向 SDK ⇒ `LNK1181: 无法打开 kernel32.lib`
3. **`.bat` 必须纯 ASCII**（cmd 用 GBK 解析，中文注释会被当命令执行）
4. cargo 的 git 依赖要 PortableGit 在 PATH + `ghfast.top` insteadOf 镜像
5. vite `emptyDir` 被沙箱拦时用 `tauri build --config tauri.override.json`（把 `beforeBuildCommand` 置空）复用已有 dist
6. **sea-query 1.0 API**：`group_by_col()` / `order_by(col, Order::Desc)` / `add_group_by` 收迭代器 / `limit()` 收 `u64` / `Func::count(1)` 要 `Expr::from(...)`。查 API 看 `~/.cargo/registry/src/*/sea-query-1.0.2/src/query/select.rs`，别猜。
