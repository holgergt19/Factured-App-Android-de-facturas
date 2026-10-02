package com.example.factured.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.factured.data.Factura
import com.example.factured.data.FacturaConLineas
import com.example.factured.data.FacturaRepository
import com.example.factured.data.LineaFactura
import com.example.factured.util.GuardadoImagen
import com.example.factured.util.finDelMes
import com.example.factured.util.inicioDelMes
import com.example.factured.util.leerImporte
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class GastosViewModel(
    private val repo: FacturaRepository
) : ViewModel() {

    private val _busqueda = MutableStateFlow("")
    val busqueda: StateFlow<String> = _busqueda.asStateFlow()

    /**
     * flatMapLatest: cada vez que cambia el texto de búsqueda cancela la
     * consulta anterior y se suscribe a la nueva. Sin esto se acumularían
     * suscripciones a Room.
     */
    val facturas: StateFlow<List<Factura>> = _busqueda
        .flatMapLatest { texto ->
            if (texto.isBlank()) repo.observarTodas() else repo.buscar(texto.trim())
        }
        .stateIn(
            scope = viewModelScope,
            // Mantiene la suscripción 5 s tras irse la UI: al volver de la
            // cámara no se relanza la consulta desde cero.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Total del mes en curso. No le afecta la búsqueda, a propósito. */
    val totalMesActual: StateFlow<Double> =
        repo.observarTotalEntre(inicioDelMes(), finDelMes())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    fun cambiarBusqueda(texto: String) {
        _busqueda.value = texto
    }

    fun observarDetalle(id: Long): StateFlow<FacturaConLineas?> =
        repo.observarConLineas(id)
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Convierte el borrador en filas de base de datos.
     *
     * Avisa por callback de si salió bien: la pantalla necesita saberlo para
     * cerrarse solo cuando el guardado ha terminado de verdad.
     */
    fun guardar(borrador: BorradorFactura, alTerminar: (Boolean) -> Unit) {
        val total = leerImporte(borrador.total)
        if (total == null || total <= 0.0) {
            alTerminar(false)
            return
        }

        val lineas = borrador.lineas
            .filter { it.descripcion.isNotBlank() }
            .map { linea ->
                LineaFactura(
                    facturaId = borrador.id ?: 0,
                    descripcion = linea.descripcion.trim(),
                    importe = leerImporte(linea.importe) ?: 0.0
                )
            }

        viewModelScope.launch {
            val factura = borrador.aFactura(total)
            if (borrador.id == null) {
                repo.guardar(factura, lineas)
            } else {
                repo.actualizar(factura, lineas)
            }
            alTerminar(true)
        }
    }

    /** Borra la fila y también el JPEG, para no dejar basura ocupando sitio. */
    fun borrar(factura: Factura) {
        viewModelScope.launch {
            repo.borrar(factura)
            GuardadoImagen.borrar(factura.rutaFoto)
        }
    }

    /**
     * Fábrica manual. Hace falta porque el ViewModel recibe el repositorio en
     * el constructor y el sistema no sabe construirlo solo.
     */
    class Factory(private val repo: FacturaRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GastosViewModel::class.java)) {
                "ViewModel desconocido: ${modelClass.name}"
            }
            @Suppress("UNCHECKED_CAST")
            return GastosViewModel(repo) as T
        }
    }
}
