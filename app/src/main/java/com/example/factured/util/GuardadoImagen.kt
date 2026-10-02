package com.example.factured.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Las fotos viven en el almacenamiento privado de la app (context.filesDir).
 *
 * Ventaja: no hace falta ningún permiso de almacenamiento y nada más las ve.
 * Contrapartida: al desinstalar la app desaparecen. Es el trade-off aceptado
 * en el checkpoint 1.
 */
object GuardadoImagen {

    private const val CARPETA = "facturas"

    fun carpetaFotos(context: Context): File =
        File(context.filesDir, CARPETA).apply { if (!exists()) mkdirs() }

    /** Nombre con marca de tiempo para que ordenen solos y no colisionen. */
    fun nuevoArchivo(context: Context): File {
        val sello = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return File(carpetaFotos(context), "ticket_$sello.jpg")
    }

    fun borrar(ruta: String?) {
        if (ruta.isNullOrBlank()) return
        runCatching { File(ruta).takeIf { it.exists() }?.delete() }
    }


    /**
     * Reduce el JPEG ya guardado para que no ocupe una barbaridad.
     *
     * La cámara dispara a máxima calidad porque el OCR necesita el detalle,
     * pero una foto de 12 MP son 3-4 MB. Cien tickets serían casi medio giga, y
     * además el respaldo automático de Android tiene un tope de 25 MB por app.
     *
     * Por eso se comprime DESPUÉS de haber pasado el OCR: se conserva la
     * calidad donde hace falta y se ahorra donde no. Si algo falla se deja el
     * archivo original intacto; perder espacio es preferible a perder la foto.
     */
    suspend fun comprimir(ruta: String, ladoMaximo: Int = 1600, calidad: Int = 80) {
        withContext(Dispatchers.IO) {
            runCatching {
                val archivo = File(ruta)
                if (!archivo.exists()) return@runCatching

                val medir = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(ruta, medir)
                val ladoMayor = maxOf(medir.outWidth, medir.outHeight)
                if (ladoMayor <= 0) return@runCatching

                // Ya es pequeña: no merece la pena recomprimir y perder calidad.
                if (ladoMayor <= ladoMaximo) return@runCatching

                var muestreo = 1
                while (ladoMayor / (muestreo * 2) >= ladoMaximo) {
                    muestreo *= 2
                }

                val original = BitmapFactory.decodeFile(
                    ruta,
                    BitmapFactory.Options().apply { inSampleSize = muestreo }
                ) ?: return@runCatching

                val escala = ladoMaximo.toFloat() / maxOf(original.width, original.height)
                val destino = if (escala < 1f) {
                    Bitmap.createScaledBitmap(
                        original,
                        (original.width * escala).toInt().coerceAtLeast(1),
                        (original.height * escala).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    original
                }

                // Se escribe a un temporal y se renombra: si el proceso muere a
                // medias, no queda un JPEG corrupto en su sitio.
                val temporal = File(archivo.parentFile, archivo.name + ".tmp")
                FileOutputStream(temporal).use { salida ->
                    destino.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
                }
                if (temporal.length() > 0) {
                    archivo.delete()
                    temporal.renameTo(archivo)
                } else {
                    temporal.delete()
                }

                if (destino !== original) destino.recycle()
                original.recycle()
            }
        }
    }

    /**
     * Carga el JPEG reducido al tamaño que se va a mostrar.
     *
     * Una foto de 12 MP en memoria son ~48 MB. Cargarla entera para pintarla en
     * una miniatura de 48 dp revienta la app en cuanto hay unas cuantas en la
     * lista. inSampleSize hace que el decodificador salte píxeles al leer, así
     * que nunca llega a existir el bitmap grande.
     */
    suspend fun cargarEscalado(ruta: String, anchoObjetivo: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val archivo = File(ruta)
                if (!archivo.exists()) return@runCatching null

                val medir = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(ruta, medir)
                if (medir.outWidth <= 0) return@runCatching null

                var muestreo = 1
                while (medir.outWidth / muestreo > anchoObjetivo * 2) {
                    muestreo *= 2
                }

                BitmapFactory.decodeFile(
                    ruta,
                    BitmapFactory.Options().apply { inSampleSize = muestreo }
                )
            }.getOrNull()
        }
}
