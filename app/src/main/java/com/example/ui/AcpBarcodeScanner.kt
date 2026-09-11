package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.acp.AcpBarcodeGate
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AcpBarcodeScanner(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun cameraAllowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var permitted by remember { mutableStateOf(cameraAllowed()) }
    var requested by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    LaunchedEffect(Unit) {
        if (!permitted && !requested) { requested = true; permission.launch(Manifest.permission.CAMERA) }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permitted = cameraAllowed() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = { TopAppBar(title = { Text("Ler código de barras") }, navigationIcon = {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fechar câmera") }
        }) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (permitted) {
                    Text("Aponte para um único código de barras. A busca será automática.")
                    AcpCameraPreview(Modifier.weight(1f).fillMaxWidth(), onResult)
                } else {
                    Text("Permita o acesso à câmera para ler o código. Você também pode fechar e digitá-lo na consulta.")
                    Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Permitir câmera") }
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }) { Text("Abrir permissões do aplicativo") }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun AcpCameraPreview(modifier: Modifier, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentResult by rememberUpdatedState(onResult)
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var error by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    DisposableEffect(owner, attempt) {
        val main = ContextCompat.getMainExecutor(context)
        val executor = Executors.newSingleThreadExecutor()
        val disposed = AtomicBoolean(false)
        val processing = AtomicBoolean(false)
        val scannerClosed = AtomicBoolean(false)
        val gate = AcpBarcodeGate()
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(
            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_ITF, Barcode.FORMAT_CODABAR
        ).build())
        fun closeScanner() { if (scannerClosed.compareAndSet(false, true)) scanner.close() }
        val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var provider: ProcessCameraProvider? = null
        error = null
        analysis.setAnalyzer(executor) { proxy ->
            if (disposed.get() || !processing.compareAndSet(false, true)) {
                proxy.close()
            } else {
                val media = proxy.image
                if (media == null) {
                    proxy.close()
                    processing.set(false)
                    if (disposed.get()) closeScanner()
                }
                else try {
                    scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener(main) { barcodes ->
                            if (!disposed.get()) gate.accept(barcodes.map { it.rawValue })?.let { currentResult(it) }
                        }
                        .addOnFailureListener(main) { if (!disposed.get()) error = "Não foi possível ler a imagem. Tente novamente." }
                        .addOnCompleteListener(main) {
                            proxy.close()
                            processing.set(false)
                            if (disposed.get()) closeScanner()
                        }
                } catch (_: Exception) {
                    proxy.close()
                    processing.set(false)
                    main.execute { if (!disposed.get()) error = "Não foi possível iniciar o leitor." else closeScanner() }
                }
            }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed.get()) try {
                val ready = future.get()
                provider = ready
                camera = ready.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {
                error = "Não foi possível abrir a câmera. Feche outros aplicativos que estejam usando a câmera e tente novamente."
            }
        }, main)
        onDispose {
            disposed.set(true)
            analysis.clearAnalyzer()
            camera?.cameraControl?.enableTorch(false)
            provider?.unbind(preview, analysis) // Do not unbind another NRD camera use case.
            camera = null
            torch = false
            executor.shutdown()
            if (!processing.get()) closeScanner()
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AndroidView(factory = { previewView }, modifier = Modifier.weight(1f).fillMaxWidth())
        if (camera?.cameraInfo?.hasFlashUnit() == true) OutlinedButton(onClick = {
            val enabled = !torch
            camera?.cameraControl?.enableTorch(enabled)?.let { change ->
                change.addListener({
                    runCatching { change.get() }.onSuccess { torch = enabled }
                        .onFailure { error = "Não foi possível alterar a lanterna." }
                }, ContextCompat.getMainExecutor(context))
            }
        }) { Text(if (torch) "Desligar lanterna" else "Ligar lanterna") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { attempt++ }) { Text("Tentar novamente") } }
    }
}
