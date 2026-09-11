package me.weishu.kernelsu.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import me.weishu.kernelsu.ui.component.liquid.InnerShadow
import me.weishu.kernelsu.ui.component.liquid.innerShadow
import me.weishu.kernelsu.ui.component.liquid.lens
import me.weishu.kernelsu.ui.component.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * 全局液态玻璃栏（顶栏/导航栏容器）。
 *
 * 优先使用液态玻璃（lens 折射 + vibrancy 饱和增强 + 内阴影），
 * 仅在 RuntimeShader 不可用（Android 13 / API 33 以下）时回退到毛玻璃
 * textureBlur，两者都不可用时退回纯色。
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = when {
            !blurActive || backdrop == null -> Modifier
            isRuntimeShaderSupported() -> {
                // 液态玻璃：折射透镜 + 饱和度提升 + 轻微高斯 + 内阴影，
                // 形成比毛玻璃更明显的「玻璃折射」层次。
                Modifier
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = {
                            padding = 28.dp.toPx()
                            vibrancy()
                            blur(6.dp.toPx(), 6.dp.toPx())
                            lens(
                                refractionHeight = 24.dp.toPx(),
                                refractionAmount = 24.dp.toPx(),
                            )
                        },
                        onDrawSurface = {
                            drawRect(MiuixTheme.colorScheme.surface.copy(alpha = 0.55f))
                        },
                    )
                    .innerShadow(shape = RectangleShape) {
                        InnerShadow(
                            radius = 12.dp,
                            color = Color.Black.copy(alpha = 0.08f),
                        )
                    }
            }

            else -> {
                // 回退：毛玻璃（RenderEffect，API 31-32）
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    blurRadius = 25f,
                    colors = BlurColors(
                        blendColors = listOf(
                            BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.87f)),
                        ),
                    ),
                )
            }
        },
    ) {
        content()
    }
}
