package com.simplemobiletools.smsmessenger.dialogs

import android.view.*
import android.widget.PopupMenu
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.copyToClipboard
import com.simplemobiletools.commons.extensions.getPopupMenuTheme
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.setupViewBackground
import com.simplemobiletools.commons.interfaces.RefreshRecyclerViewListener
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.config
import kotlinx.android.synthetic.main.item_manage_blocked_keyword.view.manage_blocked_keyword_holder
import kotlinx.android.synthetic.main.item_manage_blocked_keyword.view.manage_blocked_keyword_title
import kotlinx.android.synthetic.main.item_manage_blocked_keyword.view.overflow_menu_anchor
import kotlinx.android.synthetic.main.item_manage_blocked_keyword.view.overflow_menu_icon

class ManageBlockedKeywordsAdapter(
    activity: BaseSimpleActivity, var blockedKeywords: ArrayList<String>, val listener: RefreshRecyclerViewListener?,
    recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {
    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_blocked_keywords

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_copy_keyword).isVisible = isOneItemSelected()
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_copy_keyword -> copyKeywordToClipboard()
            R.id.cab_delete -> deleteSelection()
        }
    }

    override fun getSelectableItemCount() = blockedKeywords.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = blockedKeywords.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = blockedKeywords.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_manage_blocked_keyword, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val blockedKeyword = blockedKeywords[position]
        holder.bindView(blockedKeyword, true, true) { itemView, _ ->
            setupView(itemView, blockedKeyword)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = blockedKeywords.size

    private fun getSelectedItems() = blockedKeywords.filter { selectedKeys.contains(it.hashCode()) }

    private fun setupView(view: View, blockedKeyword: String) {
        view.apply {
            setupViewBackground(activity)
            manage_blocked_keyword_holder?.isSelected = selectedKeys.contains(blockedKeyword.hashCode())
            manage_blocked_keyword_title.apply {
                text = blockedKeyword
                setTextColor(textColor)
            }

            overflow_menu_icon.drawable.apply {
                mutate()
                setTint(activity.getProperTextColor())
            }

            overflow_menu_icon.setOnClickListener {
                showPopupMenu(overflow_menu_anchor, blockedKeyword)
            }
        }
    }

    private fun showPopupMenu(view: View, blockedKeyword: String) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(getActionMenuId())
            setOnMenuItemClickListener { item ->
                val blockedKeywordId = blockedKeyword.hashCode()
                when (item.itemId) {
                    R.id.cab_copy_keyword -> {
                        executeItemMenuOperation(blockedKeywordId) {
                            copyKeywordToClipboard()
                        }
                    }

                    R.id.cab_delete -> {
                        executeItemMenuOperation(blockedKeywordId) {
                            deleteSelection()
                        }
                    }
                }
                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(blockedKeywordId: Int, callback: () -> Unit) {
        selectedKeys.add(blockedKeywordId)
        callback()
        selectedKeys.remove(blockedKeywordId)
    }

    private fun copyKeywordToClipboard() {
        val selectedKeyword = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(selectedKeyword)
        finishActMode()
    }

    private fun deleteSelection() {
        val deleteBlockedKeywords = HashSet<String>(selectedKeys.size)
        val positions = getSelectedItemPositions()

        getSelectedItems().forEach {
            deleteBlockedKeywords.add(it)
            activity.config.removeBlockedKeyword(it)
        }

        blockedKeywords.removeAll(deleteBlockedKeywords)
        removeSelectedItems(positions)
        if (blockedKeywords.isEmpty()) {
            listener?.refreshItems()
        }
    }
}
