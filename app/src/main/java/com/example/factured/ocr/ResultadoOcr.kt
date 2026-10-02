package com.example.factured.ocr

/**
 * Un trozo de texto reconocido con su posición en la foto.
 *
 * La posición es imprescindible: ML Kit devuelve el ticket por columnas, no por
 * filas. Sin las coordenadas no hay forma de saber que "1 PAN M. 55% CENTENO" y
 * "1,35" son la misma línea del ticket.
 */
data class TrozoOcr(
    val texto: String,
    val x: Int,
    val centroY: Int,
    val alto: Int
)

/** El resultado del OCR ya reordenado en filas visuales. */
data class TextoTicket(
    /** Cada elemento es una fila del ticket, con sus columnas unidas. */
    val filas: List<String>,
    /** El texto tal cual lo devolvió ML Kit, para guardarlo y depurar. */
    val crudo: String
)

/** Un artículo que el parser cree haber encontrado. */
data class LineaDetectada(
    val descripcion: String,
    val importe: Double
)

/**
 * Lo que el parser deduce del ticket.
 *
 * Todos los campos son opcionales a propósito: cualquiera puede fallar y la
 * pantalla de confirmación tiene que poder mostrar el hueco vacío.
 */
data class ResultadoOcr(
    val comercio: String?,
    val fechaEmision: Long?,
    val total: Double?,
    val lineas: List<LineaDetectada>,
    val textoCrudo: String
)
