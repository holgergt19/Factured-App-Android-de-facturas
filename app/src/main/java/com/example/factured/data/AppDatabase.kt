package com.example.factured.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Punto único de acceso a SQLite.
 *
 * exportSchema = true escribe el esquema de cada versión en `app/schemas/`.
 * Sin ese registro no se pueden escribir migraciones con garantías, porque no
 * hay forma de comparar cómo era la tabla antes y cómo queda después.
 *
 * Ya no hay `fallbackToDestructiveMigration()`: a partir de ahora los datos son
 * reales y borrarlos en silencio ante un descuadre de esquema sería lo peor que
 * podría hacer la app. Si falta una migración, que falle y se note.
 */
@Database(
    entities = [Factura::class, LineaFactura::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun facturaDao(): FacturaDao

    companion object {
        @Volatile
        private var INSTANCIA: AppDatabase? = null

        fun obtener(context: Context): AppDatabase =
            INSTANCIA ?: synchronized(this) {
                INSTANCIA ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gastos.db"
                )
                    .addMigrations(*MIGRACIONES)
                    .build()
                    .also { INSTANCIA = it }
            }
    }
}
