package me.weishu.kernelsu.ui.screen.module

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.Module
import me.weishu.kernelsu.data.model.ModuleUpdateInfo
import me.weishu.kernelsu.data.repository.isSoftRebootPreferred
import me.weishu.kernelsu.ui.component.ListPopupDefaults
import me.weishu.kernelsu.ui.component.ObserveAsEvents
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.dialog.XDialog
import me.weishu.kernelsu.ui.component.dialog.XDialogTitle
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.dialog.rememberLoadingDialog
import me.weishu.kernelsu.ui.component.miuix.SearchBarFake
import me.weishu.kernelsu.ui.component.miuix.SearchBox
import me.weishu.kernelsu.ui.component.miuix.SearchPager
import me.weishu.kernelsu.ui.design.glass.xGlassRim
import me.weishu.kernelsu.ui.design.liquid.xWaterDropClick
import me.weishu.kernelsu.ui.design.token.Xc
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.getFileName
import me.weishu.kernelsu.ui.util.reboot
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.FloatingActionButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@SuppressLint("StringFormatInvalid", "LocalContextGetResourceValueCall")
@Composable
fun ModulePagerMiuix(
    uiState: ModuleUiState,
    confirmDialogState: ModuleConfirmDialogState?,
    moduleEvent: Flow<ModuleEffect>,
    actions: ModuleActions,
    bottomInnerPadding: Dp,
) {
    val modules = uiState.moduleList
    val searchStatus = uiState.searchStatus

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val enableBlur = LocalEnableBlur.current

    val installPromptWithName = stringResource(R.string.module_install_prompt_with_name, "%s")
    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            when (val request = confirmDialogState?.request) {
                is ModuleConfirmRequest.Uninstall -> {
                    actions.onUninstallModule(request.module)
                }

                is ModuleConfirmRequest.Update -> {
                    actions.onConfirmUpdate(request)
                }

                null -> Unit
            }
        },
        onDismiss = actions.onDismissConfirmRequest,
    )

    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }

    val shortcutState = rememberModuleShortcutState(context)
    val showShortcutDialog = remember { mutableStateOf(false) }

    val pickShortcutIconLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        shortcutState.updateIconUri(uri?.toString())
    }

    LaunchedEffect(confirmDialogState) {
        confirmDialogState?.let {
            confirmDialog.showConfirm(
                title = it.title,
                content = it.content,
                markdown = it.markdown,
                html = it.html,
                confirm = it.confirm,
                dismiss = it.dismiss,
            )
        }
    }

    val scope = rememberCoroutineScope()
    val snackbarJob = remember { mutableStateOf<Job?>(null) }
    ObserveAsEvents(moduleEvent) { event ->
        when (event) {
            is ModuleEffect.Toast -> {
                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }

            is ModuleEffect.SnackBar -> {
                // Cancel the previous reboot snackbar so a new one replaces it instead of queueing
                snackbarJob.value?.cancel()
                snackbarHostState.newestSnackbarData()?.dismiss()
                // Soft reboot keeps the jailbreak and still applies module changes
                val softReboot = isSoftRebootPreferred()
                snackbarJob.value = scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = context.getString(if (softReboot) R.string.reboot_soft else R.string.reboot),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        reboot(if (softReboot) "soft_reboot" else "")
                    }
                }
            }
        }
    }

    fun onModuleAddShortcut(module: Module, type: ShortcutType) {
        shortcutState.bindModule(module)
        shortcutState.selectType(type)
        showShortcutDialog.value = true
    }

    val listState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }

    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface.copy(alpha = 1f)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.module),
                        actions = {
                            Box {
                                val showTopPopup = remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = { showTopPopup.value = true },
                                    holdDownState = showTopPopup.value
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Sort,
                                        tint = colorScheme.onSurface,
                                        contentDescription = null
                                    )
                                }
                                OverlayListPopup(
                                    show = showTopPopup.value,
                                    popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                                    alignment = PopupPositionProvider.Align.TopEnd,
                                    onDismissRequest = {
                                        showTopPopup.value = false
                                    },
                                    content = {
                                        ListPopupColumn {
                                            DropdownImpl(
                                                text = stringResource(R.string.module_sort_action_first),
                                                optionSize = 2,
                                                isSelected = uiState.sortActionFirst,
                                                onSelectedIndexChange = {
                                                    actions.onToggleSortActionFirst()
                                                    showTopPopup.value = false
                                                },
                                                index = 0
                                            )
                                            DropdownImpl(
                                                text = stringResource(R.string.module_sort_enabled_first),
                                                optionSize = 2,
                                                isSelected = uiState.sortEnabledFirst,
                                                onSelectedIndexChange = {
                                                    actions.onToggleSortEnabledFirst()
                                                    showTopPopup.value = false
                                                },
                                                index = 1
                                            )
                                        }
                                    }
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = actions.onOpenRepo,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Download,
                                    tint = colorScheme.onSurface,
                                    contentDescription = null
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        bottomContent = {
                            Box(
                                modifier = Modifier
                                    .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                                    .onGloballyPositioned { coordinates ->
                                        with(density) {
                                            val newOffsetY = coordinates.positionInWindow().y.toDp()
                                            if (searchStatus.offsetY != newOffsetY) {
                                                actions.onSearchStatusChange(searchStatus.copy(offsetY = newOffsetY))
                                            }
                                        }
                                    }
                                    .then(
                                        if (searchStatus.isCollapsed()) {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures {
                                                    actions.onSearchStatusChange(searchStatus.copy(current = SearchStatus.Status.EXPANDING))
                                                }
                                            }
                                        } else Modifier,
                                    ),
                            ) {
                                SearchBarFake(searchStatus.label, dynamicTopPadding)
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (uiState.installButtonVisible) {
                val moduleInstall = stringResource(id = R.string.module_install)
                val confirmTitle = stringResource(R.string.module)
                var zipUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
                val confirmDialog = rememberConfirmDialog(
                    onConfirm = {
                        actions.onOpenFlash(zipUris)
                    }
                )
                val selectZipLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { activityResult ->
                    val uris = mutableListOf<Uri>()
                    if (activityResult.resultCode != RESULT_OK) {
                        return@rememberLauncherForActivityResult
                    }
                    val data = activityResult.data ?: return@rememberLauncherForActivityResult
                    val clipData = data.clipData

                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                        }
                    } else {
                        data.data?.let { uris.add(it) }
                    }

                    if (uris.size == 1) {
                        actions.onOpenFlash(listOf(uris.first()))
                    } else if (uris.size > 1) {
                        // multiple files selected
                        zipUris = uris
                        val moduleNames = uris.mapIndexed { index, uri -> "\n${index + 1}. ${uri.getFileName(context)}" }.joinToString("")
                        val confirmContent = installPromptWithName.format(moduleNames)
                        confirmDialog.showConfirm(
                            title = confirmTitle,
                            content = confirmContent
                        )
                    }
                }
                FloatingActionButton(
                    modifier = Modifier
                        .padding(bottom = bottomInnerPadding + 20.dp, end = 20.dp)
                        .border(0.05.dp, colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                    shadowElevation = 0.dp,
                    onClick = {
                        // Select the zip files to install
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                        selectZipLauncher.launch(intent)
                    },
                    content = {
                        Icon(
                            Icons.Rounded.Add,
                            moduleInstall,
                            modifier = Modifier.size(40.dp),
                            tint = colorScheme.onPrimary
                        )
                    },
                )
            }
        },
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = actions.onSearchStatusChange,
                defaultResult = {},
                searchBarTopPadding = dynamicTopPadding,
            ) {
                val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                ModuleList(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical(),
                    modules = uiState.searchResults,
                    updateInfoMap = uiState.updateInfo,
                    actions = actions,
                    onModuleAddShortcut = ::onModuleAddShortcut,
                    contentPadding = PaddingValues(
                        top = 6.dp,
                        start = 0.dp,
                        end = 0.dp,
                        bottom = maxOf(bottomInnerPadding, imeBottomPadding),
                    ),
                )
            }
        },
        snackbarHost = {
            SnackbarHost(
                state = snackbarHostState,
                modifier = if (uiState.installButtonVisible) {
                    Modifier
                } else {
                    // No FAB slot to stack above: keep the snackbar clear of the main bottom bar.
                    Modifier.padding(bottom = bottomInnerPadding + 20.dp)
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        if (uiState.magiskInstalled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.module_magisk_conflict),
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        val layoutDirection = LocalLayoutDirection.current
        searchStatus.SearchBox {
            val pullToRefreshState = rememberPullToRefreshState()
            val refreshTexts = listOf(
                stringResource(R.string.refresh_pulling),
                stringResource(R.string.refresh_release),
                stringResource(R.string.refresh_refresh),
                stringResource(R.string.refresh_complete),
            )
            val contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = bottomInnerPadding + FloatingActionButtonDefaults.MinHeight + 20.dp + 12.dp,
            )
            PullToRefresh(
                isRefreshing = uiState.isRefreshing,
                pullToRefreshState = pullToRefreshState,
                onRefresh = {
                    actions.onRefresh()
                    refreshTick.intValue++
                },
                refreshTexts = refreshTexts,
                contentPadding = contentPadding,
            ) {
                if (modules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection),
                                bottom = bottomInnerPadding
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // The refresh indicator doubles as the first-load hint;
                        // only announce emptiness once loading has finished.
                        if (uiState.hasLoaded) {
                            Text(
                                stringResource(R.string.module_empty),
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                            )
                        }
                    }
                } else {
                    val latestModules = rememberUpdatedState(modules)
                    val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
                    ScrollToTopOnChange(
                        listState,
                        uiState.sortEnabledFirst,
                        uiState.sortActionFirst,
                        refreshTick.intValue,
                        isBusy = { latestRefreshing.value },
                    ) { latestModules.value }
                    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                        ModuleList(
                            modifier = Modifier
                                .fillMaxHeight()
                                .scrollEndHaptic()
                                .overScrollVertical()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            modules = modules,
                            updateInfoMap = uiState.updateInfo,
                            actions = actions,
                            onModuleAddShortcut = { module, type ->
                                onModuleAddShortcut(module, type)
                            },
                            contentPadding = contentPadding,
                            listState = listState,
                        )
                    }
                }
            }
        }
    }
    ModuleShortcutDialog(
        show = showShortcutDialog.value,
        onDismissRequest = { showShortcutDialog.value = false },
        shortcutState = shortcutState,
        onPickShortcutIcon = { pickShortcutIconLauncher.launch("image/*") },
        onDeleteShortcut = {
            shortcutState.deleteShortcut(context)
            showShortcutDialog.value = false
        },
        onConfirmShortcut = {
            shortcutState.createShortcut(context)
            showShortcutDialog.value = false
        },
    )
}

@Composable
private fun ModuleShortcutDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    shortcutState: ModuleShortcutState,
    onPickShortcutIcon: () -> Unit,
    onDeleteShortcut: () -> Unit,
    onConfirmShortcut: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    fun copyShortcutUrl() {
        val url = shortcutState.buildShortcutUrl() ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("XEC deep link", url))
        Toast.makeText(context, resources.getString(R.string.module_shortcut_scheme_copied), Toast.LENGTH_SHORT).show()
    }

    XDialog(
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        XDialogTitle(text = stringResource(R.string.module_shortcut_title))
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .size(100.dp)
                    .clip(Xc.shapes.xl)
            ) {
                val preview = shortcutState.previewIcon
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        modifier = Modifier.size(100.dp),
                        contentDescription = null,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color.White)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        contentScale = FixedScale(1.5f)
                    )
                }
            }
            Row {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.module_shortcut_icon_pick),
                    onClick = onPickShortcutIcon,
                )
                AnimatedVisibility(
                    visible = shortcutState.iconUri != shortcutState.defaultShortcutIconUri,
                    enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                    exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) {
                    IconButton(
                        onClick = shortcutState::resetIconToDefault,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Undo,
                            contentDescription = null,
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            TextField(
                value = shortcutState.name,
                onValueChange = shortcutState::updateName,
                label = stringResource(id = R.string.module_shortcut_name_label)
            )
            if (shortcutState.hasExistingShortcut) {
                TextButton(
                    text = stringResource(id = R.string.module_shortcut_delete),
                    onClick = onDeleteShortcut,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(
                text = stringResource(id = R.string.module_shortcut_copy_scheme),
                onClick = ::copyShortcutUrl,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = stringResource(id = android.R.string.cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = if (shortcutState.hasExistingShortcut) {
                        stringResource(id = R.string.module_update)
                    } else {
                        stringResource(id = android.R.string.ok)
                    },
                    onClick = onConfirmShortcut,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ModuleList(
    modifier: Modifier = Modifier,
    modules: List<Module>,
    updateInfoMap: Map<String, ModuleUpdateInfo>,
    actions: ModuleActions,
    onModuleAddShortcut: (Module, ShortcutType) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
) {
    val loadingDialog = rememberLoadingDialog()
    val scope = rememberCoroutineScope()
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxHeight(),
        contentPadding = contentPadding,
        overscrollEffect = null,
    ) {
        items(
            items = modules,
            key = { it.id },
            contentType = { "module" }
        ) { module ->
            val currentModuleState = rememberUpdatedState(module)
            val moduleUpdateInfo = updateInfoMap[module.id] ?: ModuleUpdateInfo.Empty
            val content: @Composable () -> Unit = {
                ModuleItem(
                    module = module,
                    updateUrl = moduleUpdateInfo.downloadUrl,
                    onUninstall = {
                        actions.onRequestUninstallConfirmation(currentModuleState.value)
                    },
                    onUndoUninstall = {
                        scope.launch {
                            loadingDialog.withLoading { actions.onUndoUninstallModule(module) }
                        }
                    },
                    onCheckChanged = { _: Boolean ->
                        scope.launch {
                            loadingDialog.withLoading {
                                actions.onToggleModule(module)
                            }
                        }
                    },
                    onUpdate = {
                        scope.launch {
                            loadingDialog.withLoading {
                                actions.onRequestUpdateConfirmation(currentModuleState.value, moduleUpdateInfo)
                            }
                        }
                    },
                    onExecuteAction = {
                        actions.onExecuteModuleAction(currentModuleState.value)
                    },
                    onAddActionShortcut = { type: ShortcutType ->
                        onModuleAddShortcut(currentModuleState.value, type)
                    },
                    onOpenWebUi = {
                        if (module.hasWebUi) {
                            actions.onOpenWebUi(module)
                        }
                    }
                )
            }

            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModuleItem(
    module: Module,
    updateUrl: String,
    onUndoUninstall: () -> Unit,
    onUninstall: () -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onExecuteAction: () -> Unit,
    onAddActionShortcut: (ShortcutType) -> Unit,
    onOpenWebUi: () -> Unit
) {
    val textDecoration = if (module.remove) TextDecoration.LineThrough else null
    val hasDescription = module.description.isNotBlank()
    val hasUpdate = updateUrl.isNotEmpty()
    val actionWidth = 88.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    var offsetX by remember(module.id) { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    fun settleTo(target: Float) {
        scope.launch {
            animate(
                initialValue = offsetX,
                targetValue = target,
                animationSpec = tween(durationMillis = 220),
            ) { value, _ -> offsetX = value }
        }
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .height(IntrinsicSize.Min)
            .clip(Xc.shapes.md)
            .pointerInput(module.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        settleTo(if (offsetX < -actionWidthPx / 2) -actionWidthPx else 0f)
                    },
                    onDragCancel = {
                        settleTo(if (offsetX < -actionWidthPx / 2) -actionWidthPx else 0f)
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(-actionWidthPx, 0f)
                    }
                )
            }
    ) {
        // 右侧红色操作栏（底层，向左滑出后露出）
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidth)
                .fillMaxHeight()
                .background(Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFB71C1C))))
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            SlideActionButton(
                icon = Icons.Rounded.PowerSettingsNew,
                label = stringResource(if (module.enabled) R.string.disable else R.string.enable),
                onClick = {
                    onCheckChanged(!module.enabled)
                    settleTo(0f)
                },
            )
            if (module.hasActionScript) {
                SlideActionButton(
                    icon = Icons.Rounded.PlayArrow,
                    label = stringResource(R.string.action),
                    onClick = {
                        onExecuteAction()
                        settleTo(0f)
                    },
                    onLongClick = { onAddActionShortcut(ShortcutType.Action) },
                )
            }
            if (module.hasWebUi) {
                SlideActionButton(
                    icon = Icons.Rounded.Code,
                    label = stringResource(R.string.open),
                    onClick = {
                        onOpenWebUi()
                        settleTo(0f)
                    },
                    onLongClick = { onAddActionShortcut(ShortcutType.WebUI) },
                )
            }
            SlideActionButton(
                icon = if (module.remove) MiuixIcons.Undo else MiuixIcons.Delete,
                label = stringResource(if (module.remove) R.string.undo else R.string.uninstall),
                onClick = {
                    if (module.remove) onUndoUninstall() else onUninstall()
                    settleTo(0f)
                },
            )
        }

        // 模块内容（顶层，随滑动偏移）
        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .background(colorScheme.surfaceContainer)
                .xGlassRim(Xc.shapes.md)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = module.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    textDecoration = textDecoration,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (module.metamodule) {
                    Text(
                        text = "META",
                        fontSize = 12.sp,
                        color = colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(Xc.shapes.xs)
                            .background(colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = "${stringResource(R.string.module_version)}: ${module.version}",
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariantSummary,
                textDecoration = textDecoration,
            )
            Text(
                text = "${stringResource(R.string.module_author)}: ${module.author}",
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 1.dp),
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariantSummary,
                textDecoration = textDecoration,
            )
            if (hasDescription) {
                Text(
                    text = module.description,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp),
                    color = colorScheme.onSurfaceVariantSummary,
                    textDecoration = textDecoration,
                )
            }
            if (hasUpdate && !module.remove) {
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(CircleShape)
                        .background(colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                        .xWaterDropClick(onClick = onUpdate)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = MiuixIcons.UploadCloud,
                        tint = colorScheme.onTertiaryContainer,
                        contentDescription = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 6.dp),
                        text = stringResource(R.string.module_update),
                        color = colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .xWaterDropClick(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = icon,
            tint = Color.White,
            contentDescription = label,
        )
        Text(
            modifier = Modifier.padding(top = 2.dp),
            text = label,
            color = Color.White,
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}
