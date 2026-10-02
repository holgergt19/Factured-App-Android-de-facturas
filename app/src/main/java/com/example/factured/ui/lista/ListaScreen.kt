package com.example.factured.ui.lista

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.factured.data.Factura
import com.example.factured.ui.comun.FotoTicket
import com.example.factured.util.claveMes
import com.example.factured.util.etiquetaMes
import com.example.factured.util.formatearFecha
import com.example.factured.util.formatearImporte
import com.example.factured.viewmodel.GastosViewModel

/**
 * Pantalla inicial: total del mes, buscador y todas las facturas agrupadas
 * por mes. Sin categorías: una sola sección continua.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaScreen(
    vm: GastosViewModel,
    onAbrirFactura: (Long) -> Unit,
    onNuevaFactura: () -> Unit,
    modifier: Modifier = Modifier
) {
    // collectAsState convierte el StateFlow en algo que Compose observa: cuando
    // Room emite, la pantalla se recompone sola.
    val facturas by vm.facturas.collectAsState()
    val totalMes by vm.totalMesActual.collectAsState()
    val busqueda by vm.busqueda.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Facturas") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNuevaFactura,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Escanear") }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {

            CabeceraTotal(totalMes)

            OutlinedTextField(
                value = busqueda,
                onValueChange = vm::cambiarBusqueda,
                placeholder = { Text("Buscar comercio o artículo") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (busqueda.isNotEmpty()) {
                        IconButton(onClick = { vm.cambiarBusqueda("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (facturas.isEmpty()) {
                EstadoVacio(hayBusqueda = busqueda.isNotBlank())
            } else {
                ListaAgrupada(facturas = facturas, onAbrirFactura = onAbrirFactura)
            }
        }
    }
}

@Composable
private fun CabeceraTotal(total: Double) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "Gastado este mes",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = formatearImporte(total),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ListaAgrupada(
    facturas: List<Factura>,
    onAbrirFactura: (Long) -> Unit
) {
    // Room ya las devuelve por fecha descendente, así que agrupar conserva el
    // orden y cada grupo sale con su mes correcto.
    val grupos = facturas.groupBy { claveMes(it.fechaEmision) }
    val anchoMiniaturaPx = with(LocalDensity.current) { 52.dp.roundToPx() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        grupos.forEach { (_, delMes) ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = etiquetaMes(delMes.first().fechaEmision),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatearImporte(delMes.sumOf { it.total }),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            items(items = delMes, key = { it.id }) { factura ->
                FilaFactura(
                    factura = factura,
                    anchoMiniaturaPx = anchoMiniaturaPx,
                    onClick = { onAbrirFactura(factura.id) }
                )
            }
        }
    }
}

@Composable
private fun FilaFactura(
    factura: Factura,
    anchoMiniaturaPx: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                if (factura.rutaFoto != null) {
                    FotoTicket(
                        ruta = factura.rutaFoto,
                        anchoObjetivoPx = anchoMiniaturaPx,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = factura.comercio.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = factura.comercio,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatearFecha(factura.fechaEmision),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = formatearImporte(factura.total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EstadoVacio(hayBusqueda: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hayBusqueda) {
                    "Ninguna factura coincide con la búsqueda."
                } else {
                    "Todavía no hay facturas."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hayBusqueda) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Pulsa Escanear y hazle una foto a un ticket.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}
