package com.quitkelly.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quitkelly.app.KellyApp
import com.quitkelly.app.data.ChatMessageEntity
import com.quitkelly.app.ui.components.KellyAvatar
import com.quitkelly.app.ui.components.KellyTopBar
import com.quitkelly.app.ui.components.MarkdownText
import com.quitkelly.app.ui.components.MessageBanner
import com.quitkelly.app.ui.vm.ChatViewModel

@Composable
fun ChatScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? KellyApp ?: return
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(app.container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshKeyState()
        onPauseOrDispose { }
    }

    val itemCount = state.messages.size + (if (state.isStreaming) 1 else 0)
    LaunchedEffect(itemCount, state.streamingText.length) {
        if (itemCount > 0) {
            runCatching { listState.scrollToItem(itemCount - 1) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KellyTopBar(
            title = "凯小丽",
            subtitle = "财经专家智能体 · DeepSeek",
            actions = {
                IconButton(
                    onClick = viewModel::clearChat,
                    enabled = !state.isStreaming
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "清空对话")
                }
            }
        )

        if (!state.hasApiKey) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                MessageBanner(
                    text = "还没有配置 DeepSeek API Key，配置后即可开始对话。",
                    isError = false
                )
                TextButton(onClick = onOpenSettings) {
                    Text("去设置")
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.messages.isEmpty() && !state.isStreaming) {
                item { WelcomeCard() }
            }
            items(state.messages, key = { it.id }) { message ->
                MessageRow(message)
            }
            if (state.isStreaming) {
                item { StreamingRow(state.streamingText) }
            }
        }

        state.error?.let { error ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                MessageBanner(text = error, onDismiss = viewModel::dismissError)
            }
        }

        if (state.messages.isEmpty() && !state.isStreaming) {
            QuickPrompts(onPick = viewModel::send)
            Spacer(Modifier.height(6.dp))
        }

        ChatInput(
            value = input,
            enabled = !state.isStreaming,
            onChange = { input = it },
            onSend = {
                val text = input
                input = ""
                viewModel.send(text)
            }
        )
    }
}

@Composable
private fun WelcomeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KellyAvatar(42.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "你好，我是凯小丽",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "你的个人财经分析师",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "我可以结合今天的财经日报，帮你梳理市场主线、解读政策与行业影响。试试下面的问题，或者直接输入你的疑问。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessageEntity) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            KellyAvatar(32.dp)
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            tonalElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    MarkdownText(message.content)
                }
            }
        }
    }
}

@Composable
private fun StreamingRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        KellyAvatar(32.dp)
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                if (text.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "凯小丽正在思考…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    MarkdownText(text + "\n\n▍")
                }
            }
        }
    }
}

@Composable
private fun QuickPrompts(onPick: (String) -> Unit) {
    val prompts = listOf(
        "用三句话总结今天最重要的财经信息",
        "今天有哪些利好和利空？",
        "对 A 股影响最大的三件事是什么？",
        "帮我梳理今天的投资主线"
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(prompts) { prompt ->
            AssistChip(
                onClick = { onPick(prompt) },
                label = { Text(prompt) }
            )
        }
    }
}

@Composable
private fun ChatInput(
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("问问凯小丽…") },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                enabled = enabled
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
