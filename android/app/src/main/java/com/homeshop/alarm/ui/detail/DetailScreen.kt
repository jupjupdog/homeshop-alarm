package com.homeshop.alarm.ui.detail

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.homeshop.alarm.model.ProductItem
import com.homeshop.alarm.ui.PriceSite
import com.homeshop.alarm.ui.appContainer
import com.homeshop.alarm.ui.formatDayTime
import com.homeshop.alarm.ui.formatPrice
import com.homeshop.alarm.ui.formatTime

/**
 * 홈쇼핑 상품 정보(위) + 쇼핑몰 최저가 검색 결과(아래 탭)를 한 화면에서 같이 본다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(productKey: String, onBack: () -> Unit) {
    val c = appContainer()
    val flow = remember(productKey) { c.scheduleRepository.observe(productKey) }
    val product by flow.collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("최저가 비교") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        val p = product
        if (p == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("방송이 끝났거나 편성표에서 사라진 상품이에요.") }
        } else {
            DetailContent(p)
        }
    }
}

@Composable
private fun DetailContent(p: ProductItem) {
    val context = LocalContext.current
    var query by rememberSaveable(p.key) { mutableStateOf(p.searchQuery) }
    var appliedQuery by rememberSaveable(p.key) { mutableStateOf(p.searchQuery) }
    var siteIndex by rememberSaveable { mutableStateOf(0) }
    val site = PriceSite.entries[siteIndex]

    fun openExternal(url: String) =
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))

    Column(Modifier.fillMaxSize()) {
        // ── 홈쇼핑 상품 ──
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            AsyncImage(
                model = p.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${p.channel} · ${formatDayTime(p.startMillis)}~${formatTime(p.endMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (p.isOnAir()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text("홈쇼핑가 ${formatPrice(p.price)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                p.productUrl?.let { url ->
                    OutlinedButton(onClick = { openExternal(url) }, modifier = Modifier.padding(top = 4.dp)) {
                        Text("홈쇼핑 상품 페이지")
                    }
                }
            }
        }

        // ── 검색어 (홈쇼핑 전용 구성 때문에 직접 고칠 수 있게) ──
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("최저가 검색어") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { appliedQuery = query }) { Icon(Icons.Filled.Search, "검색") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { appliedQuery = query }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        // ── 쇼핑몰 탭 + 검색 결과 ──
        TabRow(selectedTabIndex = siteIndex, modifier = Modifier.padding(top = 8.dp)) {
            PriceSite.entries.forEachIndexed { i, s ->
                Tab(selected = i == siteIndex, onClick = { siteIndex = i }, text = { Text(s.label, maxLines = 1) })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { openExternal(site.url(appliedQuery)) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "${site.label} 브라우저로 열기")
            }
        }
        SearchWebView(url = site.url(appliedQuery), modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SearchWebView(url: String, modifier: Modifier = Modifier) {
    var loading by remember { mutableStateOf(true) }
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true      // 쇼핑몰 검색 결과가 JS 로 그려짐
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) { loading = false }
                        // 앱 설치 유도 링크(intent://, market:// 등)는 WebView 에서 열지 않음
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) =
                            request?.url?.scheme !in setOf("http", "https")
                    }
                }
            },
            update = { web ->
                if (web.url != url && web.tag != url) {
                    web.tag = url
                    loading = true
                    web.loadUrl(url)
                }
            },
        )
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
}
