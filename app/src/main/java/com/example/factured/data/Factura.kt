package com.example.factured.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una factura escaneada.
 *
 * Las fechas se guardan como epoch millis porque Room no persiste tipos de
 * fecha y minSdk 24 no permite java.time sin desugaring.
 */
@Entity(tableName = "facturas")
data class Factura(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Fecha impresa en el ticket. */
    val fechaEmision: Long,

    /** Momento en que se escaneó. */
    val fechaRegistro: Long,

    val comercio: String,
    val total: Double,

    /** Ruta absoluta del JPEG en el almacenamiento privado de la app. */
    val rutaFoto: String? = null,

    /** Texto tal cual lo devolvió el OCR, para reprocesar sin reescanear. */
    val textoOcrCrudo: String? = null,

    val notas: String? = null
)
