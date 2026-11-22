package cl.induztek.app

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import cl.induztek.app.data.BorradorFormularioEntity
import cl.induztek.app.data.ClienteEntity
import cl.induztek.app.data.InduztekDatabase
import cl.induztek.app.model.ResultadoPrueba
import cl.induztek.app.model.TipoEquipo
import cl.induztek.app.model.TipoPrueba
import cl.induztek.app.model.UsuariosDemo
import cl.induztek.app.viewmodel.FormularioEstado
import cl.induztek.app.viewmodel.PerfilViewModel
import cl.induztek.app.viewmodel.RegistroViewModel
import cl.induztek.app.viewmodel.TrabajoViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ViewModelsConRoomTest {
    private val contexto: Application get() = RuntimeEnvironment.getApplication()
    private val tecnico = UsuariosDemo.todos.first { it.id == "tecnico1" }

    private fun esperar(condicion: () -> Boolean) {
        val limite = System.nanoTime() + 5_000_000_000L
        do {
            shadowOf(Looper.getMainLooper()).idle()
            if (condicion()) return
            Thread.sleep(10)
        } while (System.nanoTime() < limite)
        fail("El ViewModel no alcanzó el estado esperado")
    }

    @Test fun registroDeAislacionValidaCalculaYPersiste() {
        val store = ViewModelStore()
        val vm = RegistroViewModel(contexto)
        store.put("registro", vm)
        try {
            vm.guardar()
            assertEquals("Revisa los campos indicados", vm.error)
            assertFalse(vm.guardando)
            vm.cambiarLectura1("4.9e-324")
            vm.cambiarLectura10("1.0e308")
            vm.calcular()
            assertNull(vm.evaluacion)
            assertTrue(vm.error.orEmpty().contains("fuera de rango"))
            vm.cambiarLectura1("100,5")
            vm.cambiarLectura10("201")
            vm.calcular()
            assertEquals(ResultadoPrueba.APROBADO, vm.evaluacion?.resultado)
            assertEquals(2.0, vm.evaluacion!!.indice, 0.0)
            vm.guardar()
            vm.guardar()
            esperar { !vm.guardando && vm.historial.any { it.lectura1 == 100.5 } }
            assertEquals("Medición guardada correctamente", vm.mensaje)
            assertEquals(1, vm.historial.count { it.lectura1 == 100.5 })
            vm.cambiarLectura10("125")
            assertNull(vm.evaluacion)
            assertNull(vm.mensaje)
        } finally {
            store.clear()
        }
    }

    @Test fun trabajoRestauraBorradoresValidaYActualizaCatalogos() {
        val dao = InduztekDatabase.obtener(contexto).mvpDao()
        runBlocking { dao.guardar(BorradorFormularioEntity("${tecnico.id}:prueba-vm", """{"nombre":"Borrador anterior"}""")) }
        val store = ViewModelStore()
        val usuariosPendientes = mutableListOf<String>()
        val vm = TrabajoViewModel(contexto, tecnico) { _, usuarioId -> usuariosPendientes += usuarioId }
        store.put("trabajo", vm)
        try {
            esperar { !vm.cargando }
            assertNull(vm.error)
            val f = vm.formulario("prueba-vm", mapOf("nombre" to "Inicial"))
            assertEquals("Borrador anterior", f["nombre"])
            assertSame(f, vm.formulario("prueba-vm", emptyMap()))
            val cliente = ClienteEntity("cliente-vm", "Cliente ViewModel", "12.345.678-5")
            val nuevo = FormularioEstado(mapOf("nombre" to cliente.nombre, "rut" to cliente.rut)) {}
            assertTrue(vm.validarCatalogo("cliente", "", nuevo))
            val duplicado = FormularioEstado(mapOf("nombre" to "Duplicado", "rut" to "76.482.915-8")) {}
            assertFalse(vm.validarCatalogo("cliente", "", duplicado))
            assertTrue(duplicado.errores.containsKey("rut"))
            val equivalente = FormularioEstado(mapOf("instalacion" to "lampa", "tag" to "tr-01", "tipo" to "TRANSFORMADOR")) {}
            assertFalse(vm.validarCatalogo("equipo", "", equivalente))
            assertTrue(equivalente.errores.containsKey("tag"))
            val instrumento = FormularioEstado(mapOf("serie" to "MIT-0045821")) {}
            assertFalse(vm.validarCatalogo("instrumento", "", instrumento))
            assertTrue(instrumento.errores.containsKey("serie"))
            assertFalse(vm.validarCatalogo("instalacion", "", FormularioEstado(emptyMap()) {}))
            val visita = FormularioEstado(mapOf("ot" to "OT-2026-1187")) {}
            assertFalse(vm.validarVisita(visita))
            assertTrue(visita.errores.getValue("ot").contains("registrada"))

            val medicion = FormularioEstado(mapOf("realizada" to "true", "unidad" to "GΩ", "tension" to "5",
                "lectura1" to "1,0", "lectura10" to "2,1", "instrumento" to "meg5")) {}
            val fecha = LocalDate.of(2026, 11, 19).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            assertTrue(vm.validarPrueba(medicion, listOf("instrumento"), fecha))
            assertEquals(ResultadoPrueba.APROBADO, vm.previsualizar(TipoEquipo.TRANSFORMADOR, TipoPrueba.AISLACION, medicion, null)?.resultado)
            medicion.cambiar("instrumento", "meg10")
            assertFalse(vm.validarPrueba(medicion, listOf("instrumento"), fecha))
            medicion.cambiar("lectura1", "")
            assertNull(vm.previsualizar(TipoEquipo.TRANSFORMADOR, TipoPrueba.AISLACION, medicion, null))
            medicion.cambiar("realizada", "false")
            assertNull(vm.previsualizar(TipoEquipo.TRANSFORMADOR, TipoPrueba.AISLACION, medicion, null))

            f.cambiar("nombre", "Borrador actualizado")
            esperar { runBlocking { dao.borradores() }.any { "Borrador actualizado" in it.contenido } }
            vm.guardarBorrador("prueba-vm", emptyMap())
            assertNotSame(f, vm.formulario("prueba-vm", emptyMap()))
            esperar { runBlocking { dao.borradores() }.any { it.id.endsWith(":prueba-vm") && it.contenido == "{}" } }

            vm.accion("Cliente guardado") { cliente(tecnico, cliente) }
            esperar { !vm.ocupado }
            assertEquals("Cliente guardado", vm.mensaje)
            assertEquals(listOf(tecnico.id, tecnico.id), usuariosPendientes)
            assertTrue(vm.estado.clientes.any { it.id == cliente.id })
            vm.accion("No se muestra") { cliente(tecnico, cliente.copy(id = "duplicado")) }
            esperar { !vm.ocupado }
            assertTrue(vm.error.orEmpty().contains("Ya existe un cliente"))

            val lector = TrabajoViewModel(contexto, UsuariosDemo.todos.first { it.id == "cliente1" }) { _, _ -> }
            store.put("lector", lector)
            esperar { !lector.cargando }
            assertEquals(listOf("cliente-pacifico"), lector.estado.clientes.map { it.id })
            assertTrue(lector.estado.instalaciones.all { it.clienteId == "cliente-pacifico" })
        } finally {
            store.clear()
        }
    }

    @Test fun perfilConservaSuFotoCuandoElUsuarioCancela() {
        val store = ViewModelStore()
        val vm = PerfilViewModel(contexto, "perfil-prueba")
        store.put("perfil", vm)
        try {
            esperar { !vm.ocupado }
            vm.permisoCamaraDenegado()
            assertTrue(vm.error.orEmpty().contains("denegado"))
            val imagen = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            vm.desdeCamara(imagen)
            esperar { !vm.ocupado }
            assertEquals("Fotografía guardada", vm.mensaje)
            assertNotNull(vm.foto)
            vm.desdeCamara(null)
            assertTrue(vm.mensaje.orEmpty().contains("Captura cancelada"))
            vm.desdeGaleria(null)
            assertTrue(vm.mensaje.orEmpty().contains("Selección cancelada"))
            assertSame(imagen, vm.foto)
            vm.noDisponible("la galería")
            assertTrue(vm.error.orEmpty().contains("la galería"))
        } finally {
            store.clear()
        }
    }
}
