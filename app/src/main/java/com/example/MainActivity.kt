package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup

import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val targetUrl = "https://fanzones.site"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(targetUrl = targetUrl)
                }
            }
        }
    }
}

@Composable
fun MainScreen(targetUrl: String) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1600) // Professional splash screen duration
        showSplash = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FanZonesApp(targetUrl = targetUrl)

        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(500))
        ) {
            SplashScreen()
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0F1A),
                        Color(0xFF1A102F),
                        Color(0xFF0A0A0F)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF9C27B0), Color(0xFF00E5FF))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = "Logo",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "FanZones",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your Ultimate Fan Community",
                color = Color(0xFFB0BEC5),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(36.dp))
            CircularProgressIndicator(
                color = Color(0xFF00E5FF),
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FanZonesApp(targetUrl: String) {
    val context = LocalContext.current

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(targetUrl) }
    var currentTitle by remember { mutableStateOf("FanZones") }
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            filePathCallback?.onReceiveValue(uris.toTypedArray())
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    // Network connectivity observer
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOffline = false
            }
            override fun onLost(network: Network) {
                isOffline = true
            }
        }
        
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        isOffline = caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        onDispose {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {}
        }
    }

    BackHandler(enabled = canGoBack) {
        webViewInstance?.goBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = currentTitle.ifEmpty { "FanZones" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "fanzones.site",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { webViewInstance?.reload() },
                            modifier = Modifier.testTag("reload_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload")
                        }

                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "FanZones")
                                    putExtra(Intent.EXTRA_TEXT, currentUrl)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share FanZones"))
                            },
                            modifier = Modifier.testTag("share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }

                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Home / Reset") },
                                onClick = {
                                    showMenu = false
                                    webViewInstance?.loadUrl(targetUrl)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open in Browser") },
                                onClick = {
                                    showMenu = false
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                    context.startActivity(intent)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear App Cache") },
                                onClick = {
                                    showMenu = false
                                    webViewInstance?.clearCache(true)
                                    CookieManager.getInstance().removeAllCookies(null)
                                    Toast.makeText(context, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        },
        bottomBar = {
            // Native feeling bottom navigation dock for quick navigation
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (canGoBack) webViewInstance?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    IconButton(
                        onClick = { if (canGoForward) webViewInstance?.goForward() },
                        enabled = canGoForward
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    FloatingActionButton(
                        onClick = { webViewInstance?.loadUrl(targetUrl) },
                        modifier = Modifier.size(44.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(22.dp))
                    }

                    IconButton(
                        onClick = { webViewInstance?.reload() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Browser",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                            loadsImagesAutomatically = true
                            setSupportMultipleWindows(false)
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                url?.let { currentUrl = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                url?.let { currentUrl = it }
                                canGoBack = canGoBack()
                                canGoForward = canGoForward()
                                view?.title?.let { if (it.isNotEmpty()) currentTitle = it }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    isOffline = true
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    return false
                                } else {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        ctx.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, "Cannot open link", Toast.LENGTH_SHORT).show()
                                    }
                                    return true
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                progress = newProgress / 100f
                                if (newProgress == 100) {
                                    isLoading = false
                                }
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                title?.let { currentTitle = it }
                            }

                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: GeolocationPermissions.Callback?
                            ) {
                                callback?.invoke(origin, true, false)
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                cb: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = cb
                                try {
                                    fileChooserLauncher.launch("*/*")
                                } catch (e: Exception) {
                                    filePathCallback = null
                                    return false
                                }
                                return true
                            }
                        }

                        setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                            try {
                                val request = DownloadManager.Request(Uri.parse(url)).apply {
                                    setMimeType(mimeType)
                                    addRequestHeader("User-Agent", userAgent)
                                    setDescription("Downloading file...")
                                    setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType))
                                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    setDestinationInExternalPublicDir(
                                        Environment.DIRECTORY_DOWNLOADS,
                                        android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                                    )
                                }
                                val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                dm.enqueue(request)
                                Toast.makeText(ctx, "Downloading file...", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        loadUrl(targetUrl)
                        webViewInstance = this
                    }
                },
                update = { view ->
                    webViewInstance = view
                }
            )

            AnimatedVisibility(
                visible = isOffline,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Offline",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "No internet connection. Please check your network.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                webViewInstance?.reload()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
