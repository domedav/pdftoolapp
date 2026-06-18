package com.domedav.pdftoolapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ImageCropper {

    fun cropPerspective(
        context: Context,
        sourceUri: Uri,
        normPoints: List<Offset>
    ): Uri? {
        return try {
            // 1. Decode original bitmap bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val origW = options.outWidth.toFloat()
            val origH = options.outHeight.toFloat()
            if (origW <= 0 || origH <= 0) return null

            // 2. Map normalized points to original pixels
            val p0 = normPoints[0]
            val p1 = normPoints[1]
            val p2 = normPoints[2]
            val p3 = normPoints[3]

            val x0 = p0.x * origW
            val y0 = p0.y * origH
            val x1 = p1.x * origW
            val y1 = p1.y * origH
            val x2 = p2.x * origW
            val y2 = p2.y * origH
            val x3 = p3.x * origW
            val y3 = p3.y * origH

            // 3. Compute destination width and height
            val w1 = Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble())
            val w2 = Math.hypot((x2 - x3).toDouble(), (y2 - y3).toDouble())
            val destW = max(w1, w2).toFloat()

            val h1 = Math.hypot((x3 - x0).toDouble(), (y3 - y0).toDouble())
            val h2 = Math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble())
            val destH = max(h1, h2).toFloat()

            if (destW <= 0f || destH <= 0f) return null

            // 4. Load source bitmap with a safe sample size to prevent OOM
            val maxDecodedDim = 3072 // Safe maximum for memory
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDecodedDim || options.outHeight / sampleSize > maxDecodedDim) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val srcBitmap = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // Adjust mapped points based on actual loaded dimensions
            val loadedW = srcBitmap.width.toFloat()
            val loadedH = srcBitmap.height.toFloat()
            val scaleX = loadedW / origW
            val scaleY = loadedH / origH

            val srcPts = floatArrayOf(
                x0 * scaleX, y0 * scaleY,
                x1 * scaleX, y1 * scaleY,
                x2 * scaleX, y2 * scaleY,
                x3 * scaleX, y3 * scaleY
            )

            val dstPts = floatArrayOf(
                0f, 0f,
                destW * scaleX, 0f,
                destW * scaleX, destH * scaleY,
                0f, destH * scaleY
            )

            val finalDestW = (destW * scaleX).toInt().coerceAtLeast(1)
            val finalDestH = (destH * scaleY).toInt().coerceAtLeast(1)

            // 5. Create destination bitmap and apply warp
            val destBitmap = Bitmap.createBitmap(finalDestW, finalDestH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(destBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val matrix = android.graphics.Matrix()
            val polySuccess = matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)
            if (!polySuccess) {
                srcBitmap.recycle()
                destBitmap.recycle()
                return null
            }

            canvas.drawBitmap(srcBitmap, matrix, paint)
            srcBitmap.recycle()

            // 6. Save warped bitmap to a new cached file
            val outFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                destBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
            destBitmap.recycle()

            Uri.fromFile(outFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
