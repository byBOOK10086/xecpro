package me.weishu.kernelsu.ui.design.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.component.liquid.InnerShadow
import me.weishu.kernelsu.ui.component.liquid.innerShadow
import me.weishu.kernelsu.ui.component.liquid.lens
import me.weishu.kernelsu.ui.component.liquid.vibrancy
import me.weishu.kernelsu.ui.design.token.Xc
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

/**
 * XEC Fluid Glass · 玻璃表面。
 *
 * 全应用唯一允许绘制"玻璃"的入口。所有容器（栏、卡片、弹层）都必须走这里，
 * 原因是这里一次性管住了三件容易各自出错的事：
 *
 * 1. **圆角**：`shape` 只会取 `Xc.shapes` 的值。本设计里没有直角矩形容器。
 * 2. **折射真的生效**：miuix 的 `lens()` 内部要求 `shape as? CornerBasedShape`，
 *    一旦传 `RectangleShape` 它会静默 `return` —— 过去"液态玻璃"其实只有模糊、
 *    没有折射，根因就在这里。
 * 3. **三档降级**：`lens` 需要 AGSL（API 33+），`textureBlur` 需要 RenderEffect（API 31+），
 *    都没有时退回半透明纯色，保证任何设备上都不出现"没画出来的空白块"。
 *
 * @param backdrop 由 `rememberBlurBackdrop` 产出；为 `null` 表示设备/设置不支持模糊。
 * @param tint 玻璃上覆色，叠在模糊结果之上，决定玻璃"有多深"。
 * @param rim 是否描一圈渐变亮边。没有模糊的设备上，这圈边是唯一的层次来源。
 */
@Composable
fun XGlassSurface(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = Xc.shapes.lg,
    tint: Color = Xc.colors.glassTint,
    blurRadius: Dp = 6.dp,
    refraction: Dp = 24.dp,
    rimColor: Color = Xc.colors.glassRim,
    rim: Boolean = true,
    innerHighlight: Boolean = true,
    glassEnabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val surface = Xc.colors.surface
    val shaderSupported = remember { isRuntimeShaderSupported() }
    val active = glassEnabled && backdrop != null

    val glassModifier = when {
        active && shaderSupported -> Modifier
            .drawBackdrop(
                backdrop = backdrop!!,
                shape = { shape },
                effects = {
                    val refractPx = refraction.toPx()
                    // 折射要在控件边界外取样，padding 小于折射量时边缘会被裁掉，
                    // 表现为"折射只在中段出现、贴边消失"。
                    padding = maxOf(28.dp.toPx(), refractPx)
                    vibrancy()
                    blur(blurRadius.toPx(), blurRadius.toPx())
                    lens(refractionHeight = refractPx, refractionAmount = refractPx)
                },
                onDrawSurface = {
                    drawRect(tint)
                },
            )
            .clipTo(shape)
            .xGlassRim(shape, rimColor, rim)
            .innerShadow(shape = shape) {
                if (innerHighlight) {
                    InnerShadow(
                        radius = 12.dp,
                        color = Color.Black.copy(alpha = 0.10f),
                    )
                } else {
                    null
                }
            }

        // 回退一档：毛玻璃（RenderEffect，API 31-32）
        active -> Modifier
            .textureBlur(
                backdrop = backdrop!!,
                shape = shape,
                blurRadius = 25f,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = surface.copy(alpha = 0.87f)),
                    ),
                ),
            )
            .clipTo(shape)
            .xGlassRim(shape, rimColor, rim)

        // 没有 backdrop（用户关掉了模糊 / 设备不支持 RenderEffect / 还没采样到图层）
        // 时**不能**继续拿半透明的 tint 兜底：tint 是 0.55 Alpha 的深色，
        // 直接 background 画上去会透出窗口底色，整块面板就塌成一块「黑框」——
        // 这正是"弹窗/栏变黑方块"的来源。把 tint 合成到不透明的 surface 上，
        // 色相保留、Alpha 归 1，与 `BlurredBar` 的无毛玻璃分支保持一致。
        else -> {
            val solid = if (tint.alpha >= 1f) tint else tint.compositeOver(surface)
            Modifier
                .background(color = solid, shape = shape)
                .clipTo(shape)
                .xGlassRim(shape, rimColor, rim)
        }
    }

    Box(modifier = modifier.then(glassModifier), content = content)
}

/**
 * 把内容裁进圆角里。
 *
 * 必须排在玻璃绘制**之后**：裁剪切在 `drawBackdrop` 前面会把折射所需的
 * 外扩取样区一起裁掉，折射就又没了。
 */
@Composable
private fun Modifier.clipTo(shape: Shape): Modifier = this.clip(shape)

/**
 * 玻璃边：上亮下暗的 1dp 渐变描边。
 * 比纯色描边贵不了多少，但能立刻把"一块半透明色"变成"一片玻璃"。
 *
 * 公开给各屏的 `Card` 用 —— 它们大多是 miuix `Card`，圆角由 miuix 自己裁，
 * 缺的就是这圈亮边，所以这里只需要一个 `shape`：`.xGlassRim(Xc.shapes.md)`。
 * `rimColor` / `rim` 留默认值正是为了这个单参数用法；需要临时关掉亮边时
 * 才显式传 `rim = false`。
 */
@Composable
internal fun Modifier.xGlassRim(
    shape: Shape,
    rimColor: Color = Xc.colors.glassRim,
    rim: Boolean = true,
): Modifier =
    if (!rim) {
        this
    } else {
        this.border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    rimColor.copy(alpha = 0.55f),
                    rimColor.copy(alpha = 0.10f),
                ),
            ),
            shape = shape,
        )
    }

/**
 * 玻璃卡片：内容区最常用的容器。
 *
 * 与 [XGlassSurface] 的区别只有在语义上——卡片额外给一层极轻的投影，
 * 把"内容块"从"贴底的栏"里区分出来。圆角仍由 `Xc.shapes` 说了算。
 */
@Composable
fun XGlassCard(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = Xc.shapes.md,
    tint: Color = Xc.colors.glassTint,
    glassEnabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    XGlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        tint = tint,
        blurRadius = 8.dp,
        refraction = 20.dp,
        glassEnabled = glassEnabled,
        content = content,
    )
}

/**
 * 玻璃栏：顶栏 / 底栏 / 侧边栏。
 *
 * 栏体默认用最大的一档圆角，这是"每个方形框角要圆滑"在壳层上的落点。
 * 全宽贴边的栏应在调用点传一个只圆某几个角的 `RoundedCornerShape`，
 * 例如顶栏只圆下沿。
 */
@Composable
fun XGlassBar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = Xc.shapes.bar,
    tint: Color = Xc.colors.glassTint,
    glassEnabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    XGlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        tint = tint,
        blurRadius = 10.dp,
        refraction = 28.dp,
        innerHighlight = false,
        glassEnabled = glassEnabled,
        content = content,
    )
}
