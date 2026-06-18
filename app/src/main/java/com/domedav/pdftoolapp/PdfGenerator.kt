package com.domedav.pdftoolapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

object PdfGenerator {

    // A4 méret (PostScript pontokban: 595x842). 
    // Nagyobb felbontáshoz szorozzuk fel (pl. 2x), hogy éles legyen.
    // 595 * 2 = 1190, 842 * 2 = 1684
    private const val PAGE_WIDTH = 1190
    private const val PAGE_HEIGHT = 1684
    private const val MAX_IMAGE_DIMENSION = 2048

    fun generatePdf(context: Context, imageUris: List<Uri>, qualityIndex: Int): File {
        val pdfFile = createPdfFile(context)
        val pdfDocument = PdfDocument()
        val paint = Paint().apply { isFilterBitmap = true } // Szebb skálázás

        try {
            for ((index, uri) in imageUris.withIndex()) {
                try {
                    val maxDimension = Consts.QUALITY_VALUES.getOrNull(qualityIndex) ?: MAX_IMAGE_DIMENSION
                    val bitmap = getScaledBitmapFromUri(context, uri, maxDimension) ?: continue
                    
                    // Oldal létrehozása fix A4 méretben
                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas: Canvas = page.canvas

                    // 1. Fehér háttér (hogy ne legyen fekete a széle)
                    canvas.drawColor(Color.WHITE)

                    // 2. Arányos méretezés és középre igazítás kiszámítása
                    val destRect = calculateAspectFitRect(
                        bitmap.width.toFloat(), 
                        bitmap.height.toFloat(), 
                        PAGE_WIDTH.toFloat(), 
                        PAGE_HEIGHT.toFloat()
                    )

                    // 3. Kép kirajzolása a számolt helyre
                    canvas.drawBitmap(bitmap, null, destRect, paint)
                    
                    pdfDocument.finishPage(page)
                    if (!bitmap.isRecycled) bitmap.recycle()
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            FileOutputStream(pdfFile).use { fos -> pdfDocument.writeTo(fos) }
        } catch (e: Exception) {
            throw e
        } finally {
            pdfDocument.close()
        }
        
        return pdfFile
    }

    // Segédfüggvény: Kiszámolja, hova kell tenni a képet, hogy ne nyúljon meg
    private fun calculateAspectFitRect(imgW: Float, imgH: Float, pageW: Float, pageH: Float): RectF {
        val scale = min(pageW / imgW, pageH / imgH) // A kisebb arányhoz igazítunk
        val scaledW = imgW * scale
        val scaledH = imgH * scale

        // Középre igazítás
        val left = (pageW - scaledW) / 2f
        val top = (pageH - scaledH) / 2f

        // Opcionális: Hagyjunk egy kis margót (pl. 5%)
        val marginScale = 0.95f
        val finalW = scaledW * marginScale
        val finalH = scaledH * marginScale
        val finalLeft = (pageW - finalW) / 2f
        val finalTop = (pageH - finalH) / 2f

        return RectF(finalLeft, finalTop, finalLeft + finalW, finalTop + finalH)
    }

    private fun getScaledBitmapFromUri(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
            }
            var inSampleSize = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }
            val finalOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = inSampleSize
            }
            val decoded = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, finalOptions)
            } ?: return null

            // Scale precisely if it exceeds maxDimension
            val width = decoded.width
            val height = decoded.height
            val maxDim = maxOf(width, height)
            if (maxDim > maxDimension) {
                val scale = maxDimension.toFloat() / maxDim
                val targetW = (width * scale).toInt().coerceAtLeast(1)
                val targetH = (height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                if (scaled != decoded) {
                    decoded.recycle()
                }
                scaled
            } else {
                decoded
            }
        } catch (e: Exception) { null }
    }

    // PDF oldalainak renderelése Bitmap listába a preview-hoz
    fun renderPages(context: Context, file: File): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val renderer = PdfRenderer(pfd)
                // Limitáljuk a preview oldalakat, hogy ne fogyjon el a RAM (pl. max 10 oldal)
                val count = min(renderer.pageCount, 10) 
                
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        // Preview felbontás (nem kell teljes minőség)
                        val width = context.resources.displayMetrics.widthPixels
                        val height = (width * 1.414).toInt() // A4 arány
                        
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bitmap)
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return bitmaps
    }
    
    fun renderPreviewPages(context: Context, file: File): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val renderer = PdfRenderer(pfd)
                // Max 8 oldalt renderelünk előnézetnek a memória védelme miatt
                // Ha több kell, akkor LazyColumn-ban kellene aszinkron tölteni, de ez így stabil lesz.
                val count = min(renderer.pageCount, 8) 
                
                // Képernyő szélesség lekérése (kb. 1080px szokott lenni)
                val screenWidth = context.resources.displayMetrics.widthPixels
                
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        // Kiszámoljuk a magasságot az arányok alapján (Aspect Ratio)
                        val height = (screenWidth * (page.height.toFloat() / page.width.toFloat())).toInt()
                        
                        val bitmap = Bitmap.createBitmap(screenWidth, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bitmap)
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return bitmaps
    }

    // Extension a PdfRenderer.Page-hez
    private inline fun PdfRenderer.Page.use(block: (PdfRenderer.Page) -> Unit) {
        try { block(this) } finally { this.close() }
    }

    private fun createPdfFile(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "PDF_$timestamp.pdf"
        val storageDir = context.getExternalFilesDir(null)
        if (storageDir?.exists() == false) storageDir.mkdirs()
        return File(storageDir, fileName)
    }
    
    fun cleanup(context: Context, keepFile: File? = null) {
         try {
            context.getExternalFilesDir(null)?.listFiles { file -> 
                file.extension.lowercase() == "pdf" && (keepFile == null || file.absolutePath != keepFile.absolutePath) 
            }?.forEach { it.delete() }
        } catch (e: Exception) { }
    }
}