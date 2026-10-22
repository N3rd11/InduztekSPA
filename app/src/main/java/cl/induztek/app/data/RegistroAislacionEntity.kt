
package cl.induztek.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "registros_aislacion")
data class RegistroAislacionEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val equipoTag: String,
    val instalacion: String,

    val lectura1: Double,
    val lectura10: Double,

    val indice: Double,
    val resultado: String,

    val fechaHora: Long = System.currentTimeMillis()
)
