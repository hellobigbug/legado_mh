package io.legado.app.ui.main.bookshelf

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogAddBookBinding
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.ui.book.import.remote.RemoteBookActivity
import io.legado.app.utils.startActivity

/**
 * 添加书籍统一入口（底部弹出对话框）。
 *
 * 整合原菜单的"添加本地"、"添加远程"、"添加 URL"3 项。
 */
class AddBookDialog : BaseDialogFragment(R.layout.dialog_add_book) {

    private val binding by lazy { DialogAddBookBinding.bind(requireView()) }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val attr = attributes
            attr.gravity = Gravity.BOTTOM
            attr.windowAnimations = android.R.style.Animation_InputMethod
            attributes = attr
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.llAddLocal.setOnClickListener {
            dismiss()
            startActivity<ImportBookActivity>()
        }
        binding.llAddRemote.setOnClickListener {
            dismiss()
            startActivity<RemoteBookActivity>()
        }
        binding.llAddUrl.setOnClickListener {
            dismiss()
            (parentFragment as? BaseBookshelfFragment)?.showAddBookByUrlAlert()
        }
    }
}
