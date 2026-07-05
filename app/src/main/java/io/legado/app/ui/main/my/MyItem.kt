package io.legado.app.ui.main.my

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * 我的页面列表项数据模型。
 *
 * - [TYPE_CATEGORY]：分组标题（数据管理 / 外观与个性化 / 其他）
 * - [TYPE_ENTRY_GROUP]：单张卡片，内部包含多个入口行
 */
sealed class MyItem {

    data class Category(
        @StringRes val titleRes: Int
    ) : MyItem()

    data class EntryGroup(
        val entries: List<Entry>
    ) : MyItem()

    data class Entry(
        val key: String,
        @DrawableRes val iconRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val summaryRes: Int? = null,
        val action: Action
    )

    enum class Action {
        BOOK_SOURCE,
        TXT_TOC_RULE,
        REPLACE_RULE,
        BOOKMARK,
        READ_RECORD,
        THEME_MODE,
        STYLE_MODE,
        THEME_SETTING,
        WEB_SERVICE,
        BACKUP_RESTORE,
        OTHER_SETTING,
        ABOUT,
        EXIT
    }

    companion object {
        const val TYPE_CATEGORY = 0
        const val TYPE_ENTRY_GROUP = 1
    }
}
