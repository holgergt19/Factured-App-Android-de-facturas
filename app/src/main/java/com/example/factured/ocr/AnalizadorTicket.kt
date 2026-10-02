package com.example.factured.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * Envoltorio sobre ML Kit: foto -> filas de texto.
 *
 * Todo ocurre en el dispositivo. Sin servidor, sin internet, sin cuotas.
 *
 * La parte importante no es llamar al OCR, es lo que viene después.
 * ML Kit agrupa el texto en bloques por proximidad, y en un ticket eso
 * significa que la columna de descripciones y la columna de importes salen como
 * bloques distintos. Su propiedad `text` los concatena uno detrás de otro:
 *
 *     1 PAN M. 55% CENTENO
 *     1 ZUMO 100% NARANJA
 *     ...
 *     1,35
 *     2,10
 *     ...
 *
 * Leído así no hay manera de emparejar cada artículo con su precio. Por eso se
 * usan las coordenadas: se agrupan los trozos que están a la misma altura y se
 * ordenan de izquierda a derecha, reconstruyendo la fila tal como se ve.
 */
object AnalizadorTicket {

    private val reconocedor by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun leer(context: Context, rutaFoto: String): TextoTicket {
        val resultado = reconocer(context, rutaFoto)
        return TextoTicket(
            filas = reconstruirFilas(extraerTrozos(resultado)),
            crudo = resultado.text
        )
    }

    private suspend fun reconocer(context: Context, rutaFoto: String): Text =
        suspendCancellableCoroutine { cont ->
            try {
                val imagen = InputImage.fromFilePath(context, Uri.fromFile(File(rutaFoto)))
                reconocedor.process(imagen)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }

    /**
     * Se trabaja al nivel de `line` y no de `element`: dentro de una columna,
     * ML Kit ya junta bien las palabras de una misma línea. Lo que hay que
     * arreglar es la separación entre columnas.
     */
    private fun extraerTrozos(texto: Text): List<TrozoOcr> =
        texto.textBlocks
            .flatMap { it.lines }
            .mapNotNull { linea ->
                val caja = linea.boundingBox ?: return@mapNotNull null
                TrozoOcr(
                    texto = linea.text.trim(),
                    x = caja.left,
                    centroY = caja.centerY(),
                    alto = caja.height()
                )
            }
            .filter { it.texto.isNotEmpty() }

    /**
     * Agrupa por altura y ordena por posición horizontal.
     *
     * La tolerancia se calcula a partir de la altura media del texto en vez de
     * ser un número fijo: así funciona igual con una foto de cerca que con una
     * de lejos, donde las letras ocupan muchos menos píxeles.
     */
    internal fun reconstruirFilas(trozos: List<TrozoOcr>): List<String> {
        if (trozos.isEmpty()) return emptyList()

        val alturaMedia = trozos.map { it.alto }.average()
        val tolerancia = (alturaMedia * 0.6).toInt().coerceAtLeast(6)

        val filas = mutableListOf<MutableList<TrozoOcr>>()

        trozos.sortedBy { it.centroY }.forEach { trozo ->
            val ultima = filas.lastOrNull()
            val alturaUltima = ultima?.map { it.centroY }?.average()

            if (alturaUltima != null && abs(alturaUltima - trozo.centroY) <= tolerancia) {
                ultima.add(trozo)
            } else {
                filas.add(mutableListOf(trozo))
            }
        }

        return filas.map { fila ->
            fila.sortedBy { it.x }.joinToString("   ") { it.texto }
        }
    }
}
