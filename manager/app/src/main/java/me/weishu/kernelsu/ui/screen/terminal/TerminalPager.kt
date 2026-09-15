package me.weishu.kernelsu.ui.screen.terminal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.design.token.Xc
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** Auto_Trigger 模块自带的网页管理界面。 */
private const val TERMINAL_URL = "http://127.0.0.1:2222"

/**
 * 终端页：打开即自动加载模块的网页管理界面（`http://127.0.0.1:2222`）。
 *
 * 这里刻意不复用 [me.weishu.kernelsu.ui.webui.WebUIActivity] 那一套：那套走
 * `WebViewAssetLoader` + `SuFilePathHandler`，要求模块有 `webroot` 目录并且页面
 * 通过 `window.ksu` 桥调用管理器；Auto_Trigger 两者都没有，它自己常驻一个 HTTP
 * 服务（`service.sh` 里 `autotrigger --port 2222`），所以这里只需要一个裸 WebView。
 *
 * `127.0.0.1` 的明文流量已经由 `network_security_config.xml` 放行，无需额外配置。
 */
@Composable
fun TerminalPager(
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface.copy(alpha = 1f)

    // WebView 只能建在组合之外，所以用一个可观察引用把它交给下面的 effect。
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorUrl by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    // 自增即触发一次重载（重试按钮 / 顶栏刷新）。
    var reloadToken by remember { mutableIntStateOf(0) }

    // 打开终端页（成为当前页）时自动加载一次；离开后再回来会重新加载。
    LaunchedEffect(webView, isCurrentPage, reloadToken) {
        val view = webView ?: return@LaunchedEffect
        if (!isCurrentPage) return@LaunchedEffect
        isLoading = true
        errorUrl = null
        view.loadUrl(TERMINAL_URL)
    }

    // 先走网页自己的历史，历史到底了再交还给 Pager（回首页）。
    // 终端页在非当前页时也可能被 Pager 预组合，所以必须再判一次 isCurrentPage，
    // 否则会隔着好几页把返回手势吃掉。
    BackHandler(enabled = isCurrentPage && canGoBack) {
        webView?.goBack()
    }

    DisposableEffect(lifecycleOwner, webView) {
        val view = webView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val target = view ?: return@onDispose
            // WebView 不会跟着 Compose 一起回收，必须自己销毁，否则每进一次终端页
            // 就多泄漏一个 WebView（含它自己的渲染进程）。
            runCatching {
                (target.parent as? ViewGroup)?.removeView(target)
                target.stopLoading()
                target.destroy()
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.terminal),
                    actions = {
                        IconButton(onClick = { reloadToken++ }) {
                            MiuixIcon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 网页是被整块裁剪的，不像列表那样能靠尾部留白把内容顶上来，
                // 所以直接把可视区域让出底栏高度，免得底部控件永远压在玻璃条下面。
                .padding(bottom = bottomInnerPadding),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    createTerminalWebView(
                        context = ctx,
                        onPageStarted = { isLoading = true },
                        onPageFinished = { isLoading = false },
                        onMainFrameError = { failedUrl ->
                            isLoading = false
                            errorUrl = failedUrl
                        },
                        onHistoryChanged = { canGoBack = it },
                    ).also { webView = it }
                },
                update = { view ->
                    view.requestLayout()
                },
            )

            val failure = errorUrl
            when {
                failure != null -> TerminalErrorState(
                    url = failure,
                    onRetry = { reloadToken++ },
                )

                isLoading -> TerminalLoadingState()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createTerminalWebView(
    context: Context,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onMainFrameError: (String) -> Unit,
    onHistoryChanged: (Boolean) -> Unit,
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // 本地 HTTP 服务，不需要读本地文件。
            allowFileAccess = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                onPageStarted()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                // 出错后系统会渲染自己的错误页并回调到这里，所以这里只收 loading，
                // 不能顺手清掉 errorUrl，否则错误态会被自己的错误页覆盖掉。
                onPageFinished()
                onHistoryChanged(view.canGoBack())
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                // 只认主文档失败；子资源（图标、接口轮询）失败不影响页面可用。
                if (!request.isForMainFrame) return
                onMainFrameError(request.url?.toString() ?: TERMINAL_URL)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                onHistoryChanged(view.canGoBack())
            }
        }
    }
}

@Composable
private fun TerminalLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Xc.colors.surface.copy(alpha = 1f)),
        contentAlignment = Alignment.Center,
    ) {
        InfiniteProgressIndicator()
    }
}

@Composable
private fun TerminalErrorState(
    url: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Xc.colors.surface.copy(alpha = 1f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.terminal_unavailable, url),
                color = Xc.colors.text,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.terminal_hint),
                color = Xc.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRetry,
                text = stringResource(R.string.network_retry),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
