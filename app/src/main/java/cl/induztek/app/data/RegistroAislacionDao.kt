
package cl.induztek.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RegistroAislacionDao {

    @Insert
    suspend fun insertar(
        registro: RegistroAislacionEntity
    )

    @Query(
        "SELECT * FROM registros_aislacion ORDER BY fechaHora DESC"
    )
    fun obtenerTodos(): Flow<List<RegistroAislacionEntity>>
}
