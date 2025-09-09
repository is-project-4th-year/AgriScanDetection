package com.example.agriscan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.agriscan.data.local.FieldEntity
import com.example.agriscan.ui.defaultFieldRepo
import kotlinx.coroutines.launch

@Composable
fun FieldsScreen() {
    val repo = defaultFieldRepo()
    val scope = rememberCoroutineScope()

    // Collect the Room flow
    val fields by repo.fields.collectAsState(initial = emptyList())

    // Add-field inputs
    var name = remember { mutableStateOf("") }
    var notes = remember { mutableStateOf("") }

    fun addField() {
        val n = name.value.trim()
        if (n.isEmpty()) return
        scope.launch {
            repo.addField(name = n, notes = notes.value.trim())
            name.value = ""
            notes.value = ""
        }
    }

    fun deleteField(id: Long) {
        scope.launch { repo.deleteField(id) }
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
            value = name.value,
            onValueChange = { name.value = it },
            label = { Text("Field name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = notes.value,
            onValueChange = { notes.value = it },
            label = { Text("Notes (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { addField() },
            enabled = name.value.trim().isNotEmpty(),
            modifier = Modifier.align(Alignment.End)
        ) { Text("Add Field") }

        HorizontalDivider()

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
                // ✅ Use the List<T> overload of items(...)
                items(fields, key = { it.id }) { field: FieldEntity ->
                    FieldRow(
                        field = field,
                        onDelete = { deleteField(field.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    field: FieldEntity,
    onDelete: () -> Unit
) {
    ElevatedCard {
        Column(Modifier.padding(12.dp)) {
            Text(field.name, style = MaterialTheme.typography.titleMedium)
            if (field.notes.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(field.notes, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = { /* TODO: details/edit later */ }) { Text("Details") }
            }
        }
    }
}
