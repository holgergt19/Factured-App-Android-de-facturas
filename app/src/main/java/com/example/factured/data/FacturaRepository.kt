package com.example.factured.data

import kotlinx.coroutines.flow.Flow

/**
 * Capa intermedia entre el ViewModel y Room.
 *
 * Hoy es un envoltorio fino. Su valor está en que cuando aparezca otra fuente
 * de datos (exportación, respaldo, sincronización) se resuelve aquí y el
 * ViewModel no se entera.
 */
class FacturaRepository(private val dao: FacturaDao) {

    fun observarTodas(): Flow<List<Factura>> = dao.observarTodas()

    fun buscar(texto: String): Flow<List<Factura>> = dao.buscar(texto)

    fun observarTotalEntre(desde: Long, hasta: Long): Flow<Double> =
        dao.observarTotalEntre(desde, hasta)

    fun observarConLineas(id: Long): Flow<List<FacturaConLineas>> =
        dao.observarConLineas(id)

    suspend fun obtenerPorId(id: Long): Factura? = dao.obtenerPorId(id)

    suspend fun guardar(factura: Factura, lineas: List<LineaFactura>): Long =
        dao.guardarConLineas(factura, lineas)

    suspend fun actualizar(factura: Factura, lineas: List<LineaFactura>) =
        dao.actualizarConLineas(factura, lineas)

    suspend fun borrar(factura: Factura) = dao.borrar(factura)
}
