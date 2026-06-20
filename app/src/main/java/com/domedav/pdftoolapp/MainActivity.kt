package com.domedav.pdftoolapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.domedav.pdftoolapp.util.AppIcons
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.domedav.pdftoolapp.ui.theme.PdfToolTheme
import kotlinx.coroutines.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.graphics.shapes.*
import java.io.File
import java.util.UUID
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset

sealed class Screen {
    data object Main : Screen()
    data class Success(val pdfFile: File, val isRestored: Boolean) : Screen()
    data class Crop(val selectedImage: SelectedImage) : Screen()
}

data class SelectedImage(
    val id: String,
    val originalUri: Uri,
    val croppedUri: Uri? = null,
    val cropPoints: List<Offset>? = null
)

fun serializePoints(points: List<Offset>?): String {
    if (points == null) return ""
    return points.joinToString(";") { "${it.x},${it.y}" }
}

fun deserializePoints(str: String): List<Offset>? {
    if (str.isEmpty()) return null
    return try {
        str.split(";").map {
            val parts = it.split(",")
            Offset(parts[0].toFloat(), parts[1].toFloat())
        }
    } catch (e: Exception) {
        null
    }
}

val SelectedImageListSaver = listSaver<List<SelectedImage>, String>(
    save = { list ->
        list.flatMap { img ->
            listOf(
                img.id,
                img.originalUri.toString(),
                img.croppedUri?.toString() ?: "",
                serializePoints(img.cropPoints)
            )
        }
    },
    restore = { list ->
        list.chunked(4).map { chunk ->
            SelectedImage(
                id = chunk[0],
                originalUri = Uri.parse(chunk[1]),
                croppedUri = if (chunk[2].isEmpty()) null else Uri.parse(chunk[2]),
                cropPoints = deserializePoints(chunk[3])
            )
        }
    }
)

class MainActivity : ComponentActivity() {
    private var lastClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PdfToolTheme {
                val context = LocalContext.current
                var currentScreen by remember { mutableStateOf<Screen>(restoreState(context)) }
                var selectedImages by rememberSaveable(stateSaver = SelectedImageListSaver) { mutableStateOf<List<SelectedImage>>(emptyList()) }
                var selectedQualityIndex by rememberSaveable { mutableStateOf(1) }

                LaunchedEffect(currentScreen) { saveState(context, currentScreen) }

                Crossfade(targetState = currentScreen, animationSpec = tween(500), label = "ScreenTransition") { screen ->
                    when (screen) {
                        is Screen.Main -> MainScreen(
                            selectedImages = selectedImages,
                            onSelectedImagesChange = { selectedImages = it },
                            selectedQualityIndex = selectedQualityIndex,
                            onSelectedQualityIndexChange = { selectedQualityIndex = it },
                            onCropImage = { currentScreen = Screen.Crop(it) },
                            onPdfCreated = { currentScreen = Screen.Success(it, false) },
                            canClick = { debounceClick() }
                        )
                        is Screen.Success -> SuccessScreen(
                            pdfFile = screen.pdfFile,
                            isRestored = screen.isRestored,
                            onBack = { 
                                if (debounceClick()) { 
                                    PdfGenerator.cleanup(context)
                                    selectedImages = emptyList()
                                    currentScreen = Screen.Main
                                    clearState(context) 
                                } 
                            },
                            canClick = { debounceClick() }
                        )
                        is Screen.Crop -> CropScreen(
                            imageUri = screen.selectedImage.originalUri,
                            initialPoints = screen.selectedImage.cropPoints,
                            onCancel = { currentScreen = Screen.Main },
                            onDone = { croppedUri, cropPoints ->
                                selectedImages = selectedImages.map {
                                    if (it.id == screen.selectedImage.id) it.copy(
                                        croppedUri = croppedUri,
                                        cropPoints = cropPoints
                                    ) else it
                                }
                                currentScreen = Screen.Main
                            }
                        )
                    }
                }
            }
        }
    }

    private fun debounceClick(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > Consts.DEBOUNCE_TIME_MS) {
            lastClickTime = currentTime
            return true
        }
        return false
    }

    private fun saveState(context: Context, screen: Screen) {
        val prefs = context.getSharedPreferences("pdf_prefs", MODE_PRIVATE)
        if (screen is Screen.Success) prefs.edit().putString("last_pdf", screen.pdfFile.absolutePath).putLong("last_timestamp", System.currentTimeMillis()).apply()
    }

    private fun restoreState(context: Context): Screen {
        val prefs = context.getSharedPreferences("pdf_prefs", MODE_PRIVATE)
        val path = prefs.getString("last_pdf", null)
        val timestamp = prefs.getLong("last_timestamp", 0)
        if (path != null && (System.currentTimeMillis() - timestamp) < Consts.RECENT_RESTORE_MINUTES * 60 * 1000) {
            val file = File(path)
            if (file.exists()) return Screen.Success(file, true)
        }
        return Screen.Main
    }

    private fun clearState(context: Context) { context.getSharedPreferences("pdf_prefs", MODE_PRIVATE).edit().clear().apply() }
}

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    selectedImages: List<SelectedImage>,
    onSelectedImagesChange: (List<SelectedImage>) -> Unit,
    selectedQualityIndex: Int,
    onSelectedQualityIndexChange: (Int) -> Unit,
    onCropImage: (SelectedImage) -> Unit,
    onPdfCreated: (File) -> Unit,
    canClick: () -> Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var isLoading by rememberSaveable { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val pickMultipleLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            onSelectedImagesChange(selectedImages + uris.map { SelectedImage(id = UUID.randomUUID().toString(), originalUri = it) })
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            val newImg = SelectedImage(id = UUID.randomUUID().toString(), originalUri = tempCameraUri!!)
            onSelectedImagesChange(selectedImages + newImg)
            onCropImage(newImg)
        }
        tempCameraUri = null 
    }

    if (isLoading) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    LoadingIndicator(modifier = Modifier.size(80.dp))
                    Spacer(Modifier.height(32.dp))
                    Text(stringResource(R.string.generating_title), style =
                    MaterialTheme.typography.headlineMedium, fontWeight =
                    FontWeight.Bold, textAlign = TextAlign.Center) 
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.generating_message), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            }
        }
    }

    val listState = rememberLazyListState()
    // Stabilabb offset figyelés a vibrálás ellen
    val scrollOffset by remember { derivedStateOf { (listState.firstVisibleItemIndex * 500 + listState.firstVisibleItemScrollOffset).toFloat() } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name), fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (selectedImages.isNotEmpty() && !isLoading) {
                ExpressiveFAB(
                    onClick = {
                        if (canClick()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isLoading = true
                            scope.launch {
                                val startTime = System.currentTimeMillis()
                                try {
                                    PdfGenerator.cleanup(context)
                                    val pdfFile = withContext(Dispatchers.IO) { 
                                        PdfGenerator.generatePdf(context, selectedImages.map { it.croppedUri ?: it.originalUri }, selectedQualityIndex) 
                                    }
                                    val elapsed = System.currentTimeMillis() - startTime
                                    if (elapsed < 2000) delay(2000 - elapsed)
                                    onPdfCreated(pdfFile)
                                } catch (e: Exception) { 
                                    e.printStackTrace()
                                    Toast.makeText(context, context.getString(R.string.error_conversion), Toast.LENGTH_SHORT).show()
                                } finally { isLoading = false }
                            }
                        }
                    },
                    icon = AppIcons.PictureAsPdf(),
                    label = stringResource(R.string.convert_to_pdf)
                )
            }
        }
    ) { padding ->
        var measuredHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        
        val guideProgress by animateFloatAsState(
            targetValue = if (scrollOffset > 50f) 1f else 0f, 
            label = "guideProgress",
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
        
        // Magasság számítása (64dp a minimum, hogy kényelmesen elférjen)
        val currentHeaderHeight = if (measuredHeightPx > 0) {
            with(density) { androidx.compose.ui.unit.lerp(measuredHeightPx.toDp(), 64.dp, guideProgress) }
        } else {
            Dp.Unspecified 
        }
    
        // BOX használata Column helyett a stabilitásért
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            
            // 1. A TARTALOM (LISTA) - Ez van alul
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(), // Kitölti a teret
                horizontalAlignment = Alignment.Start,
                contentPadding = PaddingValues(
                    // A felső padding dinamikusan követi a fejléc méretét + 16dp rés
                    //top = (if (currentHeaderHeight != Dp.Unspecified) currentHeaderHeight else 200.dp) + 16.dp, 
                    bottom = 120.dp, 
                    top = 48.dp
                )
            ) {
                item {
                    val expandedHeightDp = with(density) { measuredHeightPx.toDp() }
                    val spacerHeight = if (measuredHeightPx > 0) expandedHeightDp else 200.dp
                    Spacer(modifier = Modifier.height(spacerHeight))
                }
                
                item {
                    StepHeader(stringResource(R.string.step_label_1), AppIcons.AddPhotoAlternate())
                    ConnectedActionButtons(
                        leftLabel = stringResource(R.string.select_images),
                        leftIcon = AppIcons.PhotoLibrary(),
                        leftColor = MaterialTheme.colorScheme.primaryContainer,
                        onLeftClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pickMultipleLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        rightLabel = stringResource(R.string.take_photo),
                        rightIcon = AppIcons.CameraAlt(),
                        rightColor = MaterialTheme.colorScheme.tertiaryContainer,
                        onRightClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            try {
                                val photoFile = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Camera error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                item {
                    if (selectedImages.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(24.dp))
                            StepHeader(stringResource(R.string.step_label_2), AppIcons.LowPriority())
                            ReorderableImageGrid(
                                 images = selectedImages,
                                 onRemove = { i -> 
                                     haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                     onSelectedImagesChange(selectedImages.filterIndexed { idx, _ -> idx != i }) 
                                 },
                                 onReorder = { from, to ->
                                     onSelectedImagesChange(selectedImages.toMutableList().apply { add(to, removeAt(from)) })
                                 },
                                 onImageClick = { img ->
                                     onCropImage(img)
                                 }
                             )

                            Spacer(Modifier.height(16.dp))
                            StepHeader(stringResource(R.string.step_label_3), AppIcons.HighQuality())
                            QualityPicker(selectedQualityIndex) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSelectedQualityIndexChange(it) }
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            EmptyStateView()
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                  
                }
            }

            // 2. A FEJLÉC - Ez lebeg a lista felett (Z-Index)
            Box(modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(1f) // Biztosítjuk, hogy felül legyen
                .background(MaterialTheme.colorScheme.surface) // Háttérszín, hogy ne látszódjon át a lista
                .then(if (currentHeaderHeight != Dp.Unspecified) Modifier.height(currentHeaderHeight) else Modifier.wrapContentHeight())
                .clipToBounds()
            ) {
                DynamicUsageGuide(
                  progress = guideProgress,
                  modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { 
                            if (measuredHeightPx == 0) measuredHeightPx = it.height 
                        }
                )
            }
        }
    }
}

@Composable
fun DynamicUsageGuide(progress: Float, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(), // Itt NEM wrapContentHeight, a szülő mondja meg a méretet
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Kinyitott állapot
            if (progress < 0.5f) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .alpha(1f - progress * 2f)
                        .graphicsLayer { translationY = -20f * progress }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Info(), null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    listOf(R.string.step_1, R.string.step_2, R.string.step_3, R.string.step_4).forEach { res ->
                        Text("• " + stringResource(res), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp), textAlign = TextAlign.Start)
                    }
                }
            }
            
            // Összecsukott állapot
            if (progress >= 0.5f) {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .alpha((progress - 0.5f) * 2f)
                        .fillMaxWidth()
                        .fillMaxHeight() // JAVÍTÁS: Kitölti a magasságot
                        .graphicsLayer { translationY = 10f * (1f - progress) },
                    verticalAlignment = Alignment.CenterVertically, // Így középre kerül vertikálisan
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(AppIcons.AddPhotoAlternate(), AppIcons.LowPriority(), AppIcons.HighQuality(), AppIcons.PictureAsPdf()).forEachIndexed { i, icon ->
                        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        if (i < 3) Icon(AppIcons.ChevronRight(), null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectedActionButtons(
    leftLabel: String, leftIcon: androidx.compose.ui.graphics.vector.ImageVector, leftColor: Color, onLeftClick: () -> Unit,
    rightLabel: String, rightIcon: androidx.compose.ui.graphics.vector.ImageVector, rightColor: Color, onRightClick: () -> Unit
) {
    Row(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(90.dp)) {
        ExpressiveActionCard(Modifier.weight(1f), leftIcon, leftLabel, leftColor, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 4.dp, bottomEnd = 4.dp), onLeftClick)
        Spacer(Modifier.width(2.dp))
        ExpressiveActionCard(Modifier.weight(1f), rightIcon, rightLabel, rightColor, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 28.dp, bottomEnd = 28.dp), onRightClick)
    }
}

@Composable
fun ExpressiveActionCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "cardScale")

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.scale(scale),
        shape = shape,
        color = color,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderableImageGrid(
    images: List<SelectedImage>,
    onRemove: (Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onImageClick: (SelectedImage) -> Unit
) {
    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        onReorder(from.index, to.index)
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().heightIn(max = 2000.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        itemsIndexed(images, key = { _, item -> item.id }) { index, item ->
            ReorderableItem(reorderableState, key = item.id) { isDragging ->
                val scale by animateFloatAsState(if (isDragging) 1.1f else 1f, label = "gridScale")
                Box(modifier = Modifier.aspectRatio(1f).scale(scale).longPressDraggableHandle()) {
                    ElevatedCard(
                        onClick = { onImageClick(item) },
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        AsyncImage(item.croppedUri ?: item.originalUri, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    FilledIconButton(onClick = { onRemove(index) }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Icon(AppIcons.Close(), null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SuccessScreen(pdfFile: File, isRestored: Boolean, onBack: () -> Unit, canClick: () -> Boolean) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    var isExpanded by remember { mutableStateOf(false) }
    val previewListState = rememberLazyListState()
    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    
    val currentVisiblePageIndex by remember {
        derivedStateOf {
            if (pdfPages.isEmpty()) 0 
            else previewListState.firstVisibleItemIndex.coerceIn(0, pdfPages.lastIndex)
        }
    }
    
    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "expandProgress"
    )

// 1. A4 arány
    val a4Ratio = 1.4142f

    // 2. shapeA: Megadjuk neki az A4 arányt (height = a4Ratio)
    // Így amikor a kártya A4 méretű, a mátrix nem fogja "széthúzni" a sarkokat.
    val shapeA = remember {
        RoundedPolygon.rectangle(
            width = 1f,
            height = a4Ratio,
            rounding = CornerRounding(0.12f)
        )
    }

    // 3. shapeB: Szintén A4 szerkezetű (hogy a Morph szép legyen), de 0-s kerekítéssel.
    // Amikor ez fullscreenre nyúlik, torzulni fog, de mivel 0 a kerekítés (hegyes), ez nem látszik!
    val shapeB = remember {
        RoundedPolygon.rectangle(
            width = 1f,
            height = a4Ratio,
            rounding = CornerRounding(0.0f)
        )
    }
    
    val morph = remember { Morph(shapeA, shapeB) }

    // 4. A RÉGI, EGYSZERŰ LOGIKA (minimális matekkal)
    val morphShape = remember(expandProgress) {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val matrix = android.graphics.Matrix()

                // Skálázás:
                // Szélesség: 1.0 -> size.width
                // Magasság: 1.41 -> size.height (Ezért osztjuk a4Ratio-val, hogy a matek kijöjjön)
                
                // Amikor a doboz A4 (kicsi): scaleX == scaleY -> TÖKÉLETES KÖRÍV
                // Amikor a doboz Fullscreen (nagy): scaleY > scaleX -> TORZUL (de nem baj, mert shapeB hegyes!)
                matrix.setScale(size.width, size.height / a4Ratio)

                // Középre igazítás (Mivel a poligon középpontja a 0,0)
                matrix.postTranslate(size.width / 2f, size.height / 2f)

                val path = morph.toPath(expandProgress)
                path.transform(matrix)
                return Outline.Generic(path.asComposePath())
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { out -> pdfFile.inputStream().use { it.copyTo(out) } }
    }

    LaunchedEffect(pdfFile) {
        pdfPages = withContext(Dispatchers.IO) { PdfGenerator.renderPreviewPages(context, pdfFile) }
        //if (!isRestored) sharePdf(context, pdfFile)
    }


    Box(modifier = Modifier.fillMaxSize()) {
        
        // --- HÁTTÉR (Gombok) ---
        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.success_title), fontWeight = FontWeight.Bold) }) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .graphicsLayer { alpha = 1f - expandProgress }, 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.success_desc), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.weight(1f))
                
                Row(modifier = Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.weight(2f).fillMaxHeight()) {
                        ExpressiveActionCard(Modifier.weight(1f), AppIcons.Save(), stringResource(R.string.save), MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 4.dp, bottomEnd = 4.dp)) { saveLauncher.launch(pdfFile.name) }
                        Spacer(Modifier.width(2.dp))
                        ExpressiveActionCard(Modifier.weight(1f), AppIcons.Share(), stringResource(R.string.share), MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 28.dp, bottomEnd = 28.dp)) { sharePdf(context, pdfFile) }
                    }
                    ExpressiveActionCard(Modifier.weight(1f), AppIcons.Add(), stringResource(R.string.create_new), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraLarge) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress); onBack()
                    }
                }
            }
        }

        // --- SÖTÉTÍTÉS ---
        if (expandProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f * expandProgress))
                    .zIndex(10f)
                    .clickable { isExpanded = false }
            )
        }

        // --- ANIMÁLT KÁRTYA / PDF OLVASÓ ---
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().zIndex(11f),
            contentAlignment = Alignment.Center
        ) {
            val screenW = maxWidth
            val screenH = maxHeight
            val a4Ratio = 1.4142f
    
            val collapsedWidth = screenW * 0.75f 
            val collapsedHeight = collapsedWidth * a4Ratio
    
            val expandedWidth = screenW
            val expandedHeight = screenH
    
            val animatedWidth = lerp(collapsedWidth, expandedWidth, expandProgress)
            val animatedHeight = lerp(collapsedHeight, expandedHeight, expandProgress)
        
            Surface(
                modifier = Modifier
                    .requiredSize(width = animatedWidth, height = animatedHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !isExpanded
                    ) { isExpanded = true },
                shape = morphShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 10.dp,
                shadowElevation = 10.dp
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    if (pdfPages.isNotEmpty()) {
                        // UNIFORM scale to completely preserve aspect ratio!
                        val scale = animatedWidth / expandedWidth
                        
                        Box(
                            modifier = Modifier
                                .requiredSize(expandedWidth, expandedHeight)
                                .graphicsLayer {
                                    this.scaleX = scale
                                    this.scaleY = scale
                                    this.transformOrigin = TransformOrigin(0.5f, 0f) // Scale from the top
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp).graphicsLayer { alpha = expandProgress },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.page_count, pdfPages.size),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                    IconButton(onClick = { isExpanded = false }) {
                                        Icon(AppIcons.Close(), null)
                                    }
                                }

                                LazyColumn(
                                    state = previewListState,
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    userScrollEnabled = isExpanded
                                ) {
                                    items(pdfPages) { bitmap ->
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                                                .shadow(4.dp),
                                            contentScale = ContentScale.FillWidth
                                        )
                                    }
                                    item { Spacer(Modifier.height(32.dp)) }
                                }
                            }
                            
                            // Collapsed preview hint overlay
                            if (expandProgress < 1f) {
                                Surface(
                                    // Pinned slightly higher so it's always visible within the cropped height
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = expandedHeight * 0.4f).graphicsLayer { 
                                        alpha = 1f - expandProgress 
                                        // Reverse scale so the button doesn't shrink!
                                        this.scaleX = 1f / scale
                                        this.scaleY = 1f / scale
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(AppIcons.OpenInNew(), null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.preview_hint), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator(modifier = Modifier.size(64.dp))
                        }
                    }
                }
            }
        }
    }
    
    if (isExpanded) {
        BackHandler { isExpanded = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(R.string.low, R.string.medium, R.string.high)
            options.forEachIndexed { i, l ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                    onClick = { onSelect(i) },
                    selected = i == selected,
                    colors = SegmentedButtonDefaults.colors()
                ) { Text(stringResource(l)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        val descRes = when(selected) {
            0 -> R.string.quality_desc_low
            1 -> R.string.quality_desc_medium
            else -> R.string.quality_desc_high
        }
        Text(text = stringResource(descRes), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ExpressiveFAB(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, label = "fabScale")

    FloatingActionButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.scale(scale).navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(modifier = Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(12.dp))
            Text(label, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun StepHeader(t: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(t, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun EmptyStateView() { Column(Modifier.padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(AppIcons.PictureAsPdf(), null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.empty_state_text), modifier = Modifier.alpha(0.6f), textAlign = TextAlign.Center) } }

private fun openPdf(c: Context, f: File) {
    val u = FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", f)
    c.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(u, "application/pdf"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
}

private fun sharePdf(c: Context, f: File) {
    val u = FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", f)
    c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, c.getString(R.string.share)))
}
