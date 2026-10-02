package com.example.factured.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un artículo dentro de una factura.
 *
 * CASCADE: al borrar la factura, Room borra sus líneas.
 * El índice sobre facturaId lo exige Room y acelera la consulta por factura.
 */
@Entity(
    tableName = "lineas",
    foreignKeys = [
        ForeignKey(
            entity = Factura::class,
            parentColumns = ["id"],
            childColumns = ["facturaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("facturaId")]
)
data class LineaFactura(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val facturaId: Long,
    val descripcion: String,
    val importe: Double
)
