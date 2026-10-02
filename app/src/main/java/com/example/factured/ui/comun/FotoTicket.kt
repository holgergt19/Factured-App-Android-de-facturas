package com.example.factured.ui.comun

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.factured.util.GuardadoImagen

/**
 * Muestra la foto de un ticket guardada en disco.
 *
 * produceState hace la carga fuera del hilo principal y va emitiendo: la
 * pantalla se dibuja inmediatamente con el hueco y la imagen aparece cuando
 * está lista. Decodificar un JPEG dentro de la composición congelaría la UI.
 */
@Composable
fun FotoTicket(
    ruta: String?,
    anchoObjetivoPx: Int,
    modifier: Modifier = Modifier,
    escala: ContentScale = ContentScale.Crop,
    textoSinFoto: String = "Sin foto"
) {
    val context = LocalContext.current

    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, ruta) {
        value = ruta?.let { GuardadoImagen.cargarEscalado(it, anchoObjetivoPx) }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val actual = bitmap
        if (actual != null) {
            Image(
                bitmap = actual.asImageBitmap(),
                contentDescription = "Foto del ticket",
                contentScale = escala,
                modifier = Modifier.fillMaxSize()
            )
        } else if (ruta == null) {
            Text(
                text = textoSinFoto,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
