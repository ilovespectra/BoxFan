package com.example.boxfan3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS-style scrolling time picker with haptic feedback
 * Allows selection of hours and minutes for sleep timer.
 */
@Composable
fun TimerPicker(
    hours: Int,
    minutes: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hours column
        NumberPickerColumn(
            value = hours,
            minValue = 0,
            maxValue = 10,
            onValueChange = onHoursChange,
            label = "h",
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Minutes column
        NumberPickerColumn(
            value = minutes,
            minValue = 0,
            maxValue = 59,
            onValueChange = onMinutesChange,
            label = "m",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun NumberPickerColumn(
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .height(150.dp)
            .verticalScroll(scrollState)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Padding items
        repeat(3) {
            Spacer(modifier = Modifier.height(30.dp))
        }
        
        // Selectable items
        for (i in minValue..maxValue) {
            Text(
                text = String.format("%02d", i),
                fontSize = 24.sp,
                fontWeight = if (i == value) FontWeight.Bold else FontWeight.Normal,
                color = if (i == value) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                },
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .height(30.dp),
                textAlign = TextAlign.Center
            )
        }
        
        // Padding items
        repeat(3) {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
    
    Text(
        text = label,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(top = 8.dp)
    )
}
