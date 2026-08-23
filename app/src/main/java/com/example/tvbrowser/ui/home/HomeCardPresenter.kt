package com.example.tvbrowser.ui.home

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.example.tvbrowser.R
import kotlin.concurrent.thread

class HomeCardPresenter(
    private val onItemClick: (HomeCard) -> Unit,
    private val onItemLongClick: (HomeCard) -> Boolean
) : Presenter() {

    class CardViewHolder(view: ImageCardView) : Presenter.ViewHolder(view) {
        var card: HomeCard? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context)
        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        cardView.setMainImageDimensions(dp(parent, CARD_WIDTH_DP), dp(parent, CARD_HEIGHT_DP))
        val holder = CardViewHolder(cardView)
        cardView.setOnClickListener { holder.card?.let(onItemClick) }
        cardView.setOnLongClickListener { holder.card?.let(onItemLongClick) ?: false }
        return holder
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val holder = viewHolder as CardViewHolder
        val card = holder.view as ImageCardView
        val homeCard = item as? HomeCard ?: return
        holder.card = homeCard
        when (homeCard) {
            is HomeCard.Service -> {
                card.titleText = homeCard.bookmark.title
                loadBanner(card, homeCard.bookmark.bannerUri)
            }
            HomeCard.GhostAddFirst -> {
                card.titleText = card.context.getString(R.string.ghost_add_first_service)
                showPlaceholder(card)
            }
            HomeCard.AddService -> {
                card.titleText = card.context.getString(R.string.card_add_service)
                showPlaceholder(card)
            }
            HomeCard.Settings -> {
                card.titleText = card.context.getString(R.string.card_settings)
                showPlaceholder(card)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val holder = viewHolder as CardViewHolder
        holder.card = null
        val card = holder.view as ImageCardView
        card.mainImage = null
    }

    private fun loadBanner(card: ImageCardView, bannerUri: String?) {
        if (bannerUri == null) {
            showPlaceholder(card)
            return
        }
        val context = card.context
        thread {
            val bitmap = runCatching {
                context.contentResolver.openInputStream(Uri.parse(bannerUri))
                    ?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            card.post {
                if (card.isAttachedToWindow) {
                    card.mainImage = bitmap
                        ?.let { BitmapDrawable(card.resources, it) }
                        ?: placeholder(context)
                }
            }
        }
    }

    private fun showPlaceholder(card: ImageCardView) {
        card.mainImage = placeholder(card.context)
    }

    private fun placeholder(context: Context) =
        ContextCompat.getDrawable(context, R.drawable.ic_service_placeholder)

    private fun dp(parent: ViewGroup, value: Int): Int =
        (value * parent.resources.displayMetrics.density).toInt()

    companion object {
        const val CARD_WIDTH_DP = 300
        const val CARD_HEIGHT_DP = 170
    }
}
