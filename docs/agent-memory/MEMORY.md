# 项目长期记忆（小工具工作区）

> 每日进展写 `YYYY-MM-DD.md`，这里只放长期有效的东西。

## 本机环境

- Win11/AMD64。**GitHub release 下载被代理拦**（502）→ 加 `https://ghfast.top/` 前缀，
  **镜像文件务必用官方 sha256 校验后再装**；`api.github.com` 正常。
- **Bash 工具 PATH 缺 coreutils**：先
  `export PATH="/c/Users/22120/.workbuddy/binaries/PortableGit/versions/1.2.0/usr/bin:$PATH"`。
- Git Bash 会把 python 正则 `\d` 转成 `/d`（静默失配）→ bash 里正则一律写 `[0-9]`；
  `/tmp` 映射 Windows Temp，python 看不到 → 用 `cygpath -w`。
  脚本一律用 `python - << 'PYEOF'`  heredoc（反引号会触发命令替换）。
- PowerShell 捕获 Python 子进程 stdout 会丢内容 → 重定向到文件再 Read。
- managed Python 3.13 无 tkinter，GUI 用系统 Python 3.12；隔离环境 `binaries/python/envs/default`。
- adb 真机（小米 2304FPN6DC/HyperOS）：`cmd power wakeup` + `wm dismiss-keyguard`
  可唤醒、`screencap`/`am start`/`dumpsys package` 正常。
  **`adb shell input` 需重测** —— 2026-10-15 实证 KEYCODE_WAKEUP 注入生效
  （Dozing→Awake），旧结论"被 HyperOS 拦"已过时；`input motionevent` 支持多指，
  理论上能注入捏合。
- 分析录屏：ffmpeg（`imageio_ffmpeg` 自带 exe）。先 `fps=1,scale=200:-1,tile=6x4`
  拼网格看流程，再对关键时刻 `-ss T -frames:v 1` 抽**原始尺寸**帧看细节。

## 装第三方软件

- **先确认目标版本有对应平台构建产物**（WeiBack-rs Windows 最高 v0.3.1，v0.3.2 无 Win 包）。
- Tauri NSIS 支持 `/S` 静默装（当前用户模式免管理员）；装后验证程序路径/体积、
  注册表卸载项、开始菜单快捷方式、启动日志无 ERROR。

## 「收藏同步到系统相册」—— 已定论（2026-10-16，别再折腾）

**同步完全正常，小米相册也能显示 —— 只是它刷新很慢。**

实测数据：

| 位置 | 条数 |
|---|---|
| 取舍收藏夹 | 286 |
| 系统 `is_favorite=1` | **286** ← 逐条对上、0 差异（回收站里 0 条） |
| 小米相册（刚同步完） | 256 |
| 小米相册（**重启一次后**） | 270 → 273 ← 在渐进刷新 |
| 小米相册（**再重启/重开相册后**） | **正常了**（用户确认） |

**结论：**
1. **同步功能 100% 正确** —— 286 条全部写进 MediaStore 的 `IS_FAVORITE`。
   用户说"1.0.7 走通了"是对的。
2. **小米相册读这一列**（256→270→273→正常的渐进过程就是证据），
   **但必须重启手机或重开相册才刷新**。所以"看不到"不等于"没同步"。
   **✔ 2026-10-17 用户确认：重启系统相册后完全正常。此事终结。**
   **→ app 的同步提示已改成"已写入 N 项。系统相册可能需要重启或等一会儿才会显示出来。"**

**我犯过的两个错（都要记住）：**
- 核对脚本把 `storeKey` 编码写反（正确：**偶数 = 图片 `id*2`，奇数 = 视频 `id*2+1`**），
  算出"0 条对上"，得出"同步没用"的错误结论。
  **教训：核对脚本本身也要先自证**（拿一条已知答案的样本验一遍）。
- 拿 `content update` 单条写入的结果，推断"小米相册不读这一列"。
  **教训：不同写入路径不等价**（shell 的 update ≠ `createFavoriteRequest` 的特权路径）。

**★ 小米相册双向读写 `is_favorite`（2026-10-17 已验证）：**
用户在小米相册里**手动收藏一个新视频** → 系统视频收藏从 141 变成 142。
⇒ 小米相册不仅读、也写这一列。
⇒ **「同步系统收藏到这里」是有效功能，不该删**（之前我怀疑它只读不写，是错的）。

**当前状态（2026-10-17 实测）**：取舍收藏 294 条 ⊂ 系统标记 295 条，
只差 1 条（就是他刚在相册收藏、还没导回取舍的那个视频）。
**⇒ 取舍里没有任何一条"漏写"**。他说的"取舍里有几条在相册看不到"，
是**小米相册的显示/缓存**问题，不是标记问题。

**唯一的真实缺口**：在取舍里取消收藏，系统标记不会跟着取消
（同步只做加法）。用户倾向改成"完全对齐"，但**明确要求先把
"相册显示延迟"和"看不到"这两件事解决再动** —— 所以暂不改。

## 微博收藏备份（已定论）

用 **WeiBack-rs v0.3.1**（程序 `%LOCALAPPDATA%\weiback-rs\`，数据 `%APPDATA%\weiback\`，
手机号+短信登录，产出 HTML 归档）。自建 downloader 已废弃，教训：
①别让用户手动复制 Cookie；②接口参数别靠搜索推测；③**强风控站点用浏览器会话或
模拟正常访问节奏，不要独立 HTTP 客户端硬刚**。

## 「取舍」Android（主线）

当前 **1.3.74**（versionCode 122）。工程 `qushe-android/`，JDK17 + Gradle 8.9 + SDK 35，
无 Android Studio，命令行构建 + 小米真机验证。

```bash
export JAVA_HOME="C:\Users\22120\.workbuddy\binaries\jdk17\jdk-17.0.20.1+1"
"C:/Users/22120/.workbuddy/binaries/gradle-8.9/bin/gradle" assembleDebug lintDebug
```

**已纳入 git**（2026-10-15 建仓，`qushe-android/.git`）：`.gitignore` 排除
`build/`、`.gradle/`、`.kotlin/`、`local.properties`（本机 SDK 路径）、`*.apk`
（散落 38 个 / 1.2 GB）、签名材料；`.gitattributes` 统一 LF。
**稳定版 tag：`v1.3.54`（用户评价"比较完美"，后续以此为回归基准）。**
**已配本地裸仓库备份** `D:/backup/qushe-android.git`（remote 名 `backup`），
`.git/hooks/post-commit` 自动 push（失败不阻塞提交）。**异地远端仍未配。**
大范围编辑前先 `git status` 确认干净、`git diff` 可回退；
改坏了 `git checkout -- <file>` 一键回退（已验证有效）。

构建后把 `app-debug.apk` 复制为 `qushe-vX.Y-debug.apk`；改版本号别漏
`SettingsScreen.kt` 里硬编码的版本字符串。

---

## Compose：绘制 / 层级（最高频踩坑）

- **★ `Modifier.background` 只影响它之后的那截链**：要让一层整体（含背景）淡入，
  `.background()` 必须写在 `graphicsLayer { alpha }` **之后**。写在前面 = 背景在 alpha
  层外 = **永远不透明**，`alpha=0` 完全无效（"捏合进去黑屏一下"查了三轮就是它）。
- **★ 圆角/裁剪同理**：要让底色跟着圆角，`background` 必须写在 `clip(圆角)` **之后**。
  外层写 background = 圆角外永远留一圈方角底色（"左右滑时黑底一起滑"就是这么来的）。
- **★ 多层垫底时，变换必须挂在"包住全部层"的那一层**：`graphicsLayer{scale/translation}`
  只挂在其中一层 ⇒ 其他层不跟着动（缩放露出没缩的底层 / 拖动留残影）。
- **"alpha = 0" 不等于"看不见"**：始终存在的底层只要还占着节点，上层未出图时就会整片
  露出来。要真消失必须让节点**不进组合**（`if (cond)`，cond 用 `derivedStateOf` 包）。
- **"内容为空" 不等于"不画"**：带 `background`/`padding` 的容器哪怕没内容也留**空壳**
  （提示胶囊的 `Row` 在文案为空时留深灰小方块，被有内容的胶囊盖住，很难定位）。
  该藏就 `return`/`if`，别只把文字清空。
- **"闪一下"分两种**：**闪黑** = 那一帧根本没内容（占位链路没接上）；**闪糊** =
  占位太糊、清晰度跳变（链路对了只差清晰度）。用户报哪个词就查哪一层，别混。
- **不透明 ≠ 挡触摸**：全屏覆盖层也要 `pointerInput` consume；深色半透明底压在黑界面上
  等于不存在（要可见用浅色半透明圆+淡边）。
- **★ 装饰性效果不要动布局基准**：为了"圆角落在图片四角"把变换层 `aspectRatio` 缩小，
  会改变整条链的尺寸基准 ⇒ 用户看到"图片会自动放大"（试了两版都失败）。
  正解 = **在图之上、变换层之内画四条角路径**（每条 = 角上方块 − 四分之一圆，填页面底色）：
  圆角准、跟着缩放平移、布局零改动。**只画四个角**，"图片以外整片盖掉"会把黑边也填上。
- **"调好的数字"要提出来共用**：圆角 `rememberContentCorner()`、
  图间间隙 `rememberPageGap()`，各自被好几处用；各写各的必然漂移
  （曾经一个是屏宽 ×2.2%、一个是写死 4.dp）。
- **★ "存在的函数说未解析"几乎总是括号问题**：重排/切片编辑大文件后，
  少一个收尾括号会把后面所有顶层定义算进函数体内，
  报错指向的却是那些函数本身。**先看顶层函数列表，再看编译错误。**
- **重排大文件：用"显式行段"，别用"按缩进猜顶层语句"**。启发式脚本会把
  多行调用参数的收尾 `)`（缩进 8）当成新语句，一页切成 125 个 item 直接编译失败。
  正解：先干跑打印每行、确认边界落在**空行**上，再按 `(起, 止)` 显式切。
- **`Column + verticalScroll` 会一次性组合全部内容**；页面重就用 `LazyColumn`。
  注意 `LazyColumn` 的各个 `item` **不共享作用域** —— 块内声明的局部 `val`
  不能被别的块用，得提到外面。
- **★ 同一件事有两个"真源"时，只改一个 = 半生效**：底栏读 `tab`、内容读 `pager`，
  `BackHandler { tab = Tab.Review }` 只改了前者 ⇒ "底栏回首页、内容没回"（用户反馈）。
  凡是有 `goToX()` 这类"把两件事一起做"的函数，回调里必须走它；
  `BackHandler(enabled = …)` 的判据也要跟**真正决定内容的那份状态**一致。
- **★ 优化手段本身可能成为新的卡顿源**：`beyondViewportPageCount = 1` 把组合成本
  "挪到空闲时"，但启动时同时组合三页、两页都是网格各自查 MediaStore ⇒
  后台持续解码/查询，把省下的帧预算又吃掉了（1.3.55 引入 → 1.3.56 撤回）。
  **把成本挪去空闲，只在那个空闲真的空闲时成立。正解是把重页面本身做轻。**
- **Pager 卡顿先看 `beyondViewportPageCount`**（默认 0 ⇒ 滑动中现组合下一页）；
  但加它之前先想清楚后台 IO 会不会被带起来。
- **"信息胶囊 vs 详情面板"的分工规则（用户定，全 App 统一）**：
  胶囊只有一行 ⇒ 只放 `日期 · 文件夹 · 大小`；分辨率/时长/地点/EXIF 一律进详情面板
  （硬塞进胶囊的结果是挤成一团再被省略号截断）。
  三个页面共用同一个 `InfoPill` + 同一个 `PhotoDetailContent` ——
  **胶囊出现多次就要有唯一构造函数**，各写各的必然漂移。
  位置也讲分工：信息入口居中（胶囊），操作按钮靠边。
- **"拍摄时间"和"添加时间"是两回事**：截图/下载/微信保存的文件，
  EXIF 拍摄时间与 `DATE_ADDED` 入库时间可能差好几年，混成一个"日期"会误导。
  `MediaItem.dateAddedMillis`（**单位是秒**，进模型统一成毫秒）；两者相同则只显示一行。
- **圆角：值必须全 App 唯一，手法按载体分两种**。唯一来源 `rememberContentCorner()`
  （屏宽 × 2.3%、上限 14dp）。格子用 `clip(rememberContentShape())`（格子本身就是那个矩形）；
  大图用四角遮罩（Fit 画在整屏盒子里，clip 整屏只会裁到黑边）。
  首页卡片、胶片条缩略图不是照片，不参与统一。
- **★ 垫底"下一张"别用小阈值淡入**：alpha 绑在"翻页判定线"的 18% 上，
  而判定线只有 72dp ⇒ 手指走十几像素下一张就整个糊上来。
  正解 = **停在旁边一整屏 + 和当前这张同速移动**（`translationX = dx - dir*屏宽`），
  静止时在屏幕外、拖动时滑进来、回弹时滑回去、翻页成立时正好归位。
  配套：出场目标必须**正好一屏**，甩过头会把下一张带过 0.3 屏、交接错位。
- **★ 增量式 `snapTo` 会丢帧**：`snapTo(currentValue + delta)` 读的是挂起写出来的当前值，
  每帧还要 `launch` 一次；后一次取消前一次时**那一段增量就丢了** ⇒ "拖一点点顿住"。
  正解：**手势内用本地变量累加、每次 `snapTo` 写绝对值**。
- **轴锁定顺带简化判定**：越过 touchSlop 时按主方向定死轴 ⇒ 位移向量恒有一分量为 0，
  判定只看那一个轴，不会"斜着划同时命中翻页和删除"，也少一次比大小猜方向。
- **★ 精确的边界估计不等于更好的体验 —— 它可能把功能锁死**：
  给长图传 `contentRatio` 会让 `snapScale = 容器/Fit后绘制区` 取到 1~1.25
  ⇒ 上限只有一两倍，且横向 `panLimit = 0` ⇒ "放大后拖不动"（查了三轮）。
  **长图不传 `contentRatio`**（照收藏页），上限回 `maxScale`、边界用宽松估计。
- **单击 + 双击共存 ⇒ 交给 `detectTapsWithoutConsuming`**：它内置"延后 280ms 等第二击、
  双击取消单击"。放在父层"没拖过"分支里虽然"松手当帧"，但每次双击都会切两次（抖一下）。
- **状态机不能用"投影"当指针**：三态循环拿 `filmstripVisible` 当前值推下一态，
  而状态①和③在那个变量上取值相同 ⇒ 永远回不到①。必须自己记 `stage`。
- **两个 `weight(1f)` 不等于居中**：那只保证"扣掉两侧内容后的剩余空间"居中；
  两侧宽度不等（固定按钮 vs 随数字变化的标签）就会偏。要真正居中得给两侧各套等宽 `Box`。
- **"随心所欲/不像 pager"多半是 rotation**：`rotationZ = (offsetX/80).coerceIn(-8,8)`
  这类"跟着歪一点"的花样就是"不按轨迹"的观感来源。pager 严格一比一平移。
- **★ 淡出和位移绑在同一层 = 退场"卡一下"**：alpha 按 `offset/阈值` 算，
  而退场要把 offset 涨到屏高 ×1.15 ⇒ 内容在动画前 20% 就淡没了，
  剩下 100+ms 屏幕全空。**拆层**：黑底只淡出 / 内容只位移（不淡出）/ chrome 只淡出，
  且 **chrome 必须画在内容之后**，否则被整屏内容盖住点不到。
- **★ 组件已经内聚的行为，调用方就原样传参**：`DayViewScreen` 用
  `markedKeys → removedFlags → CellSlot 宽度收 0` 实现"格子飞出去、同排左移补位"；
  调用方再 `filterNot { in marked }` 就等于**换了一套机制**，整排重新布局、偶发残留。
- **★ 同一个动作有两条路 ⇒ 净效果为零**：chrome 显隐既有外层 `Box.clickable`
  又有 `ZoomableImage.onSingleTap`（后者因双击判定延后 280ms）⇒ 切两次互相抵消，
  观感是"按了没反应"。**排查"点击没反应/状态跳"先数这条动作有几条路径。**
- **★ 父节点抢手势，子节点的"放大后拖动看细节"就废了**：`DraggablePhoto`
  在 Main 阶段先拿到单指事件并 `consume()`，`ZoomableImage` 收不到
  ⇒ 长图放大后拖不动。**放大态父节点一个事件都不消费，整条让给子节点**
  （和 `HorizontalPager` + `ZoomableImage` 同一套分工）。
- **单击切 chrome 别走 `ZoomableImage.onSingleTap`**：要和双击判定共存
  ⇒ 必然延后 280ms、手感钝。放在父层"没拖过"分支里（松手当帧判定），
  双击时调两次净效果为零 ⇒ 互不干扰。
- **★★ 一个 state 有多个写者时，每个写者只写自己"拥有的那次变化"**：
  `chromeVisible` 既有 `clickable` 的 toggle、又有 `onZoomedChange` 的
  `chromeVisible = !isZoomed`（而 `ZoomableImage` 每次手势结束都报一次）
  ⇒ 切掉→写回→再切→再写回，**看起来就是"只收不回来"**。
  解法：判 `if (isZoomed != wasZoomed)` 才写。
- **状态变化要报"跨越的那一刻"，不是"手势结束"**：只在收尾报的话，
  "放大时收起 chrome"会晚一整段（图都铺满了 chrome 还杵着）。
  在 `scale` 越过阈值**那一帧**报，且**只在跨越时报**（不每帧报，调用方要自己判重）。
- **toggle 还是强制赋值，看用户意图**：chrome 缩回 1x 用**强制显示**而非 toggle ——
  用户刚松手的是一次"看图"动作，不是"收起 chrome"。
- **★★★★ SharedPreferences 读不是 Compose 状态**：各页面直接读
  `prefs.xxx` ⇒ 设置改完不重组 ⇒ "要切换几下才跟上"（`relativeDates` 那次）。
  **凡是 UI 要跟随的设置，必须由 `AppRoot` 持有 state 再往下传。**
- **★★★★ `LaunchedEffect` 的 effect 体里绝不修改自己的 key**：
  `LaunchedEffect(x) { x = emptyList(); launcher.launch(...) }` —— 改 x 那一下
  键就变了 ⇒ 协程被取消重启 ⇒ `launch()` 来不及执行。表现是"点了什么都没发生"。
  要改内容就另加一个只用来当键的 `tick`。
- **★★★★ 动手前先 grep 有没有现成实现**（最不该犯的错，2026-10-16 犯的）：
  `MediaRepository.allSystemFavoriteKeys()` 本来就在且编码正确，
  我又新加了一个 `allFavoriteKeys()`，把图片/视频的奇偶写反
  ⇒ 两侧永不重合 ⇒ 差额永远是"全部待同步"、写进去也永远对不上
  ⇒ "收藏同步完全没用"。
  **同名/近名重复函数是这个工程反复出问题的根源**（`SegChip` 那次也是）。
  凡是有两处以上要算同一个 key 的，抽成唯一函数（`mediaStoreKey`）。
- **★★★ 同步/记账类功能：别自己记"已同步"，去问对方**（收藏同步返工三次的结论）。
  自己维护的"已同步"集合必然和事实脱节（导入时就被标成"已同步"）。
  **正解：每次同步读对方的当前状态、算差集**，写没写进去下一轮再问一次就知道。
  `markSynced` / `markUnsynced` / `syncedFavorites` 已删。
- **★★ 单向同步时，绝不做自动"删除对方"**：系统收藏可能来自别处（云端恢复、其他应用），
  我们没有能力区分"谁写的"。自动取消 = 可能毁用户数据。**只做加法，减法只由用户主动触发。**
- **★ Box 的子节点默认堆叠在左上角**：把 `Row { A; B }` 改成 `Box` 时，
  A/B 若不自己套一层 Row 就会**叠在一起**（用户反馈"按钮堆叠到一起"）。
- **★★ 平台能力先实测，别猜**：`adb shell content update --bind is_favorite:i:1`
  返回 `No result found` ⇒ 这一列普通应用写不进去；`createFavoriteRequest` 一次带
  276 个 URI 弹窗直接弹不出来（要分批）。用 `content query` 比读代码可靠得多。
- **★★★ 记账类字段必须区分「谁写的」和「什么时候写的」**：
  `importSystemFavorites` 把导入条目标成"已同步"，而"已同步"=「取舍写入系统并核对成功」
  ⇒ 手动同步只推 1 条、用户以为没反应。给用户一个 `force = true`
  **忽略记账全量重推**的入口，比纠结记账准不准有用。
- **★ 局部 lambda 里要用 `align` 就得改签名成 Scope**：
  `photoActions: (@Composable (MediaItem) -> Unit)` 写 `align` 编译不过，
  **在外面包一层 `Box` 没用**，签名必须是 `BoxScope.(MediaItem) -> Unit`。
- **★★★ 结论没坐实前不要给方案建议**（2026-10-16 差点犯）：我凭一次
  `content update` 就断定"小米相册不读 is_favorite"，还给用户列了三个
  "替代方案" —— 结果真相是**同步一直正常、相册只是刷新慢**。
  **另：核对脚本本身也要先自证**（拿一条已知答案的样本验一遍），
  我的 `storeKey` 解码写反过，差点据此删掉正常功能。
- **★★★ 同一病根（已犯三次）：替用户做决定 + 不告诉他**。
  ① 下线设置项只删 UI、留着已存的 false 值；② `swipeUpDeletes` 残留；
  ③ 同步失败就 `autoSyncFavorites = false` 偷偷关掉用户的开关。
  **通则：任何"失败就自动改用户设置"的逻辑都要去掉**，只如实报事实；
  下线设置项 = 删 UI + 停读 +（可选）清存量，三件事一起做。
- **手动入口要和自动入口走同一条链路**（别各写一份，必然漂移）。
  只删 UI 的话，曾经把开关关过的设备会**永远读到旧值且再也改不回来**
  ⇒ 行为悄悄变样（1.3.48 删了「上滑删除/收藏」，`swipeUpDeletes` 残留 false
  ⇒「回到那天」上滑变成收藏、格子不消失）。
  没有开关就别留读它的地方：`Prefs.swipeUpDeletes` 直接 `get() = true`。
- **同一个交互在不同入口必须同一套语义**：跨入口复用组件时，
  连"什么时候真正提交"也要一起复用（只标记 + 撤销 + 退出时统一提交），
  否则用户会感到"这个页面不太一样"。
- **★ 同一个参数往往同时管着好几件事，删它会连带别的**：
  `contentRatio` 同时管着 ① `snapScale`（吸边界/放大上限）
  ② `decodeSize`（给 Coil 的解码尺寸）。为放开长图上限而删掉它，
  结果**解码退化成容器尺寸** ⇒ 长图放大后必然是插值的糊图（1.3.62→1.3.65）。
  正解：拆开，另加 `sourceSize`（原始像素）专门给 decodeSize 封顶。
  **改"看起来只管一件事"的参数前，先 grep 它到底被谁读了。**
- **★★ `Dialog` 是独立窗口：调用方在外面盖的东西一律被压住**。
  `MediaPreviewOverlay` 整体是 `Dialog { ... }` —— 往它上面画视图时，
  画在**调用方**那边属于 Activity 窗口，会被整个盖住，
  症状是"代码明明打开了，界面上什么都看不到"。
  **正解：组件暴露 `overlayContent` 槽位（类型做成 `BoxScope.() -> Unit`，
  否则调用方拿不到 `align`），内容塞进同一个窗口、排在该窗口所有内容之后。**
  跨窗口的 z 序不是"后写的在上面"，而是谁的窗口在上面。**已踩两次。**
  **★ 而且 Dialog 的内容框不与屏幕重合**（实测：顶边在状态栏下沿、底边还越过屏幕下沿）——
  `fillMaxSize` 填的是**窗口**不是屏幕，底部会掉到屏幕外。
  overlay 要显式给高度 `屏幕高 − 窗口顶边`，顶边不动（Dialog 里 insets 读到 0）。
- **"调好的数字"共用前先分清场景**：缩略图网格的 4.dp 是**密集小图**的间距，
  和**大图之间的接缝**不是一回事。`rememberPageGap()` 的三处是
  瀑布流格子 / 整理页翻页接缝 / 预览翻页（`HorizontalPager(pageSpacing=…)`）。
- **脚本改完必须 grep 关键符号确认落地**：`str.replace` 锚点没匹配上会**静默跳过**，
  表现为"状态会切但什么都不显示"，很容易误判成遮挡/层级问题。
- **★ 覆盖层起不来时，"什么都不发生"远好于"被踢回去"**：切换全屏视图时**别把下层拆掉**，
  让新层**盖在上面**（且排在后面 = z 序在上）。覆盖层没起来时用户最多觉得"没反应"，
  拆掉下层则会被弹回去、像手势失效。另：`remember` 住传下去的 list，
  每次重组换新实例会把 effect 重启。
- **要一道干净的黑缝：缩放只作用在"图"，底色保持满屏**
  （`PhotoLayer(photoScale)` 把 scale 只加在 AsyncImage 上）。缝还要**越翻越窄** ——
  到位那一刻它就是下一张"当前图"，不收窄的话最后一帧会"长出"一道黑边。
- **★ "比 pager 突兀" = 出场用了 tween 而不是 spring**：
  `tween(200ms 走完一屏)` 是定时的（1080px/0.2s = 5400px/s 恒定），
  而翻页判定线只有 72dp ⇒ 轻扫时手指才 1000px/s，**松手瞬间速度跳 5 倍**。
  换成 `spring(StiffnessMediumLow)`（`PagerDefaults` 同款）——
  `Animatable.animateTo` 会把当前速度当弹簧初速度 ⇒ 速度连续。
  **语义决定用哪个**：翻页"接住手指"用 spring；删除"扔掉"用 tween。
- **会骗人的设置项直接删**：某选项的分支从来没调 `onDecision` ⇒ 选中它就失效。
  **选项存在但无效比没有更糟**。偏好值本身保留（老配置读得出来）。
- **跨页复用组件时，起点必须"实测"不能"估"**：被 pager 平移过 / 带偏移的容器，
  用尺寸去估会有偏差，而飞行动画第一帧就看得出来 ⇒ 用 `boundsInWindow()`。
- **收尾动画要跟参照物用同一套参数**：用户说"像整理页上滑删除那样丝滑"就直接复用
  `flingTo`（tween 200 FastOutLinearInEasing）+ `spring(StiffnessMediumLow)`。
  默认 `spring()` 更硬，手感发木。
- **★ "动画跑完才 commit 状态"的设计里，动画时长 = 用户的感知延迟**：
  翻页的 `onDecision` 在出场动画之后才换 `index`（换早了会瞬移回正中），
  于是翻页动画走完一整屏要多久，用户就得等多久 —— `StiffnessMediumLow`（400~600ms）
  单次滑动看不出来，**连续滑动就变成"滑一张要等一下"**。
- **手势本地累加值的起点必须是当前 Animatable 值，不能是 0**：上一段动画可能还在跑，
  `offsetX.value` 已 -800，从 0 累加 ⇒ 第一次 `snapTo` 把照片瞬间拽回 -10（"跳一下"）。
- **滑动类手势的判据必须是"距离 _或_ 速度"**：只判距离的话，快速一甩
  （手指没走多远就松手）会被判成"没到阈值"⇒ 每次都弹回去（用户报"触发不了"）。
  用 `VelocityTracker`（`androidx.compose.ui.input.pointer.util`）。
- **★★ `pointerInput` 的闭包只在键变化时重启**：里面读 `var x` 会永远拿到
  **首次组合**那个值（必须 `rememberUpdatedState` 转发）。
  **把值放进键也救不了** —— 键不变时根本不重启（1.3.59 的 `zoomed` 就这样废掉的）。
  依赖布局结果的量（容器高度）同样拿不到 ⇒ 也要转发或放进键。
- **常驻的垫底层会从黑边里露脸**：顶层照片是 Fit 画的，竖图上下留很宽的黑边，
  垫在下面那张全屏图就会在黑边里露出来（"第一张下面有第二张"）。
  按 `max(|dx|,|dy|,收走进度)` 淡入即可：平时干净，翻页时仍有底牌防闪黑。
- **Dialog + 宿主 `enableEdgeToEdge()` = 半个 edge-to-edge** → 要
  `DialogProperties(decorFitsSystemWindows = false)` 显式关掉。

## Compose：手势

- **"位置不对"先查容器 `contentAlignment`**：`BoxWithConstraints(fillMaxSize)` 内容默认贴左上；
  改 `Center` 又会把指针先水平居中。正解 `CenterStart` + 补偿指针自身半宽。
- **滑条参考系不能动**：刻度静止、指针动，否则像"背景在滑"；刻度层不带底色，指针画在
  独立层；滚动状态读放进 `graphicsLayer{}`/draw，别在组合期读（每帧重组）。
- **拖拽增益与松手惯性是两份参数**：`DRAG_GAIN`（1:1.25）+ 松手速度 ×1.6。用户说"幅度小"
  先问是哪一段 —— 给拖拽加增益会毁掉"跟手"。
- **惯性 = 指数衰减滑行**：每帧 `withFrameNanos` 取真实间隔、`v *= 0.998^dt`，位移直接
  `dispatchRawDelta` 进滚动状态（不吃 Mutex，随时按下能接住）；新手势按下先 cancel 上次 job。
  只做"投影+动画"等于没有滑行。
- **父子手势分配**：子节点优先认领自己方向并 consume；父只 `Final` 兜底
  `if (!isConsumed) consume()`；父要认领某方向才在 `Initial` 判方向+consume，且只抢该方向。
  阈值用 `viewConfiguration.touchSlop`，不能更小。
- **全量 consume 会吃子组件手势**：按本帧位移主方向判别，竖直才消费、水平放行（阈值 3px
  防吞点击）。**消费了手势就必须让它真的能动** —— 写过「1x 归零」又「1x 也消费」，
  结果事件被吃掉位移被抹零，用户看到"一进来就吸死，左右上下全动不了"。
- **手势"难触发"先查层级竞争，别急着调阈值**：`detectDragGestures` 锁定第一根手指就
  一路拖到底，第二根手指落下也不松手 ⇒ 捏合量被平移吃掉。自己写 `awaitEachGesture`：
  **第二根手指一落下就放弃拖拽 + 回弹，且不消费任何事件**。
- **"逐帧累加 + 反向扣减"的判定不可靠**：真实手势**不单调**（调整姿势时反向帧把攒的量
  扣回去）⇒ 用户要做远超阈值的动作才能触发。必须换**单调量**：记起手基准，算 `1 - cur/base`。
  （Compose 1.7.5 没有 `calculateCentroidSize`，直接 `(p0.position - p1.position).getDistance()`。）
- **渐进阻尼 ≠ 硬边界**：硬 `coerceIn` 手感是"1px 跟手、到边戛然而止"。要 iOS 橡皮筋
  `c*(1-1/(over/c+1))`（越拖越沉、最多拉出 c 像素），松手再动画吸回 —— 两套边界并存。
- **状态机切换时检查"为旧状态算的约束"**：捏合触发后 `lowerBound` 从 0.72 变回 1f，
  `coerceIn` 把已缩到 0.85 的照片**硬抬回 1.0** ⇒ "刚好触发那一帧猛地弹大"。
- **横滑手势与横滑翻页只能存其一**，边界写进注释。
- **可逆动作当场生效，不可逆的留到退出时结算**：删除攒成 `pendingDelete` 退出时一次提交。
- **`RailAction` 这类"图标+文字"的 label 要恒定，状态只交给图标**：试过"已标记"、
  "取消删除"用户都不接受 —— "颜色变了就够了"。
- **★ `awaitEachGesture` 是受限协程作用域**：里面**不能调成员挂起函数**
  （`Animatable.snapTo` / `stop` 都编译不过：
  "Restricted suspending functions can invoke member or extension suspending
  functions only on their restricted coroutine scope"）。
  解法（本工程 `DraggablePhoto` 的现成写法）：`scope.launch { animatable.snapTo(...) }`
  —— `launch` 不是挂起函数，新协程不受限。
  `detectDragGestures` 的 `onDrag` 是**非挂起** lambda，同样调不了 snapTo。
- **门控动画要配保险丝**：把淡入绑到"内容就绪"之后，一旦回调没来就是**永久透明**，
  比原来的跳变严重得多 ⇒ 必须另加一条 `LaunchedEffect(Unit){ delay(TIMEOUT); ready = true }`。

## Compose：性能 / 组合

- **组合期读 state 是最隐蔽的卡顿根因**：
  ① **调用点会把局部做对的事抵消掉** —— 局部读在 `graphicsLayer{}` 里是对的，
  但调用点写 `Overlay(offsetX = offsetX.value)` 就把整棵子树登记成观察者了。
  **改完局部必须回看调用点**。
  ② **"结果只有两种"不等于"开销小"**：读取动作本身在组合期就等于每帧重组，
  高频 state 的派生标量一律包 `derivedStateOf`。
  ③ **每帧变的量不能当 `LaunchedEffect` 的键**（每帧重启协程），只能在协程体内读。
  ④ `RoundedCornerShape` 组合期现算不会重建裁剪路径（equals 比值），但每次 new 是白付的
  GC 压力 ⇒ 热路径上的形状提顶层单例。
  ⑤ **已正确、不要动**：`CellSlot` 的 `widthPx` 必须在组合期读（要喂 `Modifier.width`）。
- **可跳过性**：`MediaItem`（带 Uri）和捕获容器的 lambda 是不稳定参数 ⇒ 格子整片不可跳过。
  抽 `@Immutable` 小模型 + `@Stable` 回调包装类即可。
- **下游 remember 的键里凡是"调用点现造的 lambda"，换成 `rememberUpdatedState` 转发**：
  否则每次交互都换新实例，拖着一大片子树重组（`DayCellActions` 一次拖着 ~50 个格子）。
- **仓库数据不是本文件 state**：点了 toggle 后必须自增一个 tick 触发重组，否则选中态不翻。
- **别停在最像的根因**：不确定就把所有可计算的成本中心一次清掉（虚拟化 + key +
  只给需要的格子建动画）。

## Compose：布局 / 自绘虚拟化

- **虚拟化**：一排全量 `Row` 组合几百个 `AsyncImage` 会卡死；按滚动偏移算可见区间，
  区间外放等宽 Spacer —— 占位与真格子用**同一计算函数**。单节点宽上限 262143px，
  超宽空档拆多个 ≤200000px。
- **间距属于容器**：`Arrangement.spacedBy` 的 gap 不随子项消失；要"消除子项连同间距"
  就改成子项尾 padding。**但 `Modifier.width(w).padding(g)` 的总宽仍是 w** —— padding
  在盒子内部只压窄内容。想让步进含间距必须让**总宽**真的加上（外层 `width(w+g)` +
  内层放图片），否则模型与真实布局差 `k × g`，且误差随可见区间起点跳变 ⇒ 定位偏、
  横滑卡顿、多排错位一起出现。
- **模型必须与布局同取整**：累计偏移一律 `roundToPx`（`toPx` 留小数，几百格累积成几十
  像素漂移）；视口、错位偏移、单格宽、累计数组全链统一。
- **`clip` 形状要共享实例**：每个格子现算会让所有格子的 `clip()` 全量重建裁剪节点
  ⇒ 横滑"图片左右卡了一下"。
- **多排共用一个 `ScrollState`**：「去留」录屏用**互相关**实测（43fps/137 帧）上下排位移
  **100% 相同**，两排锁死，共用天然同步。代价是两个 `maxValue` 不同 ⇒ 短排先到顶，
  靠**短排补尾部垫片**（`padToPx`，不进 `cumPx`）让 maxValue 恒等。**垫片要小**：
  分排用 `splitRowsBalanced` 按宽度贪心均衡把总长差压到一格以内。
  `scroll.maxValue` 不能当刻度条分母（被"最后布局的那排"覆盖，在两排两值间跳），
  改用各排 `SideEffect` 上报自身总长（去重）、外层取 max。
  拖动定位别用 `animateScrollTo`（松手那帧指针猛跳），用 `dispatchRawDelta`。
- **`ScrollState` 外部只能调 `scrollTo()`，且会被 `maxValue` 夹**：`value` 的 setter 是
  **private**（字节码实证），`dispatchRawDelta` 同样过 `coerceIn(0, maxValue)`。
  ⇒ 要精确定位就**别猜时机**："每帧检查、没对上就再推一次"（带次数上限）比
  "等某个值就绪"稳得多。
- **「居中」要对齐格子中心，不是左边缘**：`cumPx[k]` 是左边缘，直接当中心 ⇒ 偏左半个格宽，
  要 `cumPx[k] + w/2`。
- **属性互斥时改分派，别硬凑**：如「焦点图在上排」×「分排固定 index%2」互斥，就让
  **哪一组上排由焦点图决定**，而不是去改滚动算式。
- **补偿性 padding 治不了观感**：给短排补垫片来对齐总长，垫片本身就是用户看到的空白，
  藏不掉 —— 治本要动分排本身（按宽度贪心均衡）。

## 图片加载（Coil / MediaStore）

- **Coil 不给 size 就按原图解码**（4000×3000 ≈ 48MB/张）：用 `ImageRequest.size(显示尺寸)`。
  但**动图不能给 size** —— `ImageDecoderDecoder` 会翻成 `ImageDecoder.setTargetSize()`，
  对动画源不安全（会闪退）。
- **放大后糊 = 解码尺寸按 1x 算的**：`AsyncImage(model=uri)` 自动挂 `ConstraintsSizeResolver`
  （请求尺寸 = 容器尺寸），而放大是 `graphicsLayer` 缩放、**解码器不知道**。
  要清楚就得显式 `.size(容器 × 放大上限)`（Coil 只在 `defined.sizeResolver == null`
  时才挂 canvas 尺寸）。**推不出放大上限时宁可不做** —— 否则白解一张巨图。
- **★ 跨页复用一个 Bitmap 的唯一办法是"请求尺寸完全相同"**：memoryCacheKey 含 size，
  尺寸不同必然 miss。所以让来源页把 `decodeSize ?: container` **回调出去**、目标页照用，
  这才是"同一份像素"。垫低清缩略图只会把"空白几十毫秒"换成"清晰度跳变"，都是闪。
- **★ 占位必须是"已经拿在手上的对象"，不能是"一个会去取它的请求"**：走 Coil 的占位
  （系统缩略图、缩略图请求）都要 IO + 一帧回调，纯黑背景上那一两帧就是"闪一下"。
  正解：调用方提前把缩略图**读成 `ImageBitmap`** 交进来，直接 `Image(bitmap=...)` 同步画。
  （"上一张垫底"不行 —— 只在前后同一内容时成立。）
- **★ "避免空档"比"尽快填满空档"更稳**：背景纯黑 + 内容异步就绪时，往空档里塞东西
  都是在赌"来得及"。正解是让**旧内容留在原地**直到新内容可画。
- **自制翻页/列表要自己实现预加载**（`HorizontalPager` 的 `beyondBoundsPageCount` 帮不了你）。
  预加载的**请求尺寸必须和主请求一致**才可能命中缓存 ⇒ 放在知道尺寸的那一层。
- **缩略图墙用系统缓存 `ContentResolver.loadThumbnail`**：图片视频都适用，比 Coil 自己解
  快一个量级。但**只给"明确要一格小缩略图"的请求用**（靠 `ImageRequest` parameter 打标记），
  系统那份最长边只有 512。加保护：**短边 < 目标一半就丢弃**，退回常规解码。
- **Coil 2.7 的 `Parameters` 没有 `Key<T>`**：键就是 `String`；写用 `setParameter`，
  读用 `options.parameters.value<Boolean>(key)`。默认不参与 memoryCacheKey。
- **★ Content Uri 里没有文件名**：`.../media/12345`，`lastPathSegment` 是一串数字 ⇒
  **任何"从 Uri 猜扩展名/媒体类型"的代码都是坏的**。要类型就由调用方传进来。
- **MediaStore 的 WIDTH/HEIGHT 不带 rotation**，横竖屏以播放器 `videoSize` 为准。
- **★ `DATE_ADDED` ≠ 拍摄时间**：它是"被扫描进 MediaStore 的时间"（老照片复制、微信收图
  都对不上）⇒ 按时间一律 `DATE_TAKEN`，再挂 `DATE_ADDED` 当 tie-breaker。
- **`LazyVerticalGrid` 的 `itemsIndexed` 要单独 import**（grid 包），否则"类型推断失败"。

## 跨页飞入 / 共享元素动效

- **三道坎**：① 目标页那一层若**瞬间完全不透明**，底下那张就"啪"地被盖住 ⇒ 要从透明淡入
  （两张图在起点本来就重合，视觉上是同一张）；② 目标元素的请求和来源页 **size 不同 ⇒
  cache 必然 miss**，重新解码的几十毫秒里格子是空的 ⇒ 用系统缩略图当 placeholder 顶住
  （1~2ms），高清到了 Coil 自己淡入；③ **起点必须按"触发那一刻"的形态算** ⇒ 把触发时的
  scale 等状态当回调参数带出去，不要回调去"读当前值"。
- **让目标元素自己从起点插值**，别"另画一张会飞的图"（后者要再解一次码，慢于动画就会
  "目标已到位、飞行图才出现"）。起点用 `onGloballyPositioned` + `ContentScale.Fit` 算，
  终点用目标元素自己的 `boundsInWindow()`（**不受 graphicsLayer 变换影响**，正是最终位置）。
  **终点没测到之前那个元素必须 alpha=0**，否则会闪一帧最终位置。
- 有飞入时目标页**只淡入、不缩放不位移**，两层变换叠加会算不准起点。
- **统一比例取几何平均**：两头宽高比有差时，分别按宽高算 scale 会**拉变形**。
  `sqrt(sx * sy)` 全程不变形。
- **异步内容 + 立即启动的进场动画 = 亮度跳变**：`enter` 在 `LaunchedEffect(Unit)` 里
  立刻开始淡入，而数据还在加载、loading 分支画着**屏幕正中的转圈** ⇒
  "照片被压暗 → 转圈突然消失、图突然亮回来"。修法：进场动画**绑到"内容就绪"**，或**提前预加载**。

## 根因与流程

- **用户说"偶发"先找那条只在一半情况下成立的路径**，别急着怀疑阈值或性能 —— 那才是
  "偶发"的形状。
- **用户说"被挡住/太显眼"先确认是哪个页面哪个控件** —— "最近改过的"不是"用户看到的"；
  **说"两排/一列"先确认行还是列**；**参考截图先分辨常态界面还是引导/教程页**
  （被带偏过两次），看不准就问，别凭猜改布局。
- **测平移必须用互相关，不能用像素差**：相邻帧"变化量"在画面内容变化时会给出假位移差
  （1.3.19 据此误判"两排独立滚动"，被当场纠正，白改一版架构）。正确做法：取一维信号
  （列均值）、找使两帧最匹配的平移量。**用单一可疑证据改架构之前，先换一种测量方法复核。**
- **改架构时要回头清掉依赖旧不变量的分支**（分排从 `index % 2` 改成贪心均衡后，
  `seededTarget` 里 `focusIndex / 2` 的 else 分支让**两排都去定位**、共用 ScrollState
  时互相覆盖 ⇒ "有时候偏左有时候偏右"）。
- **注释和实现不符当场对齐**：`DayFilmstrip` 函数头写"下排传 null"，代码却传
  `focusItem + ownsFocus = true` —— 上面那个 bug 就长在这里。
- **按缩进批量删代码后必须回看调用点**（删提示块时把 `DecisionOverlay(...)` 调用点一起删了，
  编译器不报"少了调用"，提示整整两个版本不显示）。**批量删完 grep 被删区域周边的函数名。**
- **一个动作只留一个入口**：组件里同时保留新旧两套触发方式，迟早出现"这里点了没反应"。
- **"改成和 X 一样"先确认适用范围**（"喜欢页和收藏页的按钮都改成和整理视频页一样"
  指的是**视频条目**，图片条目本该保持原样 —— 被当场纠正）。
- **红色在视频页 = "已标记待删"，不是"删除"**：照搬样式到"点了就立即删"的页面时
  别把 tint 也抄成红 —— 常红会被读成"已经删掉了"。
- **清零 state 要挑"状态真正切换"的时机，不是挑一个看起来会变的 key**：
  `LaunchedEffect(index){ p = 0f }` 救不了"从子页面返回"（index 没变），
  写在"触发进入子页面"的那个函数里才可靠。
- **别相信"以前 lint 过了"**：lint 增量分析会连续几轮漏掉同一处问题，
  只有完整 `lintDebug`（或 clean 后重跑）才扫得出来。
- **局部 fun 后紧跟同级语句踩 lint SuspiciousIndentation** → 改用 `val f = {}` lambda。
- **Kotlin 字符串模板：中文是合法标识符**，`"$var中文"` 会解析成变量，必须 `"${var}中文"`。
- **首帧门控**：常驻 + alpha 显隐的播放视图，TextureView 会留上一段最后一帧 ⇒
  `playKey`/`readyKey` 双钥匙；同类问题要全仓扫。
- **预览页动作回调传"当前条目"不传索引**；量窗口位置用 `onGloballyPositioned`
  （`onSizeChanged` 刚挂载时返回 0）；既有工程先 grep 标准件再调数值。
- **`getOrElse(-1)` 会抛下标异常**：`indexOfFirst{}` 求出的局部序号，
  取值前一律 `idx in arr.indices` 守卫。

## 改造 WeiBack-rs（微博备份客户端）

源码 `小工具/weiback-rs-src/`，构建目录 `C:/wb-build/`（**必须英文路径**）。
三项增强已实现并验证（成品在 `小工具/weiback-installer/`）：
每页条数可调（12/24/48/96/192，默认 48）、本地作者列表（一键切换）、
批量删除（复选框 + 深/浅层）、删除按钮改「⋮」菜单（原来被卡片宽度裁掉）。

**构建要点（重装环境时照做）**
1. 依赖三件套：**VS Build Tools**（含 MSVC）+ **Windows SDK x64** + **Rust**
   `winget install Microsoft.VisualStudio.2022.BuildTools --override "--quiet --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"`
2. `vcvars64.bat` 之后**手工补 `LIB`/`INCLUDE`**：纯 Build Tools 安装时它不会自动
   指向 SDK，导致 `LNK1181: 无法打开 kernel32.lib`
3. **`.bat` 必须纯 ASCII**：cmd 用 GBK 解析，中文注释和中文路径会被当命令执行
4. cargo 的 git 依赖要 PortableGit 在 PATH + `ghfast.top` insteadOf 镜像
5. 打包时若 vite `emptyDir` 被沙箱拦，用
   `tauri build --config tauri.override.json`（把 `beforeBuildCommand` 置空）复用已有 dist
6. **sea-query 1.0 改了 API**（项目用的就是 1.0）：`group_by_col()` 而非 `group_by()`、
   `order_by(col, Order::Desc)` 而非 `order_by_desc()`、`add_group_by` 收迭代器、
   `limit()` 收 `u64`、`Func::count(1)` 要 `Expr::from(...)` 显式转换。
   查 API 直接看 `~/.cargo/registry/src/*/sea-query-1.0.2/src/query/select.rs`，别猜。
