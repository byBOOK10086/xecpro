package me.weishu.kernelsu.ui.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.design.glass.XGlassDialog
import me.weishu.kernelsu.ui.design.token.Xc
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField

/**
 * 下载远程 boot 分区镜像的输入对话框。
 *
 * 这个对话框**没有**登记到 [XDialogHost]，而是自己画一层 [XGlassDialog]：
 * 原因有两个——它是受控的（`show` 由调用方持有），输入框的内容也只是这份局部状态，
 * 登记到宿主反而要把这些状态来回搬。代价是它必须自己从 [LocalXDialogBackdrop]
 * 取根层 backdrop，才能采到玻璃的模糊源。
 */
@Composable
fun DownloadDialog(
    show: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    XGlassDialog(
        show = show,
        onDismissRequest = onDismiss,
        backdrop = LocalXDialogBackdrop.current,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.download_dialog_title),
            color = Xc.colors.text,
            fontWeight = FontWeight.SemiBold,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.download_dialog_msg),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(android.R.string.ok),
                    enabled = isValidUrl(url.trim()),
                    onClick = { onConfirm(url.trim()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

private fun isValidUrl(url: String): Boolean {
    if (url.isEmpty()) return false
    val uri = url.toUri()
    return uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrEmpty() &&
        !uri.path.isNullOrEmpty()
}
