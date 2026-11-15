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
import android.view.WindowManager
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val targetUrl = "https://fanzones.site"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
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
        delay(1500)
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
                    imageVector = Icons.Default.PlayArrow,
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
                text = "Streaming Experience",
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
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }

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

    // NO TOP BAR! True 100% Fullscreen Immersive Native App layout
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Minimalist native floating bottom control dock
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
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
                        modifier = Modifier.size(40.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(20.dp))
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
                            contentDescription = "Open in Chrome",
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
                                url?.let { currentUrl = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                url?.let { currentUrl = it }
                                canGoBack = canGoBack()
                                canGoForward = canGoForward()
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
                                val host = request.url.host ?: ""

                                // Open UPI payment intents and payment/checkout/subscription pages externally in Chrome
                                // so UPI apps (Google Pay, PhonePe, Paytm, etc.) open properly.
                                if (url.startsWith("upi://") || 
                                    url.startsWith("intent://") || 
                                    url.contains("pay") || 
                                    url.contains("checkout") || 
                                    url.contains("order") || 
                                    url.contains("cart") ||
                                    url.contains("subscribe") ||
                                    url.contains("gateway")) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        ctx.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Fallback if no external app handles it
                                        return false
                                    }
                                    return true
                                }

                                // Keep fanzones.site navigation inside app
                                if (host.contains("fanzones.site")) {
                                    return false
                                }

                                // Open other external links in Chrome browser
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    ctx.startActivity(intent)
                                    return true
                                } catch (e: Exception) {
                                    return false
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
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
