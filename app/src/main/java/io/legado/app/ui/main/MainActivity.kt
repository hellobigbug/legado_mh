package io.legado.app.ui.main

import android.os.Bundle
import android.text.format.DateUtils
import android.view.MenuItem
import android.view.MotionEvent
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentStateAdapter
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ActivityMainBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.elevation
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.about.CrashLogsDialog
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.style1.BookshelfFragment1
import io.legado.app.ui.main.explore.ExploreFragment
import io.legado.app.ui.main.my.MyFragment
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.observeEvent
import io.legado.app.utils.shouldHideSoftInput
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 主界面
 */
class MainActivity : VMBaseActivity<ActivityMainBinding, MainViewModel>(),
    BottomNavigationView.OnNavigationItemSelectedListener,
    BottomNavigationView.OnNavigationItemReselectedListener {

    override val binding by viewBinding(ActivityMainBinding::inflate)
    override val viewModel by viewModels<MainViewModel>()
    private val idBookshelf1 = 11
    private val idExplore = 1
    private val idMy = 3
    private var exitTime: Long = 0
    private var bookshelfReselected: Long = 0
    private var exploreReselected: Long = 0
    private var pagePosition = 0
    private val fragmentMap = hashMapOf<Int, Fragment>()
    private var bottomMenuCount = 4
    private val realPositions = arrayOf(idBookshelf1, idExplore, idMy)
    private val adapter by lazy {
        TabFragmentPagerAdapter(this)
    }
    private val onUpBooksBadgeView by lazy {
        binding.bottomNavigationView.addBadgeView(0)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        upBottomMenu()
        initView()
        upHomePage()
        viewModel.deleteNotShelfBook()
        onBackPressedDispatcher.addCallback(this) {
            if (pagePosition != 0) {
                binding.viewPagerMain.currentItem = 0
                return@addCallback
            }
            if (System.currentTimeMillis() - exitTime > 2000) {
                toastOnUi(R.string.double_click_exit)
                exitTime = System.currentTimeMillis()
            } else {
                moveTaskToBack(true)
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            currentFocus?.let {
                if (it.shouldHideSoftInput(ev)) {
                    it.clearFocus()
                    it.hideSoftInput()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        lifecycleScope.launch {
            //隐私协议
            if (!privacyPolicy()) return@launch
            //版本更新
            upVersion()
            //设置本地密码
            setLocalPassword()
            notifyAppCrash()
            //备份同步
            backupSync()
            //自动更新书籍
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook") ?: false
            if (AppConfig.autoRefreshBook && !isAutoRefreshedBook) {
                binding.viewPagerMain.postDelayed(1000) {
                    viewModel.upAllBookToc()
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean = binding.run {
        when (item.itemId) {
            R.id.menu_bookshelf ->
                viewPagerMain.setCurrentItem(0, false)

            R.id.menu_discovery ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)

            R.id.menu_my_config ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
        }
        return false
    }

    override fun onNavigationItemReselected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_bookshelf -> {
                if (System.currentTimeMillis() - bookshelfReselected > 300) {
                    bookshelfReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.gotoTop()
                }
            }

            R.id.menu_discovery -> {
                if (System.currentTimeMillis() - exploreReselected > 300) {
                    exploreReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[1] as? ExploreFragment)?.compressExplore()
                }
            }
        }
    }

    private fun initView() = binding.run {
        viewPagerMain.setEdgeEffectColor(primaryColor)
        viewPagerMain.offscreenPageLimit = 3
        viewPagerMain.adapter = adapter
        viewPagerMain.registerOnPageChangeCallback(PageChangeCallback())
        bottomNavigationView.elevation = elevation
        bottomNavigationView.setOnNavigationItemSelectedListener(this@MainActivity)
        bottomNavigationView.setOnNavigationItemReselectedListener(this@MainActivity)
        if (AppConfig.isEInkMode) {
            bottomNavigationView.setBackgroundResource(R.drawable.bg_eink_border_top)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val height = windowInsets.navigationBarHeight
            bottomNavigationView.bottomPadding = height
            windowInsets.inset(0, 0, 0, height)
        }
    }

    /**
     * 用户隐私与协议
     */
    private suspend fun privacyPolicy(): Boolean = suspendCoroutine { block ->
        if (LocalConfig.privacyPolicyOk) {
            block.resume(true)
            return@suspendCoroutine
        }
        val privacyPolicy = String(assets.open("privacyPolicy.md").readBytes())
        alert(getString(R.string.privacy_policy), privacyPolicy) {
            noButton {
                finish()
                block.resume(false)
            }
            positiveButton(R.string.agree) {
                LocalConfig.privacyPolicyOk = true
                block.resume(true)
            }
            negativeButton(R.string.refuse) {
                finish()
                block.resume(false)
            }
        }
    }

    /**
     * 版本更新日志
     */
    private suspend fun upVersion() = suspendCoroutine { block ->
        if (LocalConfig.versionCode == appInfo.versionCode) {
            block.resume(null)
            return@suspendCoroutine
        }
        LocalConfig.versionCode = appInfo.versionCode
        if (LocalConfig.isFirstOpenApp) {
            val help = String(assets.open("web/help/md/appHelp.md").readBytes())
            val dialog = TextDialog(getString(R.string.help), help, TextDialog.Mode.MD)
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else if (!BuildConfig.DEBUG) {
            val log = String(assets.open("updateLog.md").readBytes())
            val dialog = TextDialog(getString(R.string.update_log), log, TextDialog.Mode.MD)
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else {
            block.resume(null)
        }
    }

    /**
     * 设置本地密码
     */
    private suspend fun setLocalPassword() = suspendCoroutine { block ->
        if (LocalConfig.password != null) {
            block.resume(null)
            return@suspendCoroutine
        }
        alert(R.string.set_local_password, R.string.set_local_password_summary) {
            val editTextBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "password"
            }
            customView {
                editTextBinding.root
            }
            onDismiss {
                block.resume(null)
            }
            okButton {
                LocalConfig.password = editTextBinding.editView.text.toString()
            }
            cancelButton {
                LocalConfig.password = ""
            }
        }
    }

    private fun notifyAppCrash() {
        if (!LocalConfig.appCrash || BuildConfig.DEBUG) {
            return
        }
        LocalConfig.appCrash = false
        alert(getString(R.string.draw), "检测到阅读发生了崩溃，是否打开崩溃日志以便报告问题？") {
            yesButton {
                showDialogFragment<CrashLogsDialog>()
            }
            noButton()
        }
    }

    /**
     * 备份同步
     */
    private fun backupSync() {
        lifecycleScope.launch {
            val lastBackupFile =
                withContext(IO) { AppWebDav.lastBackUp().getOrNull() } ?: return@launch
            if (lastBackupFile.lastModify - LocalConfig.lastBackup > DateUtils.MINUTE_IN_MILLIS) {
                LocalConfig.lastBackup = lastBackupFile.lastModify
                alert(R.string.restore, R.string.webdav_after_local_restore_confirm) {
                    cancelButton()
                    okButton {
                        viewModel.restoreWebDav(lastBackupFile.displayName)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (AppConfig.autoRefreshBook) {
            outState.putBoolean("isAutoRefreshedBook", true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    /**
     * 如果重启太快fragment不会重建,这里更新一下书架的排序
     */
    override fun recreate() {
        (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.run {
            upSort()
        }
        super.recreate()
    }

    override fun observeLiveBus() {
        viewModel.onUpBooksLiveData.observe(this) {
            onUpBooksBadgeView.setBadgeCount(it)
        }
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            binding.apply {
                upBottomMenu()
                if (it) {
                    viewPagerMain.setCurrentItem(bottomMenuCount - 1, false)
                }
            }
        }
        observeEvent<String>(PreferKey.threadCount) {
            viewModel.upPool()
        }
    }

    private fun upBottomMenu() {
        val showDiscovery = AppConfig.showDiscovery
        binding.bottomNavigationView.menu.let { menu ->
            menu.findItem(R.id.menu_discovery).isVisible = showDiscovery
        }
        var index = 0
        if (showDiscovery) {
            index++
            realPositions[index] = idExplore
        }
        index++
        realPositions[index] = idMy
        bottomMenuCount = index + 1
        adapter.notifyDataSetChanged()
    }

    private fun upHomePage() {
        when (AppConfig.defaultHomePage) {
            "bookshelf" -> {}
            "explore" -> if (AppConfig.showDiscovery) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)
            }

            "my" -> binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
        }
    }

    private fun getFragmentId(position: Int): Int {
        return realPositions[position]
    }

    private inner class PageChangeCallback : ViewPager2.OnPageChangeCallback() {

        override fun onPageSelected(position: Int) {
            pagePosition = position
            binding.bottomNavigationView.menu
                .getItem(realPositions[position]).isChecked = true
        }

    }

    private inner class TabFragmentPagerAdapter(fa: FragmentActivity) :
        FragmentStateAdapter(fa) {

        override fun getItemCount(): Int {
            return bottomMenuCount
        }

        override fun getItemId(position: Int): Long {
            return getFragmentId(position).toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            val id = itemId.toInt()
            for (i in 0 until bottomMenuCount) {
                if (realPositions[i] == id) return true
            }
            return false
        }

        override fun createFragment(position: Int): Fragment {
            val fragmentId = getFragmentId(position)
            val fragment = when (fragmentId) {
                idBookshelf1 -> BookshelfFragment1(position)
                idExplore -> ExploreFragment(position)
                else -> MyFragment(position)
            }
            fragmentMap[fragmentId] = fragment
            return fragment
        }

    }

}