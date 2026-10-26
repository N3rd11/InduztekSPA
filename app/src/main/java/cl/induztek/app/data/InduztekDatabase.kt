package cl.induztek.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RegistroAislacionEntity::class, ClienteEntity::class, InstalacionEntity::class,
        EquipoEntity::class, InstrumentoEntity::class, CalibracionEntity::class, VisitaEntity::class,
        ParticipanteEntity::class, ProtocoloEntity::class, PruebaEntity::class, MedicionEntity::class,
        HistorialEstadoEntity::class, PendienteSyncEntity::class, BorradorFormularioEntity::class],
    version = 2,
    exportSchema = true
)
abstract class InduztekDatabase : RoomDatabase() {

    abstract fun registroDao(): RegistroAislacionDao
    abstract fun mvpDao(): MvpDao

    companion object {

        @Volatile
        private var INSTANCE: InduztekDatabase? = null

        fun obtener(context: Context): InduztekDatabase {
            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InduztekDatabase::class.java,
                    "induztek_local.db"
                ).addMigrations(MigracionMvp).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}
