package com.example.streetvoicetv.ui.player

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.ui.components.ModalFocusTrap
import com.example.streetvoicetv.ui.components.requestFocusWhenReady
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URLEncoder

/** Test tag of the share (QR code) dialog. */
const val SHARE_DIALOG_TAG = "player_share_dialog"

/** Content description / label of the 分享 chip next to the song title. */
const val SHARE_BUTTON_LABEL = "分享"

object SongLinks {
    private const val WEB_BASE = "https://streetvoice.com"

    /**
     * The song's StreetVoice web page: `https://streetvoice.com/{username}/songs/{id}/`
     * (the canonical URL; without the trailing slash the site redirects, with another
     * username it is a 404). The username is one percent-encoded path segment.
     */
    fun webUrl(song: Song): String = webUrl(song.artistUsername, song.id)

    fun webUrl(username: String, songId: Int): String =
        "$WEB_BASE/${encodeSegment(username)}/songs/$songId/"

    private fun encodeSegment(segment: String): String =
        URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
}

object QrCodes {
    /** QR matrix of [text] (error correction M, 2-module quiet zone, one bit per module). */
    fun encode(text: String): BitMatrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
            EncodeHintType.MARGIN to 2,
        ),
    )

    /** [matrix] as a bitmap with one pixel per module (scale it up without filtering). */
    fun toImageBitmap(matrix: BitMatrix): ImageBitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height) { i ->
            if (matrix[i % width, i / width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/**
 * 分享: a QR code of the song's web page with the title, artist and the URL, so it can be
 * opened on a phone. Focus is trapped on 關閉; Back or 關閉 dismisses it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ShareDialog(song: Song, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val url = remember(song.id, song.artistUsername) { SongLinks.webUrl(song) }
    val qr = remember(url) { QrCodes.toImageBitmap(QrCodes.encode(url)) }
    val closeFocus = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .then(ModalFocusTrap),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(620.dp).testTag(SHARE_DIALOG_TAG),
        ) {
            Row(
                modifier = Modifier.padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    bitmap = qr,
                    contentDescription = "QR Code：$url",
                    filterQuality = FilterQuality.None,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("用手機掃描開啟這首歌", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        song.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        song.artistName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onDismiss, modifier = Modifier.focusRequester(closeFocus)) { Text("關閉") }
                }
            }
        }
    }
    LaunchedEffect(Unit) { closeFocus.requestFocusWhenReady() }
}
