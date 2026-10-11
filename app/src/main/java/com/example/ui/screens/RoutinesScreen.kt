package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.RoutineManager
import com.example.data.AppDatabase
import com.example.data.RoutineEntity
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.PrimaryPink
import com.example.ui.theme.SecondaryPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun RoutinesScreen(
    database: AppDatabase,
    routineManager: RoutineManager,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val routines by database.routineDao().getAllRoutines().collectAsState(initial = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var activeRunStatus by remember { mutableStateOf<String?>(null) }

    // Preload default starter routines if DB is empty
    LaunchedEffect(Unit) {
        if (routines.isEmpty()) {
            database.routineDao().insertRoutine(
                RoutineEntity(
                    triggerPhrase = "গুড মর্নিং",
                    title = "Good Morning Routine",
                    actionsJson = """[{"type":"battery_status"},{"type":"storage_status"},{"type":"open_app","args":{"app_name":"YouTube"}}]"""
                )
            )
            database.routineDao().insertRoutine(
                RoutineEntity(
                    triggerPhrase = "ঘুমানোর সময়",
                    title = "Night Sleep Routine",
                    actionsJson = """[{"type":"flashlight","args":{"turn_on":false}},{"type":"media","args":{"action":"pause"}}]"""
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Automated Routines",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "Chain multiple actions triggered by a single custom phrase",
                color = SecondaryPink,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (activeRunStatus != null) {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryPink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = activeRunStatus!!,
                        color = PrimaryPink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (routines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No routines yet. Tap + to create one!", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(routines) { item ->
                        RoutineCard(
                            routine = item,
                            onToggle = { enabled ->
                                scope.launch {
                                    database.routineDao().updateRoutine(item.copy(enabled = enabled))
                                }
                            },
                            onRun = {
                                scope.launch {
                                    activeRunStatus = "Running ${item.title}..."
                                    val summary = routineManager.executeRoutine(item)
                                    activeRunStatus = summary
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    database.routineDao().deleteRoutine(item)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = PrimaryPink,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Routine")
        }

        // Create Dialog
        if (showCreateDialog) {
            CreateRoutineDialog(
                onDismiss = { showCreateDialog = false },
                onSave = { title, trigger, actions ->
                    scope.launch {
                        database.routineDao().insertRoutine(
                            RoutineEntity(
                                title = title,
                                triggerPhrase = trigger,
                                actionsJson = actions
                            )
                        )
                        showCreateDialog = false
                    }
                }
            )
        }
    }
}

@Composable
private fun RoutineCard(
    routine: RoutineEntity,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = DarkCard,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Routine",
                        tint = PrimaryPink,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = routine.title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = routine.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryPink,
                        checkedTrackColor = DarkSurface,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Trigger phrase: \"${routine.triggerPhrase}\"",
                color = SecondaryPink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRun,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run Now", fontSize = 12.sp)
                }

                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun CreateRoutineDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, trigger: String, actionsJson: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }
    var includeBattery by remember { mutableStateOf(true) }
    var includeStorage by remember { mutableStateOf(false) }
    var includeNotifications by remember { mutableStateOf(true) }
    var includeYouTube by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Routine", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Routine Name (e.g. Work Mode)") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    label = { Text("Voice Trigger (e.g. কাজ শুরু)") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Text("Actions to include in order:", color = SecondaryPink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = includeBattery, onCheckedChange = { includeBattery = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("1. Check Battery status", color = TextPrimary, fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = includeNotifications, onCheckedChange = { includeNotifications = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("2. Read unread notifications", color = TextPrimary, fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = includeStorage, onCheckedChange = { includeStorage = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("3. Check storage free space", color = TextPrimary, fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = includeYouTube, onCheckedChange = { includeYouTube = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("4. Launch YouTube", color = TextPrimary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && trigger.isNotBlank()) {
                        val actions = mutableListOf<String>()
                        if (includeBattery) actions.add("""{"type":"battery_status"}""")
                        if (includeNotifications) actions.add("""{"type":"read_notifications"}""")
                        if (includeStorage) actions.add("""{"type":"storage_status"}""")
                        if (includeYouTube) actions.add("""{"type":"open_app","args":{"app_name":"YouTube"}}""")
                        val json = "[" + actions.joinToString(",") + "]"
                        onSave(title, trigger, json)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
            ) {
                Text("Save Routine")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkCard
    )
}
