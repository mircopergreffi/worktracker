package com.example.workhours.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.workhours.HistoryPeriod

/** Colors a client can be given (ARGB ints). */
val Palette: List<Int> = listOf(
    0xFF1F9E85, 0xFF3B82F6, 0xFF6366F1, 0xFFA855F7, 0xFFEC4899,
    0xFFEF4444, 0xFFF97316, 0xFFEAB308, 0xFF22C55E, 0xFF64748B
).map { it.toInt() }

data class Option(val id: Long, val name: String, val color: Int?)

@Composable
fun ColorDot(color: Int, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(color)))
}

/** Dropdown button with a "+ New ..." entry; grayed out when disabled. */
@Composable
fun Selector(
    label: String,
    selected: Option?,
    options: List<Option>,
    enabled: Boolean,
    addLabel: String?,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            selected?.color?.let { ColorDot(it); Spacer(Modifier.width(8.dp)) }
            Text(
                selected?.name ?: label,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                color = if (selected == null) LocalContentColor.current.copy(alpha = 0.6f)
                else LocalContentColor.current
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            o.color?.let { ColorDot(it); Spacer(Modifier.width(8.dp)) }
                            Text(o.name)
                        }
                    },
                    onClick = { open = false; onSelect(o.id) }
                )
            }
            if (addLabel != null) {
                if (options.isNotEmpty()) HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(addLabel, color = MaterialTheme.colorScheme.primary) },
                    onClick = { open = false; onAdd() }
                )
            }
        }
    }
}

/** Name field (+ optional color palette, optional Delete) used to add or edit clients and projects. */
@Composable
fun NameColorDialog(
    title: String,
    label: String,
    withColor: Boolean,
    initialName: String = "",
    initialColor: Int = Palette[0],
    onDelete: (() -> Unit)? = null,
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(label) },
                    singleLine = true
                )
                if (withColor) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Palette.chunked(5).forEach { rowColors ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowColors.forEach { c ->
                                    val selected = c == color
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape)
                                            .background(Color(c))
                                            .then(
                                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                else Modifier
                                            )
                                            .clickable { color = c }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim(), color) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Dropdown to choose a history period (last 30 days, current week, ...). */
@Composable
fun PeriodSelector(value: HistoryPeriod, onChange: (HistoryPeriod) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Text(value.label, Modifier.weight(1f), maxLines = 1, textAlign = TextAlign.Start)
            Text("▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            HistoryPeriod.entries.forEach { p ->
                DropdownMenuItem(text = { Text(p.label) }, onClick = { open = false; onChange(p) })
            }
        }
    }
}
