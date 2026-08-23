package com.example.tvbrowser.ui.home

import android.os.Bundle
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.tvbrowser.R
import com.example.tvbrowser.data.AppDatabase
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.BookmarkRepository
import kotlinx.coroutines.launch

class HomeFragment : BrowseSupportFragment() {

    interface Callbacks {
        fun onServiceLaunchRequested(bookmark: Bookmark)
        fun onAddServiceRequested()
        fun onSettingsRequested()
        fun onServiceLongPressed(bookmark: Bookmark)
    }

    private lateinit var repository: BookmarkRepository

    private val cardPresenter = HomeCardPresenter(
        onItemClick = ::dispatchClick,
        onItemLongClick = ::dispatchLongClick
    )
    private val servicesAdapter = ArrayObjectAdapter(cardPresenter)
    private val manageAdapter = ArrayObjectAdapter(cardPresenter)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.home_title)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        repository = BookmarkRepository(AppDatabase.getInstance(requireContext()).bookmarkDao())

        manageAdapter.add(HomeCard.AddService)
        manageAdapter.add(HomeCard.Settings)

        adapter = ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0L, getString(R.string.row_services)), servicesAdapter))
            add(ListRow(1L, HeaderItem(getString(R.string.row_manage)), manageAdapter))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSelectedPosition(0)
        lifecycleScope.launch {
            repository.seedPresetsIfEmpty()
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeAll().collect { services -> renderServices(services) }
            }
        }
    }

    private fun dispatchClick(card: HomeCard) {
        when (card) {
            is HomeCard.Service -> callbacks().onServiceLaunchRequested(card.bookmark)
            HomeCard.GhostAddFirst -> callbacks().onAddServiceRequested()
            HomeCard.AddService -> callbacks().onAddServiceRequested()
            HomeCard.Settings -> callbacks().onSettingsRequested()
        }
    }

    private fun dispatchLongClick(card: HomeCard): Boolean {
        val bookmark = (card as? HomeCard.Service)?.bookmark ?: return false
        callbacks().onServiceLongPressed(bookmark)
        return true
    }

    private fun renderServices(services: List<Bookmark>) {
        servicesAdapter.clear()
        if (services.isEmpty()) {
            servicesAdapter.add(HomeCard.GhostAddFirst)
        } else {
            services.forEach { servicesAdapter.add(HomeCard.Service(it)) }
        }
    }

    private fun callbacks(): Callbacks = activity as Callbacks
}
