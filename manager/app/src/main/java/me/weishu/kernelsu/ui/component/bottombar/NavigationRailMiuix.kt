package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.design.token.Xc
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NavigationRailMiuix(
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    val fullFeatured = Natives.isFullFeatured()
    if (!fullFeatured) return

    val mainState = LocalMainPagerState.current

    val items = BottomBarDestination.entries.map { destination ->
        Pair(stringResource(destination.label), destination.icon)
    }
    val settingsRepo = remember { SettingsRepositoryImpl() }
    val state = rememberNavigationRailState(
        initialValue = if (settingsRepo.navigationRailExpanded) {
            NavigationRailValue.Expanded
        } else {
            NavigationRailValue.Collapsed
        },
    )
    LaunchedEffect(state.currentValue) {
        settingsRepo.navigationRailExpanded = state.isExpanded
    }

    // miuix 的 NavigationRail 会用 `color` 画一条整高的直角底。这里改成：
    // 外层圆角玻璃面板负责背景，rail 自身不再画底，从而不出现任何直角。
    Box(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .clip(Xc.shapes.xl)
            .background(MiuixTheme.colorScheme.surface),
    ) {
        NavigationRail(
            modifier = Modifier,
            state = state,
            color = Color.Transparent,
            expandContentDescription = stringResource(R.string.nav_rail_expand),
            collapseContentDescription = stringResource(R.string.nav_rail_collapse),
        ) {
            items.forEachIndexed { index, (label, icon) ->
                NavigationRailItem(
                    selected = mainState.selectedPage == index,
                    onClick = {
                        mainState.animateToPage(index)
                    },
                    icon = icon,
                    label = label,
                    badge = navigationBadgeFor(index, navigationBadge),
                )
            }
        }
    }
}
