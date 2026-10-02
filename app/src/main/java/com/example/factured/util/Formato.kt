package com.example.factured.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Fechas e importes.
 *
 * Se usa Calendar y SimpleDateFormat, NO java.time: minSdk es 24 y java.time
 * necesita API 26. Se podría habilitar con core library desugaring, pero para
 * lo poco que hace falta aquí no compensa.
 */

private val LOCALE_ES = Locale("es", "ES")

/** 47.85 -> "47,85 €" */
fun formatearImporte(valor: Double): String =
    NumberFormat.getCurrencyInstance(LOCALE_ES).format(valor)

/** epoch millis -> "28/08/2026" */
fun formatearFecha(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", LOCALE_ES).format(Date(millis))

/** epoch millis -> "Agosto 2026". Cabecera de cada grupo de la lista. */
fun etiquetaMes(millis: Long): String {
    val texto = SimpleDateFormat("MMMM yyyy", LOCALE_ES).format(Date(millis))
    return texto.replaceFirstChar { it.uppercase(LOCALE_ES) }
}

/**
 * epoch millis -> "2026-08". Clave interna de agrupación.
 * Se separa de la etiqueta visible porque este formato ordena bien
 * alfabéticamente y "Agosto 2026" no.
 */
fun claveMes(millis: Long): String =
    SimpleDateFormat("yyyy-MM", LOCALE_ES).format(Date(millis))

/** Primer milisegundo del mes al que pertenece [base]. */
fun inicioDelMes(base: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = base
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** Último milisegundo del mes al que pertenece [base]. */
fun finDelMes(base: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = inicioDelMes(base)
    c.add(Calendar.MONTH, 1)
    c.add(Calendar.MILLISECOND, -1)
    return c.timeInMillis
}

/**
 * El DatePicker de Material 3 trabaja en UTC y el resto de la app en hora
 * local. Sin esta conversión, según la zona horaria el día elegido se guarda
 * desplazado en uno.
 */
fun utcAFechaLocal(millisUtc: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.timeInMillis = millisUtc

    val local = Calendar.getInstance()
    local.clear()
    local.set(
        utc.get(Calendar.YEAR),
        utc.get(Calendar.MONTH),
        utc.get(Calendar.DAY_OF_MONTH),
        12, 0, 0
    )
    return local.timeInMillis
}

/** La inversa: fecha local -> el millis en UTC que espera el DatePicker. */
fun fechaLocalAUtc(millisLocal: Long): Long {
    val local = Calendar.getInstance()
    local.timeInMillis = millisLocal

    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.clear()
    utc.set(
        local.get(Calendar.YEAR),
        local.get(Calendar.MONTH),
        local.get(Calendar.DAY_OF_MONTH),
        0, 0, 0
    )
    return utc.timeInMillis
}

/** Lee un importe tecleado por el usuario, aceptando coma o punto. */
fun leerImporte(texto: String): Double? =
    texto.trim().replace(" ", "").replace(",", ".").toDoubleOrNull()
