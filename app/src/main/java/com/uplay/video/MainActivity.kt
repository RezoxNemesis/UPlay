package com.uplay.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.Intent
import android.provider.OpenableColumns
import android.net.Uri
import android.view.View
import android.view.LayoutInflater
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ContentPaste
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
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private val Ink = Color(0xFF090D15)
private val Panel = Color(0xFF141C29)
private val Blue = Color(0xFF087BFF)
private val Green = Color(0xFF35E889)
private val Muted = Color(0xFF9BA9BC)

private data class RecentVideo(val uri: String, val title: String, val positionMs: Long, val remote: Boolean)

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private val incomingSharedUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingSharedUrl.value = extractSharedUrl(intent)
        val preferences = getSharedPreferences("uplay_settings", Context.MODE_PRIVATE)
        player = ExoPlayer.Builder(this).build().apply {
            setPlaybackSpeed(preferences.getFloat("playback_speed", 1f).coerceIn(0.5f, 2f))
            repeatMode = if (preferences.getBoolean("repeat_video", false)) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
        setContent {
            val systemDark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (systemDark) darkColorScheme(
                primary = Blue, secondary = Green, background = Ink, surface = Panel,
                onBackground = Color.White, onSurface = Color.White
            ) else lightColorScheme(
                primary = Blue, secondary = Color(0xFF167DDB), background = Color(0xFFF7F9FD),
                surface = Color.White, onBackground = Color(0xFF101725), onSurface = Color(0xFF101725)
            )) {
                UPlayHome(
                    player = player,
                    incomingSharedUrl = incomingSharedUrl.value,
                    onSharedUrlConsumed = { incomingSharedUrl.value = null },
                    onLocalVideo = { uri -> play(MediaItem.fromUri(uri)) },
                    onPlayUrl = { url -> play(MediaItem.fromUri(url)) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingSharedUrl.value = extractSharedUrl(intent)
    }

    private fun play(item: MediaItem) {
        player?.setMediaItem(item)
        player?.prepare()
        player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        // Keep active playback alive while Android applies a configuration change such as rotation.
        if (!isChangingConfigurations) player?.pause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("uplay_position_ms", player?.currentPosition?.coerceAtLeast(0L) ?: 0L)
        outState.putBoolean("uplay_was_playing", player?.playWhenReady == true)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UPlayHome(
    player: ExoPlayer?,
    incomingSharedUrl: String?,
    onSharedUrlConsumed: () -> Unit,
    onLocalVideo: (Uri) -> Unit,
    onPlayUrl: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Choose a video or paste a direct video URL to begin.") }
    var selected by remember { mutableStateOf(false) }
    var isMusicMode by remember { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var playbackDuration by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var originalSystemUiFlags by remember { mutableIntStateOf(0) }
    var currentTab by remember { mutableStateOf(0) } // Home, Player, Library
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(incomingSharedUrl) {
        val sharedUrl = incomingSharedUrl ?: return@LaunchedEffect
        url = sharedUrl
        currentTab = 1
        controlsVisible = true
        message = sharedLinkMessage(sharedUrl)
        onSharedUrlConsumed()
    }
    val recentVideos = remember(context) { mutableStateListOf<RecentVideo>().apply { addAll(loadRecentVideos(context)) } }
    val recentAudios = remember(context) { mutableStateListOf<RecentVideo>().apply { addAll(loadRecentVideos(context, "recent_audio")) } }
    var locked by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var radialOpen by remember { mutableStateOf(false) }
    var trackDialog by remember { mutableStateOf(0) } // 1 = audio, 2 = subtitles
    var isPlaying by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val playerPreferences = remember(context) {
        context.getSharedPreferences("uplay_settings", Context.MODE_PRIVATE)
    }
    var playbackSpeed by remember(context) {
        mutableFloatStateOf(playerPreferences.getFloat("playback_speed", 1f).coerceIn(0.5f, 2f))
    }
    var resizeMode by remember(context) {
        mutableIntStateOf(
            playerPreferences.getInt("resize_mode", AspectRatioFrameLayout.RESIZE_MODE_FIT)
                .takeIf {
                    it == AspectRatioFrameLayout.RESIZE_MODE_FIT ||
                        it == AspectRatioFrameLayout.RESIZE_MODE_FILL ||
                        it == AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } ?: AspectRatioFrameLayout.RESIZE_MODE_FIT
        )
    }
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

            override fun onPlayerError(error: PlaybackException) {
                playbackError = playbackErrorMessage(error)
                controlsVisible = true
                message = playbackError ?: "Playback failed."
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                playbackError = null
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(player, selected, isPlaying, recentVideos.size) {
        while (selected && player != null) {
            playbackPosition = player.currentPosition.coerceAtLeast(0L)
            playbackDuration = player.duration.takeIf { it > 0L } ?: 0L
            val activeUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
            if (activeUri != null && playbackPosition > 0L) {
                val index = recentVideos.indexOfFirst { it.uri == activeUri }
                if (index >= 0) {
                    recentVideos[index] = recentVideos[index].copy(positionMs = playbackPosition)
                    saveRecentVideos(context, recentVideos)
                }
            }
            delay(2500)
        }
    }

    val immersivePlayer = fullScreen || (landscape && selected)
    DisposableEffect(immersivePlayer) {
        val activity = context.findActivity()
        val decor = activity?.window?.decorView
        if (immersivePlayer && decor != null) {
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
            if (immersivePlayer && decor != null) decor.systemUiVisibility = originalSystemUiFlags
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

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                player?.setMediaItem(MediaItem.fromUri(uri))
                player?.prepare()
                player?.playWhenReady = true
                val title = queryDisplayName(context, uri)
                val previous = recentAudios.firstOrNull { it.uri == uri.toString() }
                val entry = RecentVideo(uri.toString(), title, previous?.positionMs ?: 0L, false)
                recentAudios.removeAll { it.uri == entry.uri }
                recentAudios.add(0, entry)
                while (recentAudios.size > 50) recentAudios.removeAt(recentAudios.lastIndex)
                saveRecentVideos(context, recentAudios, "recent_audio")
                player?.seekTo(entry.positionMs)
                selected = true
                isMusicMode = true
                currentTab = 1
                controlsVisible = true
                message = "Playing audio: $title"
            }.onFailure { message = "Couldn't open this audio file. Try another file." }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                onLocalVideo(uri)
                isMusicMode = false
                val title = queryDisplayName(context, uri)
                val previous = recentVideos.firstOrNull { it.uri == uri.toString() }
                val entry = RecentVideo(uri.toString(), title, previous?.positionMs ?: 0L, false)
                recentVideos.removeAll { it.uri == entry.uri }
                recentVideos.add(0, entry)
                while (recentVideos.size > 30) recentVideos.removeAt(recentVideos.lastIndex)
                saveRecentVideos(context, recentVideos)
                player?.seekTo(entry.positionMs)
            }.onSuccess {
                selected = true
                currentTab = 1
                controlsVisible = true
                message = "Loading selected video…"
            }.onFailure { message = "Couldn't open this video. Try another file." }
        }
    }

    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val systemDark = isSystemInDarkTheme()
    val appBackground = if (systemDark) Ink else Color(0xFFF7F9FD)
    Surface(modifier = Modifier.fillMaxSize(), color = appBackground) {
        Scaffold(containerColor = appBackground, contentWindowInsets = if (immersivePlayer) WindowInsets(0,0,0,0) else WindowInsets.safeDrawing, bottomBar = {
            if (!fullScreen && !(landscape && selected)) NavigationBar(containerColor = if (systemDark) Color(0xFF0B101B) else Color.White, tonalElevation = 0.dp) {
                NavigationBarItem(currentTab == 0, { currentTab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                NavigationBarItem(currentTab == 1, { currentTab = 1 }, { Icon(Icons.Default.PlayArrow, null) }, label = { Text("Player") })
                NavigationBarItem(currentTab == 2, { currentTab = 2 }, { Icon(Icons.Default.VideoLibrary, null) }, label = { Text("Library") })
            }
        }
        ) { insets ->
            if (currentTab == 1) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .padding(if (fullScreen) PaddingValues(0.dp) else insets)
                        .padding(horizontal = if (fullScreen || landscape) 0.dp else 14.dp,
                            vertical = if (fullScreen || landscape) 0.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (fullScreen || landscape) 0.dp else 10.dp)
                ) {
                    if (!landscape && !fullScreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("PLAYER", fontSize = 10.sp, letterSpacing = 1.8.sp, color = Green, fontWeight = FontWeight.Bold)
                                Text(if (selected) "Now playing" else "Ready to play", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Surface(color = Color(0xFF152B26), shape = RoundedCornerShape(50)) {
                                Text(if (!selected) "READY" else if (isPlaying) "PLAYING" else "PAUSED",
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                    color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .clip(if (fullScreen || landscape) RoundedCornerShape(0.dp) else RoundedCornerShape(22.dp))
                            .background(Color.Black)
                    ) {
                        if (selected && player != null && player.currentMediaItem != null) {
                            if (isMusicMode) {
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                        .background(Brush.verticalGradient(listOf(Color(0xFF0B1728), Color.Black, Color(0xFF111D30))))
                                        .pointerInput(locked) {
                                            detectTapGestures(onTap = { if (!locked) controlsVisible = !controlsVisible })
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                                        Box(
                                            modifier = Modifier.size(148.dp).clip(RoundedCornerShape(42.dp))
                                                .background(Brush.linearGradient(listOf(Color(0xFF152E4B), Color(0xFF0D1727)))),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFF8DD8FF), modifier = Modifier.size(72.dp))
                                        }
                                        Text(player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "Now playing" },
                                            color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom,
                                            modifier = Modifier.height(54.dp)) {
                                            repeat(19) { index -> AudioBar(index = index) }
                                        }
                                        Text("MUSIC PLAYER", color = Color(0xFF8DD8FF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp)
                                    }
                                }
                            } else AndroidView(
                                factory = { viewContext ->
                                    (LayoutInflater.from(viewContext).inflate(R.layout.uplay_player_texture_view, null, false) as PlayerView).apply {
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
                                    it.keepScreenOn = true
                                    playerView = it
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
                                                playerView?.resizeMode = resizeMode
                                                playerPreferences.edit().putInt("resize_mode", resizeMode).apply()
                                                controlsVisible = true
                                            }
                                        }
                                    }
                            )
                            Column(Modifier.fillMaxSize()) {
                            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xB8000000), Color.Transparent, Color(0xD9000000))))) {
                                    if (locked) {
                                        IconButton(
                                            onClick = { locked = false; controlsVisible = true },
                                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                                .background(Color(0x990B101B), RoundedCornerShape(50))
                                        ) { Icon(Icons.Default.LockOpen, "Unlock controls", tint = Green) }
                                    } else {
                                        Row(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(onClick = { settingsOpen = true }, shape = CircleShape,
                                                color = Color(0x77070B12), modifier = Modifier.size(42.dp)) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Settings, "Player settings", tint = Color.White, modifier = Modifier.size(21.dp))
                                                }
                                            }
                                            Surface(onClick = { locked = true; radialOpen = false; controlsVisible = true },
                                                shape = CircleShape, color = Color(0x77070B12), modifier = Modifier.size(42.dp)) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Lock, "Lock controls", tint = Color.White, modifier = Modifier.size(21.dp))
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.align(Alignment.Center),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                                        ) {
                                            IconButton(
                                                onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)); controlsVisible = true },
                                                modifier = Modifier.size(48.dp).background(Color(0x66070B12), CircleShape)
                                            ) { Icon(Icons.Default.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp)) }
                                            IconButton(
                                                onClick = { if (player.isPlaying) player.pause() else player.play(); controlsVisible = true },
                                                modifier = Modifier.size(66.dp).background(Green, CircleShape)
                                            ) {
                                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    if (isPlaying) "Pause" else "Play", tint = Ink, modifier = Modifier.size(42.dp))
                                            }
                                            IconButton(
                                                onClick = { player.seekTo((player.currentPosition + 10_000L).coerceAtLeast(0L)); controlsVisible = true },
                                                modifier = Modifier.size(48.dp).background(Color(0x66070B12), CircleShape)
                                            ) { Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp)) }
                                        }
                                        Box(
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 70.dp)
                                        ) {
                                            AnimatedVisibility(
                                                visible = radialOpen,
                                                enter = scaleIn(initialScale = 0.45f) + fadeIn(),
                                                exit = scaleOut(targetScale = 0.45f) + fadeOut()
                                            ) {
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(9.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(bottom = 48.dp).graphicsLayer {
                                                        rotationZ = if (radialOpen) 0f else -28f
                                                    }
                                                ) {
                                                    RadialControl(Icons.Default.Subtitles, "Subtitles") {
                                                        radialOpen = false; trackDialog = 2
                                                    }
                                                    RadialControl(Icons.Default.GraphicEq, "Audio") {
                                                        radialOpen = false; trackDialog = 1
                                                    }
                                                    RadialControl(Icons.Default.FitScreen, "Screen fit") {
                                                        radialOpen = false; settingsOpen = true
                                                    }
                                                }
                                            }
                                            Surface(
                                                onClick = { radialOpen = !radialOpen }, shape = CircleShape,
                                                color = if (radialOpen) Color(0xFF8DD8FF) else Color(0xDD101827),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xAA8DD8FF)),
                                                modifier = Modifier.size(46.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(if (radialOpen) Icons.Default.Settings else Icons.Default.FitScreen,
                                                        if (radialOpen) "Close quick controls" else "Quick controls",
                                                        tint = if (radialOpen) Ink else Color.White, modifier = Modifier.size(22.dp))
                                                }
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val duration = (player.duration).takeIf { it > 0L } ?: 0L
                                            val position = player.currentPosition.coerceIn(0L, duration.coerceAtLeast(1L))
                                            Slider(
                                                value = if (duration > 0L) position.toFloat() / duration else 0f,
                                                onValueChange = { fraction ->
                                                    if (duration > 0L) player.seekTo((duration * fraction).toLong())
                                                },
                                                modifier = Modifier.fillMaxWidth().height(24.dp),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Color.White,
                                                    activeTrackColor = Green,
                                                    inactiveTrackColor = Color(0x66FFFFFF),
                                                    activeTickColor = Color.Transparent,
                                                    inactiveTickColor = Color.Transparent
                                                )
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(formatTime(position), color = Color.White, fontSize = 11.sp)
                                                Text(formatTime(duration), color = Color.White, fontSize = 11.sp)
                                                IconButton(
                                                    onClick = { fullScreen = !fullScreen; controlsVisible = true },
                                                    modifier = Modifier.size(32.dp)
                                                ) { Icon(if (fullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, if (fullScreen) "Exit full screen" else "Enter full screen", tint = Color.White) }
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        } else {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(color = Color(0xFF111B29), shape = RoundedCornerShape(24.dp)) {
                                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Green,
                                        modifier = Modifier.padding(18.dp).size(42.dp))
                                }
                                Text("Ready when you are", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                                Text("Open a video or add a supported direct link.", color = Muted, fontSize = 13.sp)
                            }
                        }
                    }

                    if (!landscape && !fullScreen) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { picker.launch(arrayOf("video/*")) },
                                modifier = Modifier.weight(0.9f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Blue)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(7.dp))
                                Text("Open video", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    val candidate = url.trim()
                                    val parsed = runCatching { Uri.parse(candidate) }.getOrNull()
                                    val validUrl = parsed != null &&
                                        (parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) &&
                                        !parsed.host.isNullOrBlank()
                                    if (validUrl) {
                                        onPlayUrl(candidate)
                                        selected = true
                                        isMusicMode = false
                                        controlsVisible = true
                                        playbackError = null
                                        val entry = RecentVideo(candidate, candidate.substringAfterLast('/').ifBlank { candidate }, 0L, true)
                                        recentVideos.removeAll { it.uri == candidate }
                                        recentVideos.add(0, entry)
                                        while (recentVideos.size > 30) recentVideos.removeAt(recentVideos.lastIndex)
                                        saveRecentVideos(context, recentVideos)
                                        message = "Loading video link…"
                                    } else message = "Enter a valid HTTP(S) link first."
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Green)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text("Play link", fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (systemDark) Color(0xFF8DD8FF) else Color(0xFF167DDB))
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open music library")
                        }
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Paste a direct video URL or shared link", color = Muted) },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = Green) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Green, unfocusedBorderColor = Color(0xFF334154),
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            )
                        )
                        if (message.isNotBlank()) {
                            Text(
                                text = playbackError ?: message,
                                color = if (playbackError != null) Color(0xFFFFB4AB) else Muted,
                                fontSize = 12.sp,
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (playbackError != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = {
                                        playbackError = null
                                        runCatching {
                                            player?.let { active ->
                                                val retryPosition = active.currentPosition.coerceAtLeast(0L)
                                                active.prepare()
                                                active.seekTo(retryPosition)
                                                active.playWhenReady = true
                                            }
                                        }.onFailure {
                                            playbackError = "Retry couldn't start. Try reopening the video."
                                            message = playbackError ?: message
                                        }
                                    }) { Text("Retry") }
                                    TextButton(onClick = {
                                        playbackError = null
                                        selected = false
                                        player?.stop()
                                        message = "Playback stopped."
                                    }) { Text("Dismiss", color = Muted) }
                                }
                            }
                        }
                    }
                }
            } else if (currentTab == 2) {
                Column(modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Your library", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Pick up where you left off.", color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    if (recentVideos.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Muted, modifier = Modifier.size(42.dp))
                                Text("Your library is empty", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("Open a video to add it here.", color = Muted)
                                Button(onClick = { picker.launch(arrayOf("video/*")) }) { Text("Browse videos") }
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                            items(recentVideos, key = { it.uri }) { entry ->
                                Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(color = Color(0xFF1B2D43), shape = RoundedCornerShape(12.dp)) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Green,
                                                modifier = Modifier.padding(12.dp).size(26.dp))
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(entry.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                            Text(
                                                if (entry.positionMs > 0L) "Resume at ${formatTime(entry.positionMs)}" else if (entry.remote) "Direct media link" else "Local video",
                                                color = Muted, fontSize = 12.sp
                                            )
                                        }
                                        IconButton(onClick = {
                                            runCatching {
                                                if (entry.remote) onPlayUrl(entry.uri) else onLocalVideo(Uri.parse(entry.uri))
                                                player?.seekTo(entry.positionMs)
                                                selected = true
                                                currentTab = 1
                                                controlsVisible = true
                                                playbackError = null
                                                message = "Resuming ${entry.title}…"
                                            }.onFailure { message = "Couldn't reopen this video." }
                                        }) { Icon(Icons.Default.PlayArrow, "Play ${entry.title}", tint = Green, modifier = Modifier.size(30.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val dark = isSystemInDarkTheme()
                val foreground = if (dark) Color(0xFFF7FAFF) else Color(0xFF101725)
                val secondaryText = if (dark) Color(0xFF9BA9BC) else Color(0xFF68758A)
                val fieldSurface = if (dark) Color(0xFF111A29) else Color.White
                val fluid = rememberInfiniteTransition(label = "uplay-link-fluid")
                val wave by fluid.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 3600, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "link-wave"
                )
                Column(
                    modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(0.85f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (dark) Color(0xFF13243A) else Color(0xFFE6F5FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("U", fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.SansSerif,
                                color = if (dark) Color.White else Color(0xFF111827))
                            Text("▶", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF65C9FF),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 7.dp, bottom = 7.dp))
                        }
                        Text(
                            "UPlay",
                            fontSize = 43.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = (-1.8).sp,
                            color = foreground
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("YOUR PERSONAL MEDIA SPACE", color = if (dark) Color(0xFF8DD8FF) else Color(0xFF267CB7),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
                    Spacer(Modifier.height(42.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF91DFFF), Color(0xFF5EBEFF), Color(0xFFC0EEFF), Color(0xFF78C9FF)),
                                    startX = wave * 900f,
                                    endX = wave * 900f + 520f
                                )
                            ).padding(1.5.dp)
                    ) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).background(fieldSurface),
                            placeholder = { Text("Paste a video link…", color = secondaryText) },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = "Video link", tint = Color(0xFF61BFFF)) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val copied = clipboard.getText()?.text.orEmpty().trim()
                                    if (copied.isNotBlank()) {
                                        url = copied
                                        message = "Link pasted. Tap Play link to continue."
                                    } else message = "Your clipboard is empty."
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste link", tint = Color(0xFF61BFFF))
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            shape = RoundedCornerShape(21.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = fieldSurface,
                                unfocusedContainerColor = fieldSurface,
                                focusedTextColor = foreground,
                                unfocusedTextColor = foreground,
                                cursorColor = Color(0xFF61BFFF)
                            )
                        )
                    }
                    Spacer(Modifier.height(13.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { picker.launch(arrayOf("video/*")) },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open video", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = {
                                val candidate = url.trim()
                                val parsed = runCatching { Uri.parse(candidate) }.getOrNull()
                                val validUrl = parsed != null &&
                                    (parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) &&
                                    !parsed.host.isNullOrBlank()
                                if (validUrl) {
                                    onPlayUrl(candidate)
                                    selected = true
                                    currentTab = 1
                                    controlsVisible = true
                                    playbackError = null
                                    val entry = RecentVideo(candidate, candidate.substringAfterLast('/').ifBlank { candidate }, 0L, true)
                                    recentVideos.removeAll { it.uri == candidate }
                                    recentVideos.add(0, entry)
                                    while (recentVideos.size > 30) recentVideos.removeAt(recentVideos.lastIndex)
                                    saveRecentVideos(context, recentVideos)
                                    message = "Loading video link…"
                                } else message = "Enter a valid HTTP(S) video link first."
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (dark) Color(0xFF8DD8FF) else Color(0xFF167DDB))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Play link", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(34.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Recently watched", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = foreground)
                            Text("Pick up where you left off", fontSize = 12.sp, color = secondaryText)
                        }
                        TextButton(onClick = { currentTab = 2 }) {
                            Text("View library", color = if (dark) Color(0xFF8DD8FF) else Color(0xFF167DDB))
                        }
                    }
                    if (recentVideos.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = if (dark) Color(0xFF101827) else Color(0xFFEDF3FA),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color(0xFF61BFFF), modifier = Modifier.size(30.dp))
                                Column {
                                    Text("Your next watch starts here", color = foreground, fontWeight = FontWeight.SemiBold)
                                    Text("Open a video or paste a direct media link.", color = secondaryText, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            recentVideos.take(2).forEach { entry ->
                                Surface(
                                    onClick = {
                                        runCatching {
                                            if (entry.remote) onPlayUrl(entry.uri) else onLocalVideo(Uri.parse(entry.uri))
                                            player?.seekTo(entry.positionMs)
                                            selected = true
                                            currentTab = 1
                                            controlsVisible = true
                                            playbackError = null
                                            message = "Resuming ${entry.title}…"
                                        }.onFailure { message = "Couldn't reopen this video." }
                                    },
                                    color = if (dark) Color(0xFF111A29) else Color(0xFFEDF3FA),
                                    shape = RoundedCornerShape(15.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF61BFFF), modifier = Modifier.size(25.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(entry.title, color = foreground, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                            Text(if (entry.positionMs > 0L) "Resume at ${formatTime(entry.positionMs)}" else "Recently added",
                                                color = secondaryText, fontSize = 11.sp)
                                        }
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color(0xFF61BFFF))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (settingsOpen) ModalBottomSheet(onDismissRequest={settingsOpen=false},containerColor=Color(0xFF111722),shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Playback controls",fontSize=23.sp,fontWeight=FontWeight.Bold,color=Color.White)
            Text("PLAYBACK SPEED",color=Muted,fontSize=11.sp,fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf(0.5f,0.75f,1f,1.25f,1.5f,2f).forEach{v->
                    Surface(onClick={playbackSpeed=v;player?.setPlaybackSpeed(v);playerPreferences.edit().putFloat("playback_speed",v).apply()},
                        modifier=Modifier.weight(1f).height(40.dp),shape=RoundedCornerShape(10.dp),color=if(playbackSpeed==v) Green else Panel) {
                        Box(contentAlignment=Alignment.Center){Text(if(v==1f)"1×" else "${v}×",color=if(playbackSpeed==v) Ink else Color.White,fontSize=10.sp)}
                    }
                }
            }
            Text("SCREEN FIT",color=Muted,fontSize=11.sp,fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth().background(Panel,RoundedCornerShape(12.dp)).padding(4.dp)) {
                listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Crop").forEach{(m,t)->
                    Surface(onClick={resizeMode=m;playerView?.resizeMode=m;playerPreferences.edit().putInt("resize_mode",m).apply()},modifier=Modifier.weight(1f).height(40.dp),shape=RoundedCornerShape(9.dp),color=if(resizeMode==m) Color(0xFF334253) else Color.Transparent){Box(contentAlignment=Alignment.Center){Text(t,color=Color.White)}}
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically){Text("Repeat video",color=Color.White,modifier=Modifier.weight(1f));Switch(checked=player?.repeatMode==Player.REPEAT_MODE_ONE,onCheckedChange={player?.repeatMode=if(it)Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF;playerPreferences.edit().putBoolean("repeat_video",it).apply()})}
            OutlinedButton(onClick={settingsOpen=false;trackDialog=1},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.GraphicEq,null);Spacer(Modifier.width(8.dp));Text("Audio track")}
            OutlinedButton(onClick={settingsOpen=false;trackDialog=2},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Subtitles,null);Spacer(Modifier.width(8.dp));Text("Subtitles")}
            OutlinedButton(onClick={settingsOpen=false;subtitlePicker.launch(arrayOf("text/*","application/x-subrip","application/ttml+xml"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FolderOpen,null);Spacer(Modifier.width(8.dp));Text("Load subtitle file")}
        }
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


@Composable
private fun AudioBar(index: Int) {
    val transition = rememberInfiniteTransition(label = "audio-bar-$index")
    val height by transition.animateFloat(
        initialValue = 9f + (index % 4) * 4f,
        targetValue = 18f + ((index * 7) % 7) * 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 380 + (index % 5) * 90, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "audio-height-$index"
    )
    Box(
        modifier = Modifier.width(4.dp).height(height.dp).clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFB5E9FF), Color(0xFF3BAEFF))))
    )
}

@Composable
private fun RadialControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick, shape = CircleShape, color = Color(0xEE111A29),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x668DD8FF)),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = Color(0xFF8DD8FF), modifier = Modifier.size(21.dp))
        }
    }
}

private fun extractSharedUrl(intent: Intent?): String? {
    if (intent == null || intent.action != Intent.ACTION_SEND) return null
    val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
    val candidate = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        .find(sharedText)?.value
        ?.trimEnd('.', ',', '!', '?', ')', ']', '}')
        ?: return null
    return runCatching {
        val uri = Uri.parse(candidate)
        candidate.takeIf {
            (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) &&
                !uri.host.isNullOrBlank()
        }
    }.getOrNull()
}

private fun sharedLinkMessage(url: String): String {
    val host = runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")
    return when {
        host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" ->
            "YouTube link received. It's ready in the link field; playback or downloading depends on supported access."
        host == "instagram.com" || host.endsWith(".instagram.com") ->
            "Instagram link received. UPlay will need a supported, permitted media source to download it."
        else -> "Shared link received. Review it in the link field; available actions depend on the source."
    }
}

private fun playbackErrorMessage(error: PlaybackException): String = when {
    error.errorCodeName.contains("NETWORK", ignoreCase = true) ||
        error.errorCodeName.contains("IO_", ignoreCase = true) ->
        "Couldn't load this video. Check your connection and the video URL."
    error.errorCodeName.contains("UNSUPPORTED", ignoreCase = true) ||
        error.errorCodeName.contains("DECODING", ignoreCase = true) ->
        "This video format or codec may not be supported by the current player."
    error.errorCodeName.contains("PARSING", ignoreCase = true) ->
        "The video stream couldn't be read. Try another file or a direct media URL."
    else -> "Playback failed. Check the file or URL, then retry."
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


private fun loadRecentVideos(context: Context, key: String = "recent_videos"): List<RecentVideo> = runCatching {
    val raw = context.getSharedPreferences("uplay_library", Context.MODE_PRIVATE).getString(key, "[]") ?: "[]"
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val uri = item.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        RecentVideo(
            uri = uri,
            title = item.optString("title", uri.substringAfterLast('/')),
            positionMs = item.optLong("positionMs", 0L).coerceAtLeast(0L),
            remote = item.optBoolean("remote", false)
        )
    }.take(30)
}.getOrDefault(emptyList())

private fun saveRecentVideos(context: Context, videos: List<RecentVideo>, key: String = "recent_videos") {
    runCatching {
        val array = JSONArray()
        videos.take(30).forEach { video ->
            array.put(JSONObject().apply {
                put("uri", video.uri)
                put("title", video.title)
                put("positionMs", video.positionMs)
                put("remote", video.remote)
            })
        }
        context.getSharedPreferences("uplay_library", Context.MODE_PRIVATE)
            .edit().putString(key, array.toString()).apply()
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
    }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Video"
