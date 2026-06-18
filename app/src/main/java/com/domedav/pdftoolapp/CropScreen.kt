package com.domedav.pdftoolapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.domedav.pdftoolapp.util.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    imageUri: Uri,
    onCancel: () -> Unit,
    onDone: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isCropping by remember { mutableStateOf(false) }

    // Normalized points (relative to image bounds): Top-Left, Top-Right, Bottom-Right, Bottom-Left
    var points by remember {
        mutableStateOf(
            listOf(
                Offset(0f, 0f),     // Top-Left
                Offset(1f, 0f),     // Top-Right
                Offset(1f, 1f),     // Bottom-Right
                Offset(0f, 1f)      // Bottom-Left
            )
        )
    }

    // Load scaled bitmap for display
    LaunchedEffect(imageUri) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                val maxDim = 1080
                var sampleSize = 1
                while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                bitmap = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Kép vágása",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel, enabled = !isCropping) {
                        Icon(AppIcons.Close(), contentDescription = "Mégse", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (bitmap != null && !isCropping) {
                                isCropping = true
                                scope.launch {
                                    val cropped = withContext(Dispatchers.IO) {
                                        ImageCropper.cropPerspective(context, imageUri, points)
                                    }
                                    if (cropped != null) {
                                        onDone(cropped)
                                    } else {
                                        isCropping = false
                                    }
                                }
                            }
                        },
                        enabled = bitmap != null && !isCropping
                    ) {
                        Icon(
                            AppIcons.Check(),
                            contentDescription = "Kész",
                            tint = if (bitmap != null && !isCropping) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Húzd a 4 sarkot a vágáshoz és perspektíva javításhoz!",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        points = listOf(
                            Offset(0f, 0f),
                            Offset(1f, 0f),
                            Offset(1f, 1f),
                            Offset(0f, 1f)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    enabled = !isCropping
                ) {
                    Text("Visszaállítás", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading || bitmap == null) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                val bmp = bitmap!!
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    val density = LocalDensity.current
                    val containerWidthPx = with(density) { maxWidth.toPx() }
                    val containerHeightPx = with(density) { maxHeight.toPx() }

                    val bitmapRatio = bmp.width.toFloat() / bmp.height
                    val containerRatio = containerWidthPx / containerHeightPx

                    val drawWidth: Float
                    val drawHeight: Float
                    if (bitmapRatio > containerRatio) {
                        drawWidth = containerWidthPx
                        drawHeight = containerWidthPx / bitmapRatio
                    } else {
                        drawWidth = containerHeightPx * bitmapRatio
                        drawHeight = containerHeightPx
                    }

                    val drawLeft = (containerWidthPx - drawWidth) / 2f
                    val drawTop = (containerHeightPx - drawHeight) / 2f

                    var draggedIndex by remember { mutableStateOf(-1) }
                    val screenPoints = points.map { Offset(drawLeft + it.x * drawWidth, drawTop + it.y * drawHeight) }
                    val handleRadiusPx = with(density) { 24.dp.toPx() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(screenPoints) {
                                detectDragGestures(
                                    onStart = { offset ->
                                        val dists = screenPoints.map { (it - offset).getDistance() }
                                        val minDist = dists.minOrNull() ?: Float.MAX_VALUE
                                        val minIndex = dists.indexOf(minDist)
                                        if (minDist < handleRadiusPx * 1.5f) {
                                            draggedIndex = minIndex
                                        } else {
                                            draggedIndex = -1
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (draggedIndex != -1) {
                                            change.consume()
                                            val currentScreenPos = screenPoints[draggedIndex] + dragAmount
                                            // Clamp drag positions strictly inside the image bounds
                                            val clampedX = currentScreenPos.x.coerceIn(drawLeft, drawLeft + drawWidth)
                                            val clampedY = currentScreenPos.y.coerceIn(drawTop, drawTop + drawHeight)
                                            
                                            val normX = (clampedX - drawLeft) / drawWidth
                                            val normY = (clampedY - drawTop) / drawHeight
                                            
                                            points = points.toMutableList().apply {
                                                this[draggedIndex] = Offset(normX, normY)
                                            }
                                        }
                                    },
                                    onEnd = { draggedIndex = -1 },
                                    onCancel = { draggedIndex = -1 }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // 1. Draw Image centered
                            drawImageRect(
                                image = bmp.asImageBitmap(),
                                dstOffset = IntOffset(drawLeft.toInt(), drawTop.toInt()),
                                dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
                            )

                            // 2. Build crop path
                            val path = Path().apply {
                                moveTo(screenPoints[0].x, screenPoints[0].y)
                                lineTo(screenPoints[1].x, screenPoints[1].y)
                                lineTo(screenPoints[2].x, screenPoints[2].y)
                                lineTo(screenPoints[3].x, screenPoints[3].y)
                                close()
                            }

                            // 3. Draw semi-transparent dimming outside the crop path
                            clipPath(path, clipOp = ClipOp.Difference) {
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    topLeft = Offset(drawLeft, drawTop),
                                    size = Size(drawWidth, drawHeight)
                                )
                            }

                            // 4. Draw quadrilateral connection lines
                            drawPath(
                                path = path,
                                color = Color(0xFF81D4FA), // Nice bright accent color
                                style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round)
                            )

                            // 5. Draw interactive corner handles
                            screenPoints.forEachIndexed { index, point ->
                                val isDragging = index == draggedIndex
                                val radius = if (isDragging) 18.dp.toPx() else 14.dp.toPx()
                                val innerRadius = if (isDragging) 9.dp.toPx() else 7.dp.toPx()

                                drawCircle(
                                    color = Color.White,
                                    radius = radius,
                                    center = point
                                )
                                drawCircle(
                                    color = Color(0xFF0288D1), // Bright blue
                                    radius = innerRadius,
                                    center = point
                                )
                            }
                        }

                        if (isCropping) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
