import re

with open('app/src/main/java/com/example/ui/AboutScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

imports_to_add = """
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import android.graphics.Bitmap
import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.Color
import com.google.zxing.MultiFormatWriter
import com.google.zxing.BarcodeFormat
"""

content = content.replace("package com.example.ui", "package com.example.ui\n" + imports_to_add)

state_variables = """
            var isGeneratingQr by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var showQrDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var qrBitmap by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Bitmap?>(null) }
            var qrReleaseTag by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            var qrReleaseUrl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            var showQrErrorDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
"""

content = content.replace('if (showUpdateDialog) {', state_variables + '\n            if (showUpdateDialog) {')

qr_dialogs = """
            if (showQrDialog && qrBitmap != null) {
                AlertDialog(
                    onDismissRequest = { showQrDialog = false },
                    title = { Text("Compartilhar NRD Códigos") },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Versão disponível: $qrReleaseTag", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = "QR Code para download",
                                modifier = Modifier.size(200.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Escaneie para baixar esta versão", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = ClipData.newPlainText("Link de Download", qrReleaseUrl)
                            clipboardManager.setPrimaryClip(clipData)
                            android.widget.Toast.makeText(context, "Link copiado!", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Copiar link")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showQrDialog = false }) {
                            Text("Fechar")
                        }
                    }
                )
            }

            if (showQrErrorDialog) {
                AlertDialog(
                    onDismissRequest = { showQrErrorDialog = false },
                    title = { Text("Não foi possível gerar o QR Code no momento.") },
                    text = { Text("Verifique sua conexão e tente novamente.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showQrErrorDialog = false
                            isGeneratingQr = true
                            coroutineScope.launch {
                                val release = com.example.util.UpdateChecker.checkLatestRelease()
                                isGeneratingQr = false
                                if (release != null) {
                                    val (tag, url) = release
                                    qrReleaseTag = tag
                                    qrReleaseUrl = url
                                    try {
                                        val writer = MultiFormatWriter()
                                        val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, 512, 512)
                                        val width = bitMatrix.width
                                        val height = bitMatrix.height
                                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                                        for (x in 0 until width) {
                                            for (y in 0 until height) {
                                                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                                            }
                                        }
                                        qrBitmap = bmp
                                        showQrDialog = true
                                    } catch (e: Exception) {
                                        showQrErrorDialog = true
                                    }
                                } else {
                                    showQrErrorDialog = true
                                }
                            }
                        }) {
                            Text("Tentar novamente")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showQrErrorDialog = false }) {
                            Text("Fechar")
                        }
                    }
                )
            }
"""

qr_button = """
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    isGeneratingQr = true
                    coroutineScope.launch {
                        val release = com.example.util.UpdateChecker.checkLatestRelease()
                        isGeneratingQr = false
                        if (release != null) {
                            val (tag, url) = release
                            qrReleaseTag = tag
                            qrReleaseUrl = url
                            try {
                                val writer = MultiFormatWriter()
                                val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, 512, 512)
                                val width = bitMatrix.width
                                val height = bitMatrix.height
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                                for (x in 0 until width) {
                                    for (y in 0 until height) {
                                        bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                                    }
                                }
                                qrBitmap = bmp
                                showQrDialog = true
                            } catch (e: Exception) {
                                showQrErrorDialog = true
                            }
                        } else {
                            showQrErrorDialog = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isGeneratingQr
            ) {
                if (isGeneratingQr) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Gerar versão em QR Code")
                }
            }
"""

content = content.replace('if (showUpdateDialog) {', qr_dialogs + '\n            if (showUpdateDialog) {')

target_button_end = '''                } else {
                    Text("Verificar Atualizações")
                }
            }'''

content = content.replace(target_button_end, target_button_end + '\n' + qr_button)

with open('app/src/main/java/com/example/ui/AboutScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
