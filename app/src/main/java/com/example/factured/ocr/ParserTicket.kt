package com.example.factured.ocr

import java.util.Locale
import kotlin.math.abs

/**
 * Convierte las filas reconstruidas del ticket en total, fecha, comercio y
 * artículos.
 *
 * No hay modelo entrenado: son reglas escritas a mano. Es la parte que más se
 * va a tocar, y por eso se guarda el texto crudo en la base de datos: permite
 * reprocesar facturas viejas sin volver a escanearlas.
 *
 * El orden de las tres decisiones importa:
 *   1. Cortar la zona fiscal (tabla de IVA), que contiene un "TOTAL" falso.
 *   2. Localizar el total real por encima de ese corte.
 *   3. Extraer los artículos entre la cabecera de la tabla y el total.
 * Y al final, si la suma de los artículos coincide con otro candidato, se
 * corrige el total: es la comprobación más fiable de todas.
 */
object ParserTicket {

    /** Palabras que acompañan al importe final. */
    private val CLAVES_TOTAL = listOf(
        "TOTAL A PAGAR", "IMPORTE TOTAL", "TOTAL COMPRA", "TOTAL TICKET",
        "TOTAL (", "TOTAL(", "A PAGAR", "TOTAL", "IMPORTE", "SUMA"
    )

    /** Descartan una fila aunque contenga "TOTAL". */
    private val CLAVES_EXCLUIDAS = listOf(
        "SUBTOTAL", "SUB-TOTAL", "BASE", "IVA", "I.V.A", "ENTREGADO", "CAMBIO",
        "DEVUELTA", "DEVOLUCION", "EFECTIVO", "TARJETA", "TARJ", "CONTADO",
        "DESCUENTO", "AHORRO", "PUNTOS", "SALDO", "CUOTA", "UNIT", "AUT:",
        "APORTACION", "REDONDEO"
    )

    /**
     * A partir de aquí empieza el desglose fiscal y ya no hay nada útil.
     *
     * Este corte es lo que arregla el caso de Mercadona: la tabla de IVA
     * termina con una fila "TOTAL 23,01 1,79" que es la suma de bases
     * imponibles, no lo que se pagó.
     */
    private val INICIO_ZONA_FISCAL = listOf(
        "BASE IMPONIBLE", "BASE IMP", "CUOTA", "TIPO IVA", "% IVA", "IVA %",
        "DESGLOSE", "B.IMPONIBLE"
    )

    /** La cabecera de la tabla de artículos. Todo lo de arriba es cabecera del ticket. */
    private val CABECERA_ARTICULOS = listOf(
        "DESCRIPCION", "DESCRIPCIÓN", "P. UNIT", "P.UNIT", "PRECIO", "ARTICULO",
        "ARTÍCULO", "CONCEPTO", "UD.", "CANT"
    )

    /** Cadenas conocidas. Es lo más fiable para el nombre del comercio. */
    private val CADENAS = listOf(
        "MERCADONA", "LIDL", "CARREFOUR", "ALCAMPO", "EROSKI", "CONSUM", "ALDI",
        "DIA", "HIPERCOR", "EL CORTE INGLES", "SUPERCOR", "AHORRAMAS", "COVIRAN",
        "SUPERSOL", "CONDIS", "CAPRABO", "BONPREU", "GADIS", "FROIZ", "SPAR",
        "MASYMAS", "BONAREA", "FAMILY CASH", "PRIMAPRIX",
        "REPSOL", "CEPSA", "BP", "SHELL", "GALP", "BALLENOIL", "PETROPRIX",
        "DECATHLON", "IKEA", "LEROY MERLIN", "BRICOMART", "OBRAMAT", "BAUHAUS",
        "MEDIA MARKT", "WORTEN", "FNAC", "PCCOMPONENTES", "NORAUTO", "FEU VERT",
        "PRIMOR", "DRUNI", "ZARA", "PRIMARK", "ACTION", "TIGER", "JYSK",
        "KIWOKO", "TIENDANIMAL", "BURGER KING", "MCDONALD", "TELEPIZZA",
        "STARBUCKS", "FARMACIA", "PARAFARMACIA"
    )

    /** Palabras que delatan que una fila es dirección, contacto o dato fiscal. */
    private val RUIDO_CABECERA = listOf(
        "CIF", "NIF", "N.I.F", "C.I.F", "TEL", "TLF", "TELEFONO", "TELÉFONO",
        "FACTURA", "TICKET", "SIMPLIFICADA", "CALLE", "C/", "AVDA", "AVENIDA",
        "PLAZA", "BULEVAR", "BOULEVARD", "PASEO", "POLIGONO", "POLÍGONO",
        "CTRA", "CARRETERA", "RONDA", "TRAVESIA", "TRAVESÍA", "URB", "LOCAL",
        "WWW", ".COM", ".ES", "@", "GRACIAS", "VISITA", "ATENDIDO", "CAJA",
        "OP:", "N.º", "Nº"
    )

    /** Un importe: 12,50 · 12.50 · 1.234,56 · 1 234,56 */
    private val REGEX_IMPORTE =
        Regex("""(?<![\d.,])(\d{1,3}(?:[.\s]\d{3})*|\d+)[.,](\d{2})(?![\d])""")

    /** dd/MM/yyyy · dd-MM-yy · dd.MM.yyyy */
    private val REGEX_FECHA =
        Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})\b""")

    /** yyyy-MM-dd, en tickets de algunas cadenas. */
    private val REGEX_FECHA_ISO =
        Regex("""\b(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})\b""")

    /** Código postal español, para descartar la línea de la dirección. */
    private val REGEX_CP = Regex("""\b\d{5}\b""")

    fun analizar(ticket: TextoTicket): ResultadoOcr {
        val filas = ticket.filas.map { it.trim() }.filter { it.isNotEmpty() }

        val corteFiscal = localizarZonaFiscal(filas)
        val indiceTotal = localizarFilaTotal(filas, corteFiscal)
        val articulos = extraerArticulos(filas, indiceTotal, corteFiscal)
        val total = decidirTotal(filas, indiceTotal, corteFiscal, articulos)

        return ResultadoOcr(
            comercio = extraerComercio(filas),
            fechaEmision = extraerFecha(filas),
            total = total,
            // Si un artículo coincide exactamente con el total elegido, es la
            // fila del total colada entre los artículos.
            lineas = articulos.filter { total == null || abs(it.importe - total) > 0.001 },
            textoCrudo = ticket.crudo
        )
    }

    // ------------------------------------------------------------------
    // Zona fiscal
    // ------------------------------------------------------------------

    /** Índice donde empieza el desglose de IVA, o el final si no lo hay. */
    private fun localizarZonaFiscal(filas: List<String>): Int {
        filas.forEachIndexed { i, fila ->
            val mayus = normalizar(fila)
            if (INICIO_ZONA_FISCAL.any { mayus.contains(it) }) return i
        }
        return filas.size
    }

    // ------------------------------------------------------------------
    // Total
    // ------------------------------------------------------------------

    /**
     * Busca de abajo arriba pero solo dentro de la zona útil: el total de
     * verdad está al final de la lista de artículos, justo antes del desglose
     * de impuestos.
     */
    private fun localizarFilaTotal(filas: List<String>, corteFiscal: Int): Int {
        for (i in (corteFiscal - 1) downTo 0) {
            val mayus = normalizar(filas[i])
            if (CLAVES_EXCLUIDAS.any { mayus.contains(it) }) continue
            if (CLAVES_TOTAL.any { mayus.contains(it) }) return i
        }
        return -1
    }

    private fun decidirTotal(
        filas: List<String>,
        indiceTotal: Int,
        corteFiscal: Int,
        articulos: List<LineaDetectada>
    ): Double? {
        val candidatos = mutableListOf<Double>()

        if (indiceTotal >= 0) {
            // El importe suele ir en la misma fila que la palabra.
            ultimoImporte(filas[indiceTotal])?.let { candidatos.add(it) }
            // Si no, el OCR lo habrá dejado en la fila siguiente.
            filas.getOrNull(indiceTotal + 1)?.let { ultimoImporte(it)?.let(candidatos::add) }
        }

        // Las filas de pago ("TARJETA BANCARIA 24,80", "Importe: 24,80 €")
        // reflejan lo que se cobró de verdad. Sirven de contraste.
        filas.forEach { fila ->
            val mayus = normalizar(fila)
            if (mayus.contains("TARJETA") || mayus.contains("TARJ ") ||
                mayus.contains("EFECTIVO") || mayus.startsWith("IMPORTE")
            ) {
                ultimoImporte(fila)?.let { candidatos.add(it) }
            }
        }

        // Cruce con los artículos: si su suma coincide con alguno de los
        // candidatos, ese es casi con total seguridad el bueno. Es la
        // comprobación más fiable que tenemos.
        if (articulos.isNotEmpty()) {
            val suma = articulos.sumOf { it.importe }
            candidatos.firstOrNull { abs(it - suma) <= 0.02 }?.let { return it }
        }

        candidatos.firstOrNull()?.let { return it }

        // Nada reconocible: el importe más alto de la zona útil suele ser el
        // total. Es un apaño, pero acierta más veces de las que falla.
        return filas.take(if (corteFiscal > 0) corteFiscal else filas.size)
            .flatMap { importesDe(it) }
            .maxOrNull()
    }

    // ------------------------------------------------------------------
    // Fecha
    // ------------------------------------------------------------------

    private fun extraerFecha(filas: List<String>): Long? {
        for (fila in filas) {
            REGEX_FECHA.find(fila)?.let { m ->
                val dia = m.groupValues[1].toIntOrNull()
                val mes = m.groupValues[2].toIntOrNull()
                val anio = m.groupValues[3].toIntOrNull()
                if (dia != null && mes != null && anio != null) {
                    aFecha(dia, mes, normalizarAnio(anio))?.let { return it }
                }
            }
            REGEX_FECHA_ISO.find(fila)?.let { m ->
                val anio = m.groupValues[1].toIntOrNull()
                val mes = m.groupValues[2].toIntOrNull()
                val dia = m.groupValues[3].toIntOrNull()
                if (dia != null && mes != null && anio != null) {
                    aFecha(dia, mes, anio)?.let { return it }
                }
            }
        }
        return null
    }

    private fun normalizarAnio(anio: Int): Int = when (anio) {
        in 0..79 -> 2000 + anio
        in 80..99 -> 1900 + anio
        else -> anio
    }

    private fun aFecha(dia: Int, mes: Int, anio: Int): Long? {
        if (dia !in 1..31 || mes !in 1..12 || anio !in 2000..2100) return null

        val c = java.util.Calendar.getInstance()
        c.clear()
        // Mediodía y no medianoche: evita que un cambio de zona horaria
        // desplace la factura al día anterior.
        c.set(anio, mes - 1, dia, 12, 0, 0)

        // Una fecha futura es casi siempre un error de lectura.
        val margen = java.util.Calendar.getInstance()
        margen.add(java.util.Calendar.DAY_OF_YEAR, 2)
        if (c.timeInMillis > margen.timeInMillis) return null

        return c.timeInMillis
    }

    // ------------------------------------------------------------------
    // Comercio
    // ------------------------------------------------------------------

    /**
     * Primero se busca una cadena conocida, que es lo que casi siempre acierta.
     * Solo si no aparece ninguna se recurre a la heurística de "primera línea
     * que parece un nombre", que es la que confundió la dirección de Mercadona
     * con el nombre del establecimiento.
     */
    private fun extraerComercio(filas: List<String>): String? {
        val cabecera = filas.take(10)

        cabecera.forEach { fila ->
            val mayus = normalizar(fila)
            CADENAS.firstOrNull { cadena ->
                // Palabra completa, para que "DIA" no salte dentro de "MEDIA".
                Regex("(^|[^A-Z0-9])${Regex.escape(cadena)}([^A-Z0-9]|$)").containsMatchIn(mayus)
            }?.let { return it.lowercase(Locale.ROOT).replaceFirstChar { c -> c.uppercase() } }
        }

        return cabecera.take(6).firstOrNull { fila ->
            val mayus = normalizar(fila)
            val letras = fila.count { it.isLetter() }

            fila.length in 3..40 &&
                letras >= 3 &&
                letras.toDouble() / fila.length > 0.5 &&
                RUIDO_CABECERA.none { mayus.contains(it) } &&
                !REGEX_CP.containsMatchIn(fila) &&
                // Una dirección casi siempre termina en ", 17" o similar.
                !Regex(""",\s*\d{1,4}\s*$""").containsMatchIn(fila) &&
                REGEX_IMPORTE.find(fila) == null
        }?.trim()
    }

    // ------------------------------------------------------------------
    // Artículos
    // ------------------------------------------------------------------

    /**
     * Se queda con las filas que tienen texto a la izquierda e importe a la
     * derecha, entre la cabecera de la tabla y la fila del total.
     *
     * Esto solo funciona porque AnalizadorTicket ya reconstruyó las filas a
     * partir de las coordenadas. Sobre el texto plano de ML Kit no habría nada
     * que emparejar.
     */
    private fun extraerArticulos(
        filas: List<String>,
        indiceTotal: Int,
        corteFiscal: Int
    ): List<LineaDetectada> {
        val fin = when {
            indiceTotal > 0 -> indiceTotal
            corteFiscal in 1 until filas.size -> corteFiscal
            else -> filas.size
        }

        val inicio = localizarCabeceraArticulos(filas, fin)
        if (inicio >= fin) return emptyList()

        return filas.subList(inicio, fin).mapNotNull { fila ->
            val mayus = normalizar(fila)
            if (CLAVES_EXCLUIDAS.any { mayus.contains(it) }) return@mapNotNull null
            if (CLAVES_TOTAL.any { mayus.contains(it) }) return@mapNotNull null

            // El importe de la derecha es el que cuenta:
            // "2 MUESLI CON FRUTAS   2,10   4,20" -> 4,20
            val importe = ultimoImporte(fila) ?: return@mapNotNull null
            if (importe <= 0.0) return@mapNotNull null

            val descripcion = fila
                .replace(REGEX_IMPORTE, " ")
                .replace(Regex("""[€$]"""), " ")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
                .trimEnd('x', 'X', '*', '-', ':', '.')
                .trim()

            if (descripcion.count { it.isLetter() } < 3) return@mapNotNull null

            LineaDetectada(descripcion = descripcion, importe = importe)
        }
    }

    /** Justo debajo de "Descripción P.Unit Imp." empiezan los artículos. */
    private fun localizarCabeceraArticulos(filas: List<String>, fin: Int): Int {
        for (i in 0 until fin) {
            val mayus = normalizar(filas[i])
            if (CABECERA_ARTICULOS.any { mayus.contains(it) }) return i + 1
        }
        // Sin cabecera reconocible, se saltan las dos primeras filas
        // (nombre y dirección) y se prueba suerte.
        return minOf(2, fin)
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private fun normalizar(texto: String): String = texto.uppercase(Locale.ROOT)

    private fun importesDe(fila: String): List<Double> =
        REGEX_IMPORTE.findAll(fila).mapNotNull { aDouble(it.value) }.toList()

    private fun ultimoImporte(fila: String): Double? = importesDe(fila).lastOrNull()

    /** "1.234,56" o "1 234.56" -> 1234.56 */
    private fun aDouble(texto: String): Double? {
        val limpio = texto.trim()
        val separador = maxOf(limpio.lastIndexOf(','), limpio.lastIndexOf('.'))
        if (separador < 0) return limpio.toDoubleOrNull()

        val entero = limpio.substring(0, separador).replace(Regex("""[.,\s]"""), "")
        val decimal = limpio.substring(separador + 1)
        return "$entero.$decimal".toDoubleOrNull()
    }
}
