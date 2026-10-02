package com.example.factured.ui.confirmar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.factured.ui.comun.FotoTicket
import com.example.factured.util.fechaLocalAUtc
import com.example.factured.util.formatearFecha
import com.example.factured.util.formatearImporte
import com.example.factured.util.leerImporte
import com.example.factured.util.utcAFechaLocal
import com.example.factured.viewmodel.BorradorFactura
import com.example.factured.viewmodel.LineaBorrador

/**
 * La pantalla más importante de la app.
 *
 * Regla de diseño del checkpoint 1: el OCR propone, el usuario dispone. Nada
 * se guarda sin pasar por aquí, y todos los campos son editables.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmarScreen(
    borradorInicial: BorradorFactura,
    onGuardar: (BorradorFactura) -> Unit,
    onDescartar: () -> Unit,
    modifier: Modifier = Modifier
) {
    // El borrador inicial solo se usa como semilla; a partir de ahí el estado
    // vive aquí y el usuario lo modifica libremente.
    var borrador by remember(borradorInicial) { mutableStateOf(borradorInicial) }
    var mostrarCalendario by remember { mutableStateOf(false) }
    var intentoGuardar by remember { mutableStateOf(false) }
    var verTextoOcr by remember { mutableStateOf(false) }

    val totalValido = leerImporte(borrador.total)?.let { it > 0.0 } == true
    val esEdicion = borrador.id != null

    val sumaLineas = borrador.lineas.sumOf { leerImporte(it.importe) ?: 0.0 }
    val totalNumerico = leerImporte(borrador.total) ?: 0.0
    // Tolerancia de 1 céntimo: los redondeos del ticket no son un error.
    val descuadre = borrador.lineas.isNotEmpty() &&
        kotlin.math.abs(sumaLineas - totalNumerico) > 0.01

    val anchoFotoPx = with(LocalDensity.current) { 320.dp.roundToPx() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (esEdicion) "Editar factura" else "Confirmar datos") },
                navigationIcon = {
                    IconButton(onClick = onDescartar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Descartar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            if (borrador.rutaFoto != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    FotoTicket(
                        ruta = borrador.rutaFoto,
                        anchoObjetivoPx = anchoFotoPx,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (!borrador.totalDetectado || !borrador.fechaDetectada) {
                AvisoOcr(
                    faltaTotal = !borrador.totalDetectado,
                    faltaFecha = !borrador.fechaDetectada
                )
            }

            OutlinedTextField(
                value = borrador.total,
                onValueChange = { borrador = borrador.copy(total = it) },
                label = { Text("Total") },
                suffix = { Text("€") },
                singleLine = true,
                isError = intentoGuardar && !totalValido,
                supportingText = if (intentoGuardar && !totalValido) {
                    { Text("Escribe un importe mayor que cero") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = formatearFecha(borrador.fechaEmision),
                onValueChange = { },
                label = { Text("Fecha del ticket") },
                readOnly = true,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    // El campo es de solo lectura, así que el clic tiene que
                    // capturarse fuera del TextField para abrir el calendario.
                    .clickable { mostrarCalendario = true }
            )
            TextButton(onClick = { mostrarCalendario = true }) {
                Text("Cambiar fecha")
            }

            OutlinedTextField(
                value = borrador.comercio,
                onValueChange = { borrador = borrador.copy(comercio = it) },
                label = { Text("Comercio") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = borrador.notas,
                onValueChange = { borrador = borrador.copy(notas = it) },
                label = { Text("Notas (opcional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(Modifier.padding(top = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Artículos (${borrador.lineas.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatearImporte(sumaLineas),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (descuadre) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (descuadre) {
                Text(
                    text = "La suma de los artículos no cuadra con el total. " +
                        "Puedes guardarla igual: el total es el que manda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            borrador.lineas.forEachIndexed { indice, linea ->
                FilaEditable(
                    linea = linea,
                    onCambio = { nueva ->
                        borrador = borrador.copy(
                            lineas = borrador.lineas.toMutableList().also { it[indice] = nueva }
                        )
                    },
                    onBorrar = {
                        borrador = borrador.copy(
                            lineas = borrador.lineas.filterIndexed { i, _ -> i != indice }
                        )
                    }
                )
            }

            OutlinedButton(
                onClick = {
                    borrador = borrador.copy(lineas = borrador.lineas + LineaBorrador())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Añadir artículo")
            }

            val textoOcr = borrador.textoOcrCrudo
            if (!textoOcr.isNullOrBlank()) {
                TextoOcrPlegable(
                    texto = textoOcr,
                    abierto = verTextoOcr,
                    onAlternar = { verTextoOcr = !verTextoOcr }
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    intentoGuardar = true
                    if (totalValido) onGuardar(borrador)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Guardar", style = MaterialTheme.typography.titleMedium)
            }

            TextButton(
                onClick = onDescartar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Descartar")
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (mostrarCalendario) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = fechaLocalAUtc(borrador.fechaEmision)
        )
        DatePickerDialog(
            onDismissRequest = { mostrarCalendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let {
                        borrador = borrador.copy(
                            fechaEmision = utcAFechaLocal(it),
                            fechaDetectada = true
                        )
                    }
                    mostrarCalendario = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarCalendario = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = estado)
        }
    }
}

@Composable
private fun AvisoOcr(faltaTotal: Boolean, faltaFecha: Boolean) {
    val que = when {
        faltaTotal && faltaFecha -> "el total ni la fecha"
        faltaTotal -> "el total"
        else -> "la fecha"
    }
    Surface(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "No se pudo leer $que con seguridad. Revísalo antes de guardar.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun FilaEditable(
    linea: LineaBorrador,
    onCambio: (LineaBorrador) -> Unit,
    onBorrar: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = linea.descripcion,
            onValueChange = { onCambio(linea.copy(descripcion = it)) },
            label = { Text("Artículo") },
            singleLine = true,
            modifier = Modifier.weight(1.6f)
        )
        OutlinedTextField(
            value = linea.importe,
            onValueChange = { onCambio(linea.copy(importe = it)) },
            label = { Text("€") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onBorrar, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Quitar artículo",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Muestra el texto tal cual lo leyó ML Kit.
 *
 * No es una florituras de depuración: cuando un ticket salga mal, esto es lo
 * único que dice si el fallo fue del OCR (leyó mal el papel) o del parser
 * (leyó bien pero interpretó mal). Sin verlo, afinar las reglas es adivinar.
 */
@Composable
private fun TextoOcrPlegable(
    texto: String,
    abierto: Boolean,
    onAlternar: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAlternar)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (abierto) Icons.Filled.KeyboardArrowUp
                else Icons.Filled.KeyboardArrowDown,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Texto detectado por el OCR",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (abierto) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = texto,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        }
    }
}
