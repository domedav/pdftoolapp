package com.domedav.pdftoolapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CropScreen(
    imageUri: Uri,
    initialPoints: List<Offset>?,
    onCancel: () -> Unit,
    onDone: (Uri, List<Offset>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isCropping by remember { mutableStateOf(false) }

    // Intercept physical back press to safely exit crop page
    BackHandler(enabled = !isCropping) {
        onCancel()
    }

    // Normalized points (relative to image bounds): Top-Left, Top-Right, Bottom-Right, Bottom-Left
    var points by remember {
        mutableStateOf(
            initialPoints ?: listOf(
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
                        text = stringResource(R.string.crop_title),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.crop_instructions),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                
                // Side-by-side 2 buttons in a unified track, copying SuccessScreen's layout exactly
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight()
                    ) {
                        ExpressiveActionCard(
                            modifier = Modifier.weight(1f),
                            icon = AppIcons.Close(),
                            label = stringResource(R.string.crop_cancel),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                            onClick = onCancel
                        )
                        Spacer(Modifier.width(2.dp))
                        ExpressiveActionCard(
                            modifier = Modifier.weight(1f),
                            icon = AppIcons.Check(),
                            label = stringResource(R.string.crop_save),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                            onClick = {
                                if (bitmap != null && !isCropping) {
                                    isCropping = true
                                    scope.launch {
                                        val cropped = withContext(Dispatchers.IO) {
                                            ImageCropper.cropPerspective(context, imageUri, points)
                                        }
                                        if (cropped != null) {
                                            onDone(cropped, points)
                                        } else {
                                            isCropping = false
                                        }
                                    }
                                }
                            }
                        )
                    }
                    
                    // Small floating button for reset, placed inside the row matching the styling
                    Spacer(Modifier.width(8.dp))
                    ExpressiveActionCard(
                        modifier = Modifier.weight(1f),
                        icon = AppIcons.Refresh(),
                        label = stringResource(R.string.crop_reset),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.extraLarge,
                        onClick = {
                            if (!isCropping) {
                                points = listOf(
                                    Offset(0f, 0f),
                                    Offset(1f, 0f),
                                    Offset(1f, 1f),
                                    Offset(0f, 1f)
                                )
                            }
                        }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading || bitmap == null) {
                LoadingIndicator(modifier = Modifier.size(64.dp))
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
                    val handleRadiusPx = with(density) { 28.dp.toPx() }

                    // Define colors from current M3 theme
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val tertiaryColor = MaterialTheme.colorScheme.tertiary
                    val surfaceColor = MaterialTheme.colorScheme.surface

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(imageUri) {
                                // PointerInput key is stable (imageUri), so it does NOT restart during active drag.
                                // Inside, we read and map the screen points reactively from the 'points' state.
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val currentScreenPoints = points.map { Offset(drawLeft + it.x * drawWidth, drawTop + it.y * drawHeight) }
                                    val dists = currentScreenPoints.map { (it - down.position).getDistance() }
                                    val minDist = dists.minOrNull() ?: Float.MAX_VALUE
                                    val minIndex = dists.indexOf(minDist)
                                    val hitBoxPx = handleRadiusPx * 2.0f
                                    
                                    if (minDist < hitBoxPx) {
                                        draggedIndex = minIndex
                                        down.consume()
                                    } else {
                                        draggedIndex = -1
                                    }

                                    if (draggedIndex != -1) {
                                        var isDragging = true
                                        var previousPos = down.position
                                        while (isDragging) {
                                            val event = awaitPointerEvent()
                                            val dragChange = event.changes.firstOrNull { it.id == down.id }
                                            if (dragChange != null) {
                                                if (dragChange.pressed) {
                                                    val pos = dragChange.position
                                                    val delta = pos - previousPos
                                                    previousPos = pos
                                                    dragChange.consume()
                                                    
                                                    val currentPt = points[draggedIndex]
                                                    val currentScreenX = drawLeft + currentPt.x * drawWidth
                                                    val currentScreenY = drawTop + currentPt.y * drawHeight
                                                    val newScreenPos = Offset(currentScreenX, currentScreenY) + delta

                                                    val clampedX = newScreenPos.x.coerceIn(drawLeft, drawLeft + drawWidth)
                                                    val clampedY = newScreenPos.y.coerceIn(drawTop, drawTop + drawHeight)
                                                    val normX = (clampedX - drawLeft) / drawWidth
                                                    val normY = (clampedY - drawTop) / drawHeight
                                                    points = points.toMutableList().apply {
                                                        this[draggedIndex] = Offset(normX, normY)
                                                    }
                                                } else {
                                                    isDragging = false
                                                }
                                            } else {
                                                isDragging = false
                                            }
                                        }
                                        draggedIndex = -1
                                    }
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val screenPoints = points.map { Offset(drawLeft + it.x * drawWidth, drawTop + it.y * drawHeight) }

                            // 1. Draw Image centered
                            drawImage(
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
                                    color = Color.Black.copy(alpha = 0.5f),
                                    topLeft = Offset(drawLeft, drawTop),
                                    size = Size(drawWidth, drawHeight)
                                )
                            }

                            // 4. Draw quadrilateral connection lines
                            drawPath(
                                path = path,
                                color = primaryColor,
                                style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round)
                            )

                            // 5. Draw interactive corner handles matching M3 styling
                            screenPoints.forEachIndexed { index, point ->
                                val isDragging = index == draggedIndex
                                val radius = if (isDragging) 18.dp.toPx() else 14.dp.toPx()
                                val innerRadius = if (isDragging) 8.dp.toPx() else 6.dp.toPx()

                                // Outer white outline ring for high-contrast on photos
                                drawCircle(
                                    color = Color.White,
                                    radius = radius + 2.dp.toPx(),
                                    center = point
                                )
                                // Main handle filled ring (primary color, tertiary when dragged)
                                drawCircle(
                                    color = if (isDragging) tertiaryColor else primaryColor,
                                    radius = radius,
                                    center = point
                                )
                                // Center core circle (white/surface container color)
                                drawCircle(
                                    color = surfaceColor,
                                    radius = innerRadius,
                                    center = point
                                )
                            }
                        }

                        if (isCropping) {
                            androidx.compose.ui.window.Dialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                                Surface(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        LoadingIndicator(modifier = Modifier.size(80.dp))
                                        Spacer(Modifier.height(32.dp))
                                        Text(stringResource(R.string.crop_saving_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) 
                                        Spacer(Modifier.height(16.dp))
                                        Text(stringResource(R.string.crop_saving_message), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
