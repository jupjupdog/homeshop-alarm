package com.homeshop.alarm.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeshop.alarm.AppContainer
import com.homeshop.alarm.model.ProductItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduleFilter(
    val onlyInterests: Boolean = false,
    val onlyOnAir: Boolean = false,
    val channel: String? = null,      // null = 전체
    val category: String? = null,
)

data class ScheduleUiState(
    val items: List<ProductItem> = emptyList(),
    val channels: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val interests: Set<String> = emptySet(),
    val filter: ScheduleFilter = ScheduleFilter(),
    val now: Long = System.currentTimeMillis(),
    val refreshing: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

class ScheduleViewModel(private val c: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(ScheduleFilter())
    private val status = MutableStateFlow(Status())

    private data class Status(val refreshing: Boolean = false, val error: String? = null)

    /** 1분마다 현재 시각 갱신 → '방송 중' 표시와 지난 방송 숨김에 사용. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis()); delay(60_000)
        }
    }

    val uiState: StateFlow<ScheduleUiState> = combine(
        c.scheduleRepository.observeUpcoming(),
        c.settingsRepository.settings,
        filter,
        status,
        ticker,
    ) { all, settings, f, st, now ->
        val alive = all.filter { it.endMillis > now }
        val shown = alive.filter { p ->
            (!f.onlyInterests || p.category in settings.interestCategories) &&
                (!f.onlyOnAir || p.isOnAir(now)) &&
                (f.channel == null || p.channel == f.channel) &&
                (f.category == null || p.category == f.category)
        }
        ScheduleUiState(
            items = shown,
            channels = alive.map { it.channel }.distinct().sorted(),
            categories = alive.map { it.category }.distinct(),
            interests = settings.interestCategories,
            filter = f,
            now = now,
            refreshing = st.refreshing,
            error = st.error,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    fun updateFilter(transform: (ScheduleFilter) -> ScheduleFilter) = filter.update(transform)

    fun refresh() = viewModelScope.launch {
        status.value = Status(refreshing = true)
        status.value = try {
            val result = c.scheduleRepository.refresh()
            c.settingsRepository.setLastSyncMillis(System.currentTimeMillis())
            c.alarmScheduler.reschedule(c.scheduleRepository.upcoming())
            val failed = result.errors.map { it.substringBefore(' ') }.distinct()
            Status(error = if (failed.isEmpty()) null else "일부 채널을 받지 못했어요: ${failed.joinToString()}")
        } catch (e: Exception) {
            Status(error = e.message ?: "편성표를 불러오지 못했습니다")
        }
    }
}
