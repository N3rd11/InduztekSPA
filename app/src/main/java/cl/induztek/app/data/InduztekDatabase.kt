package cl.induztek.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RegistroAislacionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class InduztekDatabase : RoomDatabase() {

    abstract fun registroDao(): RegistroAislacionDao

    companion object {

        @Volatile
        private var INSTANCE: InduztekDatabase? = null

        fun obtener(context: Context): InduztekDatabase {
            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InduztekDatabase::class.java,
                    "induztek_local.db"
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}
