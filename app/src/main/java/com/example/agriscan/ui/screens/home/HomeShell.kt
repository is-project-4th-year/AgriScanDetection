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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.agriscan.ui.AuthViewModel
import com.example.agriscan.ui.defaultLibRepo
import com.example.agriscan.ui.screens.FieldsScreen
import com.example.agriscan.ui.screens.InsightsScreen
import com.example.agriscan.ui.screens.LibraryScreen
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.io.File

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
                FieldsScreen()
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
            // Library route
            composable("library") {
                LibraryScreen(onBack = { innerNav.popBackStack() })
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
    val scope = rememberCoroutineScope()

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

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
            // Save to library (string URI, per new repo)
            scope.launch {
                lib.addCapture(uri.toString())
                toast(context, "Imported from gallery")
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
        if (!hasCamPermission) {
            toast(context, "Camera permission required"); return
        }
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
                            lib.addCapture(uri.toString()) // ← repo expects String
                            toast(context, "Saved to Photos & Library")
                        }
                    } ?: toast(context, "Saved (no Uri)")
                }

                override fun onError(ex: ImageCaptureException) {
                    toast(context, "Capture failed: ${ex.message ?: ex.imageCaptureError}")
                }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (hasCamPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.controller = controller
                        controller.bindToLifecycle(lifecycleOwner)
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

        // Top-right controls: Flip / Torch
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
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
        }

        // Bottom bar: thumbnail • capture • upload • library
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
                    contentDescription = "Last",
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { pickImageLauncher.launch(arrayOf("image/*")) }) {
                    Text("Upload")
                }
                FilledTonalButton(onClick = onOpenLibrary) {
                    Text("Library")
                }
            }
        }

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
