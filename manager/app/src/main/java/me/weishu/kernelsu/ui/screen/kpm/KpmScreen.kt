package me.weishu.kernelsu.ui.screen.kpm

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.ObserveAsEvents
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SnackBarHost as MaterialSnackBarHost
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.flash.FlashIt
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import me.weishu.kernelsu.ui.viewmodel.KpmViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun KpmPager(
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val uiMode = LocalUiMode.current
    val viewModel = viewModel<KpmViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val materialSnackbarHostState = remember { SnackbarHostState() }
    val miuixSnackbarHostState = remember { MiuixSnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            viewModel.load()
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            when (uiMode) {
                UiMode.Material -> materialSnackbarHostState.showSnackbar(message)
                UiMode.Miuix -> miuixSnackbarHostState.showSnackbar(message)
            }
        }
    }

    ObserveAsEvents(viewModel.kpmEvent) { event ->
        when (event) {
            is KpmEffect.SnackBar -> showMessage(event.message)
        }
    }

    val pickKpmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let(viewModel::flash)
    }

    val navigator = LocalNavigator.current
    val onEmbed = {
        navigator.push(Route.Flash(FlashIt.FlashBootKpmEmbed))
    }

    when (uiMode) {
        UiMode.Miuix -> KpmPagerMiuix(
            uiState = uiState,
            snackbarHostState = miuixSnackbarHostState,
            onFlash = { pickKpmLauncher.launch("*/*") },
            onEmbed = onEmbed,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> KpmPagerMaterial(
            uiState = uiState,
            snackbarHostState = materialSnackbarHostState,
            onFlash = { pickKpmLauncher.launch("*/*") },
            onEmbed = onEmbed,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}

@Composable
private fun KpmPagerMiuix(
    uiState: KpmUiState,
    snackbarHostState: MiuixSnackbarHostState,
    onFlash: () -> Unit,
    onEmbed: () -> Unit,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.kpm),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        snackbarHost = {
            SnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomInnerPadding + 20.dp),
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            overscrollEffect = null,
        ) {
            item {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KpmStatusCardMiuix(uiState)
                    KpmModuleListMiuix(uiState.modules)
                    if (!uiState.active) {
                        Card(onClick = onEmbed) {
                            BasicComponent(
                                title = stringResource(R.string.kpm_embed),
                                summary = stringResource(R.string.kpm_embed_hint),
                                startAction = {
                                    MiuixIcon(
                                        imageVector = Icons.Rounded.Memory,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                    )
                                },
                            )
                        }
                    }
                    Card(onClick = onFlash) {
                        BasicComponent(
                            title = stringResource(R.string.kpm_flash),
                            startAction = {
                                MiuixIcon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                )
                            },
                        )
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun KpmStatusCardMiuix(uiState: KpmUiState) {
    val active = uiState.active
    val title = if (active) {
        stringResource(R.string.kpm_activated)
    } else {
        stringResource(R.string.kpm_not_activated)
    }
    val summary = if (active) {
        stringResource(R.string.kpm_version, uiState.version)
    } else {
        stringResource(R.string.kpm_not_activated_hint)
    }

    Card {
        BasicComponent(
            title = title,
            summary = summary,
            startAction = {
                MiuixIcon(
                    imageVector = if (active) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                    contentDescription = title,
                    tint = if (active) Color(0xFF36D167) else colorScheme.onSurface,
                )
            },
        )
    }
}

@Composable
private fun KpmModuleListMiuix(modules: List<String>) {
    Card {
        Column {
            if (modules.isEmpty()) {
                BasicComponent(
                    title = stringResource(R.string.kpm_empty),
                    startAction = {
                        MiuixIcon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = colorScheme.onSurface,
                        )
                    },
                )
            } else {
                modules.forEach { module ->
                    BasicComponent(
                        title = module,
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KpmPagerMaterial(
    uiState: KpmUiState,
    snackbarHostState: SnackbarHostState,
    onFlash: () -> Unit,
    onEmbed: () -> Unit,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.kpm)) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = {
            MaterialSnackBarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomInnerPadding),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            KpmStatusCardMaterial(uiState)
            KpmModuleListMaterial(uiState.modules)
            if (!uiState.active) {
                Button(
                    onClick = onEmbed,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.kpm_embed))
                }
            }
            Button(
                onClick = onFlash,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.kpm_flash))
            }
            Spacer(Modifier.height(bottomInnerPadding))
        }
    }
}

@Composable
private fun KpmStatusCardMaterial(uiState: KpmUiState) {
    val active = uiState.active
    val containerColor = if (active) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = MaterialTheme.colorScheme.contentColorFor(containerColor)
    val title = if (active) {
        stringResource(R.string.kpm_activated)
    } else {
        stringResource(R.string.kpm_not_activated)
    }
    val summary = if (active) {
        stringResource(R.string.kpm_version, uiState.version)
    } else {
        stringResource(R.string.kpm_not_activated_hint)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = if (active) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                    contentDescription = title,
                    tint = contentColor,
                )
            },
            headlineContent = {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            },
            supportingContent = {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f),
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = contentColor,
                leadingContentColor = contentColor,
                supportingContentColor = contentColor.copy(alpha = 0.7f),
            ),
        )
    }
}

@Composable
private fun KpmModuleListMaterial(modules: List<String>) {
    if (modules.isEmpty()) {
        SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.kpm_empty)) },
                    leadingContent = {
                        Icon(Icons.Rounded.Memory, contentDescription = null)
                    },
                )
            }
        }
    } else {
        SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
            modules.forEach { module ->
                item {
                    SegmentedListItem(
                        headlineContent = { Text(module) },
                        leadingContent = {
                            Icon(Icons.Rounded.Memory, contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}