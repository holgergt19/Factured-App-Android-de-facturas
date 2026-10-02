package com.example.factured.data

import androidx.room.Embedded
import androidx.room.Relation

/** No es una tabla: es cómo Room devuelve una factura junto con sus artículos. */
data class FacturaConLineas(
    @Embedded val factura: Factura,

    @Relation(
        parentColumn = "id",
        entityColumn = "facturaId"
    )
    val lineas: List<LineaFactura>
)
