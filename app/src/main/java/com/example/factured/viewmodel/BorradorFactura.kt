package com.example.factured.viewmodel

import com.example.factured.data.Factura
import com.example.factured.data.FacturaConLineas
import com.example.factured.ocr.ResultadoOcr

/** Una línea mientras se está editando: el importe es texto, no número. */
data class LineaBorrador(
    val descripcion: String = "",
    val importe: String = ""
)

/**
 * Lo que se está editando en la pantalla de confirmación.
 *
 * Los importes y la fecha se guardan como String / Long "en bruto" porque
 * mientras el usuario teclea el contenido puede no ser un número válido
 * todavía. La conversión se hace al guardar.
 */
data class BorradorFactura(
    /** null si es una factura nueva, el id si se está editando una existente. */
    val id: Long? = null,
    val rutaFoto: String? = null,
    val comercio: String = "",
    val fechaEmision: Long = System.currentTimeMillis(),
    val total: String = "",
    val notas: String = "",
    val lineas: List<LineaBorrador> = emptyList(),
    val textoOcrCrudo: String? = null,

    /** Para avisar en pantalla de que el OCR no encontró el importe. */
    val totalDetectado: Boolean = false,
    val fechaDetectada: Boolean = false
) {
    companion object {

        /** Construye el borrador a partir de lo que dedujo el parser. */
        fun desdeOcr(rutaFoto: String, ocr: ResultadoOcr): BorradorFactura =
            BorradorFactura(
                rutaFoto = rutaFoto,
                comercio = ocr.comercio.orEmpty(),
                fechaEmision = ocr.fechaEmision ?: System.currentTimeMillis(),
                total = ocr.total?.let { formatearParaEdicion(it) }.orEmpty(),
                lineas = ocr.lineas.map {
                    LineaBorrador(it.descripcion, formatearParaEdicion(it.importe))
                },
                textoOcrCrudo = ocr.textoCrudo,
                totalDetectado = ocr.total != null,
                fechaDetectada = ocr.fechaEmision != null
            )

        /** Para reabrir una factura ya guardada en modo edición. */
        fun desdeFactura(datos: FacturaConLineas): BorradorFactura =
            BorradorFactura(
                id = datos.factura.id,
                rutaFoto = datos.factura.rutaFoto,
                comercio = datos.factura.comercio,
                fechaEmision = datos.factura.fechaEmision,
                total = formatearParaEdicion(datos.factura.total),
                notas = datos.factura.notas.orEmpty(),
                lineas = datos.lineas.map {
                    LineaBorrador(it.descripcion, formatearParaEdicion(it.importe))
                },
                textoOcrCrudo = datos.factura.textoOcrCrudo,
                totalDetectado = true,
                fechaDetectada = true
            )

        /**
         * Con coma decimal, que es lo que espera un teclado español, y sin
         * separador de miles para que el campo sea fácil de reescribir.
         */
        private fun formatearParaEdicion(valor: Double): String =
            String.format(java.util.Locale.US, "%.2f", valor).replace('.', ',')
    }

    /** Solo para mostrar la factura resultante; el guardado real va por el repositorio. */
    fun aFactura(total: Double): Factura = Factura(
        id = id ?: 0,
        fechaEmision = fechaEmision,
        fechaRegistro = System.currentTimeMillis(),
        comercio = comercio.trim().ifBlank { "Sin nombre" },
        total = total,
        rutaFoto = rutaFoto,
        textoOcrCrudo = textoOcrCrudo,
        notas = notas.trim().ifBlank { null }
    )
}
