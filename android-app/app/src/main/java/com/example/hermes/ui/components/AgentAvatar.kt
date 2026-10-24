package com.example.hermes.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.theme.HermesColors
import java.io.File

/** Avatars the owner set, by profile id, and where their images live. Empty means defaults everywhere. */
data class Avatars(val specs: Map<String, AvatarSpec> = emptyMap(), val imageDir: File? = null)

val LocalAvatars = compositionLocalOf { Avatars() }

/** The one avatar of an agent: image › initials and colour › automatic fallback. Sizes 20/32/40/56/88. */
@Composable
fun AgentAvatar(persona: Persona, size: Dp, modifier: Modifier = Modifier, spec: AvatarSpec? = LocalAvatars.current.specs[persona.id]) {
    val dir = LocalAvatars.current.imageDir
    val file = spec?.image?.let { name -> dir?.let { File(it, name) } }
    // Keyed on the file's change time so a new image shows at once.
    val bitmap = remember(file?.path, file?.lastModified()) {
        file?.takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
    }
    when (val look = avatarLook(persona, spec, imageExists = bitmap != null)) {
        is AvatarLook.Image -> Image(
            bitmap!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
        is AvatarLook.Initials -> Box(
            modifier.size(size).background(HermesColors.AvatarPalette[look.color], CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // Below 32 dp two letters at the 12 sp floor do not fit: one letter.
            Text(if (size < 32.dp) look.text.take(1) else look.text, color = HermesColors.Background, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).coerceAtLeast(12f).sp)
        }
    }
}
