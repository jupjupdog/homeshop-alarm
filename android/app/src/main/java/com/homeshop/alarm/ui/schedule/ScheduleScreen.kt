package com.homeshop.alarm.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.homeshop.alarm.model.ProductItem
import com.homeshop.alarm.ui.appContainer
import com.homeshop.alarm.ui.formatDayTime
import com.homeshop.alarm.ui.formatPrice
import com.homeshop.alarm.ui.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onOpenProduct: (String) -> Unit) {
    val container = appContainer()
    val vm: ScheduleViewModel = viewModel(factory = viewModelFactory { initializer { ScheduleViewModel(container) } })
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("홈쇼핑 편성표") },
            windowInsets = WindowInsets(0, 0, 0, 0), // 상태바 여백은 바깥 Scaffold 가 처리
            actions = {
                if (state.refreshing) {
                    CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "새로고침") }
                }
            },
        )
        FilterRow(state, vm::updateFilter)
        state.error?.let {
            Text(
                "⚠ $it",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        when {
            !state.loaded -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.items.isEmpty() -> EmptyState(state)
            else -> ScheduleList(state, onOpenProduct)
        }
    }
}

@Composable
private fun FilterRow(state: ScheduleUiState, update: ((ScheduleFilter) -> ScheduleFilter) -> Unit) {
    val f = state.filter
    Column {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(f.onlyOnAir, { update { it.copy(onlyOnAir = !it.onlyOnAir) } }, { Text("● 방송 중") })
            FilterChip(f.onlyInterests, { update { it.copy(onlyInterests = !it.onlyInterests) } }, { Text("관심만") })
            state.channels.forEach { ch ->
                FilterChip(f.channel == ch, {
                    update { it.copy(channel = if (it.channel == ch) null else ch) }
                }, { Text(ch) })
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.categories.forEach { cat ->
                FilterChip(f.category == cat, {
                    update { it.copy(category = if (it.category == cat) null else cat) }
                }, { Text(if (cat in state.interests) "★ $cat" else cat) })
            }
        }
    }
}

@Composable
private fun ScheduleList(state: ScheduleUiState, onOpenProduct: (String) -> Unit) {
    // 시작 시각별로 묶어서 시간 헤더를 보여 준다
    val groups = state.items.groupBy { it.startMillis }
    LazyColumn(Modifier.fillMaxSize()) {
        groups.forEach { (start, items) ->
            item(key = "h$start") {
                Text(
                    formatDayTime(start),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            items(items, key = { it.key }) { p ->
                ProductRow(p, onAir = p.isOnAir(state.now), interest = p.category in state.interests) {
                    onOpenProduct(p.key)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ProductRow(p: ProductItem, onAir: Boolean, interest: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = p.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onAir) Badge("ON AIR", MaterialTheme.colorScheme.error)
                Text(
                    "${p.channel} · ${formatTime(p.startMillis)}~${formatTime(p.endMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formatPrice(p.price), fontWeight = FontWeight.Bold)
                Badge(
                    if (interest) "★ ${p.category}" else p.category,
                    if (interest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(4.dp)) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyState(state: ScheduleUiState) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Text(
            if (state.filter != ScheduleFilter()) "조건에 맞는 방송이 없어요. 필터를 줄여 보세요."
            else "편성표가 아직 없어요. 오른쪽 위 새로고침을 눌러 주세요.\n(gradle.properties 의 scheduleUrl 설정을 확인하세요)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
