# UI 重设计与功能优化实施计划

> **目标**：在 Legado 二次开发基础上，完成书架页/首页导航/阅读页/我的页 4 大区域 UI 重设计，提供 3 种可选风格（Material 3 大圆角 / 极简扁平 / 毛玻璃叠层），并整合设置入口、简化书籍添加流程、优化搜索体验。

**架构**：复用现有 `EventBus.RECREATE` + `BaseActivity.initTheme.setTheme()` + `AppCompatDelegate.setDefaultNightMode()` 切换链路，新增 `styleMode` 偏好维度。3 套主题 style 通过 `shapeAppearanceSmallComponent/MediumComponent/LargeComponent` 主题属性差异化呈现，避免自建热切换系统。

**技术栈**：Material Components 1.12.0（已支持 M3）、AppCompat 主题体系（保留）、Room、Glide、ViewBinding

---

## 现状摘要（来自调研）

- `Base.AppTheme` parent 是 `Theme.AppCompat.DayNight.NoActionBar`，M3 色板（`md_theme_*` 26 色）已定义但未在主题层接线
- `ShapeAppearanceApp.Small/Medium/LargeComponent`（8/12/16dp）已定义但完全未使用（死代码）
- 书架卡片是扁平 `ConstraintLayout` + `selectableItemBackground` 涟漪，无 CardView 无圆角无阴影
- 底部导航 3 Tab（书架/发现/我的），用旧版 ViewPager，ActiveIndicator 是默认 M3 药丸
- MyFragment 用 PreferenceFragment XML 驱动，与其他页面风格不一致
- Material 1.12.0 已完整支持 M3，**无需升级依赖**
- `DynamicColors.applyToActivitiesIfAvailable` 已启用（Android 12+）

---

## 阶段 A：主题系统重构 — 3 套风格可选

**目标**：用户可在设置中选择 M3 大圆角 / 极简扁平 / 毛玻璃叠层 三种风格，切换后通过 `recreate()` 全应用生效。

### A.1 重构 ShapeAppearance 命名（消除死代码）

**文件**：`app/src/main/res/values/styles.xml`

- 删除：`ShapeAppearanceApp` / `ShapeAppearanceApp.SmallComponent` / `MediumComponent` / `LargeComponent`（parent=`android:Widget` 的旧定义）
- 新增三组 ShapeAppearance，parent 用 MDC 的 `ShapeAppearance.Material3.SmallComponent`：

```xml
<!-- M3 大圆角风格：8/16/28dp -->
<style name="ShapeAppearance.M3.Small" parent="ShapeAppearance.Material3.SmallComponent">
    <item name="cornerSize">8dp</item>
</style>
<style name="ShapeAppearance.M3.Medium" parent="ShapeAppearance.Material3.MediumComponent">
    <item name="cornerSize">16dp</item>
</style>
<style name="ShapeAppearance.M3.Large" parent="ShapeAppearance.Material3.LargeComponent">
    <item name="cornerSize">28dp</item>
</style>

<!-- 极简扁平风格：0/0/0dp（直角无圆角） -->
<style name="ShapeAppearance.Flat.Small" parent="ShapeAppearance.Material3.SmallComponent">
    <item name="cornerSize">0dp</item>
</style>
<style name="ShapeAppearance.Flat.Medium" parent="ShapeAppearance.Material3.MediumComponent">
    <item name="cornerSize">0dp</item>
</style>
<style name="ShapeAppearance.Flat.Large" parent="ShapeAppearance.Material3.LargeComponent">
    <item name="cornerSize">0dp</item>
</style>

<!-- 毛玻璃叠层风格：12/20/28dp（中等圆角配合半透明） -->
<style name="ShapeAppearance.Blur.Small" parent="ShapeAppearance.Material3.SmallComponent">
    <item name="cornerSize">12dp</item>
</style>
<style name="ShapeAppearance.Blur.Medium" parent="ShapeAppearance.Material3.MediumComponent">
    <item name="cornerSize">20dp</item>
</style>
<style name="ShapeAppearance.Blur.Large" parent="ShapeAppearance.Material3.LargeComponent">
    <item name="cornerSize">28dp</item>
</style>
```

### A.2 新增 3 套 AppTheme 派生主题

**文件**：`app/src/main/res/values/styles.xml` + `app/src/main/res/values-night/styles.xml`

保留 `Base.AppTheme` 的 AppCompat parent（避免破坏现有控件），在派生主题中叠加 M3 主题属性：

```xml
<!-- M3 大圆角风格 -->
<style name="AppTheme.M3" parent="AppTheme.Light">
    <item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.M3.Small</item>
    <item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.M3.Medium</item>
    <item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.M3.Large</item>
    <item name="colorSurface">@color/md_theme_surface</item>
    <item name="colorOnSurface">@color/md_theme_onSurface</item>
    <item name="colorPrimaryContainer">@color/md_theme_primaryContainer</item>
    <item name="colorOnPrimaryContainer">@color/md_theme_onPrimaryContainer</item>
</style>

<!-- 极简扁平风格 -->
<style name="AppTheme.Flat" parent="AppTheme.Light">
    <item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.Flat.Small</item>
    <item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.Flat.Medium</item>
    <item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.Flat.Large</item>
    <item name="colorSurface">@color/md_theme_surface</item>
    <item name="colorOnSurface">@color/md_theme_onSurface</item>
</style>

<!-- 毛玻璃叠层风格 -->
<style name="AppTheme.Blur" parent="AppTheme.Light">
    <item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.Blur.Small</item>
    <item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.Blur.Medium</item>
    <item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.Blur.Large</item>
    <item name="colorSurface">@color/md_theme_surface_variant_translucent</item>
    <item name="android:windowBackground">@color/md_theme_background_translucent</item>
</style>
```

`values-night/styles.xml` 提供对应 Dark 版本（同名 style，颜色从 `md_theme_*` 自动切换 thanks to `-night` 限定符）。

### A.3 新增 styleMode 偏好

**文件**：`app/src/main/java/io/legado/app/constant/PreferKey.kt`

```kotlin
const val styleMode = "styleMode"  // "m3" / "flat" / "blur"，默认 "m3"
```

**文件**：`app/src/main/java/io/legado/app/help/config/AppConfig.kt`

```kotlin
val styleMode: String
    get() = getPrefString(PreferKey.styleMode, "m3")
```

### A.4 修改 BaseActivity 主题选择逻辑

**文件**：`app/src/main/java/io/legado/app/base/BaseActivity.kt`（`initTheme()` 方法）

```kotlin
protected open fun initTheme() {
    val styleRes = when (AppConfig.styleMode) {
        "flat" -> if (AppConfig.isNightTheme) R.style.AppTheme_Flat_Dark else R.style.AppTheme_Flat
        "blur" -> if (AppConfig.isNightTheme) R.style.AppTheme_Blur_Dark else R.style.AppTheme_Blur
        else -> if (AppConfig.isNightTheme) R.style.AppTheme_Dark else R.style.AppTheme_Light
    }
    setTheme(styleRes)
    if (AppConfig.styleMode != "blur") {
        DynamicColors.applyToActivity(this)  // 毛玻璃风格下关闭动态取色避免冲突
    }
}
```

注意：派生主题命名 `AppTheme.M3.Dark` / `AppTheme.Flat.Dark` / `AppTheme.Blur.Dark`，对应 `values-night/styles.xml` 中定义。

### A.5 设置页新增风格选择入口

**文件**：`app/src/main/res/xml/pref_main.xml`（在 `themeMode` 项后新增）

```xml
<io.legado.app.lib.preference.NameListPreference
    android:key="styleMode"
    android:title="@string/style_mode"
    android:summary="%s"
    android:entries="@array/style_mode"
    android:entryValues="@array/style_mode_value"
    android:defaultValue="m3" />
```

**文件**：`app/src/main/res/values/arrays.xml`

```xml
<string-array name="style_mode">
    <item>@string/style_m3</item>
    <item>@string/style_flat</item>
    <item>@string/style_blur</item>
</string-array>
<string-array name="style_mode_value">
    <item>m3</item>
    <item>flat</item>
    <item>blur</item>
</string-array>
```

**文件**：`app/src/main/res/values/strings.xml`

```xml
<string name="style_mode">界面风格</string>
<string name="style_m3">Material 3 大圆角</string>
<string name="style_flat">极简扁平</string>
<string name="style_blur">毛玻璃叠层</string>
```

监听 styleMode 变化触发 `EventBus.RECREATE`（已有监听链路）。

### A.6 新增半透明色资源（毛玻璃风格用）

**文件**：`app/src/main/res/values/colors.xml`

```xml
<color name="md_theme_surface_variant_translucent">#CCDAE5E3</color>
<color name="md_theme_background_translucent">#E6F4FBFA</color>
```

**文件**：`app/src/main/res/values-night/colors.xml`

```xml
<color name="md_theme_surface_variant_translucent">#CC3F4947</color>
<color name="md_theme_background_translucent">#E60E1515</color>
```

### A.7 验证清单

- [ ] 3 种风格在 MainActivity / ReadBookActivity / ConfigActivity 下均能正常切换
- [ ] 日/夜模式与 3 种风格两两组合均正常
- [ ] EInk 模式跳过风格覆盖（仍走 AppTheme.Light/Dark）
- [ ] Android 12+ 动态取色在 M3/Flat 下生效，Blur 下关闭
- [ ] CI 构建通过

---

## 阶段 B：书架页重设计 — 卡片化 + ShapeAppearance

**目标**：书架卡片从扁平 ConstraintLayout 升级为 MaterialCardView，圆角/阴影随主题风格自动变化。

### B.1 重构 item_bookshelf_grid.xml

- 根布局改 `MaterialCardView`
- `app:shapeAppearance="?attr/shapeAppearanceMediumComponent"`（随主题）
- `app:cardBackgroundColor="?attr/colorSurface"`
- `app:cardElevation="2dp"`（M3/Blur 风格）/ Flat 风格通过主题属性覆盖为 0dp
- 封面用 `FilletImageView`（项目已有圆角 ImageView）配合 `app:radius="8dp"`
- 标题 `?attr/colorOnSurface`
- 间距：卡片间 6dp，padding 8dp

### B.2 重构 item_bookshelf_list.xml

- 根布局改 `MaterialCardView`，水平排列
- 封面 60dp × 84dp（缩小一点更紧凑），圆角 8dp
- 信息行紧凑化：书名 15sp + 作者/进度合并为一行 12sp 次要色
- 卡片 elevation 1dp（比 grid 更低）

### B.3 优化列表/网格切换

**文件**：`app/src/main/java/io/legado/app/ui/main/bookshelf/style1/books/BooksFragment.kt`

- 切换 `bookshelfLayout` 时改用 `RecyclerView.setLayoutManager()` + `adapter.notifyDataSetChanged()`，不再走 `EventBus.RECREATE` 重建（避免闪烁、提升响应速度）
- 仅当涉及 SpanSize 时才重设 LayoutManager

### B.4 空态优化

**文件**：`app/src/main/res/layout/fragment_books.xml`

- 空态 TextView 改为带图标 + 引导按钮（"去发现书籍" / "导入本地书"），点击跳转对应入口
- 使用 MaterialButtonOutlined 风格

---

## 阶段 C：首页/底部导航重构

**目标**：定制 ActiveIndicator，统一与书架的视觉语言；评估 ViewPager2 迁移。

### C.1 定制 ActiveIndicator

**文件**：`app/src/main/res/layout/activity_main.xml`

将 `itemActiveIndicatorStyle` 替换为自定义 style：

```xml
app:itemActiveIndicatorStyle="@style/Widget.Legado.BottomNavigationView.ActiveIndicator"
```

**文件**：`app/src/main/res/values/styles.xml`

```xml
<style name="Widget.Legado.BottomNavigationView.ActiveIndicator" parent="Widget.Material3.BottomNavigationView.ActiveIndicator">
    <item name="android:color">?attr/colorPrimaryContainer</item>
    <item name="android:shapeAppearance">?attr/shapeAppearanceSmallComponent</item>
</style>
```

### C.2 ViewPager → ViewPager2 迁移

**文件**：`app/src/main/java/io/legado/app/ui/main/MainActivity.kt` + `activity_main.xml`

- 替换 `androidx.viewpager.widget.ViewPager` 为 `androidx.viewpager2.widget.ViewPager2`
- `FragmentStatePagerAdapter` → `FragmentStateAdapter`
- `realPositions` 数组逻辑保留，但 `view_pager.setCurrentItem(position, false)` 改为 ViewPager2 API
- 用户可保留隐藏发现页的逻辑（`AppConfig.showDiscovery`）

### C.3 顶部 TitleBar 简化

**文件**：`app/src/main/res/layout/fragment_bookshelf1.xml`

- TitleBar 高度从默认 56dp 调整为 48dp（更紧凑）
- TabLayout 指示条高度从 2dp 改为 3dp + 圆角
- 添加书架菜单按钮迁移到右侧浮动按钮（更明显）

---

## 阶段 D：阅读页交互重设计

**目标**：菜单分组简化，设置面板按功能分类（屏幕/排版/翻页/内容）。

### D.1 阅读菜单浮动按钮分组

**文件**：`app/src/main/res/layout/view_read_menu.xml`

- 4 个 FloatingActionButton 改为 2 组：
  - **快捷组**：搜索内容、夜间切换（移到顶部）
  - **功能组**：自动翻页、替换规则
- FAB 大小统一 `fabSize="mini"`，间距 12dp
- 底部三大入口（目录/界面/设置）改用 MaterialButton 文字按钮，更清晰

### D.2 MoreConfigDialog 设置分类

**文件**：`app/src/main/res/xml/pref_config_read.xml`

26 项偏好按 4 个 PreferenceCategory 重组：

- **屏幕显示**：screenOrientation / keep_light / hideStatusBar / hideNavigationBar / progressBarBehavior
- **排版**：readBodyToLh / useZhLayout / textFullJustify / textBottomJustify / doubleHorizontalPage
- **翻页交互**：volumeKeyPage / volumeKeyPageOnPlay / keyPageOnLongPress / pageTouchSlop / noAnimScrollPage / customPageKey / mouseWheelPage / disableReturnKey
- **内容功能**：autoChangeSource / selectText / showBrightnessView / previewImageByClick / optimizeRender / clickRegionalConfig / expandTextMenu / showReadTitleAddition / readBarStyleFollowPage

### D.3 ClickActionConfigDialog 视觉优化

**文件**：`app/src/main/res/layout/dialog_click_action_config.xml`

- 9 个区域 TextView 改用 MaterialCardView，选中态高亮 `colorPrimaryContainer`
- 顶部增加预览图（3×3 网格示意图）

---

## 阶段 E：我的页面重组

**目标**：将散落的设置入口整合为 3 大分类卡片。

### E.1 重构 fragment_my_config.xml

放弃 PreferenceFragment，改用 RecyclerView + 自定义 Adapter（与书架风格一致）：

- **数据管理**（卡片）：书源管理 / TXT 目录规则 / 替换净化 / 书签 / 阅读记录
- **外观与个性化**（卡片）：主题模式 / 界面风格 / 主题设置 / 背景图 / Web 服务
- **其他**（卡片）：备份恢复 / 其他设置 / 关于 / 退出

每张卡片用 MaterialCardView，内部入口为 LinearLayout（图标 + 文字 + 箭头）。

### E.2 新建 MyAdapter

**文件**：`app/src/main/java/io/legado/app/ui/main/my/MyAdapter.kt`（新建）

```kotlin
class MyAdapter(...) : ListAdapter<MyItem, RecyclerView.ViewHolder>(DIFF) {
    // 支持分组标题 + 普通入口项两种 ViewType
}
```

### E.3 保留 PreferenceFragment 作为兜底

`MyPreferenceFragment` 仍可访问（通过其他设置入口跳转 ConfigActivity），避免破坏现有偏好监听链路。

---

## 阶段 F：功能优化

### F.1 简化书籍添加流程

**目标**：从书架页提供统一"添加书籍"入口，整合本地导入 + 网络书源 + 二维码（已删） + 网址。

**文件**：`app/src/main/java/io/legado/app/ui/main/bookshelf/style1/BookshelfFragment1.kt`

- TitleBar 菜单 `menu_bookshelf` 整合为单一"添加"项，点击弹出 `AddBookDialog`（新建 BottomSheet）
- BottomSheet 三个选项卡：本地导入 / 网络书源 / 网址添加
- 本地导入直接复用 `ImportBookActivity`，但默认进入扫描模式（跳过文件夹选择）
- 网络书源跳转 `BookSourceActivity` 并自动展示导入提示

### F.2 搜索体验优化

**文件**：`app/src/main/res/layout/activity_book_search.xml` + `SearchActivity.kt`

- 搜索结果卡片化（参考书架卡片，使用 MaterialCardView）
- 顶部输入帮助区（书架匹配 + 历史记录）合并为一个 FlexboxLayout，减少垂直空间占用
- 历史记录 chip 改用 Material Chip（`com.google.android.material.chip.Chip`），带删除图标
- 搜索进度从顶部 2dp 进度条改为底部 Snackbar 文字提示（"已搜索 N 个书源，找到 M 本"）
- 空结果时显示引导卡片（建议关闭精准搜索 / 切换分组）

### F.3 搜索结果项重设计

**文件**：`app/src/main/res/layout/item_search.xml`

- 卡片化，封面 50dp × 70dp 圆角 6dp
- 书名 14sp + 作者 11sp 次要色 + 来源 chip
- 最新章节摘要一行 12sp
- 加入书架按钮移到右侧，改用 IconButton

---

## 风险与回退策略

1. **AppCompat → Material3 主题切换风险**：保留 `Base.AppTheme` 的 AppCompat parent（不破坏现有控件），仅在派生主题叠加 M3 属性，**零破坏**。
2. **EInk 模式冲突**：`BaseActivity.initTheme` 跳过风格覆盖（`isEInkMode` 分支）。
3. **CI 构建失败**：每个阶段独立 commit，失败时单独回退该阶段。
4. **性能**：毛玻璃叠层在低版本 Android 需测试（已引入 `renderscript-intrinsics-replacement-toolkit`）。
5. **配置兼容性**：新增 `styleMode` 偏好默认 `"m3"`，老用户升级无影响。

---

## 执行策略

- 每阶段独立 commit + 推送触发 CI
- CI 失败立即修复，不堆积
- 用户可在任意阶段中止，前面已完成的阶段都是可用状态
- 阶段顺序：A → B → C → D → E → F（A 是其他阶段的基础）

## 文件清单（按阶段）

| 阶段 | 关键文件 | 类型 |
|---|---|---|
| A | `values/styles.xml`, `values-night/styles.xml`, `PreferKey.kt`, `AppConfig.kt`, `BaseActivity.kt`, `pref_main.xml`, `arrays.xml`, `strings.xml`, `values/colors.xml`, `values-night/colors.xml` | 修改 |
| B | `item_bookshelf_grid.xml`, `item_bookshelf_list.xml`, `BooksFragment.kt`, `fragment_books.xml` | 修改 |
| C | `activity_main.xml`, `MainActivity.kt`, `styles.xml`, `fragment_bookshelf1.xml` | 修改 |
| D | `view_read_menu.xml`, `pref_config_read.xml`, `dialog_click_action_config.xml` | 修改 |
| E | `fragment_my_config.xml`, `MyFragment.kt`, `MyAdapter.kt` | 修改/新建 |
| F | `BookshelfFragment1.kt`, `AddBookDialog.kt`, `activity_book_search.xml`, `SearchActivity.kt`, `item_search.xml` | 修改/新建 |
