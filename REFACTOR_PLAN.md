# Legado 二次开发改造方案

> 目标：在保持核心阅读体验的前提下，让 App **更精简、更美观、更流畅、更好用**。
> 基线版本：当前 `app/src/main` 代码（数据库 v75，minSdk 21，targetSdk 35，Kotlin 2.1.0）。
> 交付策略：先出方案 → 用户确认 → 分阶段实施。

---

## 一、改造总览

| 维度 | 现状 | 改造后 |
|---|---|---|
| 功能模块 | RSS + 漫画 + 朗读 + WebDav + 字典 + 二维码 + 文件管理 全量保留 | **彻底移除 RSS / 漫画 / 朗读 / 字典 / 二维码**，WebDav 仅保留备份入口 |
| 主题 | `Theme.AppCompat.DayNight` + 第三方 `ate` 引擎 + 蓝粉硬编码配色 | **Material 3 (`Theme.Material3.DayNight`)** + 语义化色值 + 动态取色 |
| 启动 | 主线程注册多个同步初始化 + 启动 600ms 固定延时 + 异步串行块 | 启动主线程仅保留必要项，**Rhino/简繁引擎延迟懒加载**，欢迎页根据初始化信号跳转 |
| 数据库 | 21 张表，`allowMainThreadQueries()`，部分子查询每行执行 | 移除 RSS/Manga/Dict/HttpTTS 相关表，**禁止主线程查询**，分组掩码缓存 |
| 阅读 Menu | 16 个配置 Dialog 功能重叠 + 15+ 菜单项平铺 | 合并为 3 个面板（外观 / 排版 / 高级），菜单项分级展示 |
| 书架 | style1 / style2 双风格 + 列表/网格切换 | **统一为单风格**（Material 3 风格），保留列表/网格切换 |
| 包体积 | 含 ExoPlayer / NanoHttpd / Markwon / ZXing / AndroidSVG | 移除 6 项依赖，预估减小 APK ~3-5MB |

---

## 二、模块精简方案

### 2.1 彻底移除清单（代码 + 资源 + 依赖 + Manifest）

| 模块 | 移除范围 | 关联依赖 | 影响范围 |
|---|---|---|---|
| **RSS 订阅** | `ui/rss/`、`ui/main/rss/`、`model/rss/`、`data/dao/{RssSourceDao,RssArticleDao,RssReadRecordDao,RssStarDao}.kt`、`data/entities/{RssSource,RssArticle,RssReadRecord,RssStar}.kt`、`api/controller/RssSourceController.kt`、`help/source/RssSourceExtensions.kt`、`service/WebService.kt` 中的 RSS 路由 | `libs.markwon.*`（4 项） | 7 个 Activity、17 个布局、底部导航 RSS tab |
| **漫画 Manga** | `ui/book/manga/`（整包）、`model/ReadManga.kt`、`ui/book/read/MangaMenu.kt`、`BookInfoActivity` 中"切换漫画模式"按钮 | `libs.androidsvg`、`libs.glide.svg` | 1 个 Activity、6 个布局 |
| **朗读 / TTS / 音频** | `ui/book/audio/`、`ui/book/read/config/{ReadAloudDialog,ReadAloudConfigDialog,SpeakEngineDialog,HttpTtsEditDialog,AutoReadDialog}.kt`、`service/{TTSReadAloudService,HttpReadAloudService,AudioPlayService,BaseReadAloudService}.kt`、`model/{AudioPlay,ReadAloud}.kt`、`help/TTS.kt`、`help/exoplayer/`、`data/entities/HttpTTS.kt`、`data/dao/HttpTTSDao.kt`、`receiver/MediaButtonReceiver.kt`、`ReadMenu` 中朗读按钮、`MoreConfigDialog` 中朗读相关项 | `libs.media.media`、`libs.media3.exoplayer`、`libs.media3.datasource.okhttp` | 1 个 Activity + 2 个 Service、5 个布局 |
| **字典 Dict** | `ui/dict/`（整包）、`data/dao/DictRuleDao.kt`、`data/entities/DictRule.kt`、`TextActionMenu` 中"查字典"按钮、`defaultData/dictRules.json` | — | 1 个 Activity、4 个布局 |
| **二维码 QRCode** | `ui/qrcode/`（整包）、`MyFragment` 中"扫码"入口、`OnLineImportActivity` 中扫码导入分支（改为手动粘贴 URL） | `libs.zxing.lite` | 1 个 Activity、2 个布局 |
| **多余 Launcher 图标** | `Launcher1` ~ `Launcher6`（保留默认 `Launcher1` 单一入口） | — | 5 个 Manifest 入口 |

**预估收益**：移除约 80+ Kotlin 文件、34 个布局、5 个 Service/Activity、6 项三方依赖。

### 2.2 默认隐藏保留代码（用户可在"高级设置"开启）

| 模块 | 隐藏方式 | 代码保留 |
|---|---|---|
| **WebDav 备份** | `BackupConfigFragment` 中默认折叠"WebDav 备份"项到"高级"分组；`RemoteBookActivity` 入口从 `MyFragment` 移除 | 保留 `lib/webdav/`、`help/AppWebDav.kt` |
| **WebService 局域网传书源** | `MyFragment` 中默认隐藏，仅在"高级设置"开关开启后显示 | 保留 `service/WebService.kt` |
| **规则订阅 RuleSub** | 与 RSS 一并隐藏入口 | 保留 `RuleSubActivity`（与书源订阅耦合，无法完全移除） |

### 2.3 数据库 Schema 迁移（v75 → v76）

新增 Migration 75→76 执行以下 DROP TABLE：

```sql
DROP TABLE IF EXISTS rssSources;
DROP TABLE IF EXISTS rssArticles;
DROP TABLE IF EXISTS rssReadRecords;
DROP TABLE IF EXISTS rssStars;
DROP TABLE IF EXISTS dictRule;
DROP TABLE IF EXISTS httpTTS;
```

并在 `AppDatabase` 中移除对应 `@Entity` 与 `abstract fun xxxDao()` 声明。`RuleSub`、`Server`、`KeyboardAssist` 表保留（与书源订阅/远程书/WebDav 相关）。

---

## 三、UI 重构方案（Material 3 迁移）

### 3.1 主题与配色

**修改文件**：
- [app/src/main/res/values/styles.xml](file:///workspace/app/src/main/res/values/styles.xml)
- [app/src/main/res/values/colors.xml](file:///workspace/app/src/main/res/values/colors.xml)
- [app/src/main/res/values-night/colors.xml](file:///workspace/app/src/main/res/values-night/colors.xml)
- [app/src/main/res/values-night/styles.xml](file:///workspace/app/src/main/res/values-night/styles.xml)
- [app/build.gradle](file:///workspace/app/build.gradle)（升级 `com.google.android.material:material` 至 `1.12.0` 已满足）

**核心改动**：
```xml
<!-- styles.xml -->
<style name="Base.AppTheme" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="colorPrimary">@color/md_theme_primary</item>
    <item name="colorOnPrimary">@color/md_theme_onPrimary</item>
    <item name="colorPrimaryContainer">@color/md_theme_primaryContainer</item>
    <item name="colorSecondary">@color/md_theme_secondary</item>
    <item name="colorSurface">@color/md_theme_surface</item>
    <item name="colorOnSurface">@color/md_theme_onSurface</item>
    <item name="android:colorBackground">@color/md_theme_background</item>
    <item name="colorOutline">@color/md_theme_outline</item>
    <!-- ShapeAppearance 全局圆角 -->
    <item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.App.SmallComponent</item>
    <item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.App.MediumComponent</item>
    <item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.App.LargeComponent</item>
</style>
```

**新增色板**（`colors.xml`）替换 `md_light_blue_600` + `md_pink_800` 蓝粉配色为同色系（推荐蓝绿色调 `#00696D`）：

```xml
<color name="md_theme_primary">#00696D</color>
<color name="md_theme_onPrimary">#FFFFFF</color>
<color name="md_theme_primaryContainer">#6FF6FE</color>
<color name="md_theme_onPrimaryContainer">#002022</color>
<color name="md_theme_secondary">#4A6365</color>
<color name="md_theme_surface">#F4FBFA</color>
<color name="md_theme_onSurface">#161D1D</color>
<color name="md_theme_background">#F4FBFA</color>
<color name="md_theme_outline">#6F797A</color>
```

**ShapeAppearance 替代 `shape_*.xml`**：
```xml
<style name="ShapeAppearance.App.SmallComponent">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">8dp</item>
</style>
<style name="ShapeAppearance.App.MediumComponent">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">12dp</item>
</style>
<style name="ShapeAppearance.App.LargeComponent">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">16dp</item>
</style>
```

**动态取色**（Android 12+）：在 `App.onCreate` 与 `MainActivity.onCreate` 调用：
```kotlin
DynamicColors.applyToActivitiesIfAvailable(this)
```

### 3.2 底部导航重构

**修改文件**：
- [app/src/main/res/menu/main_bnv.xml](file:///workspace/app/src/main/res/menu/main_bnv.xml)（移除 RSS 项）
- [app/src/main/java/io/legado/app/ui/main/MainActivity.kt](file:///workspace/app/src/main/java/io/legado/app/ui/main/MainActivity.kt)（精简 `realPositions` 与 `TabFragmentPageAdapter`）
- [app/src/main/res/layout/activity_main.xml](file:///workspace/app/src/main/res/layout/activity_main.xml)

**改造后底部导航 3 项**：书架 / 发现 / 我的。启用 Material 3 `NavigationBarView` 的 active indicator：
```xml
<com.google.android.material.bottomnavigation.BottomNavigationView
    style="@style/Widget.Material3.BottomNavigationView"
    app:labelVisibilityMode="labeled"
    app:itemActiveIndicatorStyle="@style/Widget.App.BottomNavigationView.ActiveIndicator" />
```

### 3.3 阅读菜单重构

**目标**：将 16 个 Dialog 合并为 3 个分类面板。

**新增/合并 Dialog**：
| 原 Dialog | 合并去向 |
|---|---|
| `ReadStyleDialog` + `BgTextConfigDialog` + `PaddingConfigDialog` + `TipConfigDialog` | **`ReadAppearanceDialog`（外观面板）** - Tab：背景 / 字体 / 间距 / 提示 |
| `ReadStyleDialog` 中的翻页动画 + `AutoReadDialog` + `PageKeyDialog` + `ClickActionConfigDialog` | **`ReadLayoutDialog`（排版面板）** - Tab：翻页 / 自动阅读 / 点击区域 / 按键 |
| `MoreConfigDialog` 中的近 20 项 Preference | **`ReadAdvancedDialog`（高级面板）** - 分组：显示 / 性能 / 双页 |

**菜单项收纳**：[app/src/main/res/menu/book_read.xml](file:///workspace/app/src/main/res/menu/book_read.xml) 中，将"去重标题"、"re_segment"、"有效替换"、"反转内容"、"模拟阅读"等高级项移入二级"高级"子菜单。

**底部菜单按钮**（[view_read_menu.xml](file:///workspace/app/src/main/res/layout/view_read_menu.xml)）：
- 4 个裸 LinearLayout+ImageView+TextView 改为 `MaterialButton` + `iconOnly` style
- 4 个 `FloatingActionButton` 改为 `MaterialButton.Icon` 风格统一
- 亮度 SeekBar 增加最小 48dp 触控目标

### 3.4 书架统一

**移除**：`ui/main/bookshelf/style2/`（`BookshelfFragment2.kt` 及子目录）
**保留并改造**：`BookshelfFragment1.kt` 作为唯一书架风格
**改造**：
- 书籍卡片改用 `MaterialCardView` + `ShapeAppearance`，统一 12dp 圆角
- 网格 / 列表切换图标移到 Toolbar，使用 Material 3 `IconButton`
- 分组 Tab 改用 `TabLayout` Material 3 风格
- 移除"书架布局"配置项中的"分组风格"选择

### 3.5 我的页面精简

[MyFragment.kt](file:///workspace/app/src/main/java/io/legado/app/ui/main/my/MyFragment.kt) 重新分组：
- **主题**（保留）
- **备份**（保留本地 + WebDav 折叠）
- **高级**（默认折叠，含 WebService / 规则订阅 / 检查更新 / 实验性功能）
- **关于**（保留）

移除入口：扫码、字典规则、RSS 源、朗读引擎、HttpTTS、文件管理。

---

## 四、性能优化方案

### 4.1 启动优化

**修改文件**：[App.kt](file:///workspace/app/src/main/java/io/legado/app/App.kt)、[WelcomeActivity.kt](file:///workspace/app/src/main/java/io/legado/app/ui/welcome/WelcomeActivity.kt)

| 项 | 现状 | 改造 |
|---|---|---|
| `RhinoScriptEngine` | 启动即初始化 | 改为 `lazy` 在首次书源解析时初始化 |
| `ChineseUtils.preLoad` | 启动预加载简繁 | 改为打开书籍时按需加载 |
| `BookHelp.clearInvalidCache` | 启动同步执行文件遍历删除 | 移到 `MainActivity.onPostCreate` 后用 WorkManager 异步 |
| `SourceHelp.adjustSortNumber` | 启动全表更新 | 移到书架加载完成后异步 |
| `WelcomeActivity` 600ms 固定延迟 | 写死 | 改为 `AppInitFlow.collect { ... }` 完成即跳转 |
| `installGmsTlsProvider` | 每次启动反射 | 缓存成功结果到 SharedPreferences |
| `AppWebDav.downloadAllBookProgress` | 启动网络请求 | 延迟到 MainActivity 之后 + 5s 超时 |
| `applyDayNight` + 主题切换 | 启动 inflate | 缓存上次 themeId |
| `Firebase Analytics/Perf` | 启动即上报 | 延迟到首屏 onPostResume |

### 4.2 数据库优化

**修改文件**：[AppDatabase.kt](file:///workspace/app/src/main/java/io/legado/app/data/AppDatabase.kt)、[BookDao.kt](file:///workspace/app/src/main/java/io/legado/app/data/dao/BookDao.kt)

| 项 | 改造 |
|---|---|
| `allowMainThreadQueries()` | **移除**，强制异步访问 |
| `BookDao.flowByGroup` 子查询 | Kotlin 层缓存 `sum(groupId) where groupId > 0` 为 Long，避免每行执行 |
| `BookChapterDao.search` 全表 like | 引入 `FTS4` 虚拟表加速章节标题搜索 |
| `dbCallback.onOpen` 6 条 SQL | 仅在版本升级时执行，封装为事务 |
| `keyboardAssists` 表 | 移除 DAO，改用 SharedPreferences |
| 索引补全 | `Bookmark` 加 `(bookUrl, index)` 复合索引；`BookChapter` 加 `(bookUrl, index)` |

### 4.3 图片加载优化

**修改文件**：[LegadoGlideModule.kt](file:///workspace/app/src/main/java/io/legado/app/help/glide/LegadoGlideModule.kt)

| 项 | 现状 | 改造 |
|---|---|---|
| 磁盘缓存 | 1GB 固定 | 改为 256MB，或 `DiskCache.Factory` 按 `availableMemory` 计算 |
| `WelcomeActivity` 同步 `decodeBitmap` | 大图卡首屏 | 改用 Glide `asBitmap().load()` + Downsampling |
| 章节内图片缓存 | 全量缓存 | 增加 LRU 内存上限，按设备 RAM 动态 |

### 4.4 阅读翻页性能

**修改文件**：[ChapterProvider.kt](file:///workspace/app/src/main/java/io/legado/app/ui/book/read/page/provider/ChapterProvider.kt)、[ContentTextView.kt](file:///workspace/app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt)

| 项 | 改造 |
|---|---|
| `ChapterProvider` 全局共享 viewWidth/Height | 改为 `WeakReference` 持有当前 Activity 上下文，避免多窗口串扰 |
| 滚动模式预渲染 2 页 | 根据设备 RAM 动态调整：低端机 1 页，中高端 2 页 |
| `BitmapPool` 复用 | 增加 `inBitmap` 复用，减少 GC |

---

## 五、交互流程简化方案

### 5.1 新手引导

**新增**：首次启动时引导用户从"导入书源 → 添加书籍 → 开始阅读"3 步走完，避免空白书架不知所措。

### 5.2 书源导入简化

**修改文件**：[OnLineImportActivity.kt](file:///workspace/app/src/main/java/io/legado/app/ui/association/OnLineImportActivity.kt)

- 移除二维码扫码分支（精简后无 ZXing）
- URL 输入框添加粘贴按钮 + 历史记录下拉
- 导入成功后自动跳转该书源调试界面

### 5.3 阅读入口设置简化

将 16 个 Dialog 入口从底部 4 图标按钮改为：
- **目录**（不变）
- **外观** → 打开 `ReadAppearanceDialog`
- **排版** → 打开 `ReadLayoutDialog`
- **更多** → 打开 `ReadAdvancedDialog`

原"朗读"按钮移除（精简后无朗读）。

### 5.4 配置页精简

[ConfigActivity.kt](file:///workspace/app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt) 标签页从 5 个减为 3 个：
- **主题**（含原 Theme + Cover + Welcome 合并）
- **阅读**（保留原 Backup + Other 中阅读相关项）
- **关于**

移除标签：备份（合并入"阅读"）、封面（合并入"主题"）、欢迎（合并入"主题"）。

---

## 六、实施路线图

按风险与依赖关系分 6 个阶段，每阶段完成后单独验证可编译。

### 阶段 0：基线建立（预备工作）
- [ ] 创建 `develop/refactor` 分支
- [ ] 跑通原版 `assembleDebug` 确认基线编译通过
- [ ] 记录基线 APK 大小、冷启动时间（可作为对照）

### 阶段 1：模块精简（高风险，最先做）
- [ ] 1.1 数据库 v75 → v76 迁移：DROP 6 张表
- [ ] 1.2 移除 `ui/rss/`、`ui/main/rss/`、`model/rss/` 及相关 DAO/Entity
- [ ] 1.3 移除 `ui/book/manga/`、`model/ReadManga.kt`、`MangaMenu.kt`
- [ ] 1.4 移除朗读/TTS/音频：`ui/book/audio/`、`ui/book/read/config/{ReadAloud*,SpeakEngine*,HttpTts*,AutoRead*}.kt`、`service/{TTSReadAloud*,HttpReadAloud*,AudioPlay*,BaseReadAloud*}.kt`、`help/TTS.kt`、`help/exoplayer/`
- [ ] 1.5 移除 `ui/dict/`、`ui/qrcode/`
- [ ] 1.6 移除 `Launcher2` ~ `Launcher6` 入口
- [ ] 1.7 修改 `AndroidManifest.xml`、`main_bnv.xml`、`MyFragment.kt`、`ReadMenu.kt`、`MoreConfigDialog.kt` 移除引用
- [ ] 1.8 `app/build.gradle` 移除 6 项依赖：`markwon.*`、`androidsvg`、`glide.svg`、`media3.*`、`media.media`、`zxing.lite`
- [ ] 1.9 编译验证 + 单元测试

### 阶段 2：主题 Material 3 迁移
- [ ] 2.1 `styles.xml` 改 `Theme.Material3.DayNight.NoActionBar`
- [ ] 2.2 `colors.xml` 替换为 Material 3 色板（蓝绿色系）
- [ ] 2.3 新增 `ShapeAppearance` 样式
- [ ] 2.4 `App.onCreate` 添加 `DynamicColors.applyToActivitiesIfAvailable`
- [ ] 2.5 替换 `ate_*` 颜色引用为语义化色值（搜索 `ate_` 全局替换）
- [ ] 2.6 编译验证 + 视觉回归（每个 Activity 截图对比）

### 阶段 3：UI 控件 Material 化
- [ ] 3.1 `MainActivity` 底部导航改 Material 3 `BottomNavigationView` + active indicator
- [ ] 3.2 书架卡片改 `MaterialCardView`，移除 `shape_*.xml` 中冗余圆角 drawable
- [ ] 3.3 阅读菜单底部按钮改 `MaterialButton.Icon`
- [ ] 3.4 `ReadMenu` 4 个 FAB 改 `MaterialButton` 风格统一
- [ ] 3.5 `Dialog` 主题统一为 `MaterialAlertDialogBuilder`
- [ ] 3.6 编译验证

### 阶段 4：阅读菜单整合
- [ ] 4.1 新建 `ReadAppearanceDialog`（合并 4 个旧 Dialog）
- [ ] 4.2 新建 `ReadLayoutDialog`（合并翻页 + 自动阅读 + 点击 + 按键）
- [ ] 4.3 新建 `ReadAdvancedDialog`（重构 `MoreConfigDialog` 为分组）
- [ ] 4.4 `ReadMenu` 底部 4 按钮重新映射
- [ ] 4.5 `book_read.xml` 菜单项分级（高级项移入二级）
- [ ] 4.6 删除旧 Dialog 文件
- [ ] 4.7 编译验证 + 阅读流程手动测试

### 阶段 5：书架与配置精简
- [ ] 5.1 删除 `BookshelfFragment2` 及子目录
- [ ] 5.2 `BaseBookshelfFragment` 移除 `bookGroupStyle` 切换逻辑
- [ ] 5.3 `ConfigActivity` 标签从 5 → 3
- [ ] 5.4 `MyFragment` 重新分组（主题 / 备份 / 高级 / 关于）
- [ ] 5.5 移除 `defaultData/{dictRules,httpTTS,rssSources}.json`
- [ ] 5.6 编译验证

### 阶段 6：性能优化
- [ ] 6.1 `App.kt` 重构初始化：Rhino/简繁/BookHelp 改懒加载
- [ ] 6.2 `WelcomeActivity` 改为基于 `AppInitFlow` 信号跳转
- [ ] 6.3 `AppDatabase` 移除 `allowMainThreadQueries`
- [ ] 6.4 `BookDao` 分组掩码缓存
- [ ] 6.5 `LegadoGlideModule` 磁盘缓存改 256MB
- [ ] 6.6 `WelcomeActivity` 背景图改 Glide 异步
- [ ] 6.7 Firebase 延迟到首屏后
- [ ] 6.8 编译验证 + 启动时间测量（对比基线）

---

## 七、风险与兼容性

| 风险 | 缓解措施 |
|---|---|
| 数据库迁移失败导致数据丢失 | Migration v76 在 DROP 前先 SELECT COUNT 校验；保留 `fallbackToDestructiveMigrationFrom(1..9)` 兜底 |
| 移除 ZXing 影响"扫码导入书源" | 改为"粘贴 URL"导入，提示用户在浏览器复制链接 |
| 移除朗读影响视障用户 | 文档明确说明此版本不含无障碍朗读；保留 `android:contentDescription` |
| Material 3 主题在低版本 Android 显示异常 | minSdk 21 支持 Material3 主题（Material 1.12.0 兼容 API 21+）；增加 `values-v31` 资源限定符仅在 12+ 启用动态取色 |
| 移除 `allowMainThreadQueries` 导致 ANR 改为崩溃 | 全局搜索 `appDb.xxxDao()` 主线程调用，逐一改为 `withContext(IO)`；增加 lint 规则 |
| ExoPlayer 移除影响音频书 | 文档说明此版本仅支持文本小说 |
| `Rhino` 懒加载导致首次打开书籍延迟 | 懒加载时显示加载进度，加载完成后缓存 |
| 改动量巨大，回归测试困难 | 分阶段提交 + 每阶段编译验证；关键路径手测（启动 / 书架 / 阅读 / 设置） |

---

## 八、验证清单

每阶段完成后执行：
1. `./gradlew :app:assembleDebug` 编译通过
2. `./gradlew :app:lint` 无新增 error
3. `./gradlew :app:testDebugUnitTest` 单元测试通过
4. 安装到模拟器手测：
   - 冷启动 ≤ 2s
   - 书架加载 ≤ 500ms（100 本书）
   - 阅读翻页无明显卡顿
   - 主题切换无崩溃
   - 夜间模式正确

最终交付：
- 改造后 APK 体积 ≤ 基线 85%
- 冷启动时间 ≤ 基线 70%
- 所有原有核心功能（书架 / 阅读 / 书源 / 替换规则 / 备份 / 导入）可用

---

## 九、关键文件清单（按改动优先级）

### 阶段 1 涉及文件（模块精简）
- [app/src/main/AndroidManifest.xml](file:///workspace/app/src/main/AndroidManifest.xml)
- [app/build.gradle](file:///workspace/app/build.gradle)
- [app/src/main/java/io/legado/app/data/AppDatabase.kt](file:///workspace/app/src/main/java/io/legado/app/data/AppDatabase.kt)
- [app/src/main/java/io/legado/app/data/DatabaseMigrations.kt](file:///workspace/app/src/main/java/io/legado/app/data/DatabaseMigrations.kt)
- `app/src/main/java/io/legado/app/ui/rss/`（整目录删除）
- `app/src/main/java/io/legado/app/ui/main/rss/`（整目录删除）
- `app/src/main/java/io/legado/app/ui/book/manga/`（整目录删除）
- `app/src/main/java/io/legado/app/ui/book/audio/`（整目录删除）
- `app/src/main/java/io/legado/app/ui/dict/`（整目录删除）
- `app/src/main/java/io/legado/app/ui/qrcode/`（整目录删除）
- [app/src/main/res/menu/main_bnv.xml](file:///workspace/app/src/main/res/menu/main_bnv.xml)
- [app/src/main/java/io/legado/app/ui/main/MainActivity.kt](file:///workspace/app/src/main/java/io/legado/app/ui/main/MainActivity.kt)
- [app/src/main/java/io/legado/app/ui/main/my/MyFragment.kt](file:///workspace/app/src/main/java/io/legado/app/ui/main/my/MyFragment.kt)
- [app/src/main/java/io/legado/app/ui/book/read/ReadMenu.kt](file:///workspace/app/src/main/java/io/legado/app/ui/book/read/ReadMenu.kt)
- [app/src/main/java/io/legado/app/ui/book/read/config/MoreConfigDialog.kt](file:///workspace/app/src/main/java/io/legado/app/ui/book/read/config/MoreConfigDialog.kt)

### 阶段 2-3 涉及文件（主题与控件）
- [app/src/main/res/values/styles.xml](file:///workspace/app/src/main/res/values/styles.xml)
- [app/src/main/res/values/colors.xml](file:///workspace/app/src/main/res/values/colors.xml)
- [app/src/main/res/values-night/colors.xml](file:///workspace/app/src/main/res/values-night/colors.xml)
- [app/src/main/res/values-night/styles.xml](file:///workspace/app/src/main/res/values-night/styles.xml)
- [app/src/main/java/io/legado/app/App.kt](file:///workspace/app/src/main/java/io/legado/app/App.kt)
- [app/src/main/res/layout/activity_main.xml](file:///workspace/app/src/main/res/layout/activity_main.xml)
- [app/src/main/res/layout/view_read_menu.xml](file:///workspace/app/src/main/res/layout/view_read_menu.xml)
- [app/src/main/res/layout/item_bookshelf_grid.xml](file:///workspace/app/src/main/res/layout/item_bookshelf_grid.xml)
- [app/src/main/res/layout/item_bookshelf_list.xml](file:///workspace/app/src/main/res/layout/item_bookshelf_list.xml)

### 阶段 4 涉及文件（阅读菜单整合）
- 新建 `app/src/main/java/io/legado/app/ui/book/read/config/ReadAppearanceDialog.kt`
- 新建 `app/src/main/java/io/legado/app/ui/book/read/config/ReadLayoutDialog.kt`
- 新建 `app/src/main/java/io/legado/app/ui/book/read/config/ReadAdvancedDialog.kt`
- 删除 16 个旧 Dialog 文件
- [app/src/main/res/menu/book_read.xml](file:///workspace/app/src/main/res/menu/book_read.xml)

### 阶段 5-6 涉及文件（书架/配置/性能）
- [app/src/main/java/io/legado/app/ui/main/bookshelf/BaseBookshelfFragment.kt](file:///workspace/app/src/main/java/io/legado/app/ui/main/bookshelf/BaseBookshelfFragment.kt)
- `app/src/main/java/io/legado/app/ui/main/bookshelf/style2/`（整目录删除）
- [app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt](file:///workspace/app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt)
- [app/src/main/java/io/legado/app/ui/welcome/WelcomeActivity.kt](file:///workspace/app/src/main/java/io/legado/app/ui/welcome/WelcomeActivity.kt)
- [app/src/main/java/io/legado/app/data/dao/BookDao.kt](file:///workspace/app/src/main/java/io/legado/app/data/dao/BookDao.kt)
- [app/src/main/java/io/legado/app/help/glide/LegadoGlideModule.kt](file:///workspace/app/src/main/java/io/legado/app/help/glide/LegadoGlideModule.kt)

---

## 十、决策待确认事项

请审阅以下决策点并确认：

1. **是否同意阶段 1 的彻底移除清单**（RSS / 漫画 / 朗读 / 字典 / 二维码 全部移除）？
2. **配色方案**：推荐蓝绿色调（`#00696D`），是否同意？或希望保留原蓝粉、改用其他色系？
3. **书架风格**：是否同意移除 style2 仅保留 style1？
4. **配置页标签**：是否同意从 5 个减为 3 个？
5. **是否在阶段 0 创建 `develop/refactor` 分支**？
6. **是否需要保留 WebDav 备份功能**（隐藏入口方案）？还是也一并移除？
7. **改造完成后是否需要更新 README 与 CHANGELOG**？

确认后即按阶段 1 → 6 顺序开始实施。
