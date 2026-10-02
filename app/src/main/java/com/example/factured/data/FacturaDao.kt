package com.example.factured.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Las funciones Flow son reactivas: Room reemite sola cada vez que cambia la
 * tabla, así que la UI se refresca sin que nadie la avise.
 * Las suspend son de un disparo y se llaman desde una corrutina.
 */
@Dao
interface FacturaDao {

    @Query("SELECT * FROM facturas ORDER BY fechaEmision DESC, id DESC")
    fun observarTodas(): Flow<List<Factura>>

    @Query(
        """
        SELECT * FROM facturas
        WHERE comercio LIKE '%' || :texto || '%'
           OR id IN (SELECT facturaId FROM lineas WHERE descripcion LIKE '%' || :texto || '%')
        ORDER BY fechaEmision DESC, id DESC
        """
    )
    fun buscar(texto: String): Flow<List<Factura>>

    /** COALESCE evita el null cuando no hay ninguna fila en el rango. */
    @Query("SELECT COALESCE(SUM(total), 0.0) FROM facturas WHERE fechaEmision BETWEEN :desde AND :hasta")
    fun observarTotalEntre(desde: Long, hasta: Long): Flow<Double>

    /**
     * Devuelve lista y no un objeto suelto a propósito: si la factura se borra
     * con la pantalla de detalle abierta, llega una lista vacía en vez de null.
     */
    @Transaction
    @Query("SELECT * FROM facturas WHERE id = :id")
    fun observarConLineas(id: Long): Flow<List<FacturaConLineas>>

    @Query("SELECT * FROM facturas WHERE id = :id")
    suspend fun obtenerPorId(id: Long): Factura?

    @Insert
    suspend fun insertarFactura(factura: Factura): Long

    @Insert
    suspend fun insertarLineas(lineas: List<LineaFactura>)

    @Query("DELETE FROM lineas WHERE facturaId = :facturaId")
    suspend fun borrarLineasDe(facturaId: Long)

    /**
     * Inserta la factura, recoge el id que le asigna SQLite y lo propaga a las
     * líneas. @Transaction garantiza que entra todo o no entra nada.
     */
    @Transaction
    suspend fun guardarConLineas(factura: Factura, lineas: List<LineaFactura>): Long {
        val nuevoId = insertarFactura(factura)
        if (lineas.isNotEmpty()) {
            insertarLineas(lineas.map { it.copy(id = 0, facturaId = nuevoId) })
        }
        return nuevoId
    }

    /** Sustituye las líneas por completo; es más simple que diferenciar altas y bajas. */
    @Transaction
    suspend fun actualizarConLineas(factura: Factura, lineas: List<LineaFactura>) {
        actualizar(factura)
        borrarLineasDe(factura.id)
        if (lineas.isNotEmpty()) {
            insertarLineas(lineas.map { it.copy(id = 0, facturaId = factura.id) })
        }
    }

    @Update
    suspend fun actualizar(factura: Factura)

    @Delete
    suspend fun borrar(factura: Factura)
}
