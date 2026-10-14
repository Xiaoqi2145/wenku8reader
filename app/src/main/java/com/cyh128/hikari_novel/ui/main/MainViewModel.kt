package com.cyh128.hikari_novel.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.data.repository.AppRepository
import com.drake.channel.sendEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val appRepository: AppRepository
) : ViewModel() {
    val isAutoUpdate get() = appRepository.getIsAutoUpdate()

    val defaultTab get() = appRepository.getDefaultTab()

    fun checkUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            appRepository.checkUpdate()
                .onSuccess {
                    if (it) sendEvent(Event.HaveAvailableUpdateEvent,"event_main_activity")
                }
                // A failed *automatic* check is not worth interrupting the user
                // for: they did not ask for it, and a blocked or offline GitHub
                // API would greet every launch with a non-cancelable error
                // dialog. The manual check on the About screen still reports
                // failures, because there the user is waiting for an answer.
        }
    }
}
