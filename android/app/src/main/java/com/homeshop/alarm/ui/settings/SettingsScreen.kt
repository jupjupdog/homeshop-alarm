package com.homeshop.alarm.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homeshop.alarm.data.UserSettings
import com.homeshop.alarm.model.Categories
import com.homeshop.alarm.ui.appContainer
import kotlinx.coroutines.launch

private val LEAD_OPTIONS = listOf(0, 5, 10, 15, 30)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle(UserSettings())

    // 권한 상태는 설정 앱에 다녀온 뒤 다시 확인해야 하므로 onResume 마다 갱신
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val notifGranted = remember(resumeTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val exactGranted = remember(resumeTick) { c.alarmScheduler.canScheduleExact() }

    // 관심 카테고리나 알림 시점이 바뀌면 알람을 다시 맞춘다
    fun applyAndReschedule(block: suspend () -> Unit) = scope.launch {
        block()
        c.alarmScheduler.reschedule(c.scheduleRepository.upcoming())
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("관심 카테고리 · 알림") }, windowInsets = WindowInsets(0, 0, 0, 0))
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("관심 카테고리", "선택한 카테고리 상품이 방송되면 알려 드려요.") {
                Categories.ALL.forEach { cat ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            applyAndReschedule { c.settingsRepository.toggleInterest(cat) }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = cat in settings.interestCategories, onCheckedChange = {
                            applyAndReschedule { c.settingsRepository.toggleInterest(cat) }
                        })
                        Text(cat)
                    }
                }
            }

            Section("알림 시점", "방송 시작 몇 분 전에 알릴까요?") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LEAD_OPTIONS.forEach { m ->
                        FilterChip(
                            selected = settings.leadMinutes == m,
                            onClick = { applyAndReschedule { c.settingsRepository.setLeadMinutes(m) } },
                            label = { Text(if (m == 0) "시작할 때" else "${m}분 전") },
                        )
                    }
                }
            }

            Section("권한", null) {
                PermissionRow(
                    label = "알림 표시",
                    granted = notifGranted,
                    onFix = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PermissionRow(
                        label = "정시 알람 (허용 안 하면 몇 분 늦을 수 있어요)",
                        granted = exactGranted,
                        onFix = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text((if (granted) "✅ " else "⚠️ ") + label, Modifier.weight(1f))
        if (!granted) TextButton(onClick = onFix) { Text("허용하기") }
    }
}
