package com.example.agriscan.ui.screens

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.agriscan.data.local.CaptureEntity
import com.example.agriscan.ui.defaultFieldRepo
import com.example.agriscan.ui.defaultLibRepo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FieldDetailScreen(
    fieldId: Long,
    onBack: () -> Unit
) {
    val fieldRepo = defaultFieldRepo()
    val libRepo = defaultLibRepo()
    val scope = rememberCoroutineScope()

    val field by fieldRepo.observeField(fieldId).collectAsState(initial = null)
    val captures by libRepo.observeByField(fieldId).collectAsState(initial = emptyList())

    var showRename by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(TextFieldValue("")) }

    var localNotes by remember { mutableStateOf(TextFieldValue("")) }
    var notesDirty by remember { mutableStateOf(false) }

    LaunchedEffect(field?.id) {
        field?.let {
            newName = TextFieldValue(it.name)
            localNotes = TextFieldValue(it.notes)
            notesDirty = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(field?.name ?: "Field") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename")
                    }
                }
            )
        }
    ) { padding ->
        if (field == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Field not found")
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Notes
            Card(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Notes", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = localNotes,
                        onValueChange = {
                            localNotes = it
                            notesDirty = true
                        },
                        placeholder = { Text("Add notes about this field…") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                scope.launch {
                                    fieldRepo.updateNotes(fieldId, localNotes.text)
                                    notesDirty = false
                                }
                            },
                            enabled = notesDirty
                        ) {
                            Icon(Icons.Filled.Create, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save notes")
                        }
                    }
                }
            }

            // Photos grid
            Text(
                text = "Photos (${captures.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            if (captures.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No photos assigned yet.\nCapture from Scan or assign from Library.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(captures, key = { it.id }) { cap ->
                        CaptureTile(
                            capture = cap,
                            onUnassign = {
                                scope.launch { libRepo.assignToField(listOf(cap.id), null) }
                            },
                            onDelete = {
                                scope.launch { libRepo.remove(cap.id) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showRename && field != null) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        fieldRepo.rename(fieldId, newName.text)
                        showRename = false
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            },
            title = { Text("Rename field") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Field name") }
                )
            }
        )
    }
}

@Composable
private fun CaptureTile(
    capture: CaptureEntity,
    onUnassign: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column {
            AsyncImage(
                model = Uri.parse(capture.uri),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onUnassign) { Text("Remove") }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}
