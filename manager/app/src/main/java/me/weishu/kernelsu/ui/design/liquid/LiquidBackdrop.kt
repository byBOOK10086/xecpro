// 液态玻璃卡体的采样源：一层缓慢流动的彩色网格渐变。
//
// 架构（为什么需要这一层）：页面级 backdrop（rememberBlurBackdrop）的录制子树
// 包含全部卡片，卡片若采样它就会渲染成环（SIGSEGV，见 XGlassSurface.xGlassBody
// 的历史注释）。本文件给出的采样源是**纯程序化绘制**——不挂在任何真实节点上、
// 不含任何内容子树，因此任何位置的卡片采样它都是安全的兄弟关系。
//
// 卡片玻璃想"有东西可折"，背景就不能是一块平色。这层网格渐变就是折率的来源：
// 缓慢游动的三个高斯色团叠在底色上，玻璃卡片 blur+refraction 之后呈现液态质感。
// AGSL（API 33+）走单 pass 网格着色器；以下设备退化为分层径向渐变笔刷，
// 静态但依旧有色彩起伏。用户关闭模糊时 backdrop 为 null，卡体走既有第三档
// （不透明实色 + 亮边），可见背景退化为静态渐变。

package me.weishu.kernelsu.ui.design.liquid

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

/** 卡片玻璃的采样源。null = 模糊关闭 / 设备不支持 → 卡体走降级档。 */
val LocalLiquidBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/** 网格动画相位（0..1，约 24s 一循环）。给的是 [State]，消费方在绘制期读值，避免逐帧重组。 */
val LocalLiquidTime: androidx.compose.runtime.ProvidableCompositionLocal<State<Float>> =
    compositionLocalOf { mutableStateOf(0.35f) }

/** 网格配色。从主题令牌取色，深浅档给出不同混合策略。 */
class LiquidMeshColors(
    val base: Color,
    val blobA: Color,
    val blobB: Color,
    val blobC: Color,
    val dark: Boolean,
) {
    companion object {
        /** 从 XEC 令牌取色：底色用页面底，色团用品牌紫/青/强调色。 */
        @Composable
        fun ofTheme(): LiquidMeshColors {
            val xc = me.weishu.kernelsu.ui.design.token.Xc.colors
            val neon = me.weishu.kernelsu.ui.design.token.XcNeon.colors
            return LiquidMeshColors(
                base = xc.backdrop,
                blobA = neon.accentPurple,
                blobB = neon.accentCyan,
                blobC = xc.accent,
                dark = xc.isDark,
            )
        }
    }
}

private const val LOOP_NANOS = 24_000_000_000L
private const val FRAME_NANOS = 33_000_000L // ~30fps，背景动画足够顺滑且省电

/**
 * 网格相位。~30fps 节流 + 24s 循环；`enabled=false` 时停在固定相位（静态渐变）。
 * 只在绘制期被读，状态更新不会触发任何重组。
 */
@Composable
fun rememberLiquidTime(enabled: Boolean): State<Float> =
    produceState(initialValue = 0.35f, enabled) {
        if (!enabled) {
            value = 0.35f
            return@produceState
        }
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                if (now - last >= FRAME_NANOS) {
                    value = ((now % LOOP_NANOS).toFloat() / LOOP_NANOS)
                    last = now
                }
            }
        }
    }

/**
 * 卡片玻璃采样源：录制内容 = 纯网格渐变，**没有 drawContent**。
 * 任何卡片（无论在哪个子树里）采样它都不会成环。
 */
@Composable
fun rememberLiquidBackdrop(
    enableBlur: Boolean,
    time: State<Float>,
    colors: LiquidMeshColors,
): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val colorsState = remember { mutableStateOf(colors) }
    colorsState.value = colors
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isRuntimeShaderSupported()) {
            runCatching { RuntimeShader(MESH_SKSL) }.getOrNull()
        } else {
            null
        }
    }
    return rememberLayerBackdrop {
        drawLiquidMesh(time.value, colorsState.value, shader)
    }
}

/** 可见背景层：画同一套网格，与采样源逐像素同源。 */
@Composable
fun LiquidMeshBackground(
    time: State<Float>,
    colors: LiquidMeshColors,
    modifier: Modifier = Modifier,
) {
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isRuntimeShaderSupported()) {
            runCatching { RuntimeShader(MESH_SKSL) }.getOrNull()
        } else {
            null
        }
    }
    Spacer(modifier = modifier.drawBehind { drawLiquidMesh(time.value, colors, shader) })
}

/**
 * 网格绘制：三个高斯色团沿利萨茹轨迹游动。
 * 深色档做加法辉光，浅色档做向色团的柔和混合；无 AGSL 时退化为分层径向渐变。
 */
fun DrawScope.drawLiquidMesh(time: Float, colors: LiquidMeshColors, shader: RuntimeShader?) {
    val t = time * (2.0 * Math.PI).toFloat()

    if (shader != null) {
        shader.setFloatUniform("uRes", size.width, size.height)
        shader.setFloatUniform("uTime", t)
        shader.setFloatUniform("uDark", if (colors.dark) 1f else 0f)
        shader.setFloatUniform("uBase", colors.base.red, colors.base.green, colors.base.blue)
        shader.setFloatUniform("uA", colors.blobA.red, colors.blobA.green, colors.blobA.blue)
        shader.setFloatUniform("uB", colors.blobB.red, colors.blobB.green, colors.blobB.blue)
        shader.setFloatUniform("uC", colors.blobC.red, colors.blobC.green, colors.blobC.blue)
        val paint = Paint()
        paint.asFrameworkPaint().shader = shader
        drawIntoCanvas { it.drawRect(0f, 0f, size.width, size.height, paint) }
        return
    }

    // 无 AGSL：分层径向渐变。位置与着色器同轨迹，观感近似、静态可接受。
    drawRect(
        Brush.radialGradient(
            colors = listOf(colors.base, colors.base),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = maxOf(size.width, size.height),
        ),
    )
    val centers = blobCenters(t, size.width, size.height)
    val blobs = listOf(colors.blobA to 0.30f, colors.blobB to 0.24f, colors.blobC to 0.20f)
    centers.forEachIndexed { index, center ->
        val (color, alpha) = blobs[index]
        val radius = maxOf(size.width, size.height) * 0.55f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = if (colors.dark) alpha else alpha * 0.7f),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

private fun blobCenters(t: Float, w: Float, h: Float): List<Offset> = listOf(
    Offset(
        w * (0.5f + 0.30f * kotlin.math.sin(t * 0.55f + 0.8f)),
        h * (0.5f + 0.26f * kotlin.math.sin(t * 0.42f + 2.1f)),
    ),
    Offset(
        w * (0.5f + 0.34f * kotlin.math.sin(t * 0.33f + 3.6f)),
        h * (0.5f + 0.24f * kotlin.math.sin(t * 0.61f + 0.4f)),
    ),
    Offset(
        w * (0.5f + 0.26f * kotlin.math.sin(t * 0.47f + 5.0f)),
        h * (0.5f + 0.30f * kotlin.math.sin(t * 0.35f + 4.2f)),
    ),
)

// AGSL：单 pass 网格。色团在长宽比较正的空间里走利萨茹轨迹，
// 深色档加法辉光、浅色档向色团混合（mix 权重压低，避免冲掉前景可读性）。
private const val MESH_SKSL = """
uniform float2 uRes;
uniform float uTime;
uniform float uDark;
uniform half3 uBase;
uniform half3 uA;
uniform half3 uB;
uniform half3 uC;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uRes;
    float asp = uRes.x / uRes.y;
    float2 q = float2(uv.x * asp, uv.y);
    float t = uTime;
    float2 cA = float2(0.30 * sin(t * 0.55 + 0.8), 0.26 * sin(t * 0.42 + 2.1));
    float2 cB = float2(0.34 * sin(t * 0.33 + 3.6), 0.24 * sin(t * 0.61 + 0.4));
    float2 cC = float2(0.26 * sin(t * 0.47 + 5.0), 0.30 * sin(t * 0.35 + 4.2));
    float wA = exp(-dot(q - cA, q - cA) / 0.55);
    float wB = exp(-dot(q - cB, q - cB) / 0.75);
    float wC = exp(-dot(q - cC, q - cC) / 0.45);
    half3 rgb = uBase;
    if (uDark > 0.5) {
        rgb += uA * (wA * 0.50) + uB * (wB * 0.38) + uC * (wC * 0.30);
    } else {
        rgb = mix(rgb, uA, wA * 0.28);
        rgb = mix(rgb, uB, wB * 0.20);
        rgb = mix(rgb, uC, wC * 0.16);
    }
    return half4(clamp(rgb, half3(0.0), half3(1.0)), 1.0);
}
"""
