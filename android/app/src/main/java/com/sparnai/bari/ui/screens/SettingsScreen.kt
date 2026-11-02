@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sparnai.bari.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.sparnai.bari.BuildConfig
import com.sparnai.bari.core.AppSettings
import com.sparnai.bari.core.AppState
import com.sparnai.bari.core.Format
import com.sparnai.bari.ui.components.SectionTitle

@Composable
fun SettingsScreen(
    state: AppState,
    notificationsAllowed: Boolean,
    onRequestNotifications: () -> Unit,
    onSettings: (AppSettings) -> Unit,
    onTripName: (String) -> Unit,
    onReset: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val settings = state.settings
    val uri = LocalUriHandler.current
    var confirm by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Automatinis tikrinimas")
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kaip dažnai tikrinti kainas fone", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Išjungta", 3 to "3 val.", 6 to "6 val.", 12 to "12 val.", 24 to "24 val.").forEach { (hours, text) ->
                        FilterChip(
                            selected = settings.checkIntervalHours == hours,
                            onClick = { onSettings(settings.copy(checkIntervalHours = hours)) },
                            label = { Text(text) },
                        )
                    }
                }
                Text(
                    "Paskutinis patikrinimas: ${Format.ago(state.lastRefresh)}. Android gali šiek tiek atidėti foninius patikrinimus, kad taupytų bateriją.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionTitle("Pranešimai")
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column {
                if (!notificationsAllowed) {
                    ListItem(
                        headlineContent = { Text("Pranešimai išjungti") },
                        supportingContent = { Text("Leiskite pranešimus, kad sužinotumėte apie atpigusius skrydžius.") },
                        trailingContent = { TextButton(onClick = onRequestNotifications) { Text("Leisti") } },
                    )
                }
                ListItem(
                    headlineContent = { Text("Kai kaina nukrenta") },
                    supportingContent = { Text(if (settings.minDropEuro > 0) "Bent ${Format.euro(settings.minDropEuro)}" else "Bet koks atpigimas") },
                    trailingContent = {
                        Switch(checked = settings.notifyOnDrop, onCheckedChange = { onSettings(settings.copy(notifyOnDrop = it)) })
                    },
                )
                if (settings.notifyOnDrop) {
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.0, 1.0, 5.0, 10.0, 20.0).forEach { amount ->
                            FilterChip(
                                selected = settings.minDropEuro == amount,
                                onClick = { onSettings(settings.copy(minDropEuro = amount)) },
                                label = { Text(if (amount == 0.0) "Bet koks" else "≥ ${Format.euro(amount)}") },
                            )
                        }
                    }
                }
                ListItem(
                    headlineContent = { Text("Kai pasiekiama tikslinė kaina") },
                    supportingContent = { Text("Tikslinę kainą nustatykite kiekvienam skrydžiui") },
                    trailingContent = {
                        Switch(checked = settings.notifyOnTarget, onCheckedChange = { onSettings(settings.copy(notifyOnTarget = it)) })
                    },
                )
            }
        }

        SectionTitle("Kelionė")
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column {
                ListItem(
                    headlineContent = { Text(state.trip.name) },
                    supportingContent = { Text("${state.trip.segments.size} skrydžiai · ${state.trip.routes.size} maršrutai") },
                    trailingContent = { Icon(Icons.Filled.Edit, contentDescription = "Pervadinti") },
                    modifier = Modifier.clickable { editName = true },
                )
                ListItem(
                    headlineContent = { Text("Atkurti numatytąją kelionę") },
                    supportingContent = { Text("Grąžina Bario maršrutus iš config/trip.json") },
                    leadingContent = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
                    modifier = Modifier.clickable { confirm = "reset" },
                )
                ListItem(
                    headlineContent = { Text("Išvalyti kainų istoriją") },
                    leadingContent = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                    modifier = Modifier.clickable { confirm = "clear" },
                )
            }
        }

        SectionTitle("Apie")
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column {
                ListItem(
                    headlineContent = { Text("Bari ${BuildConfig.VERSION_NAME}") },
                    supportingContent = { Text("Kainos tikrinamos per viešą Ryanair kainų paiešką. Kitų vežėjų kainas įveskite rankiniu būdu.") },
                )
                ListItem(
                    headlineContent = { Text("Atsisiųsti naujausią versiją") },
                    supportingContent = { Text("github.com/${BuildConfig.REPO}/releases") },
                    leadingContent = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.OpenInNew, contentDescription = null) },
                    modifier = Modifier.clickable { uri.openUri("https://github.com/${BuildConfig.REPO}/releases/latest") },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    when (confirm) {
        "reset" -> ConfirmDialog(
            title = "Atkurti numatytąją kelionę?",
            text = "Jūsų pridėti skrydžiai, maršrutai ir kainų istorija bus pakeisti numatytaisiais.",
            onConfirm = { confirm = null; onReset() },
            onDismiss = { confirm = null },
        )
        "clear" -> ConfirmDialog(
            title = "Išvalyti istoriją?",
            text = "Bus paliktos tik dabartinės kainos.",
            onConfirm = { confirm = null; onClearHistory() },
            onDismiss = { confirm = null },
        )
    }

    if (editName) {
        var name by remember { mutableStateOf(state.trip.name) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Kelionės pavadinimas") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { editName = false; onTripName(name) }) { Text("Išsaugoti") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Atšaukti") } },
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Taip") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Atšaukti") } },
    )
}
