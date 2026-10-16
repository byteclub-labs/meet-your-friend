package com.byteclub.meetyourfriend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures

@Composable
fun MeetButton(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    listening: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val gestureModifier = remember(onTap, onDoubleTap, onLongPress) {
        Modifier.pointerInput(onTap, onDoubleTap, onLongPress) {
            detectTapGestures(
                onTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTap()
                },
                onDoubleTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDoubleTap()
                },
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
        }
    }
    Box(
        modifier = modifier
            .size(168.dp)
            .then(gestureModifier)
            .background(if (listening) Color(0xFFB65B3C) else Color(0xFF175B52), CircleShape)
            .semantics { contentDescription = "Meet button" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White)
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White)
            }
            Text(
                text = if (listening) "LISTENING" else "MEET",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 18.sp,
            )
        }
    }
}
