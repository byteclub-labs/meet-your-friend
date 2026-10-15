package com.byteclub.meetyourfriend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.byteclub.meetyourfriend.data.Connect

@Composable
fun ConnectList(
    connects: List<Connect>,
    onClick: (Connect) -> Unit,
    onDelete: (Connect) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        connects.forEach { connect ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable { onClick(connect) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(connect.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOf(connect.organization, connect.role).filter(String::isNotBlank)
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!connect.briefText.isNullOrBlank()) StatusChip("Brief ✓")
                            if (connect.whatsappSentAt != null) StatusChip("WhatsApp ✓")
                            if (connect.selfiePath != null) StatusChip("Selfie ✓")
                        }
                    }
                    IconButton(onClick = { onDelete(connect) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${connect.name}")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String) {
    AssistChip(onClick = {}, label = { Text(label) })
}
