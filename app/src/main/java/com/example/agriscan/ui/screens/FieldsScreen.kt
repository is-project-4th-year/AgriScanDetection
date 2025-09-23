package com.example.agriscan.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.agriscan.data.local.FieldEntity
import com.example.agriscan.ui.defaultFieldRepo
import kotlinx.coroutines.launch

@Composable
fun FieldsScreen(
    onOpenDetails: (Long) -> Unit = {} // keeps your TODO optional & compile-safe
) {
    val repo = defaultFieldRepo()
    val scope = rememberCoroutineScope()

    // Collect the Room flow
    val fields by repo.observeFields().collectAsState(initial = emptyList())

    // Add-field inputs
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    fun addField() {
        val n = name.trim()
        if (n.isEmpty()) return
        scope.launch {
            repo.create(name = n, notes = notes.trim())
            name = ""
            notes = ""
        }
    }

    fun deleteField(id: Long) {
        scope.launch { repo.delete(id) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Your Fields", style = MaterialTheme.typography.headlineSmall)

        // Add field form
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Field name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { addField() },
            enabled = name.trim().isNotEmpty(),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Add Field")
        }

        Divider()

        if (fields.isEmpty()) {
            Text(
                "No fields yet. Add your first plot above.",
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fields, key = { it.id }) { field: FieldEntity ->
                    FieldRow(
                        field = field,
                        onDelete = { deleteField(field.id) },
                        onRename = { newName ->
                            scope.launch { repo.rename(field.id, newName) }
                        },
                        onEditNotes = { newNotes ->
                            scope.launch { repo.updateNotes(field.id, newNotes) }
                        },
                        onOpen = { onOpenDetails(field.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    field: FieldEntity,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onEditNotes: (String) -> Unit,
    onOpen: () -> Unit
) {
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(TextFieldValue(field.name)) }

    var showEditNotes by remember { mutableStateOf(false) }
    var notesText by remember { mutableStateOf(TextFieldValue(field.notes)) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() } // ← tap-through on the whole row
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(field.name, style = MaterialTheme.typography.titleMedium)
            if (field.notes.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(field.notes, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = { showRename = true }) { Text("Rename") }
                TextButton(onClick = { showEditNotes = true }) { Text("Edit notes") }
                // Keep an explicit button too, if you like
                TextButton(onClick = onOpen) { Text("Details") }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename field") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Field name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameText.text.trim()
                        if (trimmed.isNotEmpty()) onRename(trimmed)
                        showRename = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditNotes) {
        AlertDialog(
            onDismissRequest = { showEditNotes = false },
            title = { Text("Edit notes") },
            text = {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Notes…") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEditNotes(notesText.text)
                        showEditNotes = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNotes = false }) { Text("Cancel") }
            }
        )
    }
}
