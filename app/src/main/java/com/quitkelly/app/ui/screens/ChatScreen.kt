package com.quitkelly.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quitkelly.app.KellyApp
import com.quitkelly.app.R
import com.quitkelly.app.data.AgentEntity
import com.quitkelly.app.data.ChatAttachment
import com.quitkelly.app.data.ChatMessageEntity
import com.quitkelly.app.data.ChatSessionEntity
import com.quitkelly.app.data.PendingAttachment
import com.quitkelly.app.ui.components.AgentEditorDialog
import com.quitkelly.app.ui.components.AgentPickerSheet
import com.quitkelly.app.ui.components.DeleteAgentDialog
import com.quitkelly.app.ui.components.KellyAvatar
import com.quitkelly.app.ui.components.KellyTopBar
import com.quitkelly.app.ui.components.MarkdownText
import com.quitkelly.app.ui.components.MessageBanner
import com.quitkelly.app.ui.vm.ChatViewModel
import com.quitkelly.app.util.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? KellyApp ?: return
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(app.container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val speechState by app.container.speechPlayer.state.collectAsStateWithLifecycle()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.addFile(uri)
    }

    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var showSessions by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showAgents by remember { mutableStateOf(false) }
    var showAgentEditor by remember { mutableStateOf(false) }
    var editingAgent by remember { mutableStateOf<AgentEntity?>(null) }
    var deleteAgentTarget by remember { mutableStateOf<AgentEntity?>(null) }
    val currentTitle = state.sessions.firstOrNull { it.id == state.currentSessionId }?.title
    val currentAgent = state.agents.firstOrNull { it.id == state.currentAgentId }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshKeyState()
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshKeyState()
    }

    val itemCount = state.messages.size + (if (state.isStreaming) 1 else 0)
    LaunchedEffect(itemCount, state.streamingText.length) {
        if (itemCount > 0) {
            runCatching { listState.scrollToItem(itemCount - 1) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KellyTopBar(
            title = currentAgent?.name ?: "凯小丽",
            subtitle = currentAgent?.description?.ifBlank { "自定义智能体" }
                ?: currentTitle
                ?: "财经专家智能体 · 支持图片与文件",
            actions = {
                if (speechState.speaking) {
                    IconButton(onClick = { app.container.speechPlayer.stop() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_speech_stop),
                            contentDescription = "停止朗读",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { showAgents = true },
                    enabled = !state.isStreaming
                ) {
                    Icon(Icons.Filled.Person, contentDescription = "切换智能体")
                }
                IconButton(
                    onClick = { showSessions = true },
                    enabled = !state.isStreaming
                ) {
                    Icon(Icons.Filled.List, contentDescription = "对话记录")
                }
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
                item { WelcomeCard(currentAgent) }
            }
            items(state.messages, key = { it.id }) { message ->
                val attachments = remember(message.attachmentsJson) {
                    app.container.attachmentStore.fromJson(message.attachmentsJson)
                }
                MessageRow(
                    message = message,
                    attachments = attachments,
                    isSpeaking = speechState.speaking &&
                        speechState.activeKey == "chat:${message.id}",
                    isPaused = speechState.paused &&
                        speechState.activeKey == "chat:${message.id}",
                    onStart = {
                        app.container.speechPlayer.toggle("chat:${message.id}", message.content)
                    },
                    onPause = { app.container.speechPlayer.pause() },
                    onResume = { app.container.speechPlayer.resume() },
                    onStop = { app.container.speechPlayer.stop() }
                )
            }
            if (state.isStreaming) {
                item { StreamingRow(state.streamingText, currentAgent?.name ?: "凯小丽") }
            }
        }

        state.error?.let { error ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                MessageBanner(text = error, onDismiss = viewModel::dismissError)
            }
        }

        speechState.error?.let { error ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                MessageBanner(
                    text = error,
                    onDismiss = { app.container.speechPlayer.clearError() }
                )
            }
        }

        if (!state.isStreaming) {
            QuickPrompts(onPick = viewModel::send)
            Spacer(Modifier.height(6.dp))
        }

        ChatInput(
            value = input,
            enabled = !state.isStreaming,
            pending = state.pendingAttachments,
            importing = state.importingAttachment,
            canSend = input.isNotBlank() || state.pendingAttachments.isNotEmpty(),
            onChange = { input = it },
            onSend = {
                val text = input
                input = ""
                viewModel.send(text)
            },
            onPickImage = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickFile = { filePicker.launch(arrayOf("*/*")) },
            onRemoveAttachment = viewModel::removeAttachment,
            agentName = currentAgent?.name ?: "凯小丽"
        )
    }

    if (showAgents) {
        AgentPickerSheet(
            agents = state.agents,
            currentAgentId = state.currentAgentId,
            onSelect = { agentId ->
                viewModel.selectAgent(agentId)
                showAgents = false
            },
            onCreate = {
                showAgents = false
                editingAgent = null
                showAgentEditor = true
            },
            onEdit = { agent ->
                showAgents = false
                editingAgent = agent
                showAgentEditor = true
            },
            onDelete = { agent ->
                showAgents = false
                deleteAgentTarget = agent
            },
            onDismiss = { showAgents = false }
        )
    }

    if (showAgentEditor) {
        AgentEditorDialog(
            agent = editingAgent,
            onSave = { emoji, name, description, prompt ->
                viewModel.saveAgent(
                    id = editingAgent?.id,
                    emoji = emoji,
                    name = name,
                    description = description,
                    systemPrompt = prompt
                )
                showAgentEditor = false
            },
            onDismiss = { showAgentEditor = false }
        )
    }

    deleteAgentTarget?.let { agent ->
        DeleteAgentDialog(
            agent = agent,
            onConfirm = {
                viewModel.deleteAgent(agent)
                deleteAgentTarget = null
            },
            onDismiss = { deleteAgentTarget = null }
        )
    }

    if (showSessions) {
        ModalBottomSheet(onDismissRequest = { showSessions = false }) {
            SessionSheet(
                sessions = state.sessions,
                currentSessionId = state.currentSessionId,
                onSelect = { id ->
                    viewModel.selectSession(id)
                    showSessions = false
                },
                onRename = { session ->
                    showSessions = false
                    renameTarget = session
                    renameInput = session.title
                },
                onDelete = viewModel::deleteSession,
                onNew = {
                    viewModel.newSession()
                    showSessions = false
                }
            )
        }
    }

    val target = renameTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名对话") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("对话名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameSession(target.id, renameInput)
                        renameTarget = null
                    },
                    enabled = renameInput.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SessionSheet(
    sessions: List<ChatSessionEntity>,
    currentSessionId: String?,
    onSelect: (String) -> Unit,
    onRename: (ChatSessionEntity) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "对话记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onNew) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("新建对话")
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            items(sessions, key = { it.id }) { session ->
                val selected = session.id == currentSessionId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onSelect(session.id) }
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatTime(session.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onRename(session) }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "重命名会话",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onDelete(session.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除会话",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard(agent: AgentEntity?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = agent?.emoji ?: "📈",
                        fontSize = 21.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "你好，我是${agent?.name ?: "凯小丽"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = agent?.description?.ifBlank { "你的个人财经分析师" }
                            ?: "你的个人财经分析师",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "我可以结合今天的财经日报，帮你梳理市场主线、解读政策与行业影响。也可以直接发我截图、PDF 或数据文件，让我帮你读图表、提取要点、做分析。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageRow(
    message: ChatMessageEntity,
    attachments: List<ChatAttachment>,
    isSpeaking: Boolean,
    isPaused: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
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
            Column {
                SelectionContainer {
                    Column(Modifier.padding(12.dp)) {
                        if (message.content.isNotBlank()) {
                            if (isUser) {
                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                MarkdownText(message.content)
                            }
                        }
                        if (attachments.isNotEmpty()) {
                            if (message.content.isNotBlank()) Spacer(Modifier.height(8.dp))
                            attachments.forEachIndexed { index, attachment ->
                                if (index > 0) Spacer(Modifier.height(6.dp))
                                AttachmentItem(attachment = attachment, isUser = isUser)
                            }
                        }
                    }
                }
                if (!isUser && message.content.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (!isSpeaking) {
                            TextButton(
                                onClick = onStart,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "朗读",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        } else {
                            TextButton(
                                onClick = if (isPaused) onResume else onPause,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                if (isPaused) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_speech_pause),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isPaused) "继续" else "暂停",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            TextButton(
                                onClick = onStop,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_speech_stop),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "停止",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentItem(attachment: ChatAttachment, isUser: Boolean) {
    if (attachment.kind == "image") {
        AttachmentImage(
            path = attachment.path,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isUser) {
                        Color.White.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_file),
                contentDescription = null,
                tint = if (isUser) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (attachment.kind == "pdf") {
                        "PDF · ${attachment.pageCount} 页"
                    } else {
                        "文本文件"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) {
                        Color(0xCCFFFFFF)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun StreamingRow(text: String, agentName: String) {
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
                            text = "$agentName 正在思考…",
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
    Column {
        Text(
            text = "推荐提问",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
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
}

@Composable
private fun PendingAttachmentsRow(
    attachments: List<PendingAttachment>,
    importing: Boolean,
    onRemove: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(attachments, key = { it.id }) { attachment ->
            Box(modifier = Modifier.size(76.dp)) {
                if (attachment.kind == "image") {
                    AttachmentImage(
                        path = attachment.path,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_file),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = attachment.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = { onRemove(attachment.id) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "移除附件",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        if (importing) {
            item {
                Box(
                    modifier = Modifier.size(76.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInput(
    value: String,
    enabled: Boolean,
    pending: List<PendingAttachment>,
    importing: Boolean,
    canSend: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    agentName: String
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            if (pending.isNotEmpty() || importing) {
                Spacer(Modifier.height(8.dp))
                PendingAttachmentsRow(
                    attachments = pending,
                    importing = importing,
                    onRemove = onRemoveAttachment
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        enabled = enabled && !importing
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_attach),
                            contentDescription = "添加附件",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("发送图片") },
                            onClick = {
                                menuExpanded = false
                                onPickImage()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("发送文件（PDF / txt / csv 等）") },
                            onClick = {
                                menuExpanded = false
                                onPickFile()
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("问问$agentName，或添加图片/文件…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    enabled = enabled
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onSend,
                    enabled = enabled && canSend,
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
}

@Composable
private fun AttachmentImage(path: String, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as? KellyApp
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            app?.container?.attachmentStore?.loadThumbnail(path, 720)?.asImageBitmap()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_file),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
