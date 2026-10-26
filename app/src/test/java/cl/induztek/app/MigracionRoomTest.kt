package cl.induztek.app

import androidx.room.Room
import cl.induztek.app.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MigracionRoomTest {
    @Test fun migracionUnoADosConservaMedicionesYValidaElEsquema() {
        val contexto = RuntimeEnvironment.getApplication()
        val nombre = "migracion-${System.nanoTime()}.db"
        contexto.openOrCreateDatabase(nombre, 0, null).use { anterior ->
            anterior.execSQL("CREATE TABLE registros_aislacion (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, equipoTag TEXT NOT NULL, instalacion TEXT NOT NULL, lectura1 REAL NOT NULL, lectura10 REAL NOT NULL, indice REAL NOT NULL, resultado TEXT NOT NULL, fechaHora INTEGER NOT NULL)")
            anterior.execSQL("INSERT INTO registros_aislacion VALUES (42,'TR-01','Planta Lampa',6100,10980,1.8,'OBSERVADO',1790000000000)")
            anterior.version = 1
        }
        val db = Room.databaseBuilder(contexto, InduztekDatabase::class.java, nombre)
            .addMigrations(MigracionMvp).allowMainThreadQueries().build()
        try {
            db.openHelper.writableDatabase.query("SELECT * FROM registros_aislacion WHERE id=42").use {
                assertTrue(it.moveToFirst())
                assertEquals(6100.0, it.getDouble(it.getColumnIndexOrThrow("lectura1")), 0.0)
                assertEquals("OBSERVADO", it.getString(it.getColumnIndexOrThrow("resultado")))
            }
            db.openHelper.writableDatabase.query("SELECT count(*) FROM protocolos").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        } finally { db.close() }
    }
}
