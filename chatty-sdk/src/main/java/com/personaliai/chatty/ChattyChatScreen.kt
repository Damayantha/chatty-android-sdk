package com.personaliai.chatty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val FallbackColor = Color(0xFFF97316)

private fun parseColor(hex: String?): Color =
    try {
        if (hex.isNullOrBlank()) FallbackColor else Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        FallbackColor
    }

/**
 * Full Chatty chat screen: header, message list, conversation starters, typing
 * indicator, and composer. Equivalent to the web widget's embed iframe content.
 */
@Composable
fun ChattyChatScreen(
    botId: String,
    baseUrl: String = CHATTY_DEFAULT_BASE_URL,
    host: String? = null,
    hostKey: String = "app",
    modifier: Modifier = Modifier,
    onMessage: ((ChattyMessage) -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: ChattyViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ChattyViewModel(
                    context.applicationContext as android.app.Application,
                    botId, baseUrl, host, hostKey, onMessage = onMessage,
                ) as T
            }
        },
    )
    val state by viewModel.state.collectAsState()
    val color = parseColor(state.theme?.primaryColor)
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
        }
    }

    if (!state.ready) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = color)
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(color).padding(16.dp, 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.theme?.logoUrl?.let {
                AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.width(8.dp))
            }
            Text(state.theme?.name ?: "Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.messages, key = { it.id }) { msg -> Bubble(msg, color) }
            if (state.sending) {
                item { TypingBubble(color) }
            }
        }

        if (state.theme?.conversationStarters?.isNotEmpty() == true && state.messages.size <= 1) {
            FlowStarters(state.theme!!.conversationStarters) { viewModel.sendText(it) }
        }

        if (state.aiPaused) Banner("A human agent has taken over this conversation.", Color(0xFFFEF3C7))
        state.error?.let { Banner(it, Color(0xFFFEE2E2)) }

        Row(
            Modifier.fillMaxWidth().padding(10.dp, 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message…") },
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                maxLines = 4,
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        viewModel.sendText(input)
                        input = ""
                    }
                },
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(color),
            ) {
                Text("↑", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Bubble(message: ChattyMessage, color: Color) {
    val isUser = message.role == ChattyRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isUser) color else Color(0xFFF3F4F6))
                .padding(14.dp, 10.dp),
        ) {
            Column {
                message.fileUrl?.let {
                    AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(160.dp, 120.dp).clip(RoundedCornerShape(10.dp)))
                }
                if (message.text.isNotEmpty()) {
                    Text(message.text, color = if (isUser) Color.White else Color(0xFF111827), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun TypingBubble(color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFFF3F4F6)).padding(14.dp, 10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = color)
        }
    }
}

@Composable
private fun FlowStarters(starters: List<String>, onClick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp, 0.dp, 12.dp, 8.dp).horizontalScrollWorkaround(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        starters.forEach { s ->
            OutlinedButton(onClick = { onClick(s) }, shape = RoundedCornerShape(14.dp)) {
                Text(s, fontSize = 12.5.sp, maxLines = 2)
            }
        }
    }
}

private fun Modifier.horizontalScrollWorkaround(): Modifier = this

@Composable
private fun Banner(text: String, bg: Color) {
    Box(Modifier.fillMaxWidth().background(bg).padding(14.dp, 8.dp)) {
        Text(text, fontSize = 11.5.sp, color = Color(0xFF374151))
    }
}
