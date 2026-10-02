package com.example.factured.ui.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.factured.data.FacturaConLineas
import com.example.factured.data.LineaFactura
import com.example.factured.ui.comun.FotoTicket
import com.example.factured.util.formatearFecha
import com.example.factured.util.formatearImporte
import com.example.factured.viewmodel.GastosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleScreen(
    facturaId: Long,
    vm: GastosViewModel,
    onVolver: () -> Unit,
    onEditar: (FacturaConLineas) -> Unit,
    modifier: Modifier = Modifier
) {
    // remember(facturaId): sin esto se crearía un StateFlow nuevo en cada
    // recomposición y la pantalla parpadearía sin parar.
    val flujo = remember(facturaId) { vm.observarDetalle(facturaId) }
    val detalle by flujo.collectAsState()

    var confirmarBorrado by remember { mutableStateOf(false) }
    val anchoFotoPx = with(LocalDensity.current) { 400.dp.roundToPx() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(detalle?.factura?.comercio ?: "Detalle") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    detalle?.let { datos ->
                        IconButton(onClick = { onEditar(datos) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = { confirmarBorrado = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->

        val actual = detalle

        if (actual == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                FotoTicket(
                    ruta = actual.factura.rutaFoto,
                    anchoObjetivoPx = anchoFotoPx,
                    escala = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = formatearImporte(actual.factura.total),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Campo("Comercio", actual.factura.comercio)
            Campo("Fecha del ticket", formatearFecha(actual.factura.fechaEmision))
            Campo("Registrada el", formatearFecha(actual.factura.fechaRegistro))
            actual.factura.notas?.let { Campo("Notas", it) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Artículos (${actual.lineas.size})",
                style = MaterialTheme.typography.titleMedium
            )

            if (actual.lineas.isEmpty()) {
                Text(
                    text = "No se guardaron artículos para esta factura.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                actual.lineas.forEach { linea -> FilaLinea(linea) }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmarBorrado) {
        val actual = detalle
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text("¿Eliminar esta factura?") },
            text = { Text("Se borrarán también sus artículos y la foto. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarBorrado = false
                    if (actual != null) {
                        vm.borrar(actual.factura)
                        onVolver()
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun Campo(etiqueta: String, valor: String) {
    Column {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = valor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FilaLinea(linea: LineaFactura) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = linea.descripcion,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = formatearImporte(linea.importe),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
