package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.casting.CastHelper
import java.io.File

@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    mediaTitle: String = "Media",
    mediaUrl: String? = null,
    localFile: File? = null,
    headers: Map<String, String> = emptyMap(),
    mimeType: String = "video/mp4"
) {
    val context = LocalContext.current
    val castHelper = remember { CastHelper.getInstance(context) }
    val isCasting by castHelper.isCasting.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = { showDialog = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                contentDescription = "Universal Cast (DLNA / UPnP / Chromecast)",
                tint = if (isCasting) Color(0xFF4CAF50) else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showDialog) {
        UniversalCastDialog(
            mediaTitle = mediaTitle,
            mediaUrl = mediaUrl,
            localFile = localFile,
            headers = headers,
            mimeType = mimeType,
            onDismissRequest = { showDialog = false }
        )
    }
}

