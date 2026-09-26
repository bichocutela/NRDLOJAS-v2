package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

@Composable
internal fun NossaGenteProfileAvatar(
    photoModel: Any?,
    size: Dp,
    iconSize: Dp,
    shape: Shape,
    backgroundColor: Color,
    iconTint: Color,
    onPhotoClick: (() -> Unit)? = null
) {
    var photoFailed by remember(photoModel) { mutableStateOf(false) }
    val hasPhoto = photoModel != null && !photoFailed

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (hasPhoto && onPhotoClick != null) Modifier.clickable(onClick = onPhotoClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
        if (hasPhoto) {
            AsyncImage(
                model = photoModel,
                contentDescription = "Foto do perfil",
                modifier = Modifier.matchParentSize().clip(shape),
                contentScale = ContentScale.Crop,
                onError = { photoFailed = true }
            )
        }
    }
}

@Composable
internal fun NossaGenteProfilePhotoDialog(
    photoModel: Any,
    userName: String?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    userName?.takeIf { it.isNotBlank() } ?: "Foto do perfil",
                    style = MaterialTheme.typography.titleMedium
                )
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = photoModel,
                        contentDescription = "Foto ampliada do perfil",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Fechar")
                }
            }
        }
    }
}
