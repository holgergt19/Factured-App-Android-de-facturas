package com.example.factured.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * dynamicColor está desactivado a propósito.
 *
 * Con el color dinámico de Android 12+, la app adopta el fondo de pantalla del
 * usuario y se ve distinta en cada móvil. Para una app de gastos interesa que
 * el verde signifique siempre lo mismo, así que se fija la paleta.
 */

private val EsquemaOscuro = darkColorScheme(
    primary = VerdeClaro,
    onPrimary = Color(0xFF00352F),
    primaryContainer = VerdeProfundo,
    onPrimaryContainer = VerdeSuave,
    secondary = VerdeMedio,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF10403B),
    onSecondaryContainer = VerdeSuave,
    background = GrisCarbon,
    onBackground = Color(0xFFE2E6E5),
    surface = GrisCarbon,
    onSurface = Color(0xFFE2E6E5),
    surfaceVariant = Color(0xFF2A3436),
    onSurfaceVariant = Color(0xFFB4BFBD),
    error = Color(0xFFEF9A9A),
    tertiary = AmbarAviso
)

private val EsquemaClaro = lightColorScheme(
    primary = VerdeProfundo,
    onPrimary = Color.White,
    primaryContainer = VerdeSuave,
    onPrimaryContainer = Color(0xFF00251F),
    secondary = VerdeMedio,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEDE8),
    onSecondaryContainer = Color(0xFF00332C),
    background = GrisPapel,
    onBackground = AzulPizarra,
    surface = Color.White,
    onSurface = AzulPizarra,
    surfaceVariant = Color(0xFFE6ECEA),
    onSurfaceVariant = Color(0xFF4A5658),
    error = RojoBorrar,
    tertiary = AmbarAviso
)

@Composable
fun FacturedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) EsquemaOscuro else EsquemaClaro,
        typography = Typography,
        content = content
    )
}
