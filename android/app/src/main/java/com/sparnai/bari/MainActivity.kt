@file:OptIn(ExperimentalMaterial3Api::class)

package com.sparnai.bari

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirplaneTicket
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sparnai.bari.core.Direction
import com.sparnai.bari.core.Format
import com.sparnai.bari.core.RouteOption
import com.sparnai.bari.core.RouteSummary
import com.sparnai.bari.core.Segment
import com.sparnai.bari.ui.MainViewModel
import com.sparnai.bari.ui.screens.FlightsScreen
import com.sparnai.bari.ui.screens.RouteEditorDialog
import com.sparnai.bari.ui.screens.SegmentDetailScreen
import com.sparnai.bari.ui.screens.SegmentEditorDialog
import com.sparnai.bari.ui.screens.SettingsScreen
import com.sparnai.bari.ui.screens.TripScreen
import com.sparnai.bari.ui.theme.BariTheme
import com.sparnai.bari.work.Notifier

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BariTheme {
                BariRoot(viewModel)
            }
        }
    }
}

private sealed interface EditorRequest {
    data class EditSegment(val segment: Segment?) : EditorRequest
    data class EditRoute(val route: RouteOption?) : EditorRequest
}

@Composable
private fun BariRoot(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    var notificationsAllowed by remember { mutableStateOf(Notifier.canNotify(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
        if (!granted) vm.toast("Be leidimo pranešimai apie kainas nebus rodomi")
    }
    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        vm.refreshIfStale()
        if (!notificationsAllowed && state.settings.checkIntervalHours > 0) requestNotifications()
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val detailSegment = detailId?.let { id -> state.trip.segments.find { it.id == id } }
    BackHandler(enabled = detailId != null) { detailId = null }
    BackHandler(enabled = detailId == null && tab != 0) { tab = 0 }

    Scaffold(
        topBar = {
            if (detailSegment != null) {
                TopAppBar(
                    title = { Text(detailSegment.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { detailId = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atgal")
                        }
                    },
                    actions = {
                        IconButton(onClick = vm::refresh, enabled = !refreshing) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Atnaujinti")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                text = when (tab) {
                                    1 -> "Transportas"
                                    2 -> "Nustatymai"
                                    else -> "Bari ✈"
                                },
                            )
                            Text(
                                if (refreshing) "Tikrinamos kainos…" else "Atnaujinta ${Format.ago(state.lastRefresh)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = vm::refresh, enabled = !refreshing) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Atnaujinti")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (detailSegment == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.AirplaneTicket, contentDescription = null) },
                        label = { Text("Kelionė") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.Commute, contentDescription = null) },
                        label = { Text("Transportas") },
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Nustatymai") },
                    )
                }
            }
        },
        floatingActionButton = {
            if (detailSegment == null && tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { editor = EditorRequest.EditSegment(null) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Pridėti") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                detailSegment != null -> SegmentDetailScreen(
                    segment = detailSegment,
                    state = state,
                    loadFlexible = vm::cheapestAround,
                    onEdit = { editor = EditorRequest.EditSegment(detailSegment) },
                    onDelete = {
                        detailId = null
                        vm.deleteSegment(detailSegment.id)
                    },
                    onMoveToDate = { day -> vm.saveSegment(detailSegment.copy(date = day), detailSegment.id) },
                )
                tab == 1 -> FlightsScreen(state, onOpen = { detailId = it })
                tab == 2 -> SettingsScreen(
                    state = state,
                    notificationsAllowed = notificationsAllowed,
                    onRequestNotifications = requestNotifications,
                    onSettings = vm::updateSettings,
                    onTripName = vm::setTripName,
                    onReset = vm::resetTrip,
                    onClearHistory = vm::clearHistory,
                )
                else -> TripScreen(
                    state = state,
                    onOpenSegment = { detailId = it },
                    onPassengers = vm::setPassengers,
                    onEditRoute = { editor = EditorRequest.EditRoute(it) },
                    onDeleteRoute = vm::deleteRoute,
                    onShare = { summary ->
                        val text = shareText(summary, state.trip.passengers)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Dalintis maršrutu"))
                    },
                )
            }
        }
    }

    when (val request = editor) {
        is EditorRequest.EditSegment -> SegmentEditorDialog(
            existing = request.segment,
            trip = state.trip,
            onDismiss = { editor = null },
            onSave = { segment, previousId ->
                editor = null
                vm.saveSegment(segment, previousId)
                if (previousId != null && previousId != segment.id) detailId = segment.id
            },
        )
        is EditorRequest.EditRoute -> RouteEditorDialog(
            existing = request.route,
            trip = state.trip,
            onDismiss = { editor = null },
            onSave = { route ->
                editor = null
                vm.saveRoute(route)
            },
        )
        null -> Unit
    }
}

private fun shareText(summary: RouteSummary, passengers: Int): String = buildString {
    val arrow = when (summary.route.direction) {
        Direction.OUT -> "→"
        Direction.BACK -> "←"
        Direction.ROUND -> "⇄"
    }
    append(arrow).append(' ').append(summary.route.name).append(": ").append(Format.euro(summary.total))
    if (passengers > 1) append(" (").append(passengers).append(" keleiviai)")
    append('\n')
    summary.legs.forEach { leg ->
        append("• ")
            .append(Format.dateShort(leg.segment.date)).append(" ")
            .append(leg.segment.origin).append("→").append(leg.segment.dest)
        leg.fare?.flightNumber?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
        if (leg.departure != null) append(" ").append(Format.time(leg.departure)).append("–").append(Format.time(leg.arrival))
        append(" – ").append(Format.total(leg.price, passengers)).append('\n')
    }
    summary.route.note?.let { append(it).append('\n') }
    append("Stebima su Bari")
}
