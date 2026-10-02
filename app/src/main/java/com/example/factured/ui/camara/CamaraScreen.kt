package com.example.factured.ui.camara

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.factured.util.GuardadoImagen
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Vista previa de la cámara y captura del ticket.
 *
 * Al capturar guarda el JPEG en el almacenamiento privado y devuelve la ruta
 * por [onCapturada]. El OCR no se lanza aquí: esta pantalla solo hace foto.
 */
@Composable
fun CamaraScreen(
    onCapturada: (String) -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var tienePermiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permisoRechazado by remember { mutableStateOf(false) }

    val solicitarPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        tienePermiso = concedido
        permisoRechazado = !concedido
    }

    LaunchedEffect(Unit) {
        if (!tienePermiso) solicitarPermiso.launch(Manifest.permission.CAMERA)
    }

    if (!tienePermiso) {
        SinPermiso(
            rechazado = permisoRechazado,
            onReintentar = { solicitarPermiso.launch(Manifest.permission.CAMERA) },
            onCancelar = onCancelar,
            modifier = modifier
        )
        return
    }

    // El PreviewView es una View clásica; CameraX todavía no tiene equivalente
    // nativo en Compose, así que se incrusta con AndroidView.
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            // Prioriza nitidez sobre velocidad: el OCR agradece el detalle.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    var camara by remember { mutableStateOf<Camera?>(null) }
    var proveedor by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var linterna by remember { mutableStateOf(false) }
    var capturando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val cp = obtenerProveedor(context)
            proveedor = cp

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // unbindAll antes de enlazar: si la pantalla se recompone, evita
            // que queden dos usos de la cámara compitiendo.
            cp.unbindAll()
            camara = cp.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }.onFailure {
            error = "No se pudo abrir la cámara: ${it.message}"
        }
    }

    // Suelta la cámara al salir; si no, la siguiente pantalla que la pida falla.
    DisposableEffect(Unit) {
        onDispose { proveedor?.unbindAll() }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Guía de encuadre. No recorta nada, solo orienta al usuario.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 120.dp)
                .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        )

        Text(
            text = "Encuadra el ticket completo,\nincluida la línea del total",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
        )

        IconButton(
            onClick = onCancelar,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Cancelar", tint = Color.White)
        }

        error?.let { mensaje ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Text(mensaje, modifier = Modifier.padding(16.dp))
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                linterna = !linterna
                camara?.cameraControl?.enableTorch(linterna)
            }) {
                Text(
                    text = if (linterna) "Linterna ON" else "Linterna",
                    color = Color.White
                )
            }

            // Botón de disparo. Se bloquea mientras la foto se escribe a disco
            // para que un doble toque no cree dos archivos.
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(74.dp)
            ) {
                IconButton(
                    onClick = {
                        if (capturando) return@IconButton
                        capturando = true
                        capturar(
                            context = context,
                            imageCapture = imageCapture,
                            onListo = { ruta ->
                                capturando = false
                                onCapturada(ruta)
                            },
                            onError = {
                                capturando = false
                                error = it
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .border(3.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
                    )
                }
            }

            TextButton(onClick = onCancelar) {
                Text("Cancelar", color = Color.White)
            }
        }
    }
}

private fun capturar(
    context: Context,
    imageCapture: ImageCapture,
    onListo: (String) -> Unit,
    onError: (String) -> Unit
) {
    val archivo = GuardadoImagen.nuevoArchivo(context)
    val opciones = ImageCapture.OutputFileOptions.Builder(archivo).build()

    imageCapture.takePicture(
        opciones,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                onListo(archivo.absolutePath)
            }

            override fun onError(excepcion: ImageCaptureException) {
                onError("No se pudo guardar la foto: ${excepcion.message}")
            }
        }
    )
}

/**
 * ProcessCameraProvider.getInstance devuelve un ListenableFuture de Guava.
 * Esto lo convierte en una función suspend sin arrastrar
 * kotlinx-coroutines-guava como dependencia extra.
 */
private suspend fun obtenerProveedor(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val futuro = ProcessCameraProvider.getInstance(context)
        futuro.addListener(
            {
                try {
                    cont.resume(futuro.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

@Composable
private fun SinPermiso(
    rechazado: Boolean,
    onReintentar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = if (rechazado) {
                "Sin permiso de cámara no se pueden escanear tickets. " +
                    "Si no vuelve a preguntarte, actívalo en Ajustes → Aplicaciones → Factured → Permisos."
            } else {
                "Concede el permiso de cámara para escanear tickets."
            },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 20.dp)
        )
        Button(onClick = onReintentar) { Text("Conceder permiso") }
        TextButton(onClick = onCancelar) { Text("Volver") }
    }
}
