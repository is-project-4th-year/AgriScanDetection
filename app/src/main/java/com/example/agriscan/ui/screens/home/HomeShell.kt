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
import com.example.agriscan.core.runAnalysis
import com.example.agriscan.ml.Inference
import com.example.agriscan.ml.Prediction
import com.example.agriscan.ml.TFLiteClassifier
import com.example.agriscan.ui.AuthViewModel
import com.example.agriscan.ui.defaultFieldRepo
import com.example.agriscan.ui.defaultLibRepo
import com.example.agriscan.ui.defaultDb
import com.example.agriscan.ui.screens.FieldDetailScreen
import com.example.agriscan.ui.screens.FieldsScreen
import com.example.agriscan.ui.screens.InsightsScreen
import com.example.agriscan.ui.screens.LibraryScreen
import com.example.agriscan.ui.util.ConfidenceBand
import com.example.agriscan.ui.util.classifyConfidence
import com.example.agriscan.ui.util.prettyLabel
import com.example.agriscan.ui.util.formatPct
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.io.File

// RAG imports
import com.example.agriscan.rag.KBLoader
import com.example.agriscan.rag.AdvisorService
import com.example.agriscan.rag.TemplatedAgent

// for scrollable Advisor answer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll


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
    val db = defaultDb()
    val scope = rememberCoroutineScope()

    // RAG services
    val kb = remember { KBLoader(context) }
    val advisorService = remember { AdvisorService(kb, db.captureDao(), db.adviceDao(), TemplatedAgent()) }

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

    // Camera / storage permissions
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

    // ML state
    var autoAnalyze by remember { mutableStateOf(true) }
    var analyzing by remember { mutableStateOf(false) }
    var inference by remember { mutableStateOf<Inference?>(null) }
    var analysisError by remember { mutableStateOf<String?>(null) }

    // RAG UI state
    var currentCaptureId by remember { mutableStateOf<Long?>(null) }
    var currentCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var userQuestion by remember { mutableStateOf("What should I do now?") }
    var asking by remember { mutableStateOf(false) }
    var advisorAnswer by remember { mutableStateOf<String?>(null) }
    var advisorSources by remember { mutableStateOf<List<String>>(emptyList()) }

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
            currentCaptureUri = uri
            scope.launch {
                // 1) insert via repository
                lib.addCapture(uri.toString(), fieldId = selectedFieldId)
                // 2) resolve DB id by uri
                val row = db.captureDao().findByUri(uri.toString())
                currentCaptureId = row?.id

                toast(context, "Imported to Library${selectedFieldId?.let { " • \"$selectedFieldName\"" } ?: ""}")
                if (autoAnalyze) {
                    analyzing = true
                    runAnalysis(context, uri) { res, err ->
                        inference = res
                        analysisError = err
                        analyzing = false

                        // persist prediction to Room
                        scope.launch {
                            val top = res?.topK?.firstOrNull()
                            val capId = currentCaptureId
                            if (top != null && capId != null) {
                                db.captureDao().setPrediction(
                                    id = capId,
                                    predictedClass = top.label,
                                    top1Prob = top.prob,
                                    modelVersion = "mobilenetv2-v1"
                                )
                            }
                        }
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
                        currentCaptureUri = uri
                        scope.launch {
                            // 1) insert via repository
                            lib.addCapture(uri.toString(), fieldId = selectedFieldId)
                            // 2) resolve DB id by uri
                            val row = db.captureDao().findByUri(uri.toString())
                            currentCaptureId = row?.id

                            toast(
                                context,
                                "Saved to Photos & Library${selectedFieldId?.let { " • \"$selectedFieldName\"" } ?: ""}"
                            )

                            if (autoAnalyze) {
                                analyzing = true
                                runAnalysis(context, uri) { res, err ->
                                    inference = res
                                    analysisError = err
                                    analyzing = false

                                    // persist prediction
                                    scope.launch {
                                        val top = res?.topK?.firstOrNull()
                                        val capId = currentCaptureId
                                        if (top != null && capId != null) {
                                            db.captureDao().setPrediction(
                                                id = capId,
                                                predictedClass = top.label,
                                                top1Prob = top.prob,
                                                modelVersion = "mobilenetv2-v1"
                                            )
                                        }
                                    }
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
        onDispose { TFLiteClassifier.shutdown() }
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

        // Top row: field picker, flip, torch, auto
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

        // Bottom bar
        // Bottom bar (REPLACE THIS WHOLE BLOCK)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)   // was 24.dp all around → save width
                .align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically
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

            Spacer(Modifier.width(12.dp))

            Button(
                onClick = { capture() },
                shape = CircleShape,
                // slightly smaller to free some width; you can keep 72.dp if you prefer
                modifier = Modifier.size(72.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("Take Photo") }

            // Push the actions group to the right and let it use remaining width
            Spacer(Modifier.weight(1f))

            // Make the action buttons horizontally scrollable so nothing gets clipped
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        val uri = lastPhotoUri
                        if (uri == null) {
                            toast(context, "No image to analyze")
                        } else {
                            scope.launch {
                                analyzing = true
                                runAnalysis(context, uri) { res, err ->
                                    inference = res
                                    analysisError = err
                                    analyzing = false

                                    // persist prediction
                                    scope.launch {
                                        val top = res?.topK?.firstOrNull()
                                        val capId = currentCaptureId
                                        if (top != null && capId != null) {
                                            db.captureDao().setPrediction(
                                                id = capId,
                                                predictedClass = top.label,
                                                top1Prob = top.prob,
                                                modelVersion = "mobilenetv2-v1"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = !analyzing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp) // tighter padding
                ) { Text(if (analyzing) "Analyzing…" else "Analyze", maxLines = 1) }

                FilledTonalButton(
                    onClick = { pickImageLauncher.launch(arrayOf("image/*")) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Upload", maxLines = 1) }

                FilledTonalButton(
                    onClick = onOpenLibrary,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Library", maxLines = 1) }
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

    // Analysis dialog + Ask Advisor (offline)
    if (inference != null || analysisError != null) {
        AlertDialog(
            onDismissRequest = {
                inference = null
                analysisError = null
                advisorAnswer = null
                advisorSources = emptyList()
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        inference = null
                        analysisError = null
                        advisorAnswer = null
                        advisorSources = emptyList()
                    }
                ) { Text("OK") }
            },
            title = { Text("Analysis") },
            text = {
                if (analysisError != null) {
                    Text(analysisError!!)
                } else {
                    val inf = inference!!
                    val band = classifyConfidence(
                        quality  = inf.quality,
                        top1Prob = inf.topK.firstOrNull()?.prob ?: 0f
                    )
                    val headerColor = when (band) {
                        ConfidenceBand.HIGH   -> MaterialTheme.colorScheme.primary
                        ConfidenceBand.MEDIUM -> MaterialTheme.typography.bodyMedium.color
                        ConfidenceBand.LOW    -> MaterialTheme.colorScheme.error
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = when (band) {
                                ConfidenceBand.HIGH   -> "High confidence • ${formatPct(inf.quality)}"
                                ConfidenceBand.MEDIUM -> "Medium confidence • ${formatPct(inf.quality)}"
                                ConfidenceBand.LOW    -> "Low confidence • consider another photo"
                            },
                            color = headerColor,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Entropy: ${String.format("%.3f", inf.entropy)} nats")

                        Spacer(Modifier.height(4.dp))

                        inf.topK.forEach { p: Prediction ->
                            val label = prettyLabel(p.label)
                            Column {
                                Text("$label  •  ${formatPct(p.prob)}")
                                LinearProgressIndicator(
                                    progress = { p.prob.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                        if (inf.topK.isEmpty()) Text("No predictions.")

                        // ---------------- Advisor (offline) ----------------
                        Spacer(Modifier.height(12.dp))
                        Divider()
                        Spacer(Modifier.height(8.dp))

                        Text("Advisor", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Get quick, offline guidance based on the predicted disease.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = userQuestion,
                            onValueChange = { userQuestion = it },
                            label = { Text("Your question") },
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        val canAsk = (currentCaptureId != null) && (inference?.topK?.isNotEmpty() == true)

                        Button(
                            enabled = canAsk && !asking,
                            onClick = {
                                val capId = currentCaptureId
                                if (capId == null) {
                                    toast(context, "Could not link this photo in the database.")
                                    return@Button
                                }
                                asking = true
                                advisorAnswer = null
                                advisorSources = emptyList()
                                scope.launch {
                                    try {
                                        val session = advisorService.advise(
                                            captureId = capId,
                                            userQuestion = userQuestion.ifBlank { "What should I do now?" }
                                        )
                                        advisorAnswer = session.answerText
                                        val titles = session.topDocIdsCsv
                                            .split(",")
                                            .mapNotNull { kb.titleOf(it.trim()) }   // <-- reliable source titles
                                        advisorSources = titles
                                    } catch (e: Exception) {
                                        advisorAnswer = "Advisor failed: ${e.message}"
                                        advisorSources = emptyList()
                                    } finally {
                                        asking = false
                                    }
                                }
                            }
                        ) { Text(if (asking) "Preparing…" else "Ask Advisor (offline)") }

                        if (advisorAnswer != null) {
                            Spacer(Modifier.height(12.dp))
                            Text("Advisor answer", style = MaterialTheme.typography.titleSmall)

                            // Scrollable answer so it won't be cut off
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 0.dp, max = 280.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = 6.dp, bottom = 6.dp)
                            ) {
                                Text(advisorAnswer!!)
                            }

                            // Sources list proves KB retrieval worked
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Sources (${advisorSources.size})",
                                style = MaterialTheme.typography.labelLarge
                            )
                            if (advisorSources.isEmpty()) {
                                Text("• (none) — check that your kb.jsonl class names match model labels.")
                            } else {
                                advisorSources.forEach { t -> Text("• $t") }
                            }
                        }
                        // ---------------------------------------------------
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
                onValueChange = { },
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
                        onSelect(null, null); expanded = false
                    }
                )
                fields.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onSelect(id, name); expanded = false
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
