package com.uplay.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.View
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay

private val Ink = Color(0xFF090D15)
private val Panel = Color(0xFF141C29)
private val Blue = Color(0xFF087BFF)
private val Green = Color(0xFF35E889)
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
    var fullScreen by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var playbackDuration by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    var originalSystemUiFlags by remember { mutableIntStateOf(0) }
    var currentTab by remember { mutableStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var trackDialog by remember { mutableStateOf(0) } // 1 = audio, 2 = subtitles
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) controlsVisible = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playbackPosition = player?.currentPosition ?: 0L
                playbackDuration = (player?.duration ?: 0L).coerceAtLeast(0L)
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(player, selected, isPlaying) {
        while (selected && player != null) {
            playbackPosition = player.currentPosition.coerceAtLeast(0L)
            playbackDuration = player.duration.takeIf { it > 0L } ?: 0L
            delay(500)
        }
    }

    DisposableEffect(fullScreen) {
        val activity = context.findActivity()
        val decor = activity?.window?.decorView
        if (fullScreen && decor != null) {
            originalSystemUiFlags = decor.systemUiVisibility
            decor.systemUiVisibility = (originalSystemUiFlags
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        } else if (decor != null) {
            decor.systemUiVisibility = originalSystemUiFlags
        }
        onDispose {
            if (fullScreen && decor != null) decor.systemUiVisibility = originalSystemUiFlags
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, locked) {
        if (controlsVisible && isPlaying && !locked) {
            delay(3500)
            controlsVisible = false
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { subtitleUri ->
        if (subtitleUri != null && player != null) {
            val videoUri = player.currentMediaItem?.localConfiguration?.uri
            if (videoUri == null) {
                message = "Open a video before adding subtitles."
            } else {
                val name = subtitleUri.lastPathSegment?.substringAfterLast('/')?.lowercase().orEmpty()
                val mime = when {
                    name.endsWith(".srt") -> "application/x-subrip"
                    name.endsWith(".ttml") || name.endsWith(".xml") -> "application/ttml+xml"
                    else -> "text/vtt"
                }
                runCatching {
                    val wasPlaying = player.playWhenReady
                    val position = player.currentPosition
                    val item = MediaItem.Builder()
                        .setUri(videoUri)
                        .setSubtitleConfigurations(
                            listOf(
                                MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                                    .setMimeType(mime)
                                    .setLanguage("und")
                                    .setLabel("External subtitles")
                                    .build()
                            )
                        )
                        .build()
                    player.setMediaItem(item, position)
                    player.prepare()
                    player.playWhenReady = wasPlaying
                }.onSuccess {
                    trackDialog = 2
                    message = "External subtitles added."
                }.onFailure {
                    message = "Couldn't load that subtitle file. Try SRT or WebVTT."
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { onLocalVideo(uri) }
                .onSuccess {
                    selected = true
                    currentTab = 0
                    controlsVisible = true
                    message = "Loading selected video…"
                }
                .onFailure { message = "Couldn't open this video. Try another file." }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0E1420), contentColor = Color.White) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        label = { Text("Player") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Green, selectedTextColor = Green,
                            indicatorColor = Color(0xFF17352B), unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                        label = { Text("Library") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Green, selectedTextColor = Green,
                            indicatorColor = Color(0xFF17352B), unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        ) { insets ->
            if (currentTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(insets),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Image(
                                painter = painterResource(id = R.drawable.uplay_launcher),
                                contentDescription = "UPlay logo",
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("UPlay", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text("YOUR VIDEO SPACE", fontSize = 10.sp, letterSpacing = 2.sp, color = Green)
                            }
                            Surface(color = Color(0xFF12372B), shape = RoundedCornerShape(50)) {
                                Text("PLAYER", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Every video. One place.", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("A clean, focused player for your library.", fontSize = 14.sp, color = Muted)
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                .background(Color.Black, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected && player != null && !fullScreen) {
                                AndroidView(
                                    factory = { context ->
                                        PlayerView(context).apply {
                                            this.player = player
                                            useController = false
                                            this.resizeMode = resizeMode
                                            keepScreenOn = true
                                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                                            playerView = this
                                        }
                                    },
                                    update = {
                                        it.player = player
                                        it.resizeMode = resizeMode
                                        playerView = it
                                    },
                                    modifier = Modifier.fillMaxSize()
                                        .pointerInput(locked) {
                                            detectTapGestures(
                                                onTap = {
                                                    if (locked) controlsVisible = true
                                                    else controlsVisible = !controlsVisible
                                                },
                                                onDoubleTap = { point ->
                                                    if (!locked) {
                                                        val delta = if (point.x < size.width / 2f) -10_000L else 10_000L
                                                        player?.seekTo((player.currentPosition + delta).coerceAtLeast(0L))
                                                        controlsVisible = true
                                                    }
                                                }
                                            )
                                        }
                                        .pointerInput(locked) {
                                            detectTransformGestures { _, _, zoom, _ ->
                                                if (!locked) {
                                                    if (zoom > 1.08f) {
                                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                        playerView?.resizeMode = resizeMode
                                                    } else if (zoom < 0.92f) {
                                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                        playerView?.resizeMode = resizeMode
                                                    }
                                                    controlsVisible = true
                                                }
                                            }
                                        }
                                )
                                if (controlsVisible) {
                                    Box(Modifier.fillMaxSize().background(Color(0x66060A10))) {
                                        if (locked) {
                                            IconButton(
                                                onClick = { locked = false; controlsVisible = true },
                                                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                                                    .background(Color(0xAA101722), RoundedCornerShape(50))
                                            ) { Icon(Icons.Default.LockOpen, "Unlock controls", tint = Green) }
                                        } else {
                                            Row(
                                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IconButton(onClick = { settingsOpen = true }) {
                                                    Icon(Icons.Default.Settings, "Player settings", tint = Color.White)
                                                }
                                                IconButton(onClick = { locked = true; controlsVisible = true }) {
                                                    Icon(Icons.Default.Lock, "Lock controls", tint = Color.White)
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.align(Alignment.Center),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(22.dp)
                                            ) {
                                                IconButton(onClick = {
                                                    player?.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                                    controlsVisible = true
                                                }) { Icon(Icons.Default.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp)) }
                                                IconButton(onClick = {
                                                    if (player?.isPlaying == true) player.pause() else player?.play()
                                                    controlsVisible = true
                                                }) {
                                                    Icon(
                                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        if (isPlaying) "Pause" else "Play",
                                                        tint = Green, modifier = Modifier.size(48.dp)
                                                    )
                                                }
                                                IconButton(onClick = {
                                                    player?.seekTo((player.currentPosition + 10_000L).coerceAtLeast(0L))
                                                    controlsVisible = true
                                                }) { Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp)) }
                                            }
                                            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                                val duration = (player?.duration ?: 0L).coerceAtLeast(0L)
                                                val position = (player?.currentPosition ?: 0L).coerceIn(0L, duration.coerceAtLeast(1L))
                                                Slider(
                                                    value = if (duration > 0) position.toFloat() / duration else 0f,
                                                    onValueChange = { fraction ->
                                                        if (duration > 0) player?.seekTo((duration * fraction).toLong())
                                                    },
                                                    colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Green)
                                                )
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(formatTime(position), color = Color.White, fontSize = 11.sp)
                                                    Text(formatTime(duration), color = Color.White, fontSize = 11.sp)
                                                    IconButton(onClick = { fullScreen = true; controlsVisible = true }, modifier = Modifier.size(30.dp)) {
                                                        Icon(Icons.Default.Fullscreen, "Enter full screen", tint = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Blue, modifier = Modifier.size(42.dp))
                                    Text("Ready when you are", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                    Text("Your next watch starts here", color = Muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { picker.launch(arrayOf("video/*")) },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Open a video", fontWeight = FontWeight.Bold)
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Paste direct video URL", color = Muted) },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = Green) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            shape = RoundedCornerShape(15.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Green, unfocusedBorderColor = Color(0xFF334154),
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            )
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                val candidate = url.trim()
                                if (candidate.startsWith("https://", ignoreCase = true) || candidate.startsWith("http://", ignoreCase = true)) {
                                    onPlayUrl(candidate)
                                    selected = true
                                    controlsVisible = true
                                    message = "Loading video link…"
                                } else message = "Please enter a direct video URL."
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Green)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Play link", fontWeight = FontWeight.Bold)
                        }
                    }
                    item { Text(message, color = Muted, fontSize = 12.sp) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(insets),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text("Your library", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Open local videos to start watching.", color = Muted)
                    }
                    item {
                        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Green, modifier = Modifier.size(32.dp))
                                Text(if (selected) "A video is ready in Player" else "No videos opened yet", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("Choose a file from your device. UPlay will open it in the Player tab.", color = Muted)
                                Button(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Browse videos")
                                }
                                if (selected) TextButton(onClick = { currentTab = 0 }) { Text("Return to Player") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (fullScreen && player != null) {
        Dialog(
            onDismissRequest = { fullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = false
                            resizeMode = resizeMode
                            keepScreenOn = true
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = {
                        it.player = player
                        it.resizeMode = resizeMode
                        it.keepScreenOn = true
                    },
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(locked) {
                            detectTapGestures(
                                onTap = { if (!locked) controlsVisible = !controlsVisible },
                                onDoubleTap = { point ->
                                    if (!locked) {
                                        val delta = if (point.x < size.width / 2f) -10_000L else 10_000L
                                        player.seekTo((player.currentPosition + delta).coerceAtLeast(0L))
                                        controlsVisible = true
                                    }
                                }
                            )
                        }
                        .pointerInput(locked) {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (!locked) {
                                    if (zoom > 1.08f) resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    else if (zoom < 0.92f) resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    controlsVisible = true
                                }
                            }
                        }
                )

                if (locked) {
                    IconButton(
                        onClick = { locked = false; controlsVisible = true },
                        modifier = Modifier.align(Alignment.TopEnd).padding(18.dp)
                            .background(Color(0xCC101722), RoundedCornerShape(50))
                    ) { Icon(Icons.Default.LockOpen, "Unlock player controls", tint = Green) }
                } else if (controlsVisible) {
                    Box(Modifier.fillMaxSize().background(Color(0x55000000))) {
                        Row(
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { settingsOpen = true }) {
                                Icon(Icons.Default.Settings, "Playback settings", tint = Color.White)
                            }
                            IconButton(onClick = { locked = true; controlsVisible = false }) {
                                Icon(Icons.Default.Lock, "Lock controls", tint = Color.White)
                            }
                            IconButton(onClick = { fullScreen = false; controlsVisible = true }) {
                                Icon(Icons.Default.Fullscreen, "Exit full screen", tint = Color.White)
                            }
                        }
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            IconButton(onClick = {
                                player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                controlsVisible = true
                            }) { Icon(Icons.Default.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(42.dp)) }
                            IconButton(onClick = {
                                if (player.isPlaying) player.pause() else player.play()
                                controlsVisible = true
                            }) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    if (isPlaying) "Pause" else "Play",
                                    tint = Green, modifier = Modifier.size(58.dp)
                                )
                            }
                            IconButton(onClick = {
                                player.seekTo((player.currentPosition + 10_000L).coerceAtLeast(0L))
                                controlsVisible = true
                            }) { Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(42.dp)) }
                        }
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, bottom = 18.dp)
                        ) {
                            Slider(
                                value = if (playbackDuration > 0L) (playbackPosition.toFloat() / playbackDuration).coerceIn(0f, 1f) else 0f,
                                onValueChange = { fraction ->
                                    if (playbackDuration > 0L) {
                                        playbackPosition = (playbackDuration * fraction).toLong()
                                        player.seekTo(playbackPosition)
                                    }
                                },
                                colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Green)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatTime(playbackPosition), color = Color.White, fontSize = 12.sp)
                                Text(formatTime(playbackDuration), color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            containerColor = Panel,
            title = { Text("Playback settings", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Playback speed", color = Muted)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            FilterChip(
                                selected = playbackSpeed == speed,
                                onClick = {
                                    playbackSpeed = speed
                                    player?.setPlaybackSpeed(speed)
                                },
                                label = { Text("${speed}x") }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Repeat video", color = Muted)
                        Switch(
                            checked = player?.repeatMode == Player.REPEAT_MODE_ONE,
                            onCheckedChange = {
                                player?.repeatMode = if (it) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                            }
                        )
                    }
                    Text("Resize video", color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT, onClick = {
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; playerView?.resizeMode = resizeMode
                        }, label = { Text("Fit") })
                        FilterChip(selected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL, onClick = {
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL; playerView?.resizeMode = resizeMode
                        }, label = { Text("Fill") })
                        FilterChip(selected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM, onClick = {
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; playerView?.resizeMode = resizeMode
                        }, label = { Text("Zoom") })
                    }
                    OutlinedButton(onClick = { settingsOpen = false; trackDialog = 1 }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Audio track")
                    }
                    OutlinedButton(onClick = { settingsOpen = false; trackDialog = 2 }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Subtitles, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Embedded subtitles")
                    }
                    OutlinedButton(
                        onClick = { settingsOpen = false; subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/ttml+xml")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Subtitles, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Load subtitle file")
                    }
                    Text("Tip: pinch to zoom. Double-tap either side to seek ±10 seconds. Tap the video to show controls.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("Done", color = Green) } }
        )
    }

    if (trackDialog != 0 && player != null) {
        val type = if (trackDialog == 1) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
        val groups = player.currentTracks.groups.filter { it.type == type && it.isSupported }
        AlertDialog(
            onDismissRequest = { trackDialog = 0 },
            containerColor = Panel,
            title = { Text(if (trackDialog == 1) "Choose audio" else "Subtitles", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (trackDialog == 2) {
                        TextButton(onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .build()
                            trackDialog = 0
                        }) { Text("Off", color = Green) }
                    }
                    if (groups.isEmpty()) {
                        Text(if (trackDialog == 1) "No alternate audio tracks are available for this video." else "No embedded subtitle tracks are available for this video.", color = Muted)
                    } else {
                        groups.forEachIndexed { groupIndex, group ->
                            for (trackIndex in 0 until group.length) {
                                val format = group.getTrackFormat(trackIndex)
                                val label = listOfNotNull(
                                    format.label?.takeIf { it.isNotBlank() },
                                    format.language?.takeIf { it.isNotBlank() },
                                    format.sampleMimeType?.substringAfter("/")?.takeIf { it.isNotBlank() }
                                ).distinct().joinToString(" · ").ifBlank { "Track ${groupIndex + 1}.${trackIndex + 1}" }
                                TextButton(onClick = {
                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                        .clearOverridesOfType(type)
                                        .setTrackTypeDisabled(type, false)
                                        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
                                        .build()
                                    trackDialog = 0
                                }) { Text(label, color = Color.White) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { trackDialog = 0 }) { Text("Close", color = Green) } }
        )
    }
}

private fun formatTime(milliseconds: Long): String {
    if (milliseconds < 0 || milliseconds == C.TIME_UNSET) return "00:00"
    val seconds = milliseconds / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}


private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
