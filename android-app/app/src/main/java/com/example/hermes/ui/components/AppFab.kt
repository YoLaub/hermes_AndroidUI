package com.example.hermes.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hermes.theme.HermesPrimary
import com.example.hermes.theme.OnyxDarkBackground

@Composable
fun AppFab(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Add
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = HermesPrimary,
        contentColor = OnyxDarkBackground,
        shape = RoundedCornerShape(16.dp),
        // Material 3 clears the text's semantics, so the icon carries the FAB's spoken name.
        icon = { Icon(icon, contentDescription = label) },
        text = { Text(label, fontWeight = FontWeight.Bold) }
    )
}
