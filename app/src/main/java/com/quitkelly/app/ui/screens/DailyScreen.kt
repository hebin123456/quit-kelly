package com.quitkelly.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quitkelly.app.KellyApp
import com.quitkelly.app.R
import com.quitkelly.app.data.DigestEntity
import com.quitkelly.app.data.NewsItemEntity
import com.quitkelly.app.ui.components.GoldDot
import com.quitkelly.app.ui.components.KellyAvatar
import com.quitkelly.app.ui.components.KellyTopBar
import com.quitkelly.app.ui.components.MarkdownText
import com.quitkelly.app.ui.components.MessageBanner
import com.quitkelly.app.ui.vm.DailyUiState
import com.quitkelly.app.ui.vm.DailyViewModel
import com.quitkelly.app.util.formatDateLabel
import com.quitkelly.app.util.formatNewsTime
import com.quitkelly.app.util.greeting
import com.quitkelly.app.util.shortDate

@Composable
fun DailyScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? KellyApp ?: return
    val viewModel: DailyViewModel = viewModel(factory = DailyViewModel.factory(app.container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val speechState by app.container.speechPlayer.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResume()
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        viewModel.onScreenResume()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KellyTopBar(
            title = "凯小丽",
            subtitle = "AI 财经日报 · 每天替你读新闻",
            actions = {
                IconButton(
                    onClick = viewModel::generateToday,
                    enabled = !state.generating
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeroCard(
                    state = state,
                    onGenerate = viewModel::generateToday,
                    onOpenSettings = onOpenSettings
                )
            }
            state.error?.let { error ->
                item {
                    MessageBanner(
                        text = error,
                        onDismiss = viewModel::dismissError
                    )
                }
            }
            speechState.error?.let { error ->
                item {
                    MessageBanner(
                        text = error,
                        onDismiss = { app.container.speechPlayer.clearError() }
                    )
                }
            }
            if (state.digestDates.size > 1) {
                item {
                    DateSelector(
                        dates = state.digestDates,
                        selected = state.selectedDate,
                        onSelect = viewModel::selectDate
                    )
                }
            }
            val digest = state.digest
            if (digest != null) {
                item {
                    DigestCard(
                        digest = digest,
                        isSpeaking = speechState.speaking &&
                            speechState.activeKey == "digest:${digest.date}",
                        onToggleSpeech = {
                            app.container.speechPlayer.toggle(
                                "digest:${digest.date}",
                                digest.content
                            )
                        }
                    )
                }
            } else if (!state.loading) {
                item {
                    EmptyDigestCard(
                        hasApiKey = state.hasApiKey,
                        generating = state.generating,
                        onGenerate = viewModel::generateToday,
                        onOpenSettings = onOpenSettings
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "当日要闻",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${state.news.size} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.news.isEmpty()) {
                item {
                    Text(
                        text = "暂无新闻，点击右上角刷新抓取。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.news, key = { it.id }) { item ->
                    NewsCard(item = item, onClick = { openUrl(context, item.url) })
                }
            }
            item {
                Text(
                    text = "以上内容由 AI 整理，仅供参考，不构成投资建议。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    state: DailyUiState,
    onGenerate: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0B2E27), Color(0xFF0F7B63), Color(0xFF178A6E))
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KellyAvatar(38.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${greeting()}，我是凯小丽",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "每天替你读完财经新闻，再讲重点",
                        color = Color(0xCCFFFFFF),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (state.digest != null) {
                    "${formatDateLabel(state.selectedDate)} · 日报已生成"
                } else {
                    "今日日报还未生成"
                },
                color = Color(0xFFF2C14E),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    state.generating -> "正在抓取新闻并撰写日报，请稍候…"
                    state.digest != null -> "共阅读 ${state.digest?.newsCount ?: 0} 条新闻，已提炼关键信息与风险提示。"
                    !state.hasApiKey -> "先配置 DeepSeek API Key，再生成你的第一份日报。"
                    else -> "点击下方按钮，凯小丽马上开始工作。"
                },
                color = Color(0xD9FFFFFF),
                style = MaterialTheme.typography.bodySmall
            )
            if (state.generating) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFF2C14E)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = if (state.hasApiKey) onGenerate else onOpenSettings,
                    enabled = !state.generating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF2C14E),
                        contentColor = Color(0xFF1B1C19)
                    )
                ) {
                    Text(
                        text = if (state.hasApiKey) "生成 / 刷新日报" else "去配置 API Key",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (state.hasApiKey) {
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = onOpenSettings,
                        border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("设置")
                    }
                }
            }
        }
    }
}

@Composable
private fun DigestCard(
    digest: DigestEntity,
    isSpeaking: Boolean,
    onToggleSpeech: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoldDot()
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatDateLabel(digest.date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = digest.model,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onToggleSpeech,
                    enabled = digest.content.isNotBlank()
                ) {
                    if (isSpeaking) {
                        Icon(
                            painter = painterResource(R.drawable.ic_speech_stop),
                            contentDescription = "停止朗读",
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "朗读日报"
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            MarkdownText(digest.content)
        }
    }
}

@Composable
private fun EmptyDigestCard(
    hasApiKey: Boolean,
    generating: Boolean,
    onGenerate: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "今天还没有日报",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (hasApiKey) {
                    "点击生成，凯小丽会抓取最新财经新闻并撰写摘要。"
                } else {
                    "请先在设置中配置 DeepSeek API Key。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = if (hasApiKey) onGenerate else onOpenSettings,
                enabled = !generating
            ) {
                Text(if (hasApiKey) "生成今日日报" else "去设置")
            }
        }
    }
}

@Composable
private fun DateSelector(
    dates: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(dates) { date ->
            FilterChip(
                selected = date == selected,
                onClick = { onSelect(date) },
                label = { Text(shortDate(date)) }
            )
        }
    }
}

@Composable
private fun NewsCard(item: NewsItemEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatNewsTime(item.publishedAt, item.timeApproximate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
