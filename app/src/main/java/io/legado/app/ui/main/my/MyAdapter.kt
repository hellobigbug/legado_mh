package io.legado.app.ui.main.my

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.utils.dpToPx

/**
 * 我的页面 RecyclerView Adapter。
 *
 * 两种 ViewType：
 * - [MyItem.TYPE_CATEGORY]：分组标题
 * - [MyItem.TYPE_ENTRY_GROUP]：单张 MaterialCardView 卡片，内部 LinearLayout 填充多个 entry row
 */
class MyAdapter(
    private val onEntryClick: (MyItem.Action) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<MyItem>()

    fun submitList(list: List<MyItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** 刷新指定 key 所在的 entry（用于主题/风格切换后更新 summary） */
    fun refreshEntry(key: String) {
        for (index in items.indices) {
            val group = items[index] as? MyItem.EntryGroup ?: continue
            if (group.entries.any { it.key == key }) {
                notifyItemChanged(index)
                return
            }
        }
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is MyItem.Category -> MyItem.TYPE_CATEGORY
        is MyItem.EntryGroup -> MyItem.TYPE_ENTRY_GROUP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            MyItem.TYPE_CATEGORY -> CategoryViewHolder(
                inflater.inflate(R.layout.item_my_category, parent, false)
            )
            else -> EntryGroupViewHolder(
                inflater.inflate(R.layout.item_my_entry_group, parent, false)
            )
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is MyItem.Category -> (holder as CategoryViewHolder).bind(item)
            is MyItem.EntryGroup -> (holder as EntryGroupViewHolder).bind(item, onEntryClick)
        }
    }

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tv_category_title)

        fun bind(item: MyItem.Category) {
            tvTitle.setText(item.titleRes)
        }
    }

    class EntryGroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val llContainer: LinearLayout = view.findViewById(R.id.ll_entry_container)

        fun bind(
            group: MyItem.EntryGroup,
            onEntryClick: (MyItem.Action) -> Unit
        ) {
            llContainer.removeAllViews()
            val context = llContainer.context
            group.entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    llContainer.addView(buildDivider(context))
                }
                val row = LayoutInflater.from(context)
                    .inflate(R.layout.item_my_entry, llContainer, false)
                row.findViewById<ImageView>(R.id.iv_icon).setImageResource(entry.iconRes)
                row.findViewById<TextView>(R.id.tv_title).setText(entry.titleRes)
                val tvSummary = row.findViewById<TextView>(R.id.tv_summary)
                if (entry.summaryRes != null) {
                    tvSummary.setText(entry.summaryRes)
                    tvSummary.visibility = View.VISIBLE
                } else {
                    tvSummary.visibility = View.GONE
                }
                row.setOnClickListener { onEntryClick(entry.action) }
                llContainer.addView(row)
            }
        }

        private fun buildDivider(context: Context): View {
            return View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1.dpToPx()
                ).apply {
                    marginStart = 60.dpToPx()
                }
                setBackgroundColor(ContextCompat.getColor(context, R.color.bg_divider_line))
            }
        }
    }
}
