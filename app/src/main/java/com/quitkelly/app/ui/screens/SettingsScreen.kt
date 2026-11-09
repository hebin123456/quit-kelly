package com.quitkelly.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quitkelly.app.BuildConfig
import com.quitkelly.app.KellyApp
import com.quitkelly.app.data.news.NewsSourceCatalog
import com.quitkelly.app.ui.components.KellyTopBar
import com.quitkelly.app.ui.vm.SettingsUiState
import com.quitkelly.app.ui.vm.SettingsViewModel

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as? KellyApp ?: return
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(app.container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var keyInput by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var keyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            KellyTopBar(
                title = "设置",
                subtitle = "配置凯小丽的 API 与每日推送"
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ApiSection(
                        state = state,
                        keyInput = keyInput,
                        keyVisible = keyVisible,
                        onKeyChange = { keyInput = it },
                        onToggleVisible = { keyVisible = !keyVisible },
                        onSave = { viewModel.saveApiKey(keyInput) },
                        onTest = viewModel::testConnection
                    )
                }
                item {
                    ScheduleSection(
                        state = state,
                        onHourChange = viewModel::setHour,
                        onMinuteChange = viewModel::setMinute,
                        onNotificationsChange = viewModel::setNotifications
                    )
                }
                item {
                    SourcesSection(
                        state = state,
                        onSourceToggle = viewModel::setSourceEnabled,
                        onRssChange = viewModel::updateRssText,
                        onRssSave = viewModel::saveRss
                    )
                }
                item {
                    SettingsCard(title = "数据") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("新闻缓存", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "最多保留 7 天，清空后下次抓取重新累积。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = viewModel::clearNews) {
                                Text("清空")
                            }
                        }
                    }
                }
                item {
                    SettingsCard(title = "关于") {
                        Text(
                            text = "凯小丽 v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "AI 财经日报与专家对话由 DeepSeek 提供模型能力。所有摘要均由 AI 生成，仅供参考，不构成投资建议。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun ApiSection(
    state: SettingsUiState,
    keyInput: String,
    keyVisible: Boolean,
    onKeyChange: (String) -> Unit,
    onToggleVisible: () -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit
) {
    SettingsCard(title = "DeepSeek 接入") {
        OutlinedTextField(
            value = keyInput,
            onValueChange = onKeyChange,
            label = { Text("DeepSeek API Key") },
            placeholder = { Text("sk-...") },
            singleLine = true,
            visualTransformation = if (keyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                TextButton(onClick = onToggleVisible) {
                    Text(if (keyVisible) "隐藏" else "显示")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onSave) {
                Text("保存 Key")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onTest, enabled = !state.testing) {
                if (state.testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text("测试连接")
            }
        }
        state.testResult?.let { result ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = result,
                style = MaterialTheme.typography.bodySmall,
                color = if (result.startsWith("连接成功")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Key 仅保存在本机（Android Keystore 加密），不会上传到其他服务器。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "模型",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "deepseek-flash",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "固定使用该模型",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScheduleSection(
    state: SettingsUiState,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onNotificationsChange: (Boolean) -> Unit
) {
    SettingsCard(title = "每日推送") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "推送时间",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            NumberPicker(
                value = state.settings.dailyHour,
                options = (0..23).toList(),
                onSelect = onHourChange
            )
            Text(
                text = ":",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            NumberPicker(
                value = state.settings.dailyMinute,
                options = listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55),
                onSelect = onMinuteChange
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("完成时发送通知", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Android 可能延迟后台任务，打开 App 会自动补刷。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.settings.notificationsEnabled,
                onCheckedChange = onNotificationsChange
            )
        }
    }
}

@Composable
private fun SourcesSection(
    state: SettingsUiState,
    onSourceToggle: (String, Boolean) -> Unit,
    onRssChange: (String) -> Unit,
    onRssSave: () -> Unit
) {
    SettingsCard(title = "新闻源") {
        NewsSourceCatalog.all.forEach { source ->
            SourceSwitch(
                label = source.name,
                description = source.description,
                id = source.id,
                checked = source.id in state.settings.enabledSources,
                onToggle = onSourceToggle
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.rssText,
            onValueChange = onRssChange,
            label = { Text("自定义 RSS（每行一个地址）") },
            placeholder = { Text("https://example.com/finance.xml") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onRssSave) {
            Text("保存订阅")
        }
    }
}

@Composable
private fun SourceSwitch(
    label: String,
    description: String,
    id: String,
    checked: Boolean,
    onToggle: (String, Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(id, it) }
        )
    }
}

@Composable
private fun NumberPicker(
    value: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("%02d".format(value), fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text("%02d".format(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
