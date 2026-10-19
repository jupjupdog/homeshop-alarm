package com.homeshop.alarm.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.homeshop.alarm.ui.theme.HomeshopTheme
import com.homeshop.alarm.work.SyncWorker

class MainActivity : ComponentActivity() {

    /** 알림을 눌러 들어왔을 때 열어야 할 상품 키. */
    private val pendingProductKey = mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 설정 화면에서 상태 표시 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingProductKey.value = intent.getStringExtra(EXTRA_PRODUCT_KEY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) SyncWorker.runNow(this)

        setContent {
            HomeshopTheme {
                AppNav(
                    openProductKey = pendingProductKey.value,
                    onOpenedProduct = { pendingProductKey.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_PRODUCT_KEY)?.let { pendingProductKey.value = it }
    }

    companion object {
        private const val EXTRA_PRODUCT_KEY = "productKey"

        fun detailIntent(context: Context, productKey: String) =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_PRODUCT_KEY, productKey)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
