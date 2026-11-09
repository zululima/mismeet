package app.mismeet.android.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mismeet.android.R
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Drag and pinch a picture under a circle; the square around the circle becomes the photo,
 * which the app always draws as a circle.
 */
@Composable
fun PhotoCropDialog(bitmap: Bitmap, onDismiss: () -> Unit, onChoose: (Bitmap) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var size by remember { mutableStateOf(IntSize.Zero) }
        var zoom by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        val margin = with(LocalDensity.current) { 24.dp.toPx() }
        val side = (min(size.width, size.height) - 2 * margin).coerceAtLeast(1f)
        // The display scale at zoom 1, where the picture's shorter side just fills the circle.
        val cover = side / min(bitmap.width, bitmap.height)

        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .onSizeChanged { size = it }
                    .pointerInput(bitmap, size) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                            offset = clamp(offset + pan, bitmap, side, cover * zoom)
                        }
                    },
            ) {
                val scale = cover * zoom
                val width = (bitmap.width * scale).roundToInt()
                val height = (bitmap.height * scale).roundToInt()
                drawImage(
                    image,
                    dstOffset = IntOffset((center.x + offset.x - width / 2f).roundToInt(), (center.y + offset.y - height / 2f).roundToInt()),
                    dstSize = IntSize(width, height),
                    filterQuality = FilterQuality.Medium,
                )
                clipPath(Path().apply { addOval(Rect(center, side / 2)) }, ClipOp.Difference) {
                    drawRect(Color.Black.copy(alpha = 0.6f))
                }
                drawCircle(Color.White, radius = side / 2, style = Stroke(2.dp.toPx()))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Color.White) }
                TextButton(onClick = { onChoose(crop(bitmap, side, cover * zoom, offset)) }, enabled = size != IntSize.Zero) {
                    Text(stringResource(R.string.choose), color = Color.White)
                }
            }
        }
    }
}

private const val MAX_ZOOM = 5f

/** Keeps the picture covering the whole circle. */
private fun clamp(offset: Offset, bitmap: Bitmap, side: Float, scale: Float): Offset {
    val maxX = (bitmap.width * scale - side) / 2
    val maxY = (bitmap.height * scale - side) / 2
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

private fun crop(bitmap: Bitmap, side: Float, scale: Float, offset: Offset): Bitmap {
    val cropSide = (side / scale).roundToInt().coerceIn(1, min(bitmap.width, bitmap.height))
    val left = (bitmap.width / 2f - offset.x / scale - cropSide / 2f).roundToInt().coerceIn(0, bitmap.width - cropSide)
    val top = (bitmap.height / 2f - offset.y / scale - cropSide / 2f).roundToInt().coerceIn(0, bitmap.height - cropSide)
    return Bitmap.createBitmap(bitmap, left, top, cropSide, cropSide)
}
