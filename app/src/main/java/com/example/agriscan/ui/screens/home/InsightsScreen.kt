package com.example.agriscan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.agriscan.ui.defaultFieldRepo

@Composable
fun InsightsScreen() {
    val fieldsRepo = defaultFieldRepo()
    val fields by fieldsRepo.fields.collectAsState(initial = emptyList())

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text("Insights", style = MaterialTheme.typography.headlineSmall)
            StatRow(label = "Fields", value = fields.size.toString())
            // You can add more (captures, scans, etc.) later
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
