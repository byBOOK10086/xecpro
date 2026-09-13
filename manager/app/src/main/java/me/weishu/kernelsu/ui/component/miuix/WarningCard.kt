package me.weishu.kernelsu.ui.component.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.ui.component.WarningLevel
import me.weishu.kernelsu.ui.design.glass.xGlassRim
import me.weishu.kernelsu.ui.design.token.Xc
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun WarningCard(
    message: String,
    modifier: Modifier = Modifier,
    level: WarningLevel = WarningLevel.Error,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        // 告警卡是全 App 复用最广的容器（首页 7 处、日志页 3 处）。
        // 容器色由 level 决定、不能替换成玻璃表面（会丢掉红/橙的语义色），
        // 所以这里只补那一圈共用的玻璃边：和顶栏、卡片群落在同一个材质里，
        // 同时让卡片在近乎纯白的背景图上有一圈明确的轮廓。
        modifier = modifier.xGlassRim(Xc.shapes.md),
        onClick = { onClick?.invoke() },
        colors = CardDefaults.defaultColors(
            color = level.containerColor(),
            contentColor = level.contentColor(),
        ),
        showIndication = onClick != null,
        pressFeedbackType = PressFeedbackType.Sink
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                fontSize = 14.sp
            )
            action?.invoke()
        }
    }
}

@Composable
private fun WarningLevel.containerColor(): Color = when (this) {
    WarningLevel.Error -> Xc.colors.dangerTint
    WarningLevel.Notice -> Xc.colors.warningTint
}

@Composable
private fun WarningLevel.contentColor(): Color = when (this) {
    WarningLevel.Error -> Xc.colors.onDangerTint
    WarningLevel.Notice -> Xc.colors.onWarningTint
}
