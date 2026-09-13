package me.weishu.kernelsu.ui.theme

import android.app.Activity
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.dynamiccolor.ColorSpec
import me.weishu.kernelsu.ui.design.liquid.XcIndication
import me.weishu.kernelsu.ui.design.token.Xc
import me.weishu.kernelsu.ui.webui.MonetColorsProvider
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
fun MiuixKernelSUTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)
    val colorStyle = appSettings.paletteStyle
    val colorSpec = appSettings.colorSpec
    // XEC 语义色（由外层的 XcTheme 提供，已按 isDark / isAmoled 解析完毕）。
    val xc = Xc.colors

    val miuixPaletteStyle = try {
        ThemePaletteStyle.valueOf(colorStyle.name)
    } catch (_: Exception) {
        ThemePaletteStyle.TonalSpot
    }

    val miuixColorSpec = if (colorSpec.effectiveFor(colorStyle) == ColorSpec.SpecVersion.SPEC_2025) {
        ThemeColorSpec.Spec2025
    } else {
        ThemeColorSpec.Spec2021
    }

    val resolvedKeyColor: Color? = when {
        appSettings.keyColor != 0 -> Color(appSettings.keyColor)
        appSettings.colorMode.isMonet ->
            if (darkTheme) dynamicDarkColorScheme(context).primary
            else dynamicLightColorScheme(context).primary

        // 默认强调色：不再用 miuix 自带的蓝，改用 XEC 的 teal，
        // 让整套 tonal 容器（primary / secondary / tertiary 各档）都从 teal 派生。
        // 用户显式选过 keyColor 或用了 Monet 时不覆盖。
        else -> xc.accent
    }

    val controller = ThemeController(
        when (appSettings.colorMode) {
            ColorMode.SYSTEM -> ColorSchemeMode.System
            ColorMode.LIGHT -> ColorSchemeMode.Light
            ColorMode.DARK -> ColorSchemeMode.Dark
            ColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
            ColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
            ColorMode.MONET_DARK, ColorMode.DARK_AMOLED -> ColorSchemeMode.MonetDark
        },
        keyColor = resolvedKeyColor,
        isDark = darkTheme,
        paletteStyle = miuixPaletteStyle,
        colorSpec = miuixColorSpec,
    )

    MiuixTheme(
        controller = controller,
        content = {
            val scheme = MiuixTheme.colorScheme
            // XEC 接管调色板。
            //
            // miuix 只会算出"上游库默认皮肤"的那套灰阶，这就是界面一眼看去
            // 像 KernelSU 的根因。这里在 miuix 算完结果之上，用 XEC 语义色
            // 覆写底/容器/文本/描边这几类中性色——强调色系交给上面的
            // keyColor 种子派生，不动 miuix 的色调生成逻辑，所以对比度仍然安全。
            //
            // 「底」是全 App 唯一允许半透明的一层：它下面不是系统窗口，而是
            // MainActivity 铺满全屏的那张背景图（`bg_lkm_active` / `bg_not_patched`）。
            //
            // 这里 alpha 取 0.80 是刻意的取舍：两张背景图的平均亮度都接近纯白
            // （实测 luma 227 / 237），底全透明就变成"白底上的浅色字"，一个字都
            // 读不清；底全不透明又会把背景图彻底盖死，等于把那个功能删了。
            // 0.80 让背景图透出约两成，作为一层很淡的肌理存在，文字对比度仍然安全。
            // 想让背景图更明显，只需要调这一个数字。
            //
            // AMOLED 档位例外：纯黑底是这个档位的全部意义，直接取实色把图盖掉。
            val amoled = appSettings.colorMode.isAmoled
            val colors = if (darkTheme) {
                scheme.copy(
                    // 底：XEC 深墨绿灰，整屏最底层。
                    background = if (amoled) xc.backdrop else xc.backdrop.copy(alpha = 0.80f),
                    onBackground = xc.text,
                    // 容器：各档容器全部来自 XEC 中性色，越高的档位越实。
                    surface = xc.surface.copy(alpha = 0.48f),
                    surfaceVariant = xc.surfaceMuted.copy(alpha = 0.48f),
                    surfaceContainer = xc.surfaceMuted.copy(alpha = 0.58f),
                    surfaceContainerHigh = xc.surfaceMuted.copy(alpha = 0.70f),
                    surfaceContainerHighest = xc.surfaceMuted.copy(alpha = 0.82f),
                    surfaceContainerLow = xc.surface.copy(alpha = 0.36f),
                    surfaceContainerLowest = xc.backdrop,
                    surfaceDim = xc.backdrop,
                    surfaceBright = xc.surfaceMuted,
                    surfaceTint = xc.accent,
                    // 文本：三档灰阶统一到 XEC，避免混入上游默认灰。
                    onSurface = xc.text,
                    onSurfaceSecondary = xc.textSecondary,
                    onSurfaceVariant = xc.textSecondary,
                    onSurfaceVariantSummary = xc.textMuted,
                    onSurfaceVariantActions = xc.textMuted.copy(alpha = 0.82f),
                    onSurfaceContainer = xc.text,
                    onSurfaceContainerHigh = xc.textSecondary,
                    disabledOnSurface = xc.textMuted.copy(alpha = 0.38f),
                    // 描边与分隔：用玻璃描边色，没有模糊的设备上也能分层。
                    outline = xc.glassRim,
                    outlineVariant = xc.glassRim.copy(alpha = 0.60f),
                    dividerLine = xc.glassRim.copy(alpha = 0.55f),
                    // 反色与遮罩。
                    inverseSurface = xc.text,
                    inverseOnSurface = xc.surface,
                    windowDimming = xc.backdropScrim,
                )
            } else {
                // 浅色沿用 miuix 的灰阶（设计文档的取舍：不为浅色单独做一套），
                // 只把容器做轻一点，让层次靠阴影和描边说话。
                scheme.copy(
                    background = scheme.background,
                    surface = scheme.surface.copy(alpha = 0.62f),
                    surfaceVariant = scheme.surfaceVariant.copy(alpha = 0.62f),
                    surfaceContainer = scheme.surfaceContainer.copy(alpha = 0.70f),
                    surfaceContainerHigh = scheme.surfaceContainerHigh.copy(alpha = 0.80f),
                    surfaceContainerHighest = scheme.surfaceContainerHighest.copy(alpha = 0.90f),
                )
            }
            MiuixTheme(
                colors = colors,
                content = {
                    LaunchedEffect(darkTheme) {
                        val window = (context as? Activity)?.window ?: return@LaunchedEffect
                        WindowInsetsControllerCompat(window, window.decorView).apply {
                            isAppearanceLightStatusBars = !darkTheme
                            isAppearanceLightNavigationBars = !darkTheme
                        }
                    }
                    MonetColorsProvider.UpdateCss()
                    // 用 XEC 的圆角高光顶掉 miuix 默认那个方块高光。
                    // 必须在 MiuixTheme 的 content 里下发——miuix 自己会在
                    // 主题内部重新 provide 一次 LocalIndication，在外面覆盖会被冲掉。
                    val indicationColor = Xc.colors.text
                    val pressRadius = Xc.shapes.pressRadius
                    val indication = remember(indicationColor, pressRadius) {
                        XcIndication(color = indicationColor, radius = pressRadius)
                    }
                    CompositionLocalProvider(
                        LocalIndication provides indication,
                        LocalContentColor provides MiuixTheme.colorScheme.onBackground,
                    ) {
                        content()
                    }
                }
            )
        }
    )
}
