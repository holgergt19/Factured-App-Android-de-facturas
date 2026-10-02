package com.example.factured

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.factured.data.AppDatabase
import com.example.factured.data.FacturaConLineas
import com.example.factured.data.FacturaRepository
import com.example.factured.ocr.AnalizadorTicket
import com.example.factured.ocr.ParserTicket
import com.example.factured.ocr.ResultadoOcr
import com.example.factured.ocr.TextoTicket
import com.example.factured.ui.camara.CamaraScreen
import com.example.factured.ui.confirmar.ConfirmarScreen
import com.example.factured.ui.detalle.DetalleScreen
import com.example.factured.ui.lista.ListaScreen
import com.example.factured.ui.theme.FacturedTheme
import com.example.factured.util.GuardadoImagen
import com.example.factured.viewmodel.BorradorFactura
import com.example.factured.viewmodel.GastosViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Cableado manual de dependencias. Para una app de un solo usuario
        // esto basta; no hace falta Hilt ni ningún inyector.
        val repositorio = FacturaRepository(
            AppDatabase.obtener(applicationContext).facturaDao()
        )

        setContent {
            FacturedTheme {
                AppFactured(repositorio)
            }
        }
    }
}

/**
 * Las cuatro pantallas de la app.
 *
 * Navegación a mano en lugar de navigation-compose: con cuatro destinos y un
 * flujo lineal (lista -> cámara -> procesando -> confirmar -> lista), la
 * librería añadiría serialización de argumentos y un grafo para algo que aquí
 * se resuelve con una variable de estado.
 */
private sealed interface Pantalla {
    data object Lista : Pantalla
    data object Camara : Pantalla
    data class Procesando(val rutaFoto: String) : Pantalla
    data class Confirmar(val borrador: BorradorFactura) : Pantalla
    data class Detalle(val id: Long) : Pantalla
}

@Composable
private fun AppFactured(repositorio: FacturaRepository) {

    val context = LocalContext.current
    val vm: GastosViewModel = viewModel(factory = GastosViewModel.Factory(repositorio))

    var pantalla by remember { mutableStateOf<Pantalla>(Pantalla.Lista) }

    when (val actual = pantalla) {

        Pantalla.Lista -> ListaScreen(
            vm = vm,
            onAbrirFactura = { id -> pantalla = Pantalla.Detalle(id) },
            onNuevaFactura = { pantalla = Pantalla.Camara },
            modifier = Modifier.fillMaxSize()
        )

        Pantalla.Camara -> {
            BackHandler { pantalla = Pantalla.Lista }
            CamaraScreen(
                onCapturada = { ruta -> pantalla = Pantalla.Procesando(ruta) },
                onCancelar = { pantalla = Pantalla.Lista },
                modifier = Modifier.fillMaxSize()
            )
        }

        is Pantalla.Procesando -> {
            // El OCR tarda entre medio segundo y tres, según el tamaño de la
            // foto. Se hace aquí, fuera de la cámara, para poder soltar el
            // hardware cuanto antes.
            LaunchedEffect(actual.rutaFoto) {
                val resultado = runCatching {
                    val ticket: TextoTicket = AnalizadorTicket.leer(context, actual.rutaFoto)
                    ParserTicket.analizar(ticket)
                }.getOrElse {
                    // Si el OCR falla, se sigue adelante con todo vacío: es
                    // preferible teclear los datos que perder la foto.
                    ResultadoOcr(
                        comercio = null,
                        fechaEmision = null,
                        total = null,
                        lineas = emptyList(),
                        textoCrudo = ""
                    )
                }

                // Comprimir DESPUÉS del OCR: se dispara a máxima calidad para
                // que el reconocimiento tenga detalle, y se archiva ligera.
                GuardadoImagen.comprimir(actual.rutaFoto)

                pantalla = Pantalla.Confirmar(
                    BorradorFactura.desdeOcr(actual.rutaFoto, resultado)
                )
            }

            BackHandler { /* bloqueado mientras procesa */ }
            PantallaProcesando(modifier = Modifier.fillMaxSize())
        }

        is Pantalla.Confirmar -> {
            val descartar = {
                // Solo se borra la foto si la factura aún no existía; al editar
                // una guardada, la imagen debe sobrevivir al "descartar".
                if (actual.borrador.id == null) {
                    GuardadoImagen.borrar(actual.borrador.rutaFoto)
                }
                pantalla = Pantalla.Lista
            }

            BackHandler { descartar() }

            ConfirmarScreen(
                borradorInicial = actual.borrador,
                onGuardar = { borrador ->
                    vm.guardar(borrador) { correcto ->
                        if (correcto) pantalla = Pantalla.Lista
                    }
                },
                onDescartar = descartar,
                modifier = Modifier.fillMaxSize()
            )
        }

        is Pantalla.Detalle -> {
            BackHandler { pantalla = Pantalla.Lista }
            DetalleScreen(
                facturaId = actual.id,
                vm = vm,
                onVolver = { pantalla = Pantalla.Lista },
                onEditar = { datos: FacturaConLineas ->
                    pantalla = Pantalla.Confirmar(BorradorFactura.desdeFactura(datos))
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PantallaProcesando(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = "Leyendo el ticket...",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}
