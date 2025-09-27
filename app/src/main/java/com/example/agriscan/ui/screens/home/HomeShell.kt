package com.example.agriscan.ui.screens.home

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.agriscan.ml.Prediction
import com.example.agriscan.ml.TFLiteClassifier
import com.example.agriscan.ui.AuthViewModel
import com.example.agriscan.ui.defaultFieldRepo
import com.example.agriscan.ui.defaultLibRepo
import com.example.agriscan.ui.screens.FieldDetailScreen
import com.example.agriscan.ui.screens.FieldsScreen
import com.example.agriscan.ui.screens.InsightsScreen
import com.example.agriscan.ui.screens.LibraryScreen
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.io.File
import com.example.agriscan.core.runAnalysis

private sealed class HomeTab(val route: String, val label: String) {
    data object Scan     : HomeTab("scan", "Scan")
    data object Fields   : HomeTab("fields", "Fields")
    data object Insights : HomeTab("insights", "Insights")
    data object Profile  : HomeTab("profile", "Profile")
}

@Composable
fun HomeShell(
    vm: AuthViewModel,
    onSignedOut: () -> Unit
) {
    val innerNav = rememberNavController()
    val tabs = listOf(HomeTab.Scan, HomeTab.Fields, HomeTab.Insights, HomeTab.Profile)
    val currentRoute = innerNav.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            innerNav.navigate(tab.route) {
                                popUpTo(innerNav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { /* label only */ },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = innerNav,
            startDestination = HomeTab.Scan.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(HomeTab.Scan.route) {
                ScanScreen(onOpenLibrary = { innerNav.navigate("library") })
            }
            composable(HomeTab.Fields.route) {
                FieldsScreen(onOpenDetails = { id -> innerNav.navigate("field/$id") })
            }
            composable(HomeTab.Insights.route) {
                InsightsScreen()
            }
            composable(HomeTab.Profile.route) {
                ProfileScreen(
                    onSignOut = {
                        vm.signOut()
                        onSignedOut()
                    }
                )
            }
            composable("library") {
                LibraryScreen(onBack = { innerNav.popBackStack() })
            }
            composable(
                route = "field/{fieldId}",
                arguments = listOf(navArgument("fieldId") { type = NavType.LongType })
            ) { backStack ->
                val fieldId = backStack.arguments?.getLong("fieldId") ?: 0L
                FieldDetailScreen(
                    fieldId = fieldId,
                    onBack = { innerNav.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun ScanScreen(
    onOpenLibrary: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lib = defaultLibRepo()
    val fieldsRepo = defaultFieldRepo()
    val scope = rememberCoroutineScope()

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    // Observed fields for assignment
    val fields by fieldsRepo.observeFields().collectAsState(initial = emptyList())
    var selectedFieldId by remember { mutableStateOf<Long?>(null) }
    var selectedFieldName by remember { mutableStateOf("None") }

    // Camera / legacy write permissions
    var hasCamPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val camPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamPermission = granted }

    var hasLegacyWrite by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= 29 ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val writePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLegacyWrite = granted || Build.VERSION.SDK_INT >= 29 }

    LaunchedEffect(Unit) {
        if (!hasCamPermission) camPermLauncher.launch(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < 29 && !hasLegacyWrite) {
            writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    var lastPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    // -------- ML state --------
    var autoAnalyze by remember { mutableStateOf(true) }
    var analyzing by remember { mutableStateOf(false) }
    var predictions by remember { mutableStateOf<List<Prediction>?>(null) }
    var analysisError by remember { mutableStateOf<String?>(null) }

    // SAF picker with persistable permission
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            lastPhotoUri = uri
            scope.launch {
                lib.addCapture(uri.toString(), fieldId = selectedFieldId)
                toast(
                    context,
                    "Imported to Library${selectedFieldId?.let { " • assigned to \"$selectedFieldName\"" } ?: ""}"
                )
                if (autoAnalyze) {
                    analyzing = true
                    runAnalysis(context, uri) { preds, err ->
                        predictions = preds
                        analysisError = err
                        analyzing = false
                    }
                }
            }
        }
    }

    fun outputOptionsForGallery(): ImageCapture.OutputFileOptions {
        val name = "agriscan_${System.currentTimeMillis()}.jpg"
        return if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AgriScan")
            }
            ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                cv
            ).build()
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .resolve("AgriScan").apply { if (!exists()) mkdirs() }
            val file = File(dir, name)
            ImageCapture.OutputFileOptions.Builder(file).build()
        }
    }

    fun capture() {
        if (!hasCamPermission) { toast(context, "Camera permission required"); return }
        if (Build.VERSION.SDK_INT < 29 && !hasLegacyWrite) {
            toast(context, "Storage permission required"); return
        }
        val opts = outputOptionsForGallery()
        controller.takePicture(
            opts, mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { uri ->
                        lastPhotoUri = uri
                        scope.launch {
                            lib.addCapture(uri.toString(), fieldId = selectedFieldId)
                            toast(
                                context,
                                "Saved to Photos & Library${selectedFieldId?.let { " • assigned to \"$selectedFieldName\"" } ?: ""}"
                            )
                            if (autoAnalyze) {
                                analyzing = true
                                runAnalysis(context, uri) { preds, err ->
                                    predictions = preds
                                    analysisError = err
                                    analyzing = false
                                }
                            }
                        }
                    } ?: toast(context, "Saved (no Uri)")
                }
                override fun onError(ex: ImageCaptureException) {
                    toast(context, "Capture failed: ${ex.message ?: ex.imageCaptureError}")
                }
            }
        )
    }

    // Bind controller to lifecycle
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            // release the optional interpreter when leaving Scan
            TFLiteClassifier.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (hasCamPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.controller = controller
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission is required to scan.")
            }
        }

        // Top row: Field selector • Flip • Torch • Auto-analyze toggle
        Row(
            Modifier
                .fillMaxWidth()
                .padding(0.2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FieldPicker(
                fields = fields.map { it.id to it.name },
                selectedId = selectedFieldId,
                onSelect = { id, name ->
                    selectedFieldId = id
                    selectedFieldName = name ?: "None"
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = {
                        controller.cameraSelector =
                            if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            else CameraSelector.DEFAULT_BACK_CAMERA
                    }
                ) { Text("Flip") }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        torchOn = !torchOn
                        runCatching { controller.enableTorch(torchOn) }
                    }
                ) { Text(if (torchOn) "Torch • On" else "Torch • Off") }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = autoAnalyze, onCheckedChange = { autoAnalyze = it })
                }
            }
        }

        // Bottom bar: thumbnail • capture • analyze • upload • library
        Row(
            Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (lastPhotoUri != null) {
                AsyncImage(
                    model = lastPhotoUri,
                    contentDescription = "Last captured image",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable { showPreview = true }
                )
            } else {
                Box(Modifier.size(56.dp))
            }

            Button(
                onClick = { capture() },
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("Take Photo") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = {
                        val uri = lastPhotoUri
                        if (uri == null) {
                            toast(context, "No image to analyze")
                        } else {
                            scope.launch {
                                analyzing = true
                                runAnalysis(context, uri) { preds, err ->
                                    predictions = preds
                                    analysisError = err
                                    analyzing = false
                                }
                            }
                        }
                    },
                    enabled = !analyzing
                ) { Text(if (analyzing) "Analyzing…" else "Analyze") }

                FilledTonalButton(onClick = { pickImageLauncher.launch(arrayOf("image/*")) }) {
                    Text("Upload")
                }
                FilledTonalButton(onClick = onOpenLibrary) {
                    Text("Library")
                }
            }
        }

        // Quick analyzing overlay
        if (analyzing) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
                contentAlignment = Alignment.Center
            ) {
                AssistChip(
                    onClick = { },
                    label = { Text("Analyzing…") },
                    leadingIcon = { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) }
                )
            }
        }

        // Preview dialog
        if (showPreview && lastPhotoUri != null) {
            AlertDialog(
                onDismissRequest = { showPreview = false },
                confirmButton = {
                    TextButton(onClick = { showPreview = false }) { Text("Close") }
                },
                text = {
                    AsyncImage(
                        model = lastPhotoUri,
                        contentDescription = "Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            )
        }
    }

    // Predictions dialog
    if (predictions != null || analysisError != null) {
        AlertDialog(
            onDismissRequest = { predictions = null; analysisError = null },
            confirmButton = {
                TextButton(onClick = { predictions = null; analysisError = null }) { Text("OK") }
            },
            title = { Text("Analysis") },
            text = {
                if (analysisError != null) {
                    Text(analysisError!!)
                } else {
                    val preds = predictions.orEmpty()
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        preds.forEach { p ->
                            Column {
                                Text("${p.label}  •  ${formatPct(p.prob)}")
                                LinearProgressIndicator(
                                    progress = { p.prob.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                        if (preds.isEmpty()) Text("No predictions.")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldPicker(
    fields: List<Pair<Long, String>>,
    selectedId: Long?,
    onSelect: (Long?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("Assign to: None") }

    LaunchedEffect(selectedId, fields) {
        val name = fields.firstOrNull { it.first == selectedId }?.second
        label = "Assign to: " + (name ?: "None")
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = TextFieldValue(label),
                onValueChange = { /* read-only */ },
                readOnly = true,
                label = { Text("Field") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .padding(8.dp)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = {
                        onSelect(null, null)
                        expanded = false
                    }
                )
                fields.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onSelect(id, name)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun toast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

@Composable
private fun ProfileScreen(onSignOut: () -> Unit) {
    val email = Firebase.auth.currentUser?.email ?: "Signed in"
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall)
        Text("Email: $email")
        Button(onClick = onSignOut) { Text("Sign out") }
    }
}

private fun formatPct(p: Float): String = String.format("%.1f%%", (p * 100f))
