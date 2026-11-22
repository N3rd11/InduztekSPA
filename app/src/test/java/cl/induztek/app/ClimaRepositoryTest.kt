package cl.induztek.app

import cl.induztek.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ClimaRepositoryTest {
    private class Lugares(var resultado: List<LugarClima>? = null) : GeocodificacionApi {
        var consulta: String? = null
        override suspend fun buscar(nombre: String): LugaresClima {
            consulta = nombre
            return LugaresClima(resultado)
        }
    }

    private class Pronostico(var actual: ClimaActual? = null) : PronosticoApi {
        var coordenadas: Pair<Double, Double>? = null
        var sinConexion = false
        override suspend fun consultar(latitud: Double, longitud: Double): PronosticoClima {
            if (sinConexion) throw IOException("Sin internet")
            coordenadas = latitud to longitud
            return PronosticoClima(actual)
        }
    }

    private val lampa = LugarClima("Lampa", -33.2863, -70.87561)

    @Test fun consultaPorComunaDevuelveDatosDeReferencia() = runBlocking {
        val lugares = Lugares(listOf(lampa))
        val pronostico = Pronostico(ClimaActual("2026-11-19T10:00", 19.5, 48.0))
        val resultado = ClimaRepository(lugares, pronostico).consultar(" Lampa ")
        assertEquals("Lampa", lugares.consulta)
        assertEquals(lampa.latitude to lampa.longitude, pronostico.coordenadas)
        assertEquals(ClimaComuna("Lampa", "2026-11-19T10:00", 19.5, 48.0), resultado)
    }

    @Test fun omiteConsultasIncompletasYDescartaDatosInvalidos() = runBlocking {
        val lugares = Lugares()
        val pronostico = Pronostico()
        val repo = ClimaRepository(lugares, pronostico)
        assertNull(repo.consultar(" "))
        assertNull(lugares.consulta)
        assertNull(repo.consultar("Lampa"))
        lugares.resultado = listOf(lampa.copy(longitude = null))
        assertNull(repo.consultar("Lampa"))
        lugares.resultado = listOf(lampa.copy(latitude = null))
        assertNull(repo.consultar("Lampa"))
        assertNull(pronostico.coordenadas)
        lugares.resultado = listOf(lampa)
        assertNull(repo.consultar("Lampa"))
        for (actual in listOf(ClimaActual(null, 15.0, 30.0), ClimaActual("10:00", Double.NaN, 30.0),
            ClimaActual("10:00", 15.0, 110.0))) {
            pronostico.actual = actual
            assertNull(repo.consultar("Lampa"))
        }
    }

    @Test fun elFalloDeRedSeInformaAlConsumidor() = runBlocking {
        val repo = ClimaRepository(Lugares(listOf(lampa)), Pronostico().apply { sinConexion = true })
        try {
            repo.consultar("Lampa")
            fail("La interfaz debe poder distinguir un fallo de red")
        } catch (e: IOException) {
            assertEquals("Sin internet", e.message)
        }
    }

    @Test fun consultaPorUbicacionOmiteGeocodificacionYValidaCoordenadas() = runBlocking {
        val lugares = Lugares()
        val pronostico = Pronostico(ClimaActual("2026-11-22T12:00", 20.0, 50.0))
        val repo = ClimaRepository(lugares, pronostico)
        assertNull(repo.consultarCoordenadas(Double.NaN, -70.58))
        assertNull(repo.consultarCoordenadas(-33.61, 181.0))
        assertNull(pronostico.coordenadas)
        val clima = repo.consultarCoordenadas(-33.61, -70.58)
        assertEquals("Ubicación actual", clima?.localidad)
        assertEquals(-33.61 to -70.58, pronostico.coordenadas)
        assertNull(lugares.consulta)
    }
}
