package com.example.agriscan.ui.screens

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.agriscan.data.local.CaptureEntity
import com.example.agriscan.ui.defaultFieldRepo
import com.example.agriscan.ui.defaultLibRepo
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*
import kotlin.math.log10
import kotlin.math.pow

private data class MediaMeta(
    val name: String,
    val sizeBytes: Long?,
    val dateMillis: Long?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val libRepo = defaultLibRepo()
    val fieldRepo = defaultFieldRepo()
    val scope = rememberCoroutineScope()

    val items by libRepo.observeAll().collectAsState(initial = emptyList())

    // fields for assignment
    val fields by fieldRepo.observeFields().collectAsState(initial = emptyList())
    val fieldNameById = remember(fields) { fields.associate { it.id to it.name } }

    var preview: Uri? by remember { mutableStateOf(null) }

    // selection by captureId for correct deletes / assigns
    val selected: SnapshotStateList<Long> = remember { mutableStateListOf() }
    val selectionMode = selected.isNotEmpty()

    // assign dialog
    var showAssignDialog by remember { mutableStateOf(false) }
    var assignChoice: Long? by remember { mutableStateOf(null) } // null = None

    fun shareSelected() {
        val toShareUris = items.filter { it.id in selected }.map { Uri.parse(it.uri) }
        if (toShareUris.isEmpty()) return
        if (toShareUris.size == 1) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, toShareUris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "Share image"))
        } else {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(toShareUris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "Share images"))
        }
    }

    fun deleteSelected() {
        val ids = selected.toList()
        if (ids.isEmpty()) return
        scope.launch {
            libRepo.removeMany(ids)
            selected.clear()
        }
    }

    fun assignSelected(toFieldId: Long?) {
        val ids = selected.toList()
        if (ids.isEmpty()) return
        scope.launch {
            libRepo.assignToField(ids, toFieldId)
            val label = toFieldId?.let { "assigned to “${fieldNameById[it] ?: "Field"}”" } ?: "unassigned"
            toast(ctx, "Updated ${ids.size} photo(s) • $label")
            selected.clear()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selected.size} selected" else "Library (${items.size})") },
                navigationIcon = {
                    IconButton(onClick = { if (selectionMode) selected.clear() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectionMode) {
                        // Assign to Field
                        IconButton(onClick = {
                            assignChoice = null // default to None
                            showAssignDialog = true
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Assign to field")
                        }
                        // Select All / Clear
                        TextButton(onClick = {
                            if (selected.size < items.size) {
                                selected.clear(); selected.addAll(items.map { it.id })
                            } else selected.clear()
                        }) { Text(if (selected.size < items.size) "Select all" else "Clear") }
                        // Share
                        IconButton(onClick = { shareSelected() }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share selected")
                        }
                        // Delete
                        IconButton(onClick = { deleteSelected() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    } else {
                        IconButton(onClick = { /* list auto-updates; button for UX */ }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No images yet.\nUse Scan to capture or Upload.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { it.id }) { cap: CaptureEntity ->
                    val ctxLocal = LocalContext.current
                    val imgUri = remember(cap.uri) { Uri.parse(cap.uri) }
                    val meta = remember(cap.uri) { loadMeta(ctxLocal, imgUri) }
                    val assignedName = cap.fieldId?.let { fieldNameById[it] }

                    LibraryCard(
                        capture = cap,
                        imageUri = imgUri,
                        meta = meta,
                        assignedName = assignedName,
                        selected = selected.contains(cap.id),
                        selectionMode = selectionMode,
                        onToggleSelect = {
                            if (selected.contains(cap.id)) selected.remove(cap.id) else selected.add(cap.id)
                        },
                        onOpen = {
                            if (selectionMode) {
                                if (selected.contains(cap.id)) selected.remove(cap.id) else selected.add(cap.id)
                            } else {
                                // preview
                                preview = imgUri
                            }
                        },
                        onShare = {
                            if (selectionMode) {
                                if (!selected.contains(cap.id)) selected.add(cap.id)
                                shareSelected()
                            } else {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_STREAM, imgUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctx.startActivity(Intent.createChooser(intent, "Share image"))
                            }
                        },
                        onDelete = {
                            if (selectionMode) {
                                if (!selected.contains(cap.id)) selected.add(cap.id)
                                deleteSelected()
                            } else {
                                scope.launch { libRepo.remove(cap.id) }
                            }
                        }
                    )
                }
            }
        }
    }

    if (preview != null) {
        AlertDialog(
            onDismissRequest = { preview = null },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("Close") } },
            text = {
                AsyncImage(
                    model = preview,
                    contentDescription = "Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop
                )
            }
        )
    }

    if (showAssignDialog) {
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            title = { Text("Assign selected to…") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    // None option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .combinedClickable(
                                onClick = { assignChoice = null },
                                onLongClick = { assignChoice = null }
                            )
                    ) {
                        RadioButton(selected = assignChoice == null, onClick = { assignChoice = null })
                        Spacer(Modifier.width(8.dp))
                        Text("None (unassign)")
                    }
                    Divider()
                    fields.forEach { f ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .combinedClickable(
                                    onClick = { assignChoice = f.id },
                                    onLongClick = { assignChoice = f.id }
                                )
                        ) {
                            RadioButton(selected = assignChoice == f.id, onClick = { assignChoice = f.id })
                            Spacer(Modifier.width(8.dp))
                            Text(f.name)
                        }
                    }
                    if (fields.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("No fields yet. Create one in the Fields tab.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        assignSelected(assignChoice)
                        showAssignDialog = false
                    },
                    enabled = selected.isNotEmpty()
                ) { Text("Assign") }
            },
            dismissButton = {
                TextButton(onClick = { showAssignDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    capture: CaptureEntity,
    imageUri: Uri,
    meta: MediaMeta?,
    assignedName: String?,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onToggleSelect
            ),
        shape = MaterialTheme.shapes.large
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (selectionMode) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (selected) 0.25f else 0.10f))
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.End
                ) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0x80000000))
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = meta?.name ?: (imageUri.lastPathSegment ?: "image"),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                maxLines = 1
                            )
                            val sub = buildString {
                                meta?.sizeBytes?.let { append(formatBytes(it)) }
                                if (meta?.dateMillis != null) {
                                    if (isNotEmpty()) append(" • ")
                                    append(formatDate(meta.dateMillis))
                                }
                                if (isNotEmpty()) append(" • ")
                                append(assignedName ?: "Unassigned")
                            }
                            Text(sub, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                        }
                        Row {
                            FilledTonalIconButton(
                                onClick = onShare,
                                modifier = Modifier.size(36.dp)
                            ) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                            Spacer(Modifier.width(8.dp))
                            FilledTonalIconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(36.dp)
                            ) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                        }
                    }
                }
            }
        }
    }
}

/* ---------- helpers (non-composable) ---------- */

private fun loadMeta(ctx: android.content.Context, uri: Uri): MediaMeta {
    var name: String? = null
    var size: Long? = null
    var date: Long? = null

    val projection = arrayOf(
        OpenableColumns.DISPLAY_NAME,
        OpenableColumns.SIZE,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.DATE_ADDED
    )

    val cursor: Cursor? = try {
        ctx.contentResolver.query(uri, projection, null, null, null)
    } catch (_: Exception) { null }

    cursor?.use {
        if (it.moveToFirst()) {
            val iName = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val iSize = it.getColumnIndex(OpenableColumns.SIZE)
            val iMod  = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val iAdd  = it.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)

            if (iName >= 0 && !it.isNull(iName)) name = it.getString(iName)
            if (iSize >= 0 && !it.isNull(iSize)) size = it.getLong(iSize)

            date = when {
                iMod >= 0 && !it.isNull(iMod) -> it.getLong(iMod) * 1000L
                iAdd >= 0 && !it.isNull(iAdd) -> it.getLong(iAdd) * 1000L
                else -> null
            }
        }
    }

    return MediaMeta(
        name = name ?: (uri.lastPathSegment ?: "image"),
        sizeBytes = size,
        dateMillis = date
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val group = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(group.toDouble())
    val rounded = if (value >= 100) String.format(Locale.US, "%.0f", value)
    else String.format(Locale.US, "%.1f", value)
    return "$rounded ${units[group]}"
}

private fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

private fun toast(ctx: Context, msg: String) =
    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
