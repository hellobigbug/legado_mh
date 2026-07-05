package io.legado.app.ui.main.my

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.FragmentMyConfigBinding
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.primaryColor
import io.legado.app.service.WebService
import io.legado.app.ui.about.AboutActivity
import io.legado.app.ui.about.ReadRecordActivity
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefString
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 我的页面 Fragment。
 *
 * 替换原 PreferenceFragment 方案，改用 RecyclerView + [MyAdapter] 呈现 3 大分类卡片：
 * - 数据管理
 * - 外观与个性化
 * - 其他
 */
class MyFragment() : BaseFragment(R.layout.fragment_my_config), MainFragmentInterface {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    private val binding by viewBinding(FragmentMyConfigBinding::bind)
    private lateinit var adapter: MyAdapter

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        setupRecyclerView()
        observeWebService()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_my, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_help -> showHelp("appHelp")
        }
    }

    private fun setupRecyclerView() {
        adapter = MyAdapter { action -> handleAction(action) }
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        adapter.submitList(buildItems())
    }

    private fun buildItems(): List<MyItem> = buildList {
        // 数据管理
        add(MyItem.Category(R.string.my_category_data))
        add(
            MyItem.EntryGroup(
                listOf(
                    MyItem.Entry(
                        key = "bookSourceManage",
                        iconRes = R.drawable.ic_cfg_source,
                        titleRes = R.string.book_source_manage,
                        summaryRes = R.string.book_source_manage_desc,
                        action = MyItem.Action.BOOK_SOURCE
                    ),
                    MyItem.Entry(
                        key = "txtTocRuleManage",
                        iconRes = R.drawable.ic_cfg_source,
                        titleRes = R.string.txt_toc_rule,
                        summaryRes = R.string.config_txt_toc_rule,
                        action = MyItem.Action.TXT_TOC_RULE
                    ),
                    MyItem.Entry(
                        key = "replaceManage",
                        iconRes = R.drawable.ic_cfg_replace,
                        titleRes = R.string.replace_purify,
                        summaryRes = R.string.replace_purify_desc,
                        action = MyItem.Action.REPLACE_RULE
                    ),
                    MyItem.Entry(
                        key = "bookmark",
                        iconRes = R.drawable.ic_bookmark,
                        titleRes = R.string.bookmark,
                        summaryRes = R.string.all_bookmark,
                        action = MyItem.Action.BOOKMARK
                    ),
                    MyItem.Entry(
                        key = "readRecord",
                        iconRes = R.drawable.ic_history,
                        titleRes = R.string.read_record,
                        summaryRes = R.string.read_record_summary,
                        action = MyItem.Action.READ_RECORD
                    )
                )
            )
        )

        // 外观与个性化
        add(MyItem.Category(R.string.my_category_appearance))
        add(
            MyItem.EntryGroup(
                listOf(
                    MyItem.Entry(
                        key = PreferKey.themeMode,
                        iconRes = R.drawable.ic_cfg_theme,
                        titleRes = R.string.theme_mode,
                        summaryRes = R.string.theme_mode_desc,
                        action = MyItem.Action.THEME_MODE
                    ),
                    MyItem.Entry(
                        key = PreferKey.styleMode,
                        iconRes = R.drawable.ic_cfg_theme,
                        titleRes = R.string.style_mode,
                        summaryRes = R.string.style_mode_desc,
                        action = MyItem.Action.STYLE_MODE
                    ),
                    MyItem.Entry(
                        key = "theme_setting",
                        iconRes = R.drawable.ic_cfg_theme,
                        titleRes = R.string.theme_setting,
                        summaryRes = R.string.theme_setting_s,
                        action = MyItem.Action.THEME_SETTING
                    ),
                    MyItem.Entry(
                        key = "webService",
                        iconRes = R.drawable.ic_cfg_web,
                        titleRes = R.string.web_service,
                        summaryRes = R.string.web_service_desc,
                        action = MyItem.Action.WEB_SERVICE
                    )
                )
            )
        )

        // 其他
        add(MyItem.Category(R.string.my_category_other))
        add(
            MyItem.EntryGroup(
                listOf(
                    MyItem.Entry(
                        key = "web_dav_setting",
                        iconRes = R.drawable.ic_cfg_backup,
                        titleRes = R.string.backup_restore,
                        summaryRes = R.string.web_dav_set_import_old,
                        action = MyItem.Action.BACKUP_RESTORE
                    ),
                    MyItem.Entry(
                        key = "setting",
                        iconRes = R.drawable.ic_cfg_other,
                        titleRes = R.string.other_setting,
                        summaryRes = R.string.other_setting_s,
                        action = MyItem.Action.OTHER_SETTING
                    ),
                    MyItem.Entry(
                        key = "about",
                        iconRes = R.drawable.ic_cfg_about,
                        titleRes = R.string.about,
                        action = MyItem.Action.ABOUT
                    ),
                    MyItem.Entry(
                        key = "exit",
                        iconRes = R.drawable.ic_exit,
                        titleRes = R.string.exit,
                        action = MyItem.Action.EXIT
                    )
                )
            )
        )
    }

    private fun handleAction(action: MyItem.Action) {
        when (action) {
            MyItem.Action.BOOK_SOURCE -> startActivity<BookSourceActivity>()
            MyItem.Action.TXT_TOC_RULE -> startActivity<TxtTocRuleActivity>()
            MyItem.Action.REPLACE_RULE -> startActivity<ReplaceRuleActivity>()
            MyItem.Action.BOOKMARK -> startActivity<AllBookmarkActivity>()
            MyItem.Action.READ_RECORD -> startActivity<ReadRecordActivity>()
            MyItem.Action.THEME_MODE -> showThemeModeSelector()
            MyItem.Action.STYLE_MODE -> showStyleModeSelector()
            MyItem.Action.THEME_SETTING -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.THEME_CONFIG)
            }
            MyItem.Action.WEB_SERVICE -> toggleWebService()
            MyItem.Action.BACKUP_RESTORE -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.BACKUP_CONFIG)
            }
            MyItem.Action.OTHER_SETTING -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.OTHER_CONFIG)
            }
            MyItem.Action.ABOUT -> startActivity<AboutActivity>()
            MyItem.Action.EXIT -> activity?.finish()
        }
    }

    private fun showThemeModeSelector() {
        val ctx = context ?: return
        val items = ctx.resources.getStringArray(R.array.theme_mode).toList()
        val values = ctx.resources.getStringArray(R.array.theme_mode_v).toList()
        ctx.selector(R.string.theme_mode, items) { _, index ->
            val value = values[index]
            ctx.putPrefString(PreferKey.themeMode, value)
            view?.post { ThemeConfig.applyDayNight(ctx) }
        }
    }

    private fun showStyleModeSelector() {
        val ctx = context ?: return
        val items = ctx.resources.getStringArray(R.array.style_mode).toList()
        val values = ctx.resources.getStringArray(R.array.style_mode_value).toList()
        ctx.selector(R.string.style_mode, items) { _, index ->
            val value = values[index]
            ctx.putPrefString(PreferKey.styleMode, value)
            view?.post { postEvent(EventBus.RECREATE, "") }
        }
    }

    private fun toggleWebService() {
        val ctx = context ?: return
        if (WebService.isRun) {
            WebService.stop(ctx)
        } else {
            WebService.start(ctx)
        }
    }

    private fun observeWebService() {
        observeEventSticky<String>(EventBus.WEB_SERVICE) {
            // 通知 webService 入口刷新（adapter 当前不显示动态状态，留作扩展）
            if (::adapter.isInitialized) {
                adapter.refreshEntry("webService")
            }
        }
    }
}
