package com.example.boxfan3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.boxfan3.ui.components.TimerPicker
import com.example.boxfan3.ui.theme.PlayButtonColor
import com.example.boxfan3.ui.theme.PauseButtonColor
import com.example.boxfan3.ui.theme.TimerActiveColor
import com.example.boxfan3.ui.viewmodel.BoxFanUiState
import com.example.boxfan3.ui.viewmodel.BoxFanViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Main screen for BoxFan audio player
 * Features play/pause, volume control, sleep timer
 */
@Composable
fun BoxFanScreen(
    modifier: Modifier = Modifier,
    viewModel: BoxFanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var showTimerPicker by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround
        ) {
            // Header
            Text(
                text = "BoxFan",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 32.dp)
            )
            
            Text(
                text = "White Noise Sleep Timer",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Display current timer (or infinity if not set)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Timer",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Text(
                        text = if (uiState.timerTotalSeconds > 0) {
                            viewModel.getTimerDisplay()
                        } else {
                            "∞"
                        },
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.timerActive) {
                            TimerActiveColor
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    
                    if (!uiState.timerActive && uiState.isPlaying) {
                        Button(
                            onClick = { viewModel.startTimer() },
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("Start Timer", fontSize = 12.sp)
                        }
                    }
                }
            }
            
            // Play/Pause Button
            Button(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier
                    .size(120.dp)
                    .scale(1.0f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isPlaying) PauseButtonColor else PlayButtonColor
                ),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(
                    imageVector = if (uiState.isPlaying) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.surface
                )
            }
            
            // Volume Control
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeOff,
                            contentDescription = "Volume Low",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Slider(
                            value = uiState.volume,
                            onValueChange = { viewModel.setVolume(it) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            valueRange = 0f..1f
                        )
                        
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = "Volume High",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Text(
                        text = "${(uiState.volume * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            
            // Set Timer Button
            Button(
                onClick = { showTimerPicker = true },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Set Sleep Timer",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // Error display
            if (uiState.errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Timer Picker Dialog
    if (showTimerPicker) {
        TimerPickerDialog(
            hours = uiState.timerMinutes / 60,
            minutes = uiState.timerMinutes % 60,
            onConfirm = { hours, minutes ->
                viewModel.setTimerDuration((hours * 60 + minutes) * 60)
                showTimerPicker = false
            },
            onDismiss = { showTimerPicker = false }
        )
    }
}

@Composable
fun TimerPickerDialog(
    hours: Int,
    minutes: Int,
    onConfirm: (hours: Int, minutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedHours by remember { mutableStateOf(hours) }
    var selectedMinutes by remember { mutableStateOf(minutes) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Sleep Timer",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimerPicker(
                    hours = selectedHours,
                    minutes = selectedMinutes,
                    onHoursChange = { selectedHours = it },
                    onMinutesChange = { selectedMinutes = it }
                )
                
                Text(
                    text = "Valid range: 15 minutes to 10 hours",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedHours, selectedMinutes)
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
