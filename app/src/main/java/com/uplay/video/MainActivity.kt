package com.uplay.video

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val Ink = Color(0xFF0B1018)
private val Panel = Color(0xFF151D29)
private val Blue = Color(0xFF087BFF)
private val Green = Color(0xFF35F28B)
private val Muted = Color(0xFF9BA9BC)

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Blue, secondary = Green, background = Ink, surface = Panel,
                onBackground = Color.White, onSurface = Color.White
            )) {
                UPlayHome(
                    player = player,
                    onLocalVideo = { uri -> play(MediaItem.fromUri(uri)) },
                    onPlayUrl = { url -> play(MediaItem.fromUri(url)) }
                )
            }
        }
    }

    private fun play(item: MediaItem) {
        player?.setMediaItem(item)
        player?.prepare()
        player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

@Composable
private fun UPlayHome(
    player: ExoPlayer?,
    onLocalVideo: (Uri) -> Unit,
    onPlayUrl: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Choose a video or paste a direct video URL to begin.") }
    var selected by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { onLocalVideo(uri) }
                .onSuccess { selected = true; message = "Loading selected video…" }
                .onFailure { message = "Couldn't open this video. Try another file." }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.uplay_launcher),
                    contentDescription = "UPlay logo",
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("UPlay", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("YOUR VIDEO SPACE", fontSize = 10.sp, letterSpacing = 2.sp, color = Green)
                }
                Surface(color = Color(0xFF12372B), shape = RoundedCornerShape(50)) {
                    Text("PLAYER", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Every video. One place.", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("A clean, focused player for your library.", fontSize = 14.sp, color = Muted)
            }

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 160.dp),
                color = Color(0xFF05080D), shape = RoundedCornerShape(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selected && player != null) {
                        AndroidView(
                            factory = { context -> PlayerView(context).apply {
                                this.player = player
                                useController = true
                            } },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.size(76.dp)) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Blue,
                                    modifier = Modifier.padding(20.dp).fillMaxSize())
                            }
                            Text("Ready when you are", color = Color.White,
                                fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Text("Your next watch starts here", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { picker.launch(arrayOf("video/*")) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Open a video", fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Paste direct video URL", color = Muted) },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = Green) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Green, unfocusedBorderColor = Color(0xFF334154),
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White
                    )
                )
                OutlinedButton(
                    onClick = {
                        val candidate = url.trim()
                        if (candidate.startsWith("https://", ignoreCase = true)) {
                            onPlayUrl(candidate)
                            selected = true
                            message = "Loading video link…"
                        } else {
                            message = "Please enter a direct HTTPS video URL."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Green)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play link", fontWeight = FontWeight.Bold)
                }
            }
            Text(message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
        }
    }
}
