package com.uplay.video

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import android.Manifest
import android.content.ContentUris
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.CancellationSignal
import android.util.Size
import android.util.LruCache
import android.content.res.Configuration
import android.content.Intent
import android.provider.OpenableColumns
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import android.view.LayoutInflater
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.view.SoundEffectConstants
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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

private val Ink = Color(0xFF090D15)
private val Panel = Color(0xFF141C29)
private val Blue = Color(0xFF087BFF)
private val Green = Color(0xFF35E889)
private val Muted = Color(0xFF9BA9BC)

private data class RecentVideo(
    val uri: String,
    val title: String,
    val positionMs: Long,
    val remote: Boolean,
    val sizeBytes: Long = 0L,
    val location: String = "",
    val durationMs: Long = 0L,
    val folder: String = "",
    val source: String = ""
)

private data class LibraryRow(val group: String, val entry: RecentVideo? = null)

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var downloadEngine: UniversalDownloadEngine
    private val downloaderReady = mutableStateOf(false)
    private val downloaderInitError = mutableStateOf<String?>(null)
    private val incomingSharedUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingSharedUrl.value = extractSharedUrl(intent)
        downloadEngine = UniversalDownloadEngine(this)
        lifecycleScope.launch {
            val initResult = withContext(Dispatchers.IO) {
                runCatching { downloadEngine.initialize() }
            }
            downloaderReady.value = initResult.isSuccess
            downloaderInitError.value = initResult.exceptionOrNull()?.message?.take(160)
        }
        val preferences = getSharedPreferences("uplay_settings", Context.MODE_PRIVATE)
        val darkThemeState = mutableStateOf(
            preferences.getBoolean(
                "dark_theme",
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            )
        )
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val mediaSourceFactory = DefaultMediaSourceFactory(
            DefaultDataSource.Factory(this, httpDataSourceFactory)
        )
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
            setPlaybackSpeed(preferences.getFloat("playback_speed", 1f).coerceIn(0.5f, 2f))
            repeatMode = if (preferences.getBoolean("repeat_video", false)) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
        setContent {
            val useDarkTheme = darkThemeState.value
            MaterialTheme(colorScheme = if (useDarkTheme) darkColorScheme(
                primary = Blue, secondary = Green, background = Ink, surface = Panel,
                onBackground = Color.White, onSurface = Color.White
            ) else lightColorScheme(
                primary = Blue, secondary = Color(0xFF167DDB), background = Color(0xFFF7F9FD),
                surface = Color.White, onBackground = Color(0xFF101725), onSurface = Color(0xFF101725)
            )) {
                UPlayHome(
                    player = player,
                    downloadEngine = downloadEngine,
                    downloaderReady = downloaderReady.value,
                    downloaderInitError = downloaderInitError.value,
                    incomingSharedUrl = incomingSharedUrl.value,
                    onSharedUrlConsumed = { incomingSharedUrl.value = null },
                    darkTheme = useDarkTheme,
                    onToggleTheme = { enabled ->
                        darkThemeState.value = enabled
                        preferences.edit().putBoolean("dark_theme", enabled).apply()
                    },
                    onLocalVideo = { uri -> play(MediaItem.fromUri(uri)) },
                    onPlayUrl = { url ->
                        val extension = runCatching {
                            Uri.parse(url).lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase()
                        }.getOrDefault("")
                        val mimeType = when (extension) {
                            "m3u8" -> "application/x-mpegURL"
                            "mpd" -> "application/dash+xml"
                            else -> null
                        }
                        val item = MediaItem.Builder().setUri(url).apply {
                            if (mimeType != null) setMimeType(mimeType)
                        }.build()
                        play(item)
                    }
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
    downloadEngine: UniversalDownloadEngine,
    downloaderReady: Boolean,
    downloaderInitError: String?,
    incomingSharedUrl: String?,
    onSharedUrlConsumed: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
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
    var downloadBusy by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadPreviewTitle by remember { mutableStateOf<String?>(null) }
    var downloadPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var downloadQuality by remember { mutableStateOf("Best available") }
    var qualityMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uiScope = rememberCoroutineScope()
    val lastProgressUpdateAt = remember { AtomicLong(0L) }
    var instagramLoginOpen by remember { mutableStateOf(false) }
    var instagramWebStatus by remember { mutableStateOf("Loading Instagram sign-in…") }
    var webPlaybackUrl by remember { mutableStateOf<String?>(null) }
    var instagramSessionReady by remember { mutableStateOf(downloadEngine.hasInstagramSession()) }
    LaunchedEffect(url) {
        val candidate = extractFirstHttpUrl(url)
        if (candidate == null) {
            downloadPreviewTitle = null
            downloadPreviewBitmap = null
            return@LaunchedEffect
        }
        val sourceHost = runCatching { Uri.parse(candidate).host.orEmpty() }.getOrDefault("")
        downloadPreviewTitle = "Looking up $sourceHost…"
        downloadPreviewBitmap = null
        delay(550)
        val preview = runCatching { downloadEngine.preview(candidate) }.getOrNull()
        if (extractFirstHttpUrl(url) != candidate) return@LaunchedEffect
        downloadPreviewTitle = preview?.title ?: sourceHost.ifBlank { "Video source" }
        val thumbnail = preview?.thumbnailUrl ?: return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(thumbnail).openConnection()
                connection.connectTimeout = 6_000
                connection.readTimeout = 6_000
                connection.getInputStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
        if (extractFirstHttpUrl(url) == candidate) downloadPreviewBitmap = bitmap
    }
    if (instagramLoginOpen) {
        Dialog(onDismissRequest = { instagramLoginOpen = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
                shape = RoundedCornerShape(20.dp),
                color = if (darkTheme) Color(0xFF111A29) else Color.White
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "Connect Instagram",
                        color = if (darkTheme) Color.White else Color(0xFF101725),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sign in here yourself, then tap Use session. UPlay stores the approved session locally on this device.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    AndroidView(
                        factory = { viewContext ->
                            CookieManager.getInstance().setAcceptCookie(true)
                            WebView(viewContext).apply {
                                setBackgroundColor(android.graphics.Color.WHITE)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.loadsImagesAutomatically = true
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.setSupportMultipleWindows(false)
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                                        instagramWebStatus = "Loading Instagram sign-in…"
                                    }
                                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                        CookieManager.getInstance().flush()
                                        view?.evaluateJavascript(
                                            "(document.body && document.body.innerText ? document.body.innerText.trim().length : 0).toString()"
                                        ) { rawLength ->
                                            val hasPageText = rawLength.orEmpty().trim('"').toIntOrNull()?.let { it > 0 } == true
                                            instagramWebStatus = when {
                                                !hasPageText -> "Instagram returned a blank sign-in page. Try Open browser; its login cookies may not transfer into this window."
                                                pageUrl.orEmpty().contains("/accounts/login") -> "Sign in above, then tap Use session."
                                                else -> "Instagram page loaded. Finish sign-in, then tap Use session."
                                            }
                                        }
                                    }
                                    override fun onReceivedError(
                                        view: WebView?, request: android.webkit.WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            instagramWebStatus = "Instagram couldn't load in this window. Check your connection or open Instagram in your browser."
                                        }
                                    }
                                    override fun onReceivedHttpError(
                                        view: WebView?,
                                        request: android.webkit.WebResourceRequest?,
                                        errorResponse: android.webkit.WebResourceResponse?
                                    ) {
                                        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                                            instagramWebStatus = "Instagram sign-in returned HTTP ${errorResponse?.statusCode}. Try again later or open Instagram in your browser."
                                        }
                                    }
                                }
                                loadUrl("https://m.instagram.com/accounts/login/")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(390.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Text(instagramWebStatus, color = Muted, fontSize = 11.sp, maxLines = 2)

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/accounts/login/")))
                            }
                        }) { Text("Open browser") }
                        TextButton(onClick = { instagramLoginOpen = false }) { Text("Cancel") }
                        if (instagramSessionReady) {
                            TextButton(onClick = {
                                downloadEngine.clearInstagramSession()
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                instagramSessionReady = false
                                instagramLoginOpen = false
                                message = "Instagram session removed from UPlay."
                            }) { Text("Disconnect") }
                        }
                    }
                    Button(
                        onClick = {
                            CookieManager.getInstance().flush()
                            val cookies = CookieManager.getInstance()
                                .getCookie("https://www.instagram.com").orEmpty()
                            if (downloadEngine.saveInstagramCookies(cookies)) {
                                instagramSessionReady = true
                                instagramLoginOpen = false
                                message = "Instagram session saved locally. Retry the public or account-authorized Reel link."
                            } else {
                                message = "No Instagram session was found in UPlay's login window. Browser sign-in does not automatically transfer cookies into this window."
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Use session", maxLines = 1) }
                }
            }
        }
    }
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var originalSystemUiFlags by remember { mutableIntStateOf(0) }
    var currentTab by remember { mutableStateOf(0) } // Home, Player, Library
    var libraryMode by remember { mutableIntStateOf(0) } // Videos, Music
    var rollRotation by remember { mutableFloatStateOf(0f) }
    var filmRollVisible by remember { mutableStateOf(false) }
    val rollLift = remember { Animatable(0f) }
    val animatedRollRotation by animateFloatAsState(
        targetValue = rollRotation,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "navigation-film-roll-rotation"
    )
    var controlsVisible by remember { mutableStateOf(true) }
    val recentVideos = remember(context) { mutableStateListOf<RecentVideo>().apply { addAll(loadRecentVideos(context)) } }
    val recentAudios = remember(context) { mutableStateListOf<RecentVideo>().apply { addAll(loadRecentVideos(context, "recent_audio")) } }
    val deviceVideos = remember { mutableStateListOf<RecentVideo>() }
    val deviceAudios = remember { mutableStateListOf<RecentVideo>() }
    var mediaPermissionGranted by remember { mutableStateOf(false) }
    var mediaScanInProgress by remember { mutableStateOf(false) }
    var mediaScanMessage by remember { mutableStateOf<String?>(null) }
    var mediaScanRequest by remember { mutableIntStateOf(0) }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val videoGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        val selectedVideosGranted = Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        mediaPermissionGranted = if (Build.VERSION.SDK_INT >= 33) {
            videoGranted || audioGranted || selectedVideosGranted
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    LaunchedEffect(Unit) {
        val requiredPermissions = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val videoGranted = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        val audioGranted = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        val selectedVideosGranted = Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        mediaPermissionGranted = if (Build.VERSION.SDK_INT >= 33) {
            videoGranted || audioGranted || selectedVideosGranted
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        if (!mediaPermissionGranted) mediaPermissionLauncher.launch(requiredPermissions)
    }

    LaunchedEffect(mediaPermissionGranted, mediaScanRequest) {
        if (!mediaPermissionGranted) {
            deviceVideos.clear()
            deviceAudios.clear()
            mediaScanMessage = "Allow media access to browse files on this device."
            return@LaunchedEffect
        }
        val canReadVideos = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
        val canReadAudio = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        mediaScanInProgress = true
        mediaScanMessage = null
        try {
            val scanned = withContext(Dispatchers.IO) {
                scanDeviceMedia(context, includeVideos = canReadVideos, includeAudio = canReadAudio)
            }
            val videoHistory = recentVideos.associateBy { it.uri }
            val audioHistory = recentAudios.associateBy { it.uri }
            deviceVideos.clear()
            deviceVideos.addAll(scanned.first.map { media ->
                videoHistory[media.uri]?.let {
                    media.copy(positionMs = it.positionMs, durationMs = media.durationMs.takeIf { d -> d > 0L } ?: it.durationMs)
                } ?: media
            })
            deviceAudios.clear()
            deviceAudios.addAll(scanned.second.map { media ->
                audioHistory[media.uri]?.let {
                    media.copy(positionMs = it.positionMs, durationMs = media.durationMs.takeIf { d -> d > 0L } ?: it.durationMs)
                } ?: media
            })
            mediaScanMessage = if (canReadVideos && canReadAudio) null else "Some media access is limited by your current permissions."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mediaScanMessage = "Couldn't finish scanning media. Check permissions and try again."
        } finally {
            mediaScanInProgress = false
        }
    }

    LaunchedEffect(incomingSharedUrl) {
        val sharedUrl = incomingSharedUrl ?: return@LaunchedEffect
        url = sharedUrl
        currentTab = 1
        controlsVisible = true
        message = sharedLinkMessage(sharedUrl)
        onSharedUrlConsumed()
    }
    var locked by remember { mutableStateOf(false) }
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

    fun startUniversalDownload(candidate: String, playAfterDownload: Boolean = false) {
        val normalizedCandidate = extractFirstHttpUrl(candidate) ?: candidate.trim()
        val parsed = runCatching { Uri.parse(normalizedCandidate) }.getOrNull()
        val valid = parsed != null &&
            (parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) &&
            !parsed.host.isNullOrBlank()
        if (!valid) {
            message = "Paste a valid HTTP(S) video page or direct media URL first."
            return
        }
        if (downloadBusy) return
        downloadBusy = true
        downloadProgress = 0f
        lastProgressUpdateAt.set(0L)
        if (downloadPreviewTitle == null) downloadPreviewTitle = "Identifying ${parsed?.host.orEmpty()}…"
        message = when {
            playAfterDownload -> "Resolving the video link for playback…"
            downloaderReady -> "Finding $downloadQuality video streams…"
            downloaderInitError != null -> "Retrying downloader initialization…"
            else -> "Preparing universal downloader…"
        }
        uiScope.launch {
            try {
                val downloadedUri = downloadEngine.download(normalizedCandidate, downloadQuality) { percent, status ->
                    // Extractors can emit many progress events per second. Coalesce UI updates
                    // so large downloads don't queue thousands of main-thread coroutines.
                    val now = android.os.SystemClock.elapsedRealtime()
                    val previous = lastProgressUpdateAt.get()
                    if ((now - previous >= 250L || percent >= 98f) &&
                        lastProgressUpdateAt.compareAndSet(previous, now)) {
                        uiScope.launch {
                            downloadProgress = (percent / 100f).coerceIn(0f, 1f)
                            if (status.isNotBlank()) message = status
                        }
                    }
                }
                if (playAfterDownload) {
                    player?.setMediaItem(MediaItem.fromUri(downloadedUri))
                    player?.prepare()
                    player?.playWhenReady = true
                    selected = true
                    isMusicMode = false
                    currentTab = 1
                    controlsVisible = true
                    playbackError = null
                }
                downloadProgress = 1f
                message = if (playAfterDownload) {
                    "Video downloaded and opened in the player."
                } else if (Build.VERSION.SDK_INT >= 29) {
                    "Download complete — saved to Downloads/UPlay."
                } else {
                    "Download complete — saved in UPlay app downloads."
                }
                mediaScanRequest++
            } catch (error: Exception) {
                val detail = error.message.orEmpty()
                if (playAfterDownload) {
                    // Try extractor-backed download and play the local result first. If
                    // extraction fails, fall back to the source page and its native player.
                    webPlaybackUrl = normalizedCandidate
                    selected = true
                    isMusicMode = false
                    currentTab = 1
                    controlsVisible = false
                    playbackError = null
                    message = "Direct stream extraction failed; opening the source page instead. " + detail.take(150)
                } else message = when {
                    detail.contains("signed-in session", true) || detail.contains("verification", true) ||
                        detail.contains("checkpoint", true) ->
                        detail.take(260)
                    detail.contains("private", true) || detail.contains("login", true) ||
                        detail.contains("sign in", true) || detail.contains("authentication", true) ->
                        "This source requires access UPlay doesn't currently have. Try a public post URL, or open the post in its official app."
                    detail.contains("HTTP Error 403", true) || detail.contains("forbidden", true) ->
                        "The source refused the download request (403). It may require an authorized session or restrict external downloads."
                    detail.contains("HTTP Error 429", true) || detail.contains("too many requests", true) ->
                        "The source is rate-limiting requests. Wait a while, then retry."
                    detail.contains("primary directory", true) || detail.contains("not allowed for content", true) ->
                        "Android rejected the save location. The latest build routes downloads through the Downloads collection; retry after updating."
                    detail.contains("unsupported", true) || detail.contains("no video", true) ||
                        detail.contains("unable to extract", true) || detail.contains("not a valid", true) ->
                        "No downloadable stream was found. The site may be unsupported or restrict external extraction."
                    detail.contains("network", true) || detail.contains("timed out", true) ||
                        detail.contains("connection", true) ->
                        "The connection failed. Check your network and retry."
                    else -> "Download failed: ${detail.take(120).ifBlank { "The source could not be processed." }}"
                }
            } finally {
                downloadBusy = false
            }
        }
    }

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

    LaunchedEffect(player, selected, isPlaying, isMusicMode, recentVideos.size, recentAudios.size) {
        var persistenceTick = 0
        while (selected && player != null) {
            playbackPosition = player.currentPosition.coerceAtLeast(0L)
            playbackDuration = player.duration.takeIf { it > 0L } ?: 0L
            if (persistenceTick % 10 == 0) {
                val activeUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
                if (activeUri != null && playbackPosition > 0L) {
                    val history = if (isMusicMode) recentAudios else recentVideos
                    val index = history.indexOfFirst { it.uri == activeUri }
                    if (index >= 0) {
                        history[index] = history[index].copy(positionMs = playbackPosition, durationMs = playbackDuration)
                        saveRecentVideos(context, history, if (isMusicMode) "recent_audio" else "recent_videos")
                    }
                }
            }
            persistenceTick++
            delay(250)
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
                selected = true
                isMusicMode = true
                currentTab = 1
                controlsVisible = true
                message = "Opening audio…"
                uiScope.launch {
                    runCatching {
                        val (title, size) = withContext(Dispatchers.IO) {
                            queryDisplayName(context, uri) to queryFileSize(context, uri)
                        }
                        val previous = recentAudios.firstOrNull { it.uri == uri.toString() }
                        val entry = RecentVideo(uri.toString(), title, previous?.positionMs ?: 0L, false,
                            sizeBytes = size, location = uri.toString(), durationMs = previous?.durationMs ?: 0L)
                        recentAudios.removeAll { it.uri == entry.uri }
                        recentAudios.add(0, entry)
                        while (recentAudios.size > 50) recentAudios.removeAt(recentAudios.lastIndex)
                        saveRecentVideos(context, recentAudios, "recent_audio")
                        player?.seekTo(entry.positionMs)
                        message = "Playing audio: $title"
                    }.onFailure { message = "Couldn't read this audio file's details." }
                }
            }.onFailure { message = "Couldn't open this audio file. Try another file." }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                onLocalVideo(uri)
                isMusicMode = false
                selected = true
                currentTab = 1
                controlsVisible = true
                message = "Opening video…"
                uiScope.launch {
                    runCatching {
                        val (title, size) = withContext(Dispatchers.IO) {
                            queryDisplayName(context, uri) to queryFileSize(context, uri)
                        }
                        val previous = recentVideos.firstOrNull { it.uri == uri.toString() }
                        val entry = RecentVideo(uri.toString(), title, previous?.positionMs ?: 0L, false,
                            sizeBytes = size, location = uri.toString(), durationMs = previous?.durationMs ?: 0L)
                        recentVideos.removeAll { it.uri == entry.uri }
                        recentVideos.add(0, entry)
                        while (recentVideos.size > 30) recentVideos.removeAt(recentVideos.lastIndex)
                        saveRecentVideos(context, recentVideos)
                        player?.seekTo(entry.positionMs)
                        message = "Loading selected video: $title"
                    }.onFailure { message = "Video opened, but its file details couldn't be read." }
                }
            }.onFailure { message = "Couldn't open this video. Try another file." }
        }
    }

    val systemDark = darkTheme
    val appBackground = if (systemDark) Ink else Color(0xFFF7F9FD)
    Surface(modifier = Modifier.fillMaxSize(), color = appBackground) {
        Scaffold(containerColor = appBackground, contentWindowInsets = if (immersivePlayer) WindowInsets(0,0,0,0) else WindowInsets.safeDrawing, bottomBar = {
            if (!fullScreen && !(landscape && selected)) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(78.dp).padding(vertical = 3.dp)) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val markerX = remember(maxWidth, density) {
                        Animatable(with(density) { (maxWidth / 6f - 10.dp).toPx() })
                    }
                    fun selectTab(tab: Int) {
                        if (tab == currentTab) return
                        val target = with(density) { (maxWidth * ((tab + 0.5f) / 3f) - 10.dp).toPx() }
                        currentTab = tab
                        rollRotation += 720f
                        filmRollVisible = true
                        uiScope.launch {
                            rollLift.snapTo(0f)
                            rollLift.animateTo(-with(density) { 13.dp.toPx() }, tween(140, easing = FastOutSlowInEasing))
                            markerX.animateTo(target, tween(780, easing = FastOutSlowInEasing))
                            rollLift.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
                            delay(100)
                            filmRollVisible = false
                        }
                    }
                    val homeIconScale by animateFloatAsState(if (currentTab == 0) 1.10f else 0.94f, tween(220, easing = FastOutSlowInEasing), label = "home-tab-scale")
                    val playerIconScale by animateFloatAsState(if (currentTab == 1) 1.12f else 0.94f, tween(220, easing = FastOutSlowInEasing), label = "player-tab-scale")
                    val libraryIconScale by animateFloatAsState(if (currentTab == 2) 1.10f else 0.94f, tween(220, easing = FastOutSlowInEasing), label = "library-tab-scale")
                    NavigationBar(
                        modifier = Modifier.fillMaxSize().padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .border(1.dp, if (systemDark) Color(0xFF1D3045) else Color(0xFFDDE8F3), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                        containerColor = if (systemDark) Color(0xFF0B101B) else Color.White,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(currentTab == 0, { selectTab(0) }, {
                            Icon(Icons.Default.Home, null, tint = if (currentTab == 0) Color(0xFF62C9FF) else if (systemDark) Color(0xFF8797AB) else Color(0xFF77859A), modifier = Modifier.size(25.dp).graphicsLayer { scaleX = homeIconScale; scaleY = homeIconScale })
                        }, label = { Text("Home") })
                        NavigationBarItem(currentTab == 1, { selectTab(1) }, {
                            Icon(Icons.Default.PlayCircleFilled, null, tint = if (currentTab == 1) Color(0xFF62C9FF) else if (systemDark) Color(0xFF8797AB) else Color(0xFF77859A), modifier = Modifier.size(27.dp).graphicsLayer { scaleX = playerIconScale; scaleY = playerIconScale })
                        }, label = { Text("Player") })
                        NavigationBarItem(currentTab == 2, { selectTab(2) }, {
                            Icon(Icons.Default.VideoLibrary, null, tint = if (currentTab == 2) Color(0xFF62C9FF) else if (systemDark) Color(0xFF8797AB) else Color(0xFF77859A), modifier = Modifier.size(25.dp).graphicsLayer { scaleX = libraryIconScale; scaleY = libraryIconScale })
                        }, label = { Text("Library") })
                    }
                    if (filmRollVisible) {
                        FilmRollIndicator(
                            darkTheme = systemDark,
                            modifier = Modifier.offset {
                                IntOffset(markerX.value.roundToInt(), 2.dp.roundToPx() + rollLift.value.roundToInt())
                            }.size(22.dp).graphicsLayer { rotationZ = animatedRollRotation }
                        )
                    }
                }
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
                                Text("U P L A Y   /   PLAYER", fontSize = 10.sp, letterSpacing = 1.5.sp, color = Color(0xFF8DD8FF), fontWeight = FontWeight.Bold)
                                Text(
                                    if (selected) player?.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "Now playing" }
                                    else "Your next watch",
                                    fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1
                                )
                            }
                            Surface(
                                color = Color(0xFF102637),
                                shape = RoundedCornerShape(50),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x554DBFFF))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(Modifier.size(6.dp).background(if (!selected) Color(0xFF718198) else if (isPlaying) Color(0xFF8DD8FF) else Color(0xFFB8C8D8), CircleShape))
                                    Text(if (!selected) "READY" else if (isPlaying) "PLAYING" else "PAUSED",
                                        color = Color(0xFFB9E9FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .clip(if (fullScreen || landscape) RoundedCornerShape(0.dp) else RoundedCornerShape(26.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF0B1725), Color.Black, Color(0xFF08111D))))
                            .border(
                                width = if (fullScreen || landscape) 0.dp else 1.dp,
                                color = Color(0xFF26384B),
                                shape = if (fullScreen || landscape) RoundedCornerShape(0.dp) else RoundedCornerShape(26.dp)
                            )
                    ) {
                        if (webPlaybackUrl != null) {
                            AndroidView(
                                factory = { viewContext ->
                                    WebView(viewContext).apply {
                                        setBackgroundColor(android.graphics.Color.BLACK)
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.databaseEnabled = true
                                        settings.loadsImagesAutomatically = true
                                        settings.mediaPlaybackRequiresUserGesture = false
                                        settings.javaScriptCanOpenWindowsAutomatically = true
                                        settings.setSupportMultipleWindows(false)
                                        settings.useWideViewPort = true
                                        settings.loadWithOverviewMode = true
                                        CookieManager.getInstance().setAcceptCookie(true)
                                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                        webChromeClient = WebChromeClient()
                                        webViewClient = WebViewClient()
                                        loadUrl(webPlaybackUrl!!)
                                    }
                                },
                                update = { view -> if (view.url != webPlaybackUrl) view.loadUrl(webPlaybackUrl!!) },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (selected && player != null && player.currentMediaItem != null) {
                            if (isMusicMode) {
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                        .background(Brush.verticalGradient(listOf(Color(0xFF0B1728), Color.Black, Color(0xFF111D30))))
                                        .pointerInput(locked) {
                                            detectTapGestures(onTap = { if (!locked) controlsVisible = !controlsVisible })
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                                    ) {
                                        MusicOrbitVisualizer(isPlaying = isPlaying, modifier = Modifier.size(190.dp))
                                        Text(
                                            player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "Now playing" },
                                            color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 2
                                        )
                                        Text(
                                            if (isPlaying) "SOUND IN MOTION" else "PAUSED",
                                            color = Color(0xFF8DD8FF), fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(26.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                                    controlsVisible = true
                                                },
                                                modifier = Modifier.size(48.dp).background(Color(0x660D1725), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (player.isPlaying) player.pause() else player.play()
                                                    controlsVisible = true
                                                },
                                                modifier = Modifier.size(68.dp)
                                                    .background(Brush.linearGradient(listOf(Color(0xFFB3F0FF), Color(0xFF62BFFF))), CircleShape)
                                                    .border(1.dp, Color(0xAAE0FAFF), CircleShape)
                                            ) {
                                                Icon(
                                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    if (isPlaying) "Pause music" else "Play music",
                                                    tint = Ink, modifier = Modifier.size(42.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                                                    controlsVisible = true
                                                },
                                                modifier = Modifier.size(48.dp).background(Color(0x660D1725), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                                            }
                                        }
                                        val musicDuration = player.duration.takeIf { it > 0L } ?: 0L
                                        val musicPosition = player.currentPosition.coerceIn(0L, musicDuration.coerceAtLeast(1L))
                                        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                            Slider(
                                                value = if (musicDuration > 0L) musicPosition.toFloat().coerceIn(0f, musicDuration.toFloat()) else 0f,
                                                onValueChange = { value ->
                                                    if (musicDuration > 0L) player.seekTo(value.toLong().coerceIn(0L, musicDuration))
                                                },
                                                valueRange = 0f..musicDuration.toFloat().coerceAtLeast(1f),
                                                enabled = musicDuration > 0L
                                            )
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(formatTime(musicPosition), color = Color.White, fontSize = 11.sp)
                                                Text(formatTime(musicDuration), color = Color.White, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    RotaryControlDial(
                                        open = radialOpen,
                                        onToggle = { radialOpen = !radialOpen },
                                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 92.dp).padding(top = 8.dp).size(240.dp),
                                        onAction = { action ->
                                            radialOpen = false
                                            controlsVisible = true
                                            when (action) {
                                                0 -> {
                                                    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                                                    val index = speeds.indexOfFirst { it == playbackSpeed }.coerceAtLeast(0)
                                                    playbackSpeed = speeds[(index + 1) % speeds.size]
                                                    player.setPlaybackSpeed(playbackSpeed)
                                                    playerPreferences.edit().putFloat("playback_speed", playbackSpeed).apply()
                                                    message = "Playback speed: ${playbackSpeed}×"
                                                }
                                                1 -> message = "Screen framing controls apply to video playback."
                                                2, 11 -> trackDialog = 2
                                                3 -> subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/ttml+xml"))
                                                4, 12 -> trackDialog = 1
                                                5, 16 -> {
                                                    val repeat = player.repeatMode != Player.REPEAT_MODE_ONE
                                                    player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                                    playerPreferences.edit().putBoolean("repeat_video", repeat).apply()
                                                    message = if (repeat) "Repeat enabled" else "Repeat disabled"
                                                }
                                                6 -> {
                                                    locked = true
                                                    message = "Player controls locked."
                                                }
                                                7 -> if (player.isPlaying) player.pause() else player.play()
                                                8 -> player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                                9 -> player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                                                10 -> player.volume = if (player.volume > 0f) 0f else 1f
                                                13, 17 -> fullScreen = !fullScreen
                                                14 -> {
                                                    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                                                    val index = speeds.indexOfFirst { it == playbackSpeed }.coerceAtLeast(0)
                                                    playbackSpeed = speeds[(index - 1 + speeds.size) % speeds.size]
                                                    player.setPlaybackSpeed(playbackSpeed)
                                                    playerPreferences.edit().putFloat("playback_speed", playbackSpeed).apply()
                                                    message = "Playback speed: ${playbackSpeed}×"
                                                }
                                                15 -> player.seekTo(0L)
                                            }
                                        }
                                    )
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
                            Column(Modifier.fillMaxSize().clipToBounds()) {
                            AnimatedVisibility(visible = controlsVisible && webPlaybackUrl == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xA6081423), Color.Transparent, Color(0xD906101D))))) {
                                    if (locked) {
                                        IconButton(
                                            onClick = { locked = false; controlsVisible = true },
                                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                                .background(Color(0x990B101B), RoundedCornerShape(50))
                                        ) { Icon(Icons.Default.LockOpen, "Unlock controls", tint = Green) }
                                    } else {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = !radialOpen,
                                            enter = fadeIn() + scaleIn(),
                                            exit = fadeOut() + scaleOut(),
                                            modifier = Modifier.align(Alignment.Center)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                                            ) {
                                                IconButton(
                                                    onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)); controlsVisible = true },
                                                    modifier = Modifier.size(42.dp).background(Color(0x77070D18), CircleShape)
                                                ) { Icon(Icons.Default.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(28.dp)) }
                                                IconButton(
                                                    onClick = { if (player.isPlaying) player.pause() else player.play(); controlsVisible = true },
                                                    modifier = Modifier.size(64.dp).background(Brush.linearGradient(listOf(Color(0xFFB3F0FF), Color(0xFF62BFFF))), CircleShape)
                                                        .border(1.dp, Color(0xAAE0FAFF), CircleShape)
                                                ) {
                                                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        if (isPlaying) "Pause" else "Play", tint = Ink, modifier = Modifier.size(42.dp))
                                                }
                                                IconButton(
                                                    onClick = { player.seekTo((player.currentPosition + 10_000L).coerceAtLeast(0L)); controlsVisible = true },
                                                    modifier = Modifier.size(42.dp).background(Color(0x77070D18), CircleShape)
                                                ) { Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(28.dp)) }
                                            }
                                        }
                                        RotaryControlDial(
                                            open = radialOpen,
                                            onToggle = { radialOpen = !radialOpen },
                                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 92.dp).padding(top = 8.dp).size(240.dp),
                                            onAction = { action ->
                                                radialOpen = false
                                                controlsVisible = true
                                                when (action) {
                                                    0 -> {
                                                        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                                                        val index = speeds.indexOfFirst { it == playbackSpeed }.coerceAtLeast(0)
                                                        playbackSpeed = speeds[(index + 1) % speeds.size]
                                                        player?.setPlaybackSpeed(playbackSpeed)
                                                        playerPreferences.edit().putFloat("playback_speed", playbackSpeed).apply()
                                                        message = "Playback speed: ${playbackSpeed}×"
                                                    }
                                                    1 -> {
                                                        val modes = listOf(
                                                            AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                                            AspectRatioFrameLayout.RESIZE_MODE_FILL,
                                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                        )
                                                        val index = modes.indexOf(resizeMode).coerceAtLeast(0)
                                                        resizeMode = modes[(index + 1) % modes.size]
                                                        playerView?.resizeMode = resizeMode
                                                        playerPreferences.edit().putInt("resize_mode", resizeMode).apply()
                                                        message = "Screen framing: ${when (resizeMode) { AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"; AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop"; else -> "Fit" }}"
                                                    }
                                                    2 -> trackDialog = 2
                                                    3 -> subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/ttml+xml"))
                                                    4 -> trackDialog = 1
                                                    5 -> {
                                                        val repeat = player?.repeatMode != Player.REPEAT_MODE_ONE
                                                        player?.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                                        playerPreferences.edit().putBoolean("repeat_video", repeat).apply()
                                                        message = if (repeat) "Repeat enabled" else "Repeat disabled"
                                                    }
                                                    6 -> {
                                                        locked = true
                                                        message = "Player controls locked."
                                                    }
                                                    7 -> { if (player?.isPlaying == true) player.pause() else player?.play() }
                                                    8 -> player?.let { it.seekTo((it.currentPosition - 10_000L).coerceAtLeast(0L)) }
                                                    9 -> player?.let { it.seekTo((it.currentPosition + 10_000L).coerceAtMost(it.duration.coerceAtLeast(0L))) }
                                                    10 -> player?.let { active -> active.volume = if (active.volume > 0f) 0f else 1f }
                                                    11 -> trackDialog = 2
                                                    12 -> trackDialog = 1
                                                    13 -> fullScreen = !fullScreen
                                                    14 -> {
                                                        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                                                        val index = speeds.indexOfFirst { it == playbackSpeed }.coerceAtLeast(0)
                                                        playbackSpeed = speeds[(index - 1 + speeds.size) % speeds.size]
                                                        player?.setPlaybackSpeed(playbackSpeed)
                                                        playerPreferences.edit().putFloat("playback_speed", playbackSpeed).apply()
                                                        message = "Playback speed: ${playbackSpeed}×"
                                                    }
                                                    15 -> player?.seekTo(0L)
                                                    16 -> {
                                                        val repeat = player?.repeatMode != Player.REPEAT_MODE_ONE
                                                        player?.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                                        playerPreferences.edit().putBoolean("repeat_video", repeat).apply()
                                                        message = if (repeat) "Repeat enabled" else "Repeat disabled"
                                                    }
                                                    17 -> fullScreen = !fullScreen
                                                }
                                            }
                                        )
                                        Column(
                                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(Color(0xB807111E))
                                                .border(1.dp, Color(0x332F80A9), RoundedCornerShape(18.dp))
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val duration = (player.duration).takeIf { it > 0L } ?: 0L
                                            val position = player.currentPosition.coerceIn(0L, duration.coerceAtLeast(1L))
                                            CinematicSeekBar(
                                                positionMs = playbackPosition,
                                                durationMs = duration,
                                                isPlaying = isPlaying,
                                                onSeek = { target -> if (duration > 0L) player.seekTo(target.coerceIn(0L, duration)) },
                                                modifier = Modifier.fillMaxWidth().height(26.dp)
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
                                onClick = { webPlaybackUrl = null; picker.launch(arrayOf("video/*")) },
                                modifier = Modifier.weight(0.9f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8DD8FF), contentColor = Color(0xFF081321))
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(7.dp))
                                Text("Open video", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    val candidate = extractFirstHttpUrl(url) ?: url.trim()
                                    val parsed = runCatching { Uri.parse(candidate) }.getOrNull()
                                    val validUrl = parsed != null &&
                                        (parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) &&
                                        !parsed.host.isNullOrBlank()
                                    if (validUrl) {
                                        val host = parsed?.host.orEmpty().lowercase()
                                        val pathPart = parsed?.path.orEmpty().lowercase()
                                        val extension = pathPart.substringAfterLast('/').substringAfterLast('.', "")
                                        val directMedia = extension in setOf(
                                            "mp4", "m4v", "mov", "webm", "mkv", "avi", "3gp",
                                            "m3u8", "mpd", "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac"
                                        )
                                        if (!directMedia) {
                                            // Play the actual source page in an embedded WebView; downloading
                                            // the entire page before playback made pasted links feel broken.
                                            player?.pause()
                                            webPlaybackUrl = candidate
                                            selected = true
                                            isMusicMode = false
                                            controlsVisible = false
                                            currentTab = 1
                                            playbackError = null
                                            message = "Opening source page in UPlay…"
                                            val entry = RecentVideo(candidate, candidate.substringAfterLast('/').ifBlank { candidate }, 0L, true)
                                            recentVideos.removeAll { it.uri == candidate }
                                            recentVideos.add(0, entry)
                                            while (recentVideos.size > 30) recentVideos.removeAt(recentVideos.lastIndex)
                                            saveRecentVideos(context, recentVideos)
                                        } else {
                                            webPlaybackUrl = null
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
                                        }
                                    } else message = "Enter a valid HTTP(S) link first."
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8DD8FF))
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text("Play link", fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Download quality",
                                color = if (systemDark) Color(0xFFB5C4D7) else Color(0xFF536277),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box {
                                OutlinedButton(
                                    onClick = { qualityMenuExpanded = true },
                                    enabled = !downloadBusy,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (systemDark) Color(0xFF8DD8FF) else Color(0xFF167DDB)
                                    )
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(downloadQuality)
                                }
                                DropdownMenu(
                                    expanded = qualityMenuExpanded,
                                    onDismissRequest = { qualityMenuExpanded = false }
                                ) {
                                    listOf("Best available", "1080p", "720p", "480p", "360p").forEach { quality ->
                                        DropdownMenuItem(
                                            text = { Text(quality) },
                                            onClick = {
                                                downloadQuality = quality
                                                qualityMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { instagramLoginOpen = true },
                                enabled = !downloadBusy,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (systemDark) Color(0xFF8DD8FF) else Color(0xFF167DDB)
                                )
                            ) {
                                Icon(
                                    if (instagramSessionReady) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (instagramSessionReady) "Instagram session · Manage" else "Sign in to Instagram")
                            }
                        }
                        if (downloadBusy || downloadPreviewTitle != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (systemDark) Color(0xFF101A29) else Color(0xFFEAF5FF)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val previewBitmap = downloadPreviewBitmap
                                    if (previewBitmap != null) {
                                        Image(
                                            bitmap = previewBitmap.asImageBitmap(),
                                            contentDescription = "Video thumbnail preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(width = 96.dp, height = 64.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(width = 96.dp, height = 64.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(if (systemDark) Color(0xFF1D2A3B) else Color(0xFFD4E9FA)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color(0xFF8DD8FF))
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            downloadPreviewTitle ?: "Identifying video…",
                                            color = if (systemDark) Color.White else Color(0xFF101725),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2
                                        )
                                        Text(
                                            if (downloadBusy) "Source preview · live transfer details below" else "Source preview",
                                            color = Muted,
                                            fontSize = 11.sp,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { startUniversalDownload(url) },
                            enabled = !downloadBusy && url.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (systemDark) Color(0xFF8DD8FF) else Color(0xFF167DDB))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (downloadBusy) "Downloading…" else if (downloaderReady) "Download video" else "Prepare downloader")
                        }
                        if (downloadBusy) {
                            LinearProgressIndicator(
                                progress = downloadProgress,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                                color = Color(0xFF8DD8FF),
                                trackColor = if (systemDark) Color(0xFF233448) else Color(0xFFD8E8F5)
                            )
                            Text(message, color = Muted, fontSize = 12.sp, maxLines = 2)
                        }
                        OutlinedButton(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
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
                            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                            placeholder = { Text("Paste a direct video URL or shared link", color = Muted) },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = Green) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF8DD8FF), unfocusedBorderColor = Color(0xFF2A4055),
                                focusedTextColor = if (darkTheme) Color.White else Color(0xFF101725),
                                unfocusedTextColor = if (darkTheme) Color.White else Color(0xFF101725),
                                cursorColor = Color(0xFF8DD8FF),
                                focusedContainerColor = if (darkTheme) Color(0xFF0E1724) else Color.White,
                                unfocusedContainerColor = if (darkTheme) Color(0xFF0E1724) else Color.White
                            )
                        )
                        if (message.isNotBlank()) {
                            Text(
                                text = playbackError ?: message,
                                color = if (playbackError != null) Color(0xFFFFB4AB) else Muted,
                                fontSize = 12.sp,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (playbackError != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = {
                                        playbackError = null
                                        runCatching {
                                            player?.let { active ->
                                                val activeUri = active.currentMediaItem?.localConfiguration?.uri
                                                val activePath = activeUri?.path.orEmpty().lowercase()
                                                val extension = activePath.substringAfterLast('/').substringAfterLast('.', "")
                                                val directMedia = extension in setOf(
                                                    "mp4", "m4v", "mov", "webm", "mkv", "avi", "3gp",
                                                    "m3u8", "mpd", "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac"
                                                )
                                                if (activeUri != null &&
                                                    (activeUri.scheme.equals("https", true) || activeUri.scheme.equals("http", true)) &&
                                                    !directMedia
                                                ) {
                                                    startUniversalDownload(activeUri.toString(), playAfterDownload = true)
                                                } else {
                                                    val retryPosition = active.currentPosition.coerceAtLeast(0L)
                                                    active.prepare()
                                                    active.seekTo(retryPosition)
                                                    active.playWhenReady = true
                                                }
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
                val dark = darkTheme
                val foreground = if (dark) Color(0xFFF7FAFF) else Color(0xFF101725)
                val secondaryText = if (dark) Color(0xFF9BA9BC) else Color(0xFF68758A)
                val cardSurface = if (dark) Panel else Color.White
                val rawMediaItems = if (libraryMode == 0) {
                    (deviceVideos + recentVideos).distinctBy { it.uri }
                } else {
                    (deviceAudios + recentAudios).distinctBy { it.uri }
                }
                val mediaItems = rawMediaItems.groupBy { entry ->
                    val folderName = entry.folder.trim('/')
                    when {
                        entry.remote -> entry.source.ifBlank { "Online links" }
                        folderName.isNotBlank() && folderName.lowercase() !in listOf("download", "downloads") -> folderName
                        else -> entry.source.ifBlank { if (libraryMode == 0) "Other videos" else "Other music" }
                    }
                }.toList().flatMap { (group, entries) ->
                    listOf(LibraryRow(group)) + entries.map { LibraryRow(group, it) }
                }
                Column(modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Your library", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = foreground)
                            Text("History, progress and file details in one place.", color = secondaryText, fontSize = 13.sp)
                        }
                        TextButton(onClick = { mediaScanRequest++ }, enabled = !mediaScanInProgress) {
                            Text(if (mediaScanInProgress) "Scanning…" else "Refresh", color = Color(0xFF45B8FF))
                        }
                    }
                    if (mediaScanInProgress) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                            color = Color(0xFF8DD8FF),
                            trackColor = if (dark) Color(0xFF233448) else Color(0xFFD8E8F5)
                        )
                    }
                    mediaScanMessage?.let { scanMessage ->
                        Text(scanMessage, color = secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (dark) Color(0xFF101827) else Color(0xFFE8EEF6)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Videos", "Music").forEachIndexed { index, label ->
                            Surface(
                                onClick = { libraryMode = index },
                                color = if (libraryMode == index) Color(0xFF8DD8FF) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (index == 0) Icons.Default.VideoLibrary else Icons.Default.GraphicEq,
                                        contentDescription = null, tint = if (libraryMode == index) Color(0xFF102033) else secondaryText,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text(label, color = if (libraryMode == index) Color(0xFF102033) else secondaryText, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(5.dp))
                                    Text((if (index == 0) (deviceVideos + recentVideos).distinctBy { it.uri }.size else (deviceAudios + recentAudios).distinctBy { it.uri }.size).toString(),
                                        color = if (libraryMode == index) Color(0xFF102033) else secondaryText, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (mediaItems.isEmpty() && mediaScanInProgress) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(color = Color(0xFF61BFFF), strokeWidth = 3.dp)
                                Text("Finding your media…", color = foreground, fontWeight = FontWeight.SemiBold)
                                Text("Your library will appear as soon as scanning finishes.", color = secondaryText, fontSize = 13.sp)
                            }
                        }
                    } else if (mediaItems.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (libraryMode == 0) Icons.Default.VideoLibrary else Icons.Default.GraphicEq,
                                    contentDescription = null, tint = Color(0xFF61BFFF), modifier = Modifier.size(46.dp))
                                Text(if (libraryMode == 0) "No video history yet" else "No music history yet",
                                    color = foreground, fontWeight = FontWeight.SemiBold)
                                Text(if (libraryMode == 0) "Open a video to see its details here." else "Open an audio file to build your music history.",
                                    color = secondaryText, fontSize = 13.sp)
                                Button(onClick = { if (libraryMode == 0) picker.launch(arrayOf("video/*")) else audioPicker.launch(arrayOf("audio/*")) }) {
                                    Text(if (libraryMode == 0) "Browse videos" else "Browse music")
                                }
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.weight(1f)) {
                            items(mediaItems, key = { row -> row.entry?.uri ?: "group:${row.group}" }) { row ->
                                val entry = row.entry
                                if (entry == null) {
                                    Text(row.group, color = Color(0xFF45B8FF), fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                                        modifier = Modifier.padding(top = 3.dp, bottom = 1.dp))
                                } else {
                                    Surface(color = cardSurface, shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            MediaThumbnail(uri = entry.uri, title = entry.title, isMusic = libraryMode == 1, dark = dark)
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                Text(entry.title, color = foreground, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                                Text(
                                                    when {
                                                        entry.durationMs > 0L && entry.positionMs > 0L ->
                                                            "${((entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) * 100).toInt()}% watched · Resume at ${formatTime(entry.positionMs)}"
                                                        entry.remote -> "Online direct media"
                                                        else -> "Local ${if (libraryMode == 0) "video" else "audio"}"
                                                    },
                                                    color = secondaryText, fontSize = 11.sp, maxLines = 2
                                                )
                                                Text("${if (entry.sizeBytes > 0L) formatBytes(entry.sizeBytes) else "Size unavailable"} · ${if (entry.remote) "Internet" else "On device"}",
                                                    color = secondaryText, fontSize = 10.sp, maxLines = 1)
                                                Text(entry.location.ifBlank { entry.uri }, color = secondaryText, fontSize = 9.sp, maxLines = 1)
                                            }
                                            IconButton(onClick = {
                                                runCatching {
                                                    if (entry.remote) onPlayUrl(entry.uri) else onLocalVideo(Uri.parse(entry.uri))
                                                    isMusicMode = libraryMode == 1
                                                    player?.seekTo(entry.positionMs)
                                                    selected = true
                                                    currentTab = 1
                                                    controlsVisible = true
                                                    playbackError = null
                                                    message = "Resuming ${entry.title}…"
                                                }.onFailure { message = "Couldn't reopen this media file." }
                                            }) {
                                                Icon(Icons.Default.PlayArrow, "Play ${entry.title}", tint = Color(0xFF2EAAFF), modifier = Modifier.size(29.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val dark = darkTheme
                val foreground = if (dark) Color(0xFFF7FAFF) else Color(0xFF101725)
                val secondaryText = if (dark) Color(0xFF9BA9BC) else Color(0xFF68758A)
                val fieldSurface = if (dark) Color(0xFF111A29) else Color.White
                val fluid = rememberInfiniteTransition(label = "uplay-link-fluid")
                val wave by fluid.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 4200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "link-wave"
                )
                Column(
                    modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(0.27f))
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(43.dp).clip(RoundedCornerShape(15.dp))
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
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = (-1.8).sp,
                                color = foreground
                            )
                        }
                        val themeIconRotation by animateFloatAsState(
                            targetValue = if (dark) 180f else 0f,
                            animationSpec = tween(420, easing = FastOutSlowInEasing),
                            label = "theme-sun-moon-rotation"
                        )
                        IconButton(
                            onClick = { onToggleTheme(!darkTheme) },
                            modifier = Modifier.align(Alignment.CenterEnd).size(44.dp)
                                .background(if (dark) Color(0xFF13243A) else Color(0xFFE6F5FF), CircleShape)
                        ) {
                            Icon(
                                if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (dark) "Switch to light theme" else "Switch to dark theme",
                                tint = if (dark) Color(0xFFFFD36B) else Color(0xFF267CB7),
                                modifier = Modifier.rotate(themeIconRotation)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("YOUR PERSONAL MEDIA SPACE", color = if (dark) Color(0xFF8DD8FF) else Color(0xFF267CB7),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
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
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(21.dp))
                                .background(fieldSurface)
                                .drawWithContent {
                                    drawContent()
                                    val waveWidth = size.width * 0.24f
                                    val waveX = wave * (size.width + waveWidth * 2f) - waveWidth
                                    val midY = size.height * 0.5f
                                    val amplitude = 4.dp.toPx()
                                    val path = Path().apply {
                                        moveTo(waveX, midY - amplitude * 0.35f)
                                        cubicTo(
                                            waveX + waveWidth * 0.18f, midY - amplitude * 1.7f,
                                            waveX + waveWidth * 0.30f, midY + amplitude * 1.5f,
                                            waveX + waveWidth * 0.48f, midY
                                        )
                                        cubicTo(
                                            waveX + waveWidth * 0.66f, midY - amplitude * 1.4f,
                                            waveX + waveWidth * 0.82f, midY + amplitude * 1.2f,
                                            waveX + waveWidth, midY - amplitude * 0.25f
                                        )
                                        lineTo(waveX + waveWidth, midY + amplitude * 0.25f)
                                        cubicTo(
                                            waveX + waveWidth * 0.72f, midY + amplitude * 1.6f,
                                            waveX + waveWidth * 0.34f, midY - amplitude * 1.2f,
                                            waveX, midY + amplitude * 0.35f
                                        )
                                        close()
                                    }
                                    drawPath(
                                        path,
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0x008DD8FF),
                                                Color(0x998DD8FF),
                                                Color(0xD9D9F7FF),
                                                Color(0x668DD8FF),
                                                Color(0x008DD8FF)
                                            ),
                                            startX = waveX,
                                            endX = waveX + waveWidth
                                        )
                                    )
                                },
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
                            modifier = Modifier.weight(1f).height(46.dp),
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
                                    val path = parsed?.path.orEmpty().lowercase()
                                    val extension = path.substringAfterLast('/').substringAfterLast('.', "")
                                    val directMedia = extension in setOf(
                                        "mp4", "m4v", "mov", "webm", "mkv", "avi", "3gp",
                                        "m3u8", "mpd", "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac"
                                    )
                                    if (!directMedia) {
                                        webPlaybackUrl = null
                                        selected = true
                                        isMusicMode = false
                                        currentTab = 1
                                        playbackError = null
                                        startUniversalDownload(candidate, playAfterDownload = true)
                                    } else {
                                        webPlaybackUrl = null
                                        onPlayUrl(candidate)
                                        selected = true
                                        currentTab = 1
                                        controlsVisible = true
                                        playbackError = null
                                        val entry = RecentVideo(candidate, Uri.decode(candidate.substringAfterLast('/')).replace('+', ' ').ifBlank { candidate }, 0L, true)
                                        recentVideos.removeAll { it.uri == candidate }
                                        recentVideos.add(0, entry)
                                        while (recentVideos.size > 30) recentVideos.removeAt(recentVideos.lastIndex)
                                        saveRecentVideos(context, recentVideos)
                                        message = "Loading direct media link…"
                                    }
                                } else message = "Enter a valid HTTP(S) video link first."
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (dark) Color(0xFF8DD8FF) else Color(0xFF167DDB))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Play link", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    OutlinedButton(
                        onClick = { startUniversalDownload(url) },
                        enabled = !downloadBusy && url.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (dark) Color(0xFF8DD8FF) else Color(0xFF167DDB))
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (downloadBusy) "Downloading…" else if (downloaderReady) "Download video" else "Prepare downloader")
                    }
                    if (downloadBusy) {
                        LinearProgressIndicator(
                            progress = downloadProgress,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                            color = Color(0xFF8DD8FF),
                            trackColor = if (dark) Color(0xFF233448) else Color(0xFFD8E8F5)
                        )
                        Text(message, color = secondaryText, fontSize = 12.sp, maxLines = 2)
                    }
                    Spacer(Modifier.height(22.dp))
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
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    dark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (dark) Color(0xFF172235) else Color.White,
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (dark) Color(0xFF293A50) else Color(0xFFDCE7F3)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(if (dark) Color(0xFF223750) else Color(0xFFE8F4FF)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color(0xFF39AFFF), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = if (dark) Color.White else Color(0xFF101725), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = if (dark) Color(0xFF9BA9BC) else Color(0xFF68758A), fontSize = 11.sp)
            }
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF39AFFF), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MusicOrbitVisualizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val tickSound = rememberRotaryTickSound(LocalContext.current)
    val motion = rememberInfiniteTransition(label = "uplay-music-orbit")
    val rotation by motion.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "music-orbit-rotation"
    )
    val pulse by motion.animateFloat(
        initialValue = 0.86f, targetValue = if (isPlaying) 1.12f else 0.94f,
        animationSpec = infiniteRepeatable(tween(if (isPlaying) 900 else 1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "music-orbit-pulse"
    )
    val soundDetent = (rotation / 30f).toInt()
    LaunchedEffect(isPlaying, soundDetent) {
        if (isPlaying && soundDetent > 0) tickSound()
    }
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension * 0.22f
        drawCircle(Color(0xFF142941).copy(alpha = 0.8f), base * 1.8f * pulse, center)
        drawCircle(Color(0xFF61C9FF).copy(alpha = 0.18f), base * 2.0f * pulse, center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx()))
        drawCircle(Color(0xFF8DD8FF).copy(alpha = 0.30f), base * 1.58f, center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
        drawCircle(Color(0xFF4BAEFF).copy(alpha = 0.38f), base * 1.22f, center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        for (i in 0 until 72) {
            val angle = Math.toRadians((i * 5.0) + rotation)
            val envelope = (0.38 + 0.62 * kotlin.math.abs(kotlin.math.sin(i * 0.53 + rotation * 0.025))).toFloat()
            val inner = base * (1.42f + envelope * 0.12f)
            val outer = base * (1.58f + envelope * (if (isPlaying) 0.48f else 0.18f)) * pulse
            val start = Offset(center.x + kotlin.math.cos(angle).toFloat() * inner, center.y + kotlin.math.sin(angle).toFloat() * inner)
            val end = Offset(center.x + kotlin.math.cos(angle).toFloat() * outer, center.y + kotlin.math.sin(angle).toFloat() * outer)
            drawLine(
                brush = Brush.linearGradient(listOf(Color(0xFF3BAEFF).copy(alpha = 0.35f), Color(0xFFB8F0FF))),
                start = start, end = end,
                strokeWidth = if (i % 3 == 0) 2.6.dp.toPx() else 1.3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        val orbitAngle = Math.toRadians(rotation * 1.7)
        val orbitRadius = base * 1.08f
        drawCircle(Color(0xFFB8F0FF), 3.8.dp.toPx(),
            Offset(center.x + kotlin.math.cos(orbitAngle).toFloat() * orbitRadius, center.y + kotlin.math.sin(orbitAngle).toFloat() * orbitRadius))
        drawCircle(Color(0xFF3BAEFF), 2.6.dp.toPx(),
            Offset(center.x + kotlin.math.cos(-orbitAngle * 0.72).toFloat() * orbitRadius * 1.15f,
                center.y + kotlin.math.sin(-orbitAngle * 0.72).toFloat() * orbitRadius * 1.15f))
        drawCircle(Color(0xFF8DD8FF).copy(alpha = 0.95f), base * 0.35f * pulse, center)
        drawCircle(Color(0xFF0B1727), base * 0.20f, center)
    }
}

@Composable
private fun CinematicSeekBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val rawProgress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "cinematic-seek-progress"
    )
    val pulse = rememberInfiniteTransition(label = "seekbar-glow")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.20f,
        targetValue = if (isPlaying) 0.72f else 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "seekbar-glow-alpha"
    )
    Canvas(
        modifier = modifier
            .pointerInput(durationMs) {
                detectTapGestures { point ->
                    if (durationMs > 0L && size.width > 0) {
                        onSeek((durationMs * (point.x / size.width).coerceIn(0f, 1f)).toLong())
                    }
                }
            }
            .pointerInput(durationMs) {
                detectDragGestures { change, _ ->
                    change.consume()
                    if (durationMs > 0L && size.width > 0) {
                        onSeek((durationMs * (change.position.x / size.width).coerceIn(0f, 1f)).toLong())
                    }
                }
            }
    ) {
        val centerY = size.height / 2f
        val trackHeight = 5.dp.toPx()
        val thumbRadius = 5.5.dp.toPx()
        val inset = thumbRadius
        val usableWidth = (size.width - inset * 2f).coerceAtLeast(1f)
        val startX = inset
        val endX = size.width - inset
        val progressX = startX + usableWidth * progress
        drawLine(
            color = Color(0xFF253449),
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = trackHeight,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        if (progressX > startX) {
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF4BAEFF), Color(0xFF8BE7FF), Color(0xFFE4FAFF)),
                    startX = startX,
                    endX = endX
                ),
                start = Offset(startX, centerY),
                end = Offset(progressX, centerY),
                strokeWidth = trackHeight,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        drawCircle(
            color = Color(0xFF6DD8FF).copy(alpha = glowAlpha),
            radius = thumbRadius * 2.1f,
            center = Offset(progressX, centerY)
        )
        drawCircle(color = Color(0xFFBCEFFF), radius = thumbRadius, center = Offset(progressX, centerY))
        drawCircle(color = Color(0xFF53BFFF), radius = thumbRadius * 0.42f, center = Offset(progressX, centerY))
    }
}

@Composable
private fun rememberRotaryTickSound(dialContext: Context): () -> Unit {
    val soundPool = remember(dialContext) {
        SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    var soundId by remember(soundPool) { mutableIntStateOf(0) }
    var soundReady by remember(soundPool) { mutableStateOf(false) }
    DisposableEffect(soundPool) {
        soundPool.setOnLoadCompleteListener { _, loadedId, status ->
            if (status == 0 && loadedId == soundId) soundReady = true
        }
        val file = File(dialContext.cacheDir, "uplay_rotary_tick_v3.wav")
        runCatching {
            if (!file.exists() || file.length() < 100L) {
                val sampleRate = 22050
                // A short layered mechanical detent: soft low body + crisp, damped click.
                val sampleCount = (sampleRate * 0.078).toInt()
                val pcmBytes = sampleCount * 2
                val wav = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
                wav.put("RIFF".toByteArray(Charsets.US_ASCII))
                wav.putInt(36 + pcmBytes)
                wav.put("WAVE".toByteArray(Charsets.US_ASCII))
                wav.put("fmt ".toByteArray(Charsets.US_ASCII))
                wav.putInt(16)
                wav.putShort(1)
                wav.putShort(1)
                wav.putInt(sampleRate)
                wav.putInt(sampleRate * 2)
                wav.putShort(2)
                wav.putShort(16)
                wav.put("data".toByteArray(Charsets.US_ASCII))
                wav.putInt(pcmBytes)
                for (i in 0 until sampleCount) {
                    val t = i.toDouble() / sampleRate
                    val envelope = kotlin.math.exp(-t * 43.0)
                    val frequency = 1180.0 - 430.0 * (i.toDouble() / sampleCount)
                    val fundamental = sin(2.0 * PI * frequency * t)
                    val overtone = sin(2.0 * PI * frequency * 2.05 * t) * 0.19
                    val lowBody = sin(2.0 * PI * 155.0 * t) * kotlin.math.exp(-t * 31.0) * 0.27
                    val brightClick = sin(2.0 * PI * 2850.0 * t) * kotlin.math.exp(-t * 175.0) * 0.17
                    val sample = ((fundamental * envelope + overtone * envelope + lowBody + brightClick) * 0.62 * Short.MAX_VALUE)
                        .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    wav.putShort(sample.toShort())
                }
                FileOutputStream(file).use { it.write(wav.array()) }
            }
        }
        soundId = runCatching { soundPool.load(file.absolutePath, 1) }.getOrDefault(0)
        onDispose { soundPool.release() }
    }
    return remember(soundPool, soundId, soundReady) {
        { if (soundReady && soundId != 0) soundPool.play(soundId, 0.92f, 0.92f, 1, 0, 1f) }
    }
}

@Composable
private fun RotaryControlDial(
    open: Boolean,
    onToggle: () -> Unit,
    onAction: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val rotationAnim = remember { Animatable(0f) }
    val rotation = rotationAnim.value
    val dialScope = rememberCoroutineScope()
    val tickSound = rememberRotaryTickSound(LocalContext.current)
    val haptic = LocalHapticFeedback.current
    var actionPage by remember { mutableIntStateOf(0) }
    val actionCatalog = listOf(
        Icons.Default.Forward10 to "Playback speed",
        Icons.Default.FitScreen to "Screen framing",
        Icons.Default.Subtitles to "Subtitles",
        Icons.Default.FolderOpen to "Load subtitle file",
        Icons.Default.GraphicEq to "Audio track",
        Icons.Default.Replay10 to "Toggle repeat",
        Icons.Default.Lock to "Lock controls",
        Icons.Default.PlayArrow to "Play or pause",
        Icons.Default.Replay10 to "Back 10 seconds",
        Icons.Default.Forward10 to "Forward 10 seconds",
        Icons.Default.VolumeUp to "Mute or unmute",
        Icons.Default.Subtitles to "Subtitle tracks",
        Icons.Default.GraphicEq to "Audio tracks",
        Icons.Default.Fullscreen to "Toggle fullscreen",
        Icons.Default.FastRewind to "Playback speed down",
        Icons.Default.FirstPage to "Restart playback",
        Icons.Default.Repeat to "Toggle repeat mode",
        Icons.Default.FullscreenExit to "Toggle fullscreen"
    )
    // Six controls occupy the full rotary ring; clipping shows only the three on
    // the screen-facing half. A complete revolution advances to the next six actions.
    val pageSize = 6
    val pageCount = (actionCatalog.size + pageSize - 1) / pageSize
    val actions = actionCatalog.drop(actionPage * pageSize).take(pageSize)
    fun pageFor(degrees: Float): Int {
        // Count completed turns in either direction. Small reverse gestures never jump pages.
        val page = if (degrees >= 0f) {
            kotlin.math.floor(degrees / 360f).toInt()
        } else {
            kotlin.math.ceil(degrees / 360f).toInt()
        }
        return ((page % pageCount) + pageCount) % pageCount
    }
    val unfold by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "dial-unfold"
    )
    val gearRotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "dial-gear-rotation"
    )

    Box(
        modifier = modifier.pointerInput(open) {
            if (open) {
                var lastAngle = Float.NaN
                var lastAngularDelta = 0f
                var lastPage = pageFor(rotationAnim.value)
                var lastSoundDetent = kotlin.math.floor(rotationAnim.value / 18f).toInt()
                detectDragGestures(
                    onDragStart = { point ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = point.x - center.x
                        val dy = point.y - center.y
                        lastAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        lastAngularDelta = 0f
                        lastPage = pageFor(rotationAnim.value)
                        lastSoundDetent = kotlin.math.floor(rotationAnim.value / 18f).toInt()
                    },
                    onDrag = { change, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = change.position.x - center.x
                        val dy = change.position.y - center.y
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (distance > 34.dp.toPx() && distance < minOf(size.width, size.height) * 0.58f) {
                            val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (!lastAngle.isNaN()) {
                                var delta = angle - lastAngle
                                if (delta > 180f) delta -= 360f
                                if (delta < -180f) delta += 360f
                                if (kotlin.math.abs(delta) < 75f) {
                                    lastAngularDelta = delta
                                    dialScope.launch {
                                        val next = rotationAnim.value + delta
                                        rotationAnim.snapTo(next)
                                        val nextSoundDetent = kotlin.math.floor(next / 18f).toInt()
                                        if (nextSoundDetent != lastSoundDetent) {
                                            lastSoundDetent = nextSoundDetent
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            tickSound()
                                        }
                                        val nextPage = pageFor(next)
                                        if (nextPage != lastPage) {
                                            actionPage = nextPage
                                            lastPage = nextPage
                                        }
                                    }
                                }
                            }
                            lastAngle = angle
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        dialScope.launch {
                            if (kotlin.math.abs(lastAngularDelta) > 0.5f) {
                                runCatching {
                                    rotationAnim.animateDecay(
                                        initialVelocity = lastAngularDelta * 22f,
                                        animationSpec = exponentialDecay(frictionMultiplier = 1.9f),
                                        block = {
                                            val animatedPage = pageFor(value)
                                            if (animatedPage != actionPage) actionPage = animatedPage
                                            val animatedTick = kotlin.math.floor(value / 18f).toInt()
                                            if (animatedTick != lastSoundDetent) {
                                                lastSoundDetent = animatedTick
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                tickSound()
                                            }
                                        }
                                    )
                                }
                            }
                            val detent = (rotationAnim.value / 60f).roundToInt() * 60f
                            rotationAnim.animateTo(
                                detent,
                                spring(dampingRatio = 0.88f, stiffness = 170f),
                                block = { actionPage = pageFor(value) }
                            )
                            actionPage = pageFor(rotationAnim.value)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            tickSound()
                        }
                    }
                )
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.455f
            if (unfold > 0.01f) {
                drawCircle(
                    color = Color(0xFF8DD8FF).copy(alpha = 0.12f * unfold),
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
                )
                for (tick in 0 until 60) {
                    val angle = Math.toRadians((tick * 6.0 + rotation).toDouble())
                    val major = tick % 5 == 0
                    val inner = radius - if (major) 8.dp.toPx() else 3.5.dp.toPx()
                    drawLine(
                        color = Color(0xFF8DD8FF).copy(alpha = (if (major) 0.72f else 0.25f) * unfold),
                        start = Offset(center.x + kotlin.math.cos(angle).toFloat() * inner,
                            center.y + kotlin.math.sin(angle).toFloat() * inner),
                        end = Offset(center.x + kotlin.math.cos(angle).toFloat() * radius,
                            center.y + kotlin.math.sin(angle).toFloat() * radius),
                        strokeWidth = if (major) 1.6.dp.toPx() else 0.8.dp.toPx()
                    )
                }
                drawArc(
                    color = Color(0xFF8DD8FF).copy(alpha = 0.7f * unfold),
                    startAngle = rotation - 90f,
                    sweepAngle = 96f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
        }

        val controlRadius = 96f * unfold
        actions.forEachIndexed { index, item ->
            val degrees = index * 60f + rotation
            val normalizedDegrees = ((degrees % 360f) + 360f) % 360f
            // Only draw controls on the visible left semicircle; the opposite half
            // travels behind the phone edge and re-enters as the dial is turned.
            if (normalizedDegrees >= 90f && normalizedDegrees <= 270f) {
                val angle = Math.toRadians(degrees.toDouble())
                val x = (kotlin.math.cos(angle) * controlRadius).dp
                val y = (kotlin.math.sin(angle) * controlRadius).dp
                RadialControl(
                    icon = item.first,
                    label = item.second,
                    enabled = open,
                    modifier = Modifier.align(Alignment.Center)
                        .offset(x = x, y = y)
                        .graphicsLayer {
                            alpha = unfold
                            val scale = 0.45f + 0.55f * unfold
                            scaleX = scale
                            scaleY = scale
                        },
                    onClick = { onAction(actionPage * pageSize + index) }
                )
            }
        }

        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                tickSound()
                onToggle()
            },
            shape = CircleShape,
            color = if (open) Color(0xFF8DD8FF) else Color(0xE6101928),
            border = androidx.compose.foundation.BorderStroke(
                width = if (open) 1.5.dp else 1.dp,
                color = Color(0xCC8DD8FF)
            ),
            modifier = Modifier.align(Alignment.Center).size(52.dp).graphicsLayer {
                rotationZ = gearRotation
                shadowElevation = 14.dp.toPx()
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = if (open) "Close rotary player controls" else "Open rotary player controls",
                    tint = if (open) Color(0xFF0B1727) else Color(0xFF8DD8FF),
                    modifier = Modifier.size(25.dp)
                )
            }
        }
    }
}

@Composable
private fun RadialControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.78f,
        animationSpec = tween(180),
        label = "dial-control-glow"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) Color(0xF0182B40) else Color(0xEE111A29),
        border = androidx.compose.foundation.BorderStroke(
            width = if (enabled) 1.5.dp else 1.dp,
            color = Color(0xAA8DD8FF).copy(alpha = tint)
        ),
        modifier = modifier.size(44.dp).graphicsLayer {
            shadowElevation = if (enabled) 12.dp.toPx() else 6.dp.toPx()
            shape = CircleShape
            clip = false
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF8DD8FF).copy(alpha = tint),
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun FilmRollIndicator(darkTheme: Boolean, modifier: Modifier = Modifier) {
    val reel = if (darkTheme) Color(0xFFF2FAFF) else Color(0xFF101827)
    val perforation = if (darkTheme) Color(0xFF0B101B) else Color(0xFFF7F9FD)
    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = diameter * 0.43f
        drawCircle(reel, radius = radius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = diameter * 0.16f))
        drawCircle(reel, radius = diameter * 0.14f, center = center)
        val holeRadius = diameter * 0.075f
        val orbit = diameter * 0.27f
        for (index in 0 until 6) {
            val angle = (Math.PI * 2.0 * index / 6.0) - Math.PI / 2.0
            drawCircle(perforation, radius = holeRadius, center = Offset(
                center.x + kotlin.math.cos(angle).toFloat() * orbit,
                center.y + kotlin.math.sin(angle).toFloat() * orbit
            ))
        }
        drawCircle(Color(0xFF8DD8FF).copy(alpha = if (darkTheme) 0.9f else 0.75f), radius = diameter * 0.08f, center = center)
    }
}

private fun extractFirstHttpUrl(input: String): String? {
    val candidate = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
        .find(input)?.value?.trimEnd('.', ',', ';', '!', '?', ')', ']', '}') ?: return null
    return runCatching {
        val uri = Uri.parse(candidate)
        candidate.takeIf {
            (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) &&
                !uri.host.isNullOrBlank()
        }
    }.getOrNull()
}

private fun extractSharedUrl(intent: Intent?): String? {
    if (intent == null || intent.action != Intent.ACTION_SEND) return null
    val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
    return extractFirstHttpUrl(sharedText)
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
            remote = item.optBoolean("remote", false),
            sizeBytes = item.optLong("sizeBytes", 0L).coerceAtLeast(0L),
            location = item.optString("location", uri),
            durationMs = item.optLong("durationMs", 0L).coerceAtLeast(0L),
            folder = item.optString("folder", ""),
            source = item.optString("source", "")
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
                put("sizeBytes", video.sizeBytes)
                put("location", video.location)
                put("durationMs", video.durationMs)
                put("folder", video.folder)
                put("source", video.source)
            })
        }
        context.getSharedPreferences("uplay_library", Context.MODE_PRIVATE)
            .edit().putString(key, array.toString()).apply()
    }
}

private fun scanDeviceMedia(
    context: Context,
    includeVideos: Boolean = true,
    includeAudio: Boolean = true
): Pair<List<RecentVideo>, List<RecentVideo>> {
    val videos = mutableListOf<RecentVideo>()
    val audios = mutableListOf<RecentVideo>()
    val volumes = if (Build.VERSION.SDK_INT >= 29) {
        runCatching { MediaStore.getExternalVolumeNames(context).toList() }.getOrDefault(listOf("external"))
    } else listOf("external")
    for (volume in volumes) {
        val videoCollection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(volume)
            else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val audioCollection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(volume)
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        if (includeVideos) {
            queryMediaCollection(context, videoCollection, false, videos)
            if (Build.VERSION.SDK_INT >= 29) {
                // UPlay downloads use MediaStore.Downloads so they can be saved to Download/UPlay.
                // Include video files from that collection in the in-app library as well.
                val downloadsCollection = MediaStore.Downloads.getContentUri(volume)
                queryMediaCollection(context, downloadsCollection, false, videos, includeDuration = false)
            }
        }
        if (includeAudio) queryMediaCollection(context, audioCollection, true, audios)
    }
    return videos.distinctBy { it.uri } to audios.distinctBy { it.uri }
}

private fun queryMediaCollection(
    context: Context,
    collection: Uri,
    isAudio: Boolean,
    destination: MutableList<RecentVideo>,
    includeDuration: Boolean = true
) {
    val idColumn = MediaStore.MediaColumns._ID
    val nameColumn = MediaStore.MediaColumns.DISPLAY_NAME
    val sizeColumn = MediaStore.MediaColumns.SIZE
    val dateColumn = MediaStore.MediaColumns.DATE_ADDED
    val durationColumn = MediaStore.MediaColumns.DURATION
    val columns = mutableListOf(idColumn, nameColumn, sizeColumn, dateColumn)
    if (includeDuration) columns += durationColumn
    if (Build.VERSION.SDK_INT >= 29) columns += MediaStore.MediaColumns.RELATIVE_PATH
    runCatching {
        context.contentResolver.query(collection, columns.toTypedArray(), null, null, "$dateColumn DESC")?.use { cursor ->
            val idIndex = cursor.getColumnIndex(idColumn)
            val nameIndex = cursor.getColumnIndex(nameColumn)
            val sizeIndex = cursor.getColumnIndex(sizeColumn)
            val durationIndex = cursor.getColumnIndex(durationColumn)
            val pathIndex = if (Build.VERSION.SDK_INT >= 29) cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val title = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: if (isAudio) "Audio" else "Video"
                val path = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty().trim('/') else ""
                val uri = ContentUris.withAppendedId(collection, id).toString()
                destination += RecentVideo(
                    uri = uri,
                    title = title,
                    positionMs = 0L,
                    remote = false,
                    sizeBytes = if (sizeIndex >= 0) cursor.getLong(sizeIndex).coerceAtLeast(0L) else 0L,
                    location = path.ifBlank { uri },
                    durationMs = if (durationIndex >= 0) cursor.getLong(durationIndex).coerceAtLeast(0L) else 0L,
                    folder = path,
                    source = classifyMediaSource(title, path, isAudio)
                )
            }
        }
    }
}

private fun classifyMediaSource(title: String, folder: String, isAudio: Boolean): String {
    val text = "$title $folder".lowercase()
    val knownSources = listOf(
        "instagram" to "Instagram",
        "whatsapp" to "WhatsApp",
        "telegram" to "Telegram",
        "facebook" to "Facebook",
        "youtube" to "YouTube",
        "tiktok" to "TikTok",
        "twitter" to "Twitter / X",
        "x.com" to "Twitter / X",
        "hamster" to "Hamster",
        "snapchat" to "Snapchat"
    )
    return knownSources.firstOrNull { text.contains(it.first) }?.second
        ?: if (isAudio) "Other music" else "Other videos"
}

private fun queryFileSize(context: Context, uri: Uri): Long = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0).coerceAtLeast(0L) else 0L
    } ?: 0L
}.getOrDefault(0L)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "${"%.1f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    bytes >= 1024L * 1024L -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    bytes >= 1024L -> "${"%.0f".format(bytes / 1024.0)} KB"
    else -> "${bytes} B"
}

private val mediaThumbnailCache = object : LruCache<String, Bitmap>(12 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
}

@Composable
private fun MediaThumbnail(uri: String, title: String, isMusic: Boolean, dark: Boolean) {
    val context = LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<Bitmap?>(initialValue = mediaThumbnailCache.get(uri), uri) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                mediaThumbnailCache.get(uri) ?: if (Build.VERSION.SDK_INT >= 29 && uri.startsWith("content://") && !isMusic) {
                    runCatching {
                        context.contentResolver.loadThumbnail(Uri.parse(uri), Size(320, 180), CancellationSignal())
                    }.getOrNull()?.also { loaded -> mediaThumbnailCache.put(uri, loaded) }
                } else null
            }
        }
    }
    Box(
        modifier = Modifier.size(width = 94.dp, height = 72.dp).clip(RoundedCornerShape(13.dp))
            .background(if (dark) Color(0xFF1B2D43) else Color(0xFFE6F3FF)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        } else {
            Icon(if (isMusic) Icons.Default.GraphicEq else Icons.Default.PlayArrow, contentDescription = title,
                tint = Color(0xFF39AFFF), modifier = Modifier.size(if (isMusic) 34.dp else 40.dp))
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
    }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Video"
