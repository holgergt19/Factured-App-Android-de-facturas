package com.example.factured.data

import androidx.room.migration.Migration

/**
 * Migraciones de la base de datos.
 *
 * A partir de aquí la app guarda facturas reales, así que ya NO se usa
 * `fallbackToDestructiveMigration()`. Eso significa que si cambias una entidad
 * sin añadir su migración, la app reventará al arrancar en vez de borrarte los
 * datos en silencio. Es incómodo, pero es lo correcto: te enteras en el acto y
 * con la base intacta.
 *
 * RECETA para cada cambio de esquema:
 *
 *   1. Modifica la entidad (p. ej. añadir `val moneda: String = "EUR"`).
 *   2. Sube `version` en AppDatabase (2 -> 3).
 *   3. Escribe la migración aquí y añádela a MIGRACIONES.
 *
 *   val MIGRACION_2_3 = object : Migration(2, 3) {
 *       override fun migrate(db: SupportSQLiteDatabase) {
 *           db.execSQL(
 *               "ALTER TABLE facturas ADD COLUMN moneda TEXT NOT NULL DEFAULT 'EUR'"
 *           )
 *       }
 *   }
 *
 * SQLite solo sabe hacer ADD COLUMN. Para renombrar o eliminar una columna hay
 * que crear la tabla nueva, copiar con INSERT INTO ... SELECT, borrar la vieja
 * y renombrar. Es tedioso pero mecánico.
 *
 * La carpeta `app/schemas/` guarda el JSON de cada versión y es lo que permite
 * comprobar que una migración deja la base exactamente como Room espera.
 * Ese JSON se sube a Git.
 */
val MIGRACIONES: Array<Migration> = arrayOf(
    // Todavía ninguna: la versión actual es la 2 y es la primera con datos reales.
)
